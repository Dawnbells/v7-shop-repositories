/**
 * 域名 Repository
 * 封装域名相关的数据库查询
 */

import { queryOne } from '../utils/db'

export interface DomainInfo {
  subDomainId: number
  fullName: string
  name: string
  hostname: string
  companyId: number
  topLevelDomainId: number
  countryId: number | null
  currencyId: number | null
  languageId: number | null
  cloakStrategy: string | null
  userId: number | null
}

/**
 * 根据完整域名查询域名信息
 * @param fullName 完整域名，如 test.axhhcx.shop
 */
export async function findDomainByFullName(fullName: string): Promise<DomainInfo | null> {
  const sql = `
    SELECT 
      sd.id as subDomainId,
      sd.full_name as fullName,
      sd.name as \`name\`,
      d.name as hostname,
      d.company_id as companyId,
      d.id as topLevelDomainId,
      sd.country_id as countryId,
      sd.currency_id as currencyId,
      sd.language_id as languageId,
      d.cloakStrategy,
      d.user_id as userId
    FROM t_top_level_domains d
    LEFT JOIN t_sub_domains sd ON sd.parent_domain_id = d.id
    WHERE sd.full_name = ?
    LIMIT 1
  `
  return queryOne<DomainInfo>(sql, [fullName])
}
