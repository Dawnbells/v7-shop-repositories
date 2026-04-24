/**
 * 文章信息加载中间件
 * 仅在文章详情页 (/article/[id]) 执行
 * 从数据库获取文章详情，设置 PageContext.articleInfo
 */

import { findArticleById } from "../repositories/articleRepository";
import { updatePageContext } from "../utils/page-context";
import { logger } from "../utils/logger";

const ARTICLE_ROUTE = /^\/article\/([\w-]+)(\?.*)?$/;

function getArticleId(path: string): string | null {
  const match = path.match(ARTICLE_ROUTE);
  return match?.[1] ?? null;
}

export default defineEventHandler(async (event) => {
  const path = event.path;

  if (!path.startsWith("/article/")) {
    return;
  }

  const articleId = getArticleId(path);
  if (!articleId) {
    return;
  }

  try {
    const articleInfo = await findArticleById(articleId);
    if (!articleInfo) {
      logger.warn(`[11-article] Article not found for id=${articleId}`);
      return;
    }

    updatePageContext(event, { articleInfo });
  } catch (error) {
    logger.error("[11-article] Error loading article info:", error);
  }
});
