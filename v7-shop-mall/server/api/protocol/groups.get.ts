/**
 * 协议组 API
 * GET /api/protocol/groups?id=xxx
 * 
 * 返回协议组及其文章列表
 */

import { findProtocolGroupsByProtocolId } from '../../repositories/protocolRepository'

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

  try {
    const groups = await findProtocolGroupsByProtocolId(protocolId)

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
