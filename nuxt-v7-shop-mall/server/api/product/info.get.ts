/**
 * 产品信息 API
 * 根据 SPU ID 和语言 ID 查询产品详细信息
 */

import { findProductBySpuId } from "../../cache/landing.cache";

export default defineEventHandler(async (event) => {
  const query = getQuery(event);

  const spuId = Number(query.spuId);
  const languageId = Number(query.languageId);
  const subDomainId = query.subDomainId ? Number(query.subDomainId) : undefined;

  // 参数验证
  if (!spuId || isNaN(spuId)) {
    throw createError({
      statusCode: 400,
      message: "Missing or invalid spuId parameter",
    });
  }

  if (!languageId || isNaN(languageId)) {
    throw createError({
      statusCode: 400,
      message: "Missing or invalid languageId parameter",
    });
  }

  console.log("[Product API] Fetching product info:", { spuId, languageId, subDomainId });

  try {
    // 使用缓存层查询产品信息
    // 如果提供了 subDomainId，则使用它来获取更精确的缓存
    const productInfo = await findProductBySpuId(
      subDomainId || 0, // 如果没有 subDomainId，使用 0 作为通用缓存 key
      spuId,
      languageId
    );

    if (!productInfo) {
      throw createError({
        statusCode: 404,
        message: "Product not found",
      });
    }

    return productInfo;
  } catch (error: any) {
    // 如果是已知的 HTTP 错误，直接抛出
    if (error.statusCode) {
      throw error;
    }

    console.error("[Product API] Error fetching product:", error);
    throw createError({
      statusCode: 500,
      message: "Failed to fetch product info",
    });
  }
});
