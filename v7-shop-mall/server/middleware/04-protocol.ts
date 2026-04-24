/**
 * 协议组加载中间件
 * 根据 landingPage.protocolId 和 currentLanguageId 查询协议组列表
 * 设置 PageContext.protocolGroups
 */

import { findProtocolGroupsByProtocolId } from "../repositories/protocolRepository";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { shouldSkipMiddleware } from "../utils/route-patterns";
import { logger } from "../utils/logger";

export default defineEventHandler(async (event) => {
  const path = event.path;

  if (shouldSkipMiddleware(path)) {
    return;
  }

  const pageContext = getPageContext(event);

  const protocolId = pageContext.landingPage?.protocolId;
  const languageId = pageContext.currentLanguageId;

  if (!protocolId) {
    return;
  }

  if (!languageId) {
    logger.warn("[04-protocol] No languageId found in pageContext");
    return;
  }

  try {
    const groups = await findProtocolGroupsByProtocolId(protocolId, languageId);
    logger.debug("[04-protocol] protocolGroups", groups);
    updatePageContext(event, { protocolGroups: groups });
  } catch (error) {
    logger.error("[04-protocol] Error loading protocol groups:", error);
  }
});
