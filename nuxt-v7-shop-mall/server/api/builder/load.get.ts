/**
 * 加载主题配置 API
 * GET /api/builder/load?subDomainId=xxx&spuId=xxx&landingType=xxx
 * 
 * 返回数据分离的 4 个字段：
 * - themeConfig: 页面布局、组件、样式
 * - variableSchema: 变量定义结构
 * - siteConfig: 站点配置值
 * - variableValues: 变量实际值
 */

import { getPool } from "../../utils/db";

/**
 * 解析 JSON 字段（mysql2 对 JSON 类型字段可能返回字符串或对象）
 */
function parseJsonField(value: any, defaultValue: any = null): any {
  if (value === null || value === undefined) {
    return defaultValue;
  }
  // 如果已经是对象/数组，直接返回
  if (typeof value === "object") {
    return value;
  }
  // 如果是字符串，尝试解析
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch {
      console.warn("[Builder API] Failed to parse JSON field");
      return defaultValue;
    }
  }
  return defaultValue;
}

export default defineEventHandler(async (event) => {
  const query = getQuery(event);

  const subDomainId = query.subDomainId as string;
  const spuId = query.spuId as string;
  const landingType = query.landingType as string || "LAND";

  if (!subDomainId || !spuId) {
    throw createError({
      statusCode: 400,
      statusMessage: "Missing required query parameters: subDomainId, spuId",
    });
  }

  const pool = getPool();

  try {
    // 查询所有分离的字段
    const sql = `
      SELECT theme_config, variable_schema, site_config, variable_values
      FROM t_sub_domain_spu_landing_pages
      WHERE sub_domain_id = ? AND spu_id = ? AND landing_page_type = ?
      LIMIT 1
    `;

    const [rows] = await pool.execute(sql, [subDomainId, spuId, landingType]);
    const result = rows as any[];

    if (result.length === 0) {
      return {
        success: true,
        data: null,
        message: "No theme configuration found",
      };
    }

    const row = result[0];

    // 解析各个 JSON 字段
    return {
      success: true,
      data: {
        themeConfig: parseJsonField(row.theme_config, null),
        variableSchema: parseJsonField(row.variable_schema, []),
        siteConfig: parseJsonField(row.site_config, {}),
        variableValues: parseJsonField(row.variable_values, {}),
      },
    };
  } catch (error: any) {
    console.error("[Builder API] Load theme error:", error);

    throw createError({
      statusCode: 500,
      statusMessage: "Failed to load theme configuration",
    });
  }
});
