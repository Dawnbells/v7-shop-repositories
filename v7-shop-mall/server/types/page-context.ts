/**
 * 页面上下文类型定义
 * 统一管理所有 middleware 注入的数据
 */

import type { SubDomain, TopLevelDomain, Country, Currency, Company, SalesUser } from './domain'
import type { CloakCheckResponse } from './cloak'
import type { SafePageType } from './safe-page'
import type { ThemeConfig, SiteConfig, VariableValues } from '../../app/types/builder'

/**
 * 页面主题配置
 */
export interface PageTheme {
  themeConfig: ThemeConfig | null
  siteConfig: SiteConfig
  variableValues: VariableValues
}

/**
 * 页面上下文
 * 包含请求处理过程中各 middleware 设置的数据
 */
export interface PageContext {
  // 01-domain.ts 设置的实体
  /** 子域名信息 */
  subDomain: SubDomain | null
  /** 顶级域名信息 */
  topLevelDomain: TopLevelDomain | null
  /** 国家信息 */
  country: Country | null
  /** 货币信息 */
  currency: Currency | null
  /** 公司信息 */
  company: Company | null
  /** 销售用户信息 */
  salesUser: SalesUser | null

  // 02-cloak.ts 设置
  /** 斗篷检查结果 */
  cloak: CloakCheckResponse | null
  /** 用户指纹 */
  fingerprint: string | null

  // 02-landing.ts 设置
  /** 页面主题配置 */
  pageTheme: PageTheme | null

  // 从 URL 解析
  /** 产品 SPU ID */
  spuId: number | null

  // 安全页面
  /** 安全页面类型（设置后显示安全页面而非正常内容） */
  safePageType: SafePageType | null
  /** 追踪 ID（用于风控追踪） */
  trackingId: string | null
}

/**
 * 创建空的页面上下文
 */
export function createEmptyPageContext(): PageContext {
  return {
    subDomain: null,
    topLevelDomain: null,
    country: null,
    currency: null,
    company: null,
    salesUser: null,
    cloak: null,
    fingerprint: null,
    pageTheme: null,
    spuId: null,
    safePageType: null,
    trackingId: null,
  }
}
