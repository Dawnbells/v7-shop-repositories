/**
 * 像素账号类型定义
 */

/**
 * 像素账号平台类型
 */
export type PixelPlatform = 'META' | 'GOOGLE' | 'TIKTOK' | 'TABOOLA' | 'BIGO';

/**
 * 像素追踪类型
 */
export type PixelTrackingType = 'GLOBAL' | 'CATEGORIES' | 'PRODUCTS';

/**
 * 像素账号信息
 */
export interface PixelAccount {
  id: number;
  pixelId: string;
  pixelName: string;
  accessToken: string;
  platform: PixelPlatform;
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
  taboola: PixelAccount[];
  bigo: PixelAccount[];
}
