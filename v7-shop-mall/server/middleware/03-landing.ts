/**
 * 主题配置加载中间件
 * 根据 cloak.page 结果从数据库加载对应的 themeConfig、siteConfig、variableValues
 * 设置 PageContext.pageTheme 和 PageContext.landingPage
 */

import { findLandingPageConfig } from "../repositories/landingPageRepository";
import { getPageContext, updatePageContext } from "../utils/page-context";
import type { PageTheme, LandingPageInfo } from "../types/page-context";
import { CloakPage } from "../types/cloak";
import { shouldSkipMiddleware } from "../utils/route-patterns";
import { logger } from "../utils/logger";

type LandingPageType = "LAND" | "CLOAK" | "BLACKLISTED";

/**
 * 将 CloakPage 映射为数据库中的 landing_page_type
 * CRAWLER 和 RISK 映射为 CLOAK
 */
function mapCloakPageToLandingType(cloakPage: CloakPage): LandingPageType {
  switch (cloakPage) {
    case CloakPage.LAND:
      return "LAND";
    case CloakPage.CLOAK:
    case CloakPage.CRAWLER:
    case CloakPage.RISK:
      return "CLOAK";
    case CloakPage.BLACKLISTED:
      return "BLACKLISTED";
    default:
      return "CLOAK";
  }
}

export default defineEventHandler(async (event) => {
  const path = event.path;

  if (shouldSkipMiddleware(path)) {
    return;
  }

  const pageContext = getPageContext(event);

  const subDomainId = pageContext.subDomain.id;
  const spuId = pageContext.spuId ?? 0;
  const landingPageType = mapCloakPageToLandingType(pageContext.cloak.page);

  try {
    const config = await findLandingPageConfig(
      subDomainId,
      spuId,
      landingPageType,
    );
    if (config) {
      updatePageContext(event, {
        pageTheme: {
          themeConfig: config.themeConfig,
          siteConfig: config.siteConfig,
          variableValues: config.variableValues,
        } as PageTheme,
        landingPage: {
          landingSpuId: config.landingSpuId,
          protocolId: config.protocolId,
          protocolPlaceholderValues: config.protocolPlaceholderValues,
          variableSchema: config.variableSchema,
        } as LandingPageInfo,
      });
    }
  } catch (error) {
    logger.error("[03-landing] Error loading theme config:", error);
  }
});
