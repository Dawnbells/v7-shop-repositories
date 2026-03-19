/**
 * 省份列表 API
 * GET /api/address/provinces?country=xx
 * 根据国家代码查询省份列表
 */

import { findProvinces } from "../../repositories/addressRepository";

export default defineEventHandler(async (event): Promise<string[]> => {
  const { country } = getQuery<{ country?: string }>(event);

  if (!country) {
    return [];
  }

  try {
    return await findProvinces(country);
  } catch (error: any) {
    console.error("[Address API] findProvinces error:", error.message);
    return [];
  }
});
