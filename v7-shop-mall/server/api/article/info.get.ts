/**
 * 文章信息 API
 * GET /api/article/info?id=xxx
 * 
 * 返回文章详情，字段与 PRESET_ARTICLE 结构匹配
 * SSR 时由 useAsyncData 直接调用，无 HTTP 开销
 */

import { findArticleById } from '../../repositories/articleRepository'

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
    const article = await findArticleById(id)

    if (!article) {
      throw createError({
        statusCode: 404,
        statusMessage: 'Article not found',
      })
    }

    return {
      success: true,
      data: article,
    }
  } catch (error: any) {
    if (error.statusCode) {
      throw error
    }

    console.error('[Article API] Error:', error)
    throw createError({
      statusCode: 500,
      statusMessage: 'Failed to load article',
    })
  }
})
