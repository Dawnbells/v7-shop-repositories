/**
 * 页面上下文类型定义
 * 统一管理所有 middleware 注入的数据
 */

import type { SubDomain, TopLevelDomain, Country, Currency, Company, SalesUser } from './domain'
import type { CloakCheckResponse } from './cloak'
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
  // 01-domain.ts 设置的实体（必需，不能为 null）
  /** 子域名信息 */
  subDomain: SubDomain
  /** 顶级域名信息 */
  topLevelDomain: TopLevelDomain
  /** 国家信息 */
  country: Country
  /** 货币信息 */
  currency: Currency
  /** 公司信息 */
  company: Company
  /** 销售用户信息 */
  salesUser: SalesUser

  // 02-cloak.ts 设置（必需，不能为 null）
  /** 斗篷检查结果 */
  cloak: CloakCheckResponse
  /** 用户指纹 */
  fingerprint: string

  // 02-landing.ts 设置
  /** 页面主题配置 */
  pageTheme: PageTheme | null

  // 从 URL 解析
  /** 产品 SPU ID */
  spuId: number | null
}

/**
 * 部分页面上下文（用于中间件初始化阶段）
 * 在 01-domain.ts 完成验证前，域名相关字段可能为 null
 */
export interface PartialPageContext {
  subDomain: SubDomain | null
  topLevelDomain: TopLevelDomain | null
  country: Country | null
  currency: Currency | null
  company: Company | null
  salesUser: SalesUser | null
  cloak: CloakCheckResponse | null
  fingerprint: string | null
  pageTheme: PageTheme | null
  spuId: number | null
}

/**
 * 创建空的页面上下文（部分）
 */
export function createEmptyPageContext(): PartialPageContext {
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
  }
}

/**
 * 检查域名相关必需字段是否都已设置
 */
export function isDomainContextComplete(ctx: PartialPageContext): ctx is PageContext & PartialPageContext {
  return (
    ctx.subDomain !== null &&
    ctx.topLevelDomain !== null &&
    ctx.country !== null &&
    ctx.currency !== null &&
    ctx.company !== null &&
    ctx.salesUser !== null
  )
}
