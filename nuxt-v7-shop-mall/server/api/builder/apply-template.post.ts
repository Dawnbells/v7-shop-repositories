/**
 * 应用主题模板到落地页 API
 * POST /api/builder/apply-template
 * 
 * 将模板的 4 个 JSON 字段复制到落地页配置
 */

import { getPool } from "../../utils/db";
import { clearProductCacheAllLanguages, clearCloakCacheAllLanguages } from "../../cache/landing.cache";

// 允许的落地页类型
const VALID_LANDING_TYPES = ["LAND", "CLOAK", "BLACKLISTED"] as const;
type LandingPageType = typeof VALID_LANDING_TYPES[number];

interface ApplyTemplateRequest {
  subDomainId: string | number;
  spuId: string | number;
  landingType: string;
  templateId: string | number;
}

export default defineEventHandler(async (event) => {
  const body = await readBody<ApplyTemplateRequest>(event);

  // 参数校验
  if (!body.subDomainId || !body.spuId || !body.landingType || !body.templateId) {
    throw createError({
      statusCode: 400,
      statusMessage: "Missing required fields: subDomainId, spuId, landingType, templateId",
    });
  }

  // 校验 landingType 枚举值
  if (!VALID_LANDING_TYPES.includes(body.landingType as LandingPageType)) {
    throw createError({
      statusCode: 400,
      statusMessage: `Invalid landingType: ${body.landingType}. Must be one of: ${VALID_LANDING_TYPES.join(", ")}`,
    });
  }

  const pool = getPool();

  try {
    // 1. 先查询模板的配置
    const templateSql = `
      SELECT theme_config, variable_schema, site_config, variable_values
      FROM t_theme_templates
      WHERE id = ? AND status = 'VALID'
      LIMIT 1
    `;
    
    const [templateRows] = await pool.execute(templateSql, [body.templateId]);
    const templates = templateRows as any[];
    
    if (templates.length === 0) {
      throw createError({
        statusCode: 404,
        statusMessage: "Template not found",
      });
    }

    const template = templates[0];

    // 2. 检查模板是否有配置数据
    if (!template.theme_config) {
      throw createError({
        statusCode: 400,
        statusMessage: "Template has no theme configuration",
      });
    }

    // 3. 应用模板配置到落地页（使用 upsert）
    const applySql = `
      INSERT INTO t_sub_domain_spu_landing_pages 
        (landing_page_type, spu_id, sub_domain_id, 
         theme_config, variable_schema, site_config, variable_values,
         created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
      ON DUPLICATE KEY UPDATE 
        theme_config = VALUES(theme_config),
        variable_schema = VALUES(variable_schema),
        site_config = VALUES(site_config),
        variable_values = VALUES(variable_values),
        updated_at = NOW()
    `;

    await pool.execute(applySql, [
      body.landingType,
      body.spuId,
      body.subDomainId,
      template.theme_config,
      template.variable_schema || "[]",
      template.site_config || "{}",
      template.variable_values || "{}",
    ]);

    // 4. 清除相关缓存
    try {
      const subDomainIdNum = Number(body.subDomainId);
      const spuIdNum = Number(body.spuId);
      
      if (body.landingType === "LAND") {
        await clearProductCacheAllLanguages(subDomainIdNum, spuIdNum);
      } else if (body.landingType === "CLOAK") {
        await clearCloakCacheAllLanguages(subDomainIdNum, spuIdNum);
      }
      console.log("[Apply Template API] Cache cleared for:", {
        subDomainId: body.subDomainId,
        spuId: body.spuId,
        landingType: body.landingType
      });
    } catch (cacheError) {
      console.warn("[Apply Template API] Failed to clear cache:", cacheError);
    }

    return {
      success: true,
      message: "Template applied successfully",
    };
  } catch (error: any) {
    console.error("[Apply Template API] Error:", error);

    if (error.statusCode) {
      throw error;
    }

    throw createError({
      statusCode: 500,
      statusMessage: "Failed to apply template",
    });
  }
});
