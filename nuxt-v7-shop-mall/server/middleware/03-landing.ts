/**
 * 落地页配置 Server Middleware
 * 根据 cloak 结果查询对应的落地页配置，将结果注入到 event.context
 *
 * - LAND：查询 LAND 类型的配置，landingProductId = null（前端需根据 spuId + languageId 查询）
 * - CLOAK/CRAWLER/RISK：查询 CLOAK 类型的配置，landingProductId 来自 landing_page_product_id
 * - BLACKLISTED：不处理（在 02-cloak.ts 中已显示安全页面）
 */

import { CloakPage } from "~/types/cloak";
import { SafePageType } from "~/types/page-context";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { showSafePage } from "../utils/safe-page";
import { findLandingPageConfig } from "../cache/landing.cache";

export default defineEventHandler(async (event) => {
  const path = event.path || "";

  // 跳过 builder 路由（编辑器不需要经过 middleware）
  if (path.startsWith("/builder")) {
    return;
  }

  const pageContext = getPageContext(event);

  // 需要 domain、spuId、cloak 都存在
  if (!pageContext.domain?.id || !pageContext.spuId || !pageContext.cloak) {
    console.log("[Landing Middleware] Missing required context, skipping");
    return;
  }

  const cloakPage = pageContext.cloak.page;
  const subDomainId = pageContext.domain.id;
  const spuId = pageContext.spuId;

  // 根据 cloak 类型确定查询的 landing page type
  const landingPageType = cloakPage === CloakPage.LAND ? "LAND" : "CLOAK";

  console.log("[Landing Middleware] Processing:", {
    cloakPage,
    subDomainId,
    spuId,
    landingPageType,
  });

  try {
    // 查询 landing page 配置
    const config = await findLandingPageConfig(
      subDomainId,
      spuId,
      landingPageType
    );

    if (config) {
      console.log(
        "[Landing Middleware] Found config, landingProductId:",
        config.landingProductId
      );
      // 打印 themeConfig 加载状态用于调试
      console.log("[Landing Middleware] themeConfig loaded:", {
        hasThemeConfig: !!config.themeConfig,
        themeConfig: JSON.stringify(config.themeConfig),
      });
      // 更新 pageContext：存入 landingProductId 和渲染配置
      updatePageContext(event, {
        landingProductId: config.landingProductId,
        themeConfig: config.themeConfig,
        siteConfig: config.siteConfig,
        variableValues: config.variableValues,
      });
    } else {
      console.log("[Landing Middleware] No config found");
      // 配置不存在，显示安全页面
      showSafePage(event, SafePageType.PRODUCT_NOT_FOUND, {
        trackingId: pageContext.cloak.pdVal,
      });
      return;
    }
  } catch (error) {
    console.error("[Landing Middleware] Error querying config:", error);
    // 出错时显示产品不存在页面
    showSafePage(event, SafePageType.PRODUCT_NOT_FOUND, {
      trackingId: pageContext.cloak.pdVal,
    });
  }
});
