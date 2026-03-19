/**
 * 地址 Repository
 * 封装省市区邮编级联查询，表名根据国家代码动态拼接
 */

import { addressQuery } from "../utils/address-db";

export interface DistrictItem {
  district: string;
  postalCode: string;
}

/**
 * 校验国家代码格式（仅允许 2-3 位字母，防止 SQL 注入）
 */
function validateCountryCode(code: string): string {
  const sanitized = code.toLowerCase().trim();
  if (!/^[a-z]{2,3}$/.test(sanitized)) {
    throw new Error(`Invalid country code: ${code}`);
  }
  return sanitized;
}

/**
 * 获取省份列表（去重）
 */
export async function findProvinces(countryCode: string): Promise<string[]> {
  const suffix = validateCountryCode(countryCode);
  const sql = `
    SELECT DISTINCT province 
    FROM t_addresses_${suffix}
    WHERE status = 'VALID'
    ORDER BY province ASC
  `;
  console.log(sql);
  const rows = await addressQuery<{ province: string }>(sql);
  return rows.map((r) => r.province);
}

/**
 * 根据省份获取城市列表（去重）
 */
export async function findCitiesByProvince(
  countryCode: string,
  province: string,
): Promise<string[]> {
  const suffix = validateCountryCode(countryCode);
  const sql = `
    SELECT DISTINCT city 
    FROM t_addresses_${suffix}
    WHERE status = 'VALID' AND province = ?
    ORDER BY city ASC
  `;
  const rows = await addressQuery<{ city: string }>(sql, [province]);
  return rows.map((r) => r.city);
}

/**
 * 根据省份+城市获取区县列表（含邮编，去重）
 */
export async function findDistrictsByCity(
  countryCode: string,
  province: string,
  city: string,
): Promise<DistrictItem[]> {
  const suffix = validateCountryCode(countryCode);
  const sql = `
    SELECT DISTINCT district, postal_code AS postalCode
    FROM t_addresses_${suffix}
    WHERE status = 'VALID' AND province = ? AND city = ?
    ORDER BY district ASC
  `;
  return addressQuery<DistrictItem>(sql, [province, city]);
}
