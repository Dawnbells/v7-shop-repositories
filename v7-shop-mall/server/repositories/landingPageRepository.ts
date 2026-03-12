/**
 * 落地页 Repository
 * 封装主题配置相关的数据库查询和保存操作
 */

import { queryOne, getPool } from '../utils/db'

export type LandingPageType = 'LAND' | 'CLOAK' | 'BLACKLISTED'

export interface LandingPageConfig {
  landingPageType: LandingPageType
  spuId: number
  subDomainId: number
  landingSpuId: number | null
  themeConfig: any
  variableSchema: any[]
  siteConfig: Record<string, any>
  variableValues: Record<string, any>
  protocolPlaceholderValues: Record<string, any>
  protocolId: number | null
}

export interface LandingPageRow {
  landing_page_type: string
  spu_id: number
  sub_domain_id: number
  landing_spu_id: number | null
  theme_config: string | null
  variable_schema: string | null
  site_config: string | null
  variable_values: string | null
  protocol_placeholder_values: string | null
  protocol_id: number | null
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
 * 根据 subDomainId、spuId、landingPageType 查询落地页配置
 */
export async function findLandingPageConfig(
  subDomainId: number,
  spuId: number,
  landingPageType: LandingPageType
): Promise<LandingPageConfig | null> {
  const sql = `
    SELECT 
      landing_page_type,
      spu_id,
      sub_domain_id,
      landing_spu_id,
      theme_config,
      variable_schema,
      site_config,
      variable_values,
      protocol_placeholder_values,
      protocol_id
    FROM t_sub_domain_spu_landing_pages
    WHERE sub_domain_id = ? AND spu_id = ? AND landing_page_type = ?
    LIMIT 1
  `

  const row = await queryOne<LandingPageRow>(sql, [subDomainId, spuId, landingPageType])

  if (!row) {
    return null
  }

  return {
    landingPageType: row.landing_page_type as LandingPageType,
    spuId: row.spu_id,
    subDomainId: row.sub_domain_id,
    landingSpuId: row.landing_spu_id,
    themeConfig: parseJsonField(row.theme_config, null),
    variableSchema: parseJsonField(row.variable_schema, []),
    siteConfig: parseJsonField(row.site_config, {}),
    variableValues: parseJsonField(row.variable_values, {}),
    protocolPlaceholderValues: parseJsonField(row.protocol_placeholder_values, {}),
    protocolId: row.protocol_id,
  }
}

export interface SaveLandingPageParams {
  subDomainId: bigint
  spuId: bigint
  landingType: string
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
  `

  await pool.execute(sql, [
    params.landingType,
    params.spuId.toString(),
    params.subDomainId.toString(),
    params.themeConfig,
    params.variableSchema,
    params.siteConfig,
    params.variableValues,
  ])
}
