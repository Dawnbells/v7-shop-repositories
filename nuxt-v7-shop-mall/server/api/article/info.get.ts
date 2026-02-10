/**
 * 文章信息 API
 * 根据 articleId 查询文章内容，并使用落地页协议占位符值替换 {{xxx}}
 *
 * 上下文数据（subDomainId、spuId、landingPageType）从服务端中间件注入的 pageContext 获取
 */

import { CloakPage } from "~/types/cloak";
import { getPageContext } from "../../utils/page-context";
import {
  findArticleById,
  findProtocolPlaceholderValues,
  replacePlaceholders,
} from "../../repositories/article.repository";

export default defineEventHandler(async (event) => {
  const query = getQuery(event);
  const articleId = query.articleId ? Number(query.articleId) : undefined;

  if (!articleId || isNaN(articleId)) {
    throw createError({
      statusCode: 400,
      message: "Missing or invalid articleId parameter",
    });
  }

  console.log("[Article API] Fetching article:", { articleId });

  try {
    // 查询文章
    const article = await findArticleById(articleId);

    if (!article) {
      throw createError({
        statusCode: 404,
        message: "Article not found",
      });
    }

    // 从 pageContext 获取上下文，用于查询占位符值
    const pageContext = getPageContext(event);
    const subDomainId = pageContext.domain?.id;
    const spuId = pageContext.spuId;
    const cloakPage = pageContext.cloak?.page;
    const landingPageType =
      cloakPage === CloakPage.LAND ? "LAND" : "CLOAK";

    // 如果有上下文信息，查询并替换占位符
    if (subDomainId && spuId) {
      const placeholderValues = await findProtocolPlaceholderValues(
        subDomainId,
        spuId,
        landingPageType
      );

      if (placeholderValues) {
        console.log("[Article API] Replacing placeholders:", {
          subDomainId,
          spuId,
          landingPageType,
          placeholderCount: Object.keys(placeholderValues).length,
        });
        article.title = replacePlaceholders(article.title, placeholderValues);
        article.content = replacePlaceholders(
          article.content,
          placeholderValues
        );
      }
    }

    return article;
  } catch (error: any) {
    if (error.statusCode) {
      throw error;
    }

    console.error("[Article API] Error fetching article:", error);
    throw createError({
      statusCode: 500,
      message: "Failed to fetch article info",
    });
  }
});
