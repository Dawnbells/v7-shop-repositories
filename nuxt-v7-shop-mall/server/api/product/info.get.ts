/**
 * 产品信息 API
 * 支持两种查询方式：
 * 1. 按 productId 直接查询（用于 CLOAK 类型，已知具体 productId）
 * 2. 按 spuId + languageId 查询（用于 LAND 类型）
 */

import { findProductById, findProductBySpuId } from "../../cache/landing.cache";

export default defineEventHandler(async (event) => {
  const query = getQuery(event);

  const productId = query.productId ? Number(query.productId) : undefined;
  const spuId = query.spuId ? Number(query.spuId) : undefined;
  const languageId = query.languageId ? Number(query.languageId) : undefined;
  const subDomainId = query.subDomainId ? Number(query.subDomainId) : undefined;

  // 方式1：按 productId 直接查询
  if (productId && !isNaN(productId)) {
    console.log("[Product API] Fetching product by productId:", { productId, subDomainId });

    try {
      // 直接按 productId 查询，不需要 spuId 和 languageId
      const productInfo = await findProductById(productId, subDomainId);

      if (!productInfo) {
        throw createError({
          statusCode: 404,
          message: "Product not found",
        });
      }

      return productInfo;
    } catch (error: any) {
      if (error.statusCode) {
        throw error;
      }
      console.error("[Product API] Error fetching product by productId:", error);
      throw createError({
        statusCode: 500,
        message: "Failed to fetch product info",
      });
    }
  }

  // 方式2：按 spuId + languageId 查询（用于 LAND 类型）
  if (!spuId || isNaN(spuId)) {
    throw createError({
      statusCode: 400,
      message: "Missing or invalid spuId or productId parameter",
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
