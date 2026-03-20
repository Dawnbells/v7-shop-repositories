/**
 * 像素账号加载中间件
 * 根据 subDomainId 和 spuId 从数据库查询像素账号列表
 * 设置 PageContext.pixels
 *
 * 加载条件：
 * 1. 路径为 /product/ 或 /order-result
 * 2. cloak.page === CloakPage.LAND（只在真实落地页加载）
 */

import { findPixelsBySubDomainAndSpu, groupPixelsByPlatform } from "../repositories/pixelRepository";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { CloakPage } from "../types/cloak";
import { logger } from "../utils/logger";

export default defineEventHandler(async (event) => {
  const path = event.path;

  // 跳过 API 路由和编辑器路由
  if (
    path.startsWith("/api/") ||
    path.startsWith("/builder") ||
    path.startsWith("/_nuxt") ||
    path.startsWith("/__nuxt")
  ) {
    return;
  }

  // 只有产品页和订单结果页才加载 pixels
  const isProductPage = path.startsWith("/product/");
  const isOrderResultPage = path.startsWith("/order-result");
  if (!isProductPage && !isOrderResultPage) {
    return;
  }

  const pageContext = getPageContext(event);

  // 只有 LAND 页面才加载 pixels
  if (pageContext.cloak.page !== CloakPage.LAND) {
    return;
  }

  const subDomainId = pageContext.subDomain?.id;
  const spuId = pageContext.spuId;

  if (!subDomainId) {
    logger.warn("[05-pixels] No subDomainId found in pageContext");
    return;
  }

  if (!spuId) {
    logger.warn("[05-pixels] No spuId found in pageContext");
    return;
  }

  try {
    const pixelList = await findPixelsBySubDomainAndSpu(subDomainId, spuId);
    const pixels = groupPixelsByPlatform(pixelList);
    logger.debug("[05-pixels] pixels loaded", {
      meta: pixels.meta.length,
      google: pixels.google.length,
      tiktok: pixels.tiktok.length,
    });
    updatePageContext(event, { pixels });
  } catch (error) {
    logger.error("[05-pixels] Error loading pixels:", error);
  }
});
