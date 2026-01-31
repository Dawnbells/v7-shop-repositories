/**
 * 保存主题模板配置 API
 * POST /api/builder/template/save
 * 
 * 数据分离设计：
 * - theme_config: 页面布局、组件、样式（前端渲染 + 编辑器）
 * - variable_schema: 变量定义结构（仅编辑器）
 * - site_config: 站点配置值（前端渲染 + 编辑器）
 * - variable_values: 变量实际值（前端渲染 + 编辑器）
 */

import { getPool } from "../../../utils/db";

interface SaveTemplateRequest {
  templateId: string | number;
  // 分离的数据字段
  themeConfig: object;                        // 页面布局、组件、样式
  variableSchema?: object[];                  // 变量定义结构
  siteConfig?: object;                        // 站点配置值
  variableValues?: object;                    // 变量实际值
}

export default defineEventHandler(async (event) => {
  const body = await readBody<SaveTemplateRequest>(event);

  // 参数校验
  if (!body.templateId || !body.themeConfig) {
    throw createError({
      statusCode: 400,
      statusMessage: "Missing required fields: templateId, themeConfig",
    });
  }

  // 确保 themeConfig 是有效的 JSON 对象
  if (typeof body.themeConfig !== "object" || body.themeConfig === null) {
    throw createError({
      statusCode: 400,
      statusMessage: "themeConfig must be a valid JSON object",
    });
  }

  const pool = getPool();

  // 序列化各个 JSON 字段
  const themeConfigJson = JSON.stringify(body.themeConfig);
  const variableSchemaJson = body.variableSchema ? JSON.stringify(body.variableSchema) : "[]";
  const siteConfigJson = body.siteConfig ? JSON.stringify(body.siteConfig) : "{}";
  const variableValuesJson = body.variableValues ? JSON.stringify(body.variableValues) : "{}";

  try {
    // 先检查模板是否存在
    const checkSql = `
      SELECT id FROM t_theme_templates 
      WHERE id = ? AND status = 'VALID'
      LIMIT 1
    `;
    
    const [checkRows] = await pool.execute(checkSql, [body.templateId]);
    const checkResult = checkRows as any[];
    
    if (checkResult.length === 0) {
      throw createError({
        statusCode: 404,
        statusMessage: "Template not found",
      });
    }

    // 更新模板配置
    const sql = `
      UPDATE t_theme_templates 
      SET 
        theme_config = ?,
        variable_schema = ?,
        site_config = ?,
        variable_values = ?,
        update_time = NOW()
      WHERE id = ? AND status = 'VALID'
    `;

    await pool.execute(sql, [
      themeConfigJson,
      variableSchemaJson,
      siteConfigJson,
      variableValuesJson,
      body.templateId,
    ]);

    return {
      success: true,
      message: "Template configuration saved successfully",
    };
  } catch (error: any) {
    console.error("[Template API] Save template error:", error);

    if (error.statusCode) {
      throw error;
    }

    throw createError({
      statusCode: 500,
      statusMessage: "Failed to save template configuration",
    });
  }
});
