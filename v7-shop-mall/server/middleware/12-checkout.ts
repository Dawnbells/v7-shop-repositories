/**
 * 收银台中间件
 * 仅在 /checkout 路由生效
 * 预置收银台所需的基础数据到 pageContext
 */

import { getPageContext, updatePageContext } from '../utils/page-context'
import { logger } from '../utils/logger'

export default defineEventHandler(async (event) => {
  const path = event.path

  // 仅处理收银台页面
  if (!path.startsWith('/checkout')) {
    return
  }

  // 跳过 API 路由
  if (path.startsWith('/api/')) {
    return
  }

  try {
    const pageContext = getPageContext(event)

    // 收银台页面需要的数据已经由前置中间件加载：
    // - 01-domain: subDomain, country, currency, company, salesUser
    // - 02-cloak: cloak, fingerprint
    // - 03-landing: pageTheme, landingPage

    // 可以在这里预加载额外的收银台配置
    // 例如：支付方式配置、运费规则等
    // 目前这些配置暂时使用默认值，后续可扩展

    logger.debug('[12-checkout] Checkout page context ready', {
      countryId: pageContext.country?.id,
      currencyCode: pageContext.currency?.code,
      spuId: pageContext.spuId,
    })

  } catch (error) {
    logger.error('[12-checkout] Error in checkout middleware:', error)
  }
})
