/**
 * 斗篷/风控相关类型定义
 */

/**
 * 斗篷页面类型
 */
export enum CloakPage {
  LAND = "LAND",
  CLOAK = "CLOAK",
  CRAWLER = "CRAWLER",
  RISK = "RISK",
  BLACKLISTED = "BLACKLISTED",
}

/**
 * 斗篷检查请求
 */
export interface CloakCheckRequest {
  clientIp: string
  requestUrl: string
  spuId?: number
  headers: Record<string, string>
  fingerprint?: string
  cloakStrategy?: string
  accessKey?: string
  continentCode?: string
  countryCode?: string
  companyDomain?: string
  userId?: number
  deptId?: number
}

/**
 * 斗篷检查响应
 */
export interface CloakCheckResponse {
  remote: boolean
  page: CloakPage
  pdVal: string
  isAdmin: boolean
}
