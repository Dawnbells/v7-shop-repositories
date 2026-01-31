/**
 * 加载主题模板配置 API
 * GET /api/builder/template/load?templateId=xxx
 * 
 * 返回数据分离的 4 个字段：
 * - themeConfig: 页面布局、组件、样式
 * - variableSchema: 变量定义结构
 * - siteConfig: 站点配置值
 * - variableValues: 变量实际值
 */

import { getPool } from "../../../utils/db";

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
      console.warn("[Template API] Failed to parse JSON field");
      return defaultValue;
    }
  }
  return defaultValue;
}

export default defineEventHandler(async (event) => {
  const query = getQuery(event);

  const templateId = query.templateId as string;

  if (!templateId) {
    throw createError({
      statusCode: 400,
      statusMessage: "Missing required query parameter: templateId",
    });
  }

  const pool = getPool();

  try {
    // 查询模板的所有分离字段
    const sql = `
      SELECT theme_config, variable_schema, site_config, variable_values
      FROM t_theme_templates
      WHERE id = ? AND status = 'VALID'
      LIMIT 1
    `;

    const [rows] = await pool.execute(sql, [templateId]);
    const result = rows as any[];

    if (result.length === 0) {
      return {
        success: true,
        data: null,
        message: "No template configuration found",
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
    console.error("[Template API] Load template error:", error);

    throw createError({
      statusCode: 500,
      statusMessage: "Failed to load template configuration",
    });
  }
});
