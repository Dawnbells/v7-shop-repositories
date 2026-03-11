/**
 * 加载主题配置 API
 * GET /api/builder/load?subDomainId=xxx&spuId=xxx&landingType=xxx
 *
 * 返回数据分离的 4 个字段：
 * - themeConfig: 页面布局、组件、样式
 * - variableSchema: 变量定义结构
 * - siteConfig: 站点配置值
 * - variableValues: 变量实际值
 */

import { findLandingPageConfig } from '../../repositories/landingPageRepository'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)

  const subDomainId = query.subDomainId as string
  const spuId = query.spuId as string
  const landingType = (query.landingType as string) || 'LAND'

  if (!subDomainId || !spuId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing required query parameters: subDomainId, spuId',
    })
  }

  try {
    const config = await findLandingPageConfig(
      parseInt(subDomainId, 10),
      parseInt(spuId, 10),
      landingType
    )

    if (!config) {
      return {
        success: true,
        data: null,
        message: 'No theme configuration found',
      }
    }

    return {
      success: true,
      data: {
        themeConfig: config.themeConfig,
        variableSchema: config.variableSchema,
        siteConfig: config.siteConfig,
        variableValues: config.variableValues,
      },
    }
  } catch (error: any) {
    console.error('[Builder API] Load theme error:', error)

    throw createError({
      statusCode: 500,
      statusMessage: 'Failed to load theme configuration',
    })
  }
})
