/**
 * 主题配置加载中间件
 * 从数据库加载 themeConfig、siteConfig、variableValues
 * 设置 event.context.pageTheme
 */

import {
  findLandingPageConfig,
  findDefaultLandingPageConfig,
  findHomeLandingPageConfig,
  type LandingPageConfig,
} from '../repositories/landingPageRepository'
import type { ThemeConfig, SiteConfig, VariableValues } from '../../app/types/builder'

interface PageTheme {
  themeConfig: ThemeConfig | null
  siteConfig: SiteConfig
  variableValues: VariableValues
}

export default defineEventHandler(async (event) => {
  const path = event.path

  // 跳过 API 路由和编辑器路由
  if (
    path.startsWith('/api/') ||
    path.startsWith('/builder') ||
    path.startsWith('/_nuxt') ||
    path.startsWith('/__nuxt')
  ) {
    return
  }

  const domain = event.context.domain

  // 没有域名信息时跳过
  if (!domain?.subDomainId) {
    event.context.pageTheme = null
    return
  }

  try {
    // 从 URL 路径解析页面类型和 spuId
    // /product/123 -> landingType = 'PRODUCT', spuId = 123
    // /article/456 -> landingType = 'ARTICLE', 需要另外处理
    // / -> landingType = 'HOME'
    let landingType = 'HOME'
    let spuId: number | null = null

    const pathParts = path.split('/').filter(Boolean)
    
    if (pathParts[0] === 'product' && pathParts[1]) {
      landingType = 'PRODUCT'
      spuId = parseInt(pathParts[1], 10) || null
    } else if (pathParts[0] === 'article') {
      landingType = 'ARTICLE'
    }

    // 查询主题配置
    // 优先查找特定 spuId 的配置，否则查找默认配置
    let config: LandingPageConfig | null = null

    if (spuId) {
      config = await findLandingPageConfig(domain.subDomainId, spuId, landingType)
    } else {
      config = await findDefaultLandingPageConfig(domain.subDomainId, landingType)
    }

    if (config) {
      event.context.pageTheme = {
        themeConfig: config.themeConfig,
        siteConfig: config.siteConfig,
        variableValues: config.variableValues,
      } as PageTheme
    } else {
      // 没有找到特定配置，尝试查找 HOME 默认配置
      const homeConfig = await findHomeLandingPageConfig(domain.subDomainId)

      if (homeConfig) {
        event.context.pageTheme = {
          themeConfig: homeConfig.themeConfig,
          siteConfig: homeConfig.siteConfig,
          variableValues: homeConfig.variableValues,
        } as PageTheme
      } else {
        event.context.pageTheme = null
      }
    }
  } catch (error) {
    console.error('[02-landing] Error loading theme config:', error)
    event.context.pageTheme = null
  }
})
