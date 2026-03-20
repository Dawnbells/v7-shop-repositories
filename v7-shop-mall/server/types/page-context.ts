/**
 * 页面上下文类型定义
 * 统一管理所有 middleware 注入的数据
 */

import type { SubDomain, TopLevelDomain, Country, Currency, Company, SalesUser } from './domain'
import type { CloakCheckResponse } from './cloak'
import type { ThemeConfig, SiteConfig, VariableValues } from '../../app/types/builder'
import type { ProductDetail } from '../repositories/productRepository'
import type { ProtocolGroup } from '../repositories/protocolRepository'
import type { ArticleInfo } from '../repositories/articleRepository'
import type { LanguageItem } from '../repositories/languageRepository'

/**
 * 页面主题配置
 */
export interface PageTheme {
  themeConfig: ThemeConfig | null
  siteConfig: SiteConfig
  variableValues: VariableValues
}

/**
 * 落地页配置信息
 * 从 03-landing.ts 中间件获取
 */
export interface LandingPageInfo {
  /** 落地页 SPU ID（用于获取商品信息） */
  landingSpuId: number | null
  /** 协议 ID */
  protocolId: number | null
  /** 协议占位符值 */
  protocolPlaceholderValues: Record<string, any>
  /** 变量 schema */
  variableSchema: any[]
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
  /** 当前选中的语言ID */
  currentLanguageId: number
  /** 当前选中的语言对象 */
  currentLanguage: LanguageItem

  // 02-cloak.ts 设置（必需，不能为 null）
  /** 斗篷检查结果 */
  cloak: CloakCheckResponse
  /** 用户指纹 */
  fingerprint: string

  // 03-landing.ts 设置
  /** 页面主题配置 */
  pageTheme: PageTheme | null
  /** 落地页配置信息 */
  landingPage: LandingPageInfo | null

  // 04-protocol.ts 设置
  /** 协议组列表 */
  protocolGroups: ProtocolGroup[] | null

  // 10-product.ts 设置
  /** 商品详细信息 */
  productInfo: ProductDetail | null

  // 11-article.ts 设置
  /** 文章详情 */
  articleInfo: ArticleInfo | null

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
  currentLanguageId: number | null
  currentLanguage: LanguageItem | null
  cloak: CloakCheckResponse | null
  fingerprint: string | null
  pageTheme: PageTheme | null
  landingPage: LandingPageInfo | null
  protocolGroups: ProtocolGroup[] | null
  productInfo: ProductDetail | null
  articleInfo: ArticleInfo | null
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
    currentLanguageId: null,
    currentLanguage: null,
    cloak: null,
    fingerprint: null,
    pageTheme: null,
    landingPage: null,
    protocolGroups: null,
    productInfo: null,
    articleInfo: null,
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
    ctx.salesUser !== null &&
    ctx.currentLanguageId !== null &&
    ctx.currentLanguage !== null
  )
}
