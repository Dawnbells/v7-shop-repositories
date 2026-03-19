/**
 * 域名相关实体类型定义
 */

import type { LanguageItem } from '../repositories/languageRepository'

/**
 * 子域名信息
 */
export interface SubDomain {
  id: number
  fullName: string
  name: string
  type: string
  status: string
  companyId: number
  websiteId: number | null
  themeId: number | null
  countryId: number | null
  currencyId: number | null
  languageId: number | null
  analyzeSuccess: boolean | null
  parentDomainId: number | null
}

/**
 * 顶级域名信息
 */
export interface TopLevelDomain {
  id: number
  name: string
  cloakStrategy: string | null
  userId: number | null
}

/**
 * 国家信息
 */
export interface Country {
  id: number
  code: string
  name: string
  continentCode: string | null
  phonePrefix: string | null
  phoneRule: string | null
  addressFields: string | null
  addressRule: string | null
  requiredEmail: boolean | null
  requiredPhone: boolean | null
  useFullName: boolean | null
  footerCopyrightInfo: string | null
  languages: LanguageItem[]
}

/**
 * 货币信息
 */
export interface Currency {
  id: number
  code: string
  name: string
  symbol: string | null
  exchangeRate: number | null
  fractionDigits: number | null
}

/**
 * 公司信息
 */
export interface Company {
  id: number
  name: string
  domain: string | null
  accessKey: string | null
  cloakFallback: string | null
}

/**
 * 销售用户信息
 */
export interface SalesUser {
  id: number
  name: string | null
  departmentId: number | null
  departmentName: string | null
}

/**
 * 域名查询结果（包含所有关联实体）
 */
export interface DomainQueryResult {
  subDomain: SubDomain
  topLevelDomain: TopLevelDomain | null
  country: Country | null
  currency: Currency | null
  company: Company | null
  salesUser: SalesUser | null
}
