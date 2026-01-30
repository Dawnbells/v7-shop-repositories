/**
 * 落地页产品信息 Server Middleware
 * 根据 cloak 结果查询对应的落地页产品信息，将结果注入到 event.context
 * 
 * - LAND：直接使用原始 spuId 查询产品信息
 * - CLOAK/CRAWLER/RISK：关联 t_sub_domain_spu_landing_pages 查询落地页产品
 * - BLACKLISTED：不处理（在 02-cloak.ts 中已显示安全页面）
 */

import { CloakPage } from "~/types/cloak";
import { SafePageType } from "~/types/page-context";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { showSafePage } from "../utils/safe-page";
import { findProductBySpuId, findCloakLandingProduct } from "../cache/landing.cache";

export default defineEventHandler(async (event) => {
  const path = event.path || "";

  // 跳过 builder 路由（编辑器不需要经过 middleware）
  if (path.startsWith("/builder")) {
    return;
  }

  const pageContext = getPageContext(event);

  // 需要 domain、spuId、cloak、languages 都存在
  if (
    !pageContext.domain?.id ||
    !pageContext.spuId ||
    !pageContext.cloak ||
    !pageContext.languages?.length
  ) {
    console.log("[Landing Middleware] Missing required context, skipping");
    return;
  }

  const cloakPage = pageContext.cloak.page;

  const subDomainId = pageContext.domain.id;
  const spuId = pageContext.spuId;
  const languageId = pageContext.languages[0].id;

  console.log("[Landing Middleware] Processing:", {
    cloakPage,
    subDomainId,
    spuId,
    languageId,
  });

  try {
    let productInfo = null;

    if (cloakPage === CloakPage.LAND) {
      // LAND：直接使用原始 spuId 查询产品信息
      console.log("[Landing Middleware] LAND - querying product by spuId");
      productInfo = await findProductBySpuId(subDomainId, spuId, languageId);
    } else {
      // CLOAK/CRAWLER/RISK：关联 t_sub_domain_spu_landing_pages 查询
      console.log("[Landing Middleware] CLOAK type - querying landing product");
      productInfo = await findCloakLandingProduct(subDomainId, spuId, languageId);
    }

    if (productInfo) {
      console.log("[Landing Middleware] Found product:", productInfo.id);
      // 打印 themeConfig 加载状态用于调试
      console.log("[Landing Middleware] themeConfig loaded:", {
        hasThemeConfig: !!productInfo.themeConfig,
        themeConfigType: typeof productInfo.themeConfig,
        themeConfigKeys: productInfo.themeConfig ? Object.keys(productInfo.themeConfig) : [],
      });
      // 更新 pageContext
      updatePageContext(event, {
        landingSpuId: productInfo.spuId,
        landingProduct: productInfo,
      });
    } else {
      console.log("[Landing Middleware] No product found");
      // 产品不存在，显示安全页面
      showSafePage(event, SafePageType.PRODUCT_NOT_FOUND, { trackingId: pageContext.cloak.pdVal });
      return;
    }
  } catch (error) {
    console.error("[Landing Middleware] Error querying product:", error);
    // 出错时显示产品不存在页面
    showSafePage(event, SafePageType.PRODUCT_NOT_FOUND, { trackingId: pageContext.cloak.pdVal });
  }
});
