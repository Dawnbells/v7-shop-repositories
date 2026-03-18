/**
 * 域名 Repository
 * 封装域名相关的数据库查询
 */

import { query, queryOne } from '../utils/db'
import { getRedisStorage } from '../utils/redis'

const CACHE_PREFIX = 'domain:'
const CACHE_TTL = 300 // 5 分钟（秒）
import type { LanguageItem } from './languageRepository'
import type {
  SubDomain,
  TopLevelDomain,
  Country,
  Currency,
  Company,
  SalesUser,
  DomainQueryResult,
} from '../types/domain'

// 重新导出类型方便外部使用
export type {
  SubDomain,
  TopLevelDomain,
  Country,
  Currency,
  Company,
  SalesUser,
  DomainQueryResult,
} from '../types/domain'

/**
 * 域名查询原始行数据
 */
interface DomainQueryRow {
  // SubDomain
  id: number
  full_name: string
  name: string
  type: string
  status: string
  company_id: number
  website_id: number | null
  theme_id: number | null
  country_id: number | null
  currency_id: number | null
  language_id: number | null
  analyze_success: number | null
  parent_domain_id: number | null
  // Country
  country_id_val: number | null
  country_code: string | null
  country_name: string | null
  continent_code: string | null
  phone_prefix: string | null
  phone_rule: string | null
  address_fields: string | null
  address_rule: string | null
  required_email: number | null
  required_phone: number | null
  use_full_name: number | null
  footer_copyright_info: string | null
  // Currency
  currency_id_val: number | null
  currency_code: string | null
  currency_name: string | null
  symbol: string | null
  exchange_rate: number | null
  fraction_digits: number | null
  // Company
  company_id_val: number | null
  company_name: string | null
  company_domain: string | null
  access_key: string | null
  cloak_fallback: string | null
  // TopLevelDomain
  tld_id: number | null
  tld_name: string | null
  cloak_strategy: string | null
  tld_user_id: number | null
  // SalesUser
  sales_user_id: number | null
  sales_user_name: string | null
  sales_department_id: number | null
}

/**
 * 将原始行数据转换为结构化的 DomainQueryResult
 */
function mapRowToResult(row: DomainQueryRow, languages: LanguageItem[] = []): DomainQueryResult {
  const subDomain: SubDomain = {
    id: row.id,
    fullName: row.full_name,
    name: row.name,
    type: row.type,
    status: row.status,
    companyId: row.company_id,
    websiteId: row.website_id,
    themeId: row.theme_id,
    countryId: row.country_id,
    currencyId: row.currency_id,
    languageId: row.language_id,
    analyzeSuccess: row.analyze_success !== null ? Boolean(row.analyze_success) : null,
    parentDomainId: row.parent_domain_id,
  }

  const topLevelDomain: TopLevelDomain | null = row.tld_id
    ? {
        id: row.tld_id,
        name: row.tld_name!,
        cloakStrategy: row.cloak_strategy,
        userId: row.tld_user_id,
      }
    : null

  const country: Country | null = row.country_id_val
    ? {
        id: row.country_id_val,
        code: row.country_code!,
        name: row.country_name!,
        continentCode: row.continent_code,
        phonePrefix: row.phone_prefix,
        phoneRule: row.phone_rule,
        addressFields: row.address_fields,
        addressRule: row.address_rule,
        requiredEmail: row.required_email !== null ? Boolean(row.required_email) : null,
        requiredPhone: row.required_phone !== null ? Boolean(row.required_phone) : null,
        useFullName: row.use_full_name !== null ? Boolean(row.use_full_name) : null,
        footerCopyrightInfo: row.footer_copyright_info,
        languages,
      }
    : null

  const currency: Currency | null = row.currency_id_val
    ? {
        id: row.currency_id_val,
        code: row.currency_code!,
        name: row.currency_name!,
        symbol: row.symbol,
        exchangeRate: row.exchange_rate,
        fractionDigits: row.fraction_digits,
      }
    : null

  const company: Company | null = row.company_id_val
    ? {
        id: row.company_id_val,
        name: row.company_name!,
        domain: row.company_domain,
        accessKey: row.access_key,
        cloakFallback: row.cloak_fallback,
      }
    : null

  const salesUser: SalesUser | null = row.sales_user_id
    ? {
        id: row.sales_user_id,
        name: row.sales_user_name,
        departmentId: row.sales_department_id,
      }
    : null

  return {
    subDomain,
    topLevelDomain,
    country,
    currency,
    company,
    salesUser,
  }
}

/**
 * 根据国家ID查询支持的语言列表
 */
async function findLanguagesByCountryId(countryId: number): Promise<LanguageItem[]> {
  const sql = `
    SELECT l.id, l.code, l.name, l.cname
    FROM t_country_languages cl
    INNER JOIN t_languages l ON cl.language_id = l.id AND l.status = 'VALID'
    WHERE cl.country_id = ?
    ORDER BY l.id ASC
  `
  return query<LanguageItem>(sql, [countryId])
}

/**
 * 根据完整域名查询域名信息及所有关联实体
 * @param fullName 完整域名，如 test.axhhcx.shop
 */
export async function findDomainByFullName(fullName: string): Promise<DomainQueryResult | null> {
  const storage = getRedisStorage()
  const cacheKey = `${CACHE_PREFIX}${fullName}`

  const cached = await storage.getItem<DomainQueryResult>(cacheKey)
  if (cached) {
    return cached
  }

  const sql = `
    SELECT 
      d.id, d.full_name, d.name, d.type, d.status, d.company_id,
      d.website_id, d.theme_id, d.country_id, d.currency_id, d.language_id, d.analyze_success,
      d.parent_domain_id,
      c.id AS country_id_val, c.code AS country_code, c.name AS country_name, 
      c.continent_code, c.phone_prefix, c.phone_rule, c.address_fields, c.address_rule,
      c.required_email, c.required_phone, c.use_full_name, c.footer_copyright_info,
      cur.id AS currency_id_val, cur.code AS currency_code, cur.name AS currency_name,
      cur.symbol, cur.exchange_rate, cur.fraction_digits,
      comp.id AS company_id_val, comp.name AS company_name, comp.domain AS company_domain, comp.access_key, comp.cloak_fallback,
      tld.id AS tld_id, tld.name AS tld_name, tld.cloakStrategy AS cloak_strategy, tld.user_id AS tld_user_id,
      su.id AS sales_user_id, su.name AS sales_user_name, su.department_id AS sales_department_id
    FROM t_sub_domains d
    INNER JOIN t_countries c ON d.country_id = c.id AND c.status = 'VALID'
    INNER JOIN t_currencies cur ON c.currency_id = cur.id AND cur.status = 'VALID'
    INNER JOIN t_companies comp ON d.company_id = comp.id AND comp.status = 'VALID'
    INNER JOIN t_top_level_domains tld ON d.parent_domain_id = tld.id AND tld.status = 'VALID'
    INNER JOIN t_system_users su ON tld.user_id = su.id AND su.status = 'VALID'
    WHERE d.full_name = ? AND d.status = 'VALID'
    LIMIT 1
  `

  const row = await queryOne<DomainQueryRow>(sql, [fullName])

  if (!row) {
    return null
  }

  let languages: LanguageItem[] = []
  if (row.country_id_val) {
    languages = await findLanguagesByCountryId(row.country_id_val)
  }

  const result = mapRowToResult(row, languages)

  await storage.setItem(cacheKey, result, { ttl: CACHE_TTL })

  return result
}
