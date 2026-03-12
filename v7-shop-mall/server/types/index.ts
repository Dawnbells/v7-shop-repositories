/**
 * 服务端类型统一导出
 */

// 安全页面类型
export { SafePageType } from './safe-page'

// 斗篷/风控类型
export { CloakPage } from './cloak'
export type { CloakCheckRequest, CloakCheckResponse } from './cloak'

// 域名相关实体
export type {
  SubDomain,
  TopLevelDomain,
  Country,
  Currency,
  Company,
  SalesUser,
  DomainQueryResult,
} from './domain'

// 页面上下文
export type { PageContext, PageTheme } from './page-context'
export { createEmptyPageContext } from './page-context'
