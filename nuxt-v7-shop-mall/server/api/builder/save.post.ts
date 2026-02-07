/**
 * 保存主题配置 API
 * POST /api/builder/save
 * 
 * 表结构：
 * PRIMARY KEY (`landing_page_type`, `spu_id`, `sub_domain_id`)
 * `landing_page_type` enum('LAND','CLOAK','BLACKLISTED')
 * 
 * 数据分离设计：
 * - theme_config: 页面布局、组件、样式（前端渲染 + 编辑器）
 * - variable_schema: 变量定义结构（仅编辑器）
 * - site_config: 站点配置值（前端渲染 + 编辑器）
 * - variable_values: 变量实际值（前端渲染 + 编辑器）
 */

import { getPool } from "../../utils/db";
import { clearProductCacheAllLanguages, clearCloakCacheAllLanguages, clearLandingConfigCache } from "../../cache/landing.cache";

// 允许的落地页类型
const VALID_LANDING_TYPES = ["LAND", "CLOAK", "BLACKLISTED"] as const;
type LandingPageType = typeof VALID_LANDING_TYPES[number];

interface VariableDefinition {
  key: string;
  defaultValue?: any;
}

interface SaveThemeRequest {
  subDomainId: string | number;
  spuId: string | number;
  landingType: string;
  landingPageProductId?: string | number | null;  // 可选：落地页产品 ID（用于 CLOAK 类型）
  // 分离的数据字段
  themeConfig: object;                        // 页面布局、组件、样式
  variableSchema?: VariableDefinition[];      // 变量定义结构
  siteConfig?: object;                        // 站点配置值
  variableValues?: Record<string, any>;       // 变量实际值
}

/**
 * 填充变量值的默认值
 * 如果 variableValues 中没有设置某个变量的值，则使用 variableSchema 中定义的 defaultValue
 */
function fillVariableDefaults(
  variableValues: Record<string, any> | undefined,
  variableSchema: VariableDefinition[] | undefined
): Record<string, any> {
  const result: Record<string, any> = { ...(variableValues || {}) };
  
  if (!variableSchema || !Array.isArray(variableSchema)) {
    return result;
  }
  
  for (const variable of variableSchema) {
    // 如果变量值未设置（undefined 或 null），则使用默认值
    if (result[variable.key] === undefined || result[variable.key] === null) {
      if (variable.defaultValue !== undefined) {
        result[variable.key] = variable.defaultValue;
      }
    }
  }
  
  return result;
}

export default defineEventHandler(async (event) => {
  const body = await readBody<SaveThemeRequest>(event);

  // 参数校验
  if (!body.subDomainId || !body.spuId || !body.landingType || !body.themeConfig) {
    throw createError({
      statusCode: 400,
      statusMessage: "Missing required fields: subDomainId, spuId, landingType, themeConfig",
    });
  }

  // 校验 landingType 枚举值
  if (!VALID_LANDING_TYPES.includes(body.landingType as LandingPageType)) {
    throw createError({
      statusCode: 400,
      statusMessage: `Invalid landingType: ${body.landingType}. Must be one of: ${VALID_LANDING_TYPES.join(", ")}`,
    });
  }

  // 转换为 bigint（数据库字段类型）
  const subDomainId = BigInt(body.subDomainId);
  const spuId = BigInt(body.spuId);
  const landingPageProductId = body.landingPageProductId ? BigInt(body.landingPageProductId) : null;

  // 确保 themeConfig 是有效的 JSON 对象
  if (typeof body.themeConfig !== "object" || body.themeConfig === null) {
    throw createError({
      statusCode: 400,
      statusMessage: "themeConfig must be a valid JSON object",
    });
  }

  const pool = getPool();

  // 填充变量默认值
  const filledVariableValues = fillVariableDefaults(body.variableValues, body.variableSchema);
  
  // 序列化各个 JSON 字段
  const themeConfigJson = JSON.stringify(body.themeConfig);
  const variableSchemaJson = body.variableSchema ? JSON.stringify(body.variableSchema) : "[]";
  const siteConfigJson = body.siteConfig ? JSON.stringify(body.siteConfig) : "{}";
  const variableValuesJson = JSON.stringify(filledVariableValues);

  try {
    // 使用 INSERT ... ON DUPLICATE KEY UPDATE 实现 upsert
    // 主键顺序：(landing_page_type, spu_id, sub_domain_id)
    const sql = `
      INSERT INTO t_sub_domain_spu_landing_pages 
        (landing_page_type, spu_id, sub_domain_id, landing_page_product_id, 
         theme_config, variable_schema, site_config, variable_values,
         created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
      ON DUPLICATE KEY UPDATE 
        landing_page_product_id = VALUES(landing_page_product_id),
        theme_config = VALUES(theme_config),
        variable_schema = VALUES(variable_schema),
        site_config = VALUES(site_config),
        variable_values = VALUES(variable_values),
        updated_at = NOW()
    `;

    await pool.execute(sql, [
      body.landingType,
      spuId.toString(),
      subDomainId.toString(),
      landingPageProductId?.toString() ?? null,
      themeConfigJson,
      variableSchemaJson,
      siteConfigJson,
      variableValuesJson,
    ]);

    // 清除相关缓存（使用模式匹配清除所有语言版本的缓存）
    try {
      // 清除 Landing Page 配置缓存（所有类型都需要清除）
      const configCleared = await clearLandingConfigCache(Number(subDomainId), Number(spuId), body.landingType);
      console.log("[Builder API] Landing config cache cleared:", configCleared ? "existed" : "not found");

      if (body.landingType === "LAND") {
        // LAND 类型清除产品缓存（所有语言）
        const cleared = await clearProductCacheAllLanguages(Number(subDomainId), Number(spuId));
        console.log("[Builder API] Product cache cleared:", cleared, "entries");
      } else if (body.landingType === "CLOAK") {
        // CLOAK 类型清除 cloak 缓存（所有语言）
        const cleared = await clearCloakCacheAllLanguages(Number(subDomainId), Number(spuId));
        console.log("[Builder API] Cloak cache cleared:", cleared, "entries");
      }
      console.log("[Builder API] Cache cleared for:", { subDomainId: subDomainId.toString(), spuId: spuId.toString(), landingType: body.landingType });
    } catch (cacheError) {
      // 缓存清除失败不影响保存结果
      console.warn("[Builder API] Failed to clear cache:", cacheError);
    }

    return {
      success: true,
      message: "Theme configuration saved successfully",
    };
  } catch (error: any) {
    console.error("[Builder API] Save theme error:", error);

    // 处理外键约束错误
    if (error.code === "ER_NO_REFERENCED_ROW_2") {
      throw createError({
        statusCode: 400,
        statusMessage: "Invalid reference: subDomainId, spuId, or landingPageProductId does not exist",
      });
    }

    if (error.statusCode) {
      throw error;
    }

    throw createError({
      statusCode: 500,
      statusMessage: "Failed to save theme configuration",
    });
  }
});
