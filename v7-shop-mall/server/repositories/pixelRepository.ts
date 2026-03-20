/**
 * Pixel Repository
 * 封装像素账号相关的数据库查询
 */

import { query } from "../utils/db";

/**
 * 像素账号平台类型
 */
export type PixelPlatform = "META" | "GOOGLE" | "TIKTOK";

/**
 * 像素账号状态
 */
export type PixelState = "VALID" | "INVALID" | "WAIT_VALID";

/**
 * 像素追踪类型
 */
export type PixelTrackingType = "GLOBAL" | "CATEGORIES" | "PRODUCTS";

/**
 * 像素账号信息
 */
export interface PixelAccount {
  id: number;
  pixelId: string;
  pixelName: string;
  accessToken: string;
  platform: PixelPlatform;
  state: PixelState;
  trackingType: PixelTrackingType;
  conversionEvent: string;
}

/**
 * 按平台分组的像素账号
 */
export interface PixelsByPlatform {
  meta: PixelAccount[];
  google: PixelAccount[];
  tiktok: PixelAccount[];
}

/**
 * 数据库查询结果行类型
 */
interface PixelAccountRow {
  id: number;
  pixel_id: string;
  pixel_name: string;
  access_token: string;
  platform: PixelPlatform;
  state: PixelState;
  tracking_type: PixelTrackingType;
  conversion_event: string;
}

/**
 * 根据子域名ID和SPU ID查询像素账号列表
 * 通过 t_sub_domain_spu_pixels 关联表查询
 * 只返回状态为 VALID 且未删除的像素账号
 */
export async function findPixelsBySubDomainAndSpu(
  subDomainId: number,
  spuId: number,
): Promise<PixelAccount[]> {
  const sql = `
    SELECT 
      pa.id,
      pa.pixel_id,
      pa.pixel_name,
      pa.access_token,
      pa.platform,
      pa.state,
      pa.tracking_type,
      pa.conversion_event
    FROM t_sub_domain_spu_pixels sdsp
    INNER JOIN t_pixel_accounts pa ON pa.id = sdsp.pixel_id
    WHERE sdsp.sub_domain_id = ? 
      AND sdsp.spu_id = ?
  `;

  const rows = await query<PixelAccountRow>(sql, [subDomainId, spuId]);

  if (!rows || rows.length === 0) {
    return [];
  }

  return rows.map((row) => ({
    id: row.id,
    pixelId: row.pixel_id,
    pixelName: row.pixel_name,
    accessToken: row.access_token,
    platform: row.platform,
    state: row.state,
    trackingType: row.tracking_type,
    conversionEvent: row.conversion_event,
  }));
}

/**
 * 将像素账号列表按平台分组
 */
export function groupPixelsByPlatform(pixels: PixelAccount[]): PixelsByPlatform {
  const result: PixelsByPlatform = {
    meta: [],
    google: [],
    tiktok: [],
  };

  for (const pixel of pixels) {
    const key = pixel.platform.toLowerCase() as keyof PixelsByPlatform;
    if (key in result) {
      result[key].push(pixel);
    }
  }

  return result;
}
