/**
 * 域名解析中间件
 * 从请求中提取 host，查询数据库获取域名和公司信息
 * 设置 event.context.domain
 * 
 * 本地开发时可通过 NUXT_DEV_DOMAIN 环境变量指定模拟域名
 * 未配置域名或域名不存在时 domain 为 null，前端显示"店铺不存在"
 */

import { findDomainByFullName, type DomainInfo } from '../repositories/domainRepository'

export default defineEventHandler(async (event) => {
  const path = event.path

  // 跳过不需要域名解析的路由
  if (
    path.startsWith('/api/builder/') ||
    path.startsWith('/builder') ||
    path.startsWith('/_nuxt') ||
    path.startsWith('/__nuxt')
  ) {
    return
  }

  // 获取请求的 host
  const host = getRequestHost(event, { xForwardedHost: true })
  
  if (!host) {
    console.warn('[01-domain] No host found in request')
    event.context.domain = null
    return
  }

  // 判断是否为本地开发环境
  const isLocalDev = host.includes('localhost') || host.includes('127.0.0.1')
  
  // 确定要查询的域名
  let queryDomain = host
  
  if (isLocalDev) {
    const config = useRuntimeConfig()
    const devDomain = config.devDomain as string
    
    if (devDomain) {
      queryDomain = devDomain
    } else {
      // 未配置开发域名，无法确定店铺
      event.context.domain = null
      return
    }
  }

  try {
    const domainInfo = await findDomainByFullName(queryDomain)

    if (domainInfo) {
      if (isLocalDev) {
        domainInfo.fullName = host
      }
      event.context.domain = domainInfo
    } else {
      console.warn(`[01-domain] Domain not found: ${queryDomain}`)
      event.context.domain = null
    }
  } catch (error) {
    console.error('[01-domain] Error querying domain:', error)
    event.context.domain = null
  }
})
