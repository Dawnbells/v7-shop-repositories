/**
 * 协议组 API
 * GET /api/protocol/groups?id=xxx&languageId=yyy
 * 
 * 返回协议组及其文章列表
 * languageId 可选，如果不传则从 PageContext 获取当前语言
 */

import { findProtocolGroupsByProtocolId } from '../../repositories/protocolRepository'
import { getPageContext } from '../../utils/page-context'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const id = query.id as string

  if (!id) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing required query parameter: id',
    })
  }

  const protocolId = parseInt(id, 10)
  if (isNaN(protocolId)) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Invalid protocol id',
    })
  }

  // 获取语言ID：优先从 URL 参数获取，其次从 PageContext 获取
  let languageId: number | null = null

  const queryLanguageId = query.languageId as string
  if (queryLanguageId) {
    languageId = parseInt(queryLanguageId, 10)
    if (isNaN(languageId)) {
      languageId = null
    }
  }

  if (!languageId) {
    const pageContext = getPageContext(event)
    languageId = pageContext.currentLanguageId
  }

  if (!languageId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing language id',
    })
  }

  try {
    const groups = await findProtocolGroupsByProtocolId(protocolId, languageId)

    return {
      success: true,
      data: {
        groups,
      },
    }
  } catch (error: any) {
    console.error('[Protocol API] Error:', error)
    throw createError({
      statusCode: 500,
      statusMessage: 'Failed to load protocol groups',
    })
  }
})
