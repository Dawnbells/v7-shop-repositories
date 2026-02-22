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

import { getPool } from '../../utils/db'

function parseJsonField(value: any, defaultValue: any = null): any {
  if (value === null || value === undefined) {
    return defaultValue
  }
  if (typeof value === 'object') {
    return value
  }
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      console.warn('[Builder API] Failed to parse JSON field')
      return defaultValue
    }
  }
  return defaultValue
}

export default defineEventHandler(async (event) => {
  const query = getQuery(event)

  const subDomainId = query.subDomainId as string
  const spuId = query.spuId as string
  const landingType = (query.landingType as string) || 'LAND'

  if (!subDomainId || !spuId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing required query parameters: subDomainId, spuId',
    })
  }

  const pool = getPool()

  try {
    const sql = `
      SELECT theme_config, variable_schema, site_config, variable_values
      FROM t_sub_domain_spu_landing_pages
      WHERE sub_domain_id = ? AND spu_id = ? AND landing_page_type = ?
      LIMIT 1
    `

    const [rows] = await pool.execute(sql, [subDomainId, spuId, landingType])
    const result = rows as any[]

    if (result.length === 0) {
      return {
        success: true,
        data: null,
        message: 'No theme configuration found',
      }
    }

    const row = result[0]

    return {
      success: true,
      data: {
        themeConfig: parseJsonField(row.theme_config, null),
        variableSchema: parseJsonField(row.variable_schema, []),
        siteConfig: parseJsonField(row.site_config, {}),
        variableValues: parseJsonField(row.variable_values, {}),
      },
    }
  } catch (error: any) {
    console.error('[Builder API] Load theme error:', error)

    throw createError({
      statusCode: 500,
      statusMessage: 'Failed to load theme configuration',
    })
  }
})
