/**
 * 邮编列表 API
 * GET /api/address/postal-codes?country=xx&province=xxx&city=xxx
 * 根据国家、省份、城市返回邮编列表（用于无区县但有邮编的场景）
 */

import { findPostalCodesByCity } from '../../repositories/addressRepository'

export default defineEventHandler(async (event): Promise<string[]> => {
  const { country, province, city } = getQuery<{ country?: string; province?: string; city?: string }>(event)

  if (!country || !province || !city) {
    return []
  }

  try {
    return await findPostalCodesByCity(country, province, city)
  } catch (error: any) {
    console.error('[Address API] findPostalCodesByCity error:', error.message)
    return []
  }
})
