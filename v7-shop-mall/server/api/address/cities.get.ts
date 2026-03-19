/**
 * 城市列表 API
 * GET /api/address/cities?country=xx&province=xxx
 * 根据国家和省份返回城市列表
 */

import { findCitiesByProvince } from '../../repositories/addressRepository'

export default defineEventHandler(async (event): Promise<string[]> => {
  const { country, province } = getQuery<{ country?: string; province?: string }>(event)

  if (!country || !province) {
    return []
  }

  try {
    return await findCitiesByProvince(country, province)
  } catch (error: any) {
    console.error('[Address API] findCitiesByProvince error:', error.message)
    return []
  }
})
