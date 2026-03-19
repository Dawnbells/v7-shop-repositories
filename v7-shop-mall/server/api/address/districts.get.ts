/**
 * 区县列表 API（含邮编）
 * GET /api/address/districts?country=xx&province=xxx&city=xxx
 * 根据国家、省份、城市返回区县列表，每项包含 district 和 postalCode
 */

import { findDistrictsByCity, type DistrictItem } from '../../repositories/addressRepository'

export default defineEventHandler(async (event): Promise<DistrictItem[]> => {
  const { country, province, city } = getQuery<{ country?: string; province?: string; city?: string }>(event)

  if (!country || !province || !city) {
    return []
  }

  try {
    return await findDistrictsByCity(country, province, city)
  } catch (error: any) {
    console.error('[Address API] findDistrictsByCity error:', error.message)
    return []
  }
})
