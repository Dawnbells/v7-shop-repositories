/**
 * 落地页 Repository
 * 封装主题配置相关的数据库查询和保存操作
 */

import { query, queryOne, getPool } from '../utils/db'

export interface LandingPageConfig {
  themeConfig: any
  variableSchema: any[]
  siteConfig: Record<string, any>
  variableValues: Record<string, any>
}

export interface LandingPageRow {
  theme_config: string | null
  variable_schema: string | null
  site_config: string | null
  variable_values: string | null
}

/**
 * 解析 JSON 字段
 */
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
      return defaultValue
    }
  }
  return defaultValue
}

/**
 * 根据 subDomainId、spuId、landingType 查询主题配置
 */
export async function findLandingPageConfig(
  subDomainId: number,
  spuId: number,
  landingType: string
): Promise<LandingPageConfig | null> {
  const sql = `
    SELECT theme_config, variable_schema, site_config, variable_values
    FROM t_sub_domain_spu_landing_pages
    WHERE sub_domain_id = ? AND spu_id = ? AND landing_page_type = ?
    LIMIT 1
  `

  const row = await queryOne<LandingPageRow>(sql, [subDomainId, spuId, landingType])

  if (!row) {
    return null
  }

  return {
    themeConfig: parseJsonField(row.theme_config, null),
    variableSchema: parseJsonField(row.variable_schema, []),
    siteConfig: parseJsonField(row.site_config, {}),
    variableValues: parseJsonField(row.variable_values, {}),
  }
}

/**
 * 查询默认主题配置（spu_id = 0 或 null）
 */
export async function findDefaultLandingPageConfig(
  subDomainId: number,
  landingType: string
): Promise<LandingPageConfig | null> {
  const sql = `
    SELECT theme_config, variable_schema, site_config, variable_values
    FROM t_sub_domain_spu_landing_pages
    WHERE sub_domain_id = ? AND (spu_id = 0 OR spu_id IS NULL) AND landing_page_type = ?
    LIMIT 1
  `

  const row = await queryOne<LandingPageRow>(sql, [subDomainId, landingType])

  if (!row) {
    return null
  }

  return {
    themeConfig: parseJsonField(row.theme_config, null),
    variableSchema: parseJsonField(row.variable_schema, []),
    siteConfig: parseJsonField(row.site_config, {}),
    variableValues: parseJsonField(row.variable_values, {}),
  }
}

/**
 * 查询 HOME 类型的默认主题配置
 */
export async function findHomeLandingPageConfig(
  subDomainId: number
): Promise<LandingPageConfig | null> {
  return findDefaultLandingPageConfig(subDomainId, 'HOME')
}

export interface SaveLandingPageParams {
  subDomainId: bigint
  spuId: bigint
  landingType: string
  landingPageProductId: bigint | null
  themeConfig: string
  variableSchema: string
  siteConfig: string
  variableValues: string
}

/**
 * 保存主题配置（INSERT ... ON DUPLICATE KEY UPDATE）
 */
export async function saveLandingPageConfig(params: SaveLandingPageParams): Promise<void> {
  const pool = getPool()

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
  `

  await pool.execute(sql, [
    params.landingType,
    params.spuId.toString(),
    params.subDomainId.toString(),
    params.landingPageProductId?.toString() ?? null,
    params.themeConfig,
    params.variableSchema,
    params.siteConfig,
    params.variableValues,
  ])
}
