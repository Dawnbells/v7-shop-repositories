/**
 * 商品信息加载中间件
 * 仅在商品详情页 (/product/[id]) 执行
 * 根据 landingSpuId + countryId 获取商品详细信息
 * 设置 PageContext.productInfo
 */

import { findProductDetailBySpuAndCountry } from "../repositories/productRepository";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { logger } from "../utils/logger";

const PRODUCT_ROUTE = /^\/product\/[\w-]+(\?.*)?$/;

function isProductRoute(path: string): boolean {
  return PRODUCT_ROUTE.test(path);
}

export default defineEventHandler(async (event) => {
  const path = event.path;

  // 跳过非商品详情页路由
  if (!isProductRoute(path)) {
    return;
  }

  // 跳过 API 路由和编辑器路由
  if (
    path.startsWith("/api/") ||
    path.startsWith("/builder") ||
    path.startsWith("/_nuxt") ||
    path.startsWith("/__nuxt")
  ) {
    return;
  }

  const pageContext = getPageContext(event);

  const landingSpuId = pageContext.landingPage?.landingSpuId;
  const countryId = pageContext.country?.id;

  if (!landingSpuId) {
    logger.warn("[04-product] No landingSpuId found in pageContext");
    return;
  }

  if (!countryId) {
    logger.warn("[04-product] No countryId found in pageContext");
    return;
  }

  try {
    const productInfo = await findProductDetailBySpuAndCountry(landingSpuId, countryId);

    if (!productInfo) {
      logger.warn(`[04-product] Product not found for landingSpuId=${landingSpuId}, countryId=${countryId}`);
      return;
    }

    updatePageContext(event, {
      productInfo,
    });
  } catch (error) {
    logger.error("[04-product] Error loading product info:", error);
  }
});
