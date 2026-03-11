/**
 * 产品信息 API
 * GET /api/product/info?id=xxx
 * 
 * 返回产品详情，字段与 PRESET_PRODUCT 结构匹配
 * SSR 时由 useAsyncData 直接调用，无 HTTP 开销
 */

import { findProductDetail, type ProductDetail } from '../../repositories/productRepository'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const id = query.id as string

  if (!id) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing required query parameter: id',
    })
  }

  try {
    const product = await findProductDetail(id)

    if (!product) {
      throw createError({
        statusCode: 404,
        statusMessage: 'Product not found',
      })
    }

    return {
      success: true,
      data: product,
    }
  } catch (error: any) {
    if (error.statusCode) {
      throw error
    }

    console.error('[Product API] Error:', error)
    throw createError({
      statusCode: 500,
      statusMessage: 'Failed to load product',
    })
  }
})
