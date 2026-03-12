import { SafePageType } from "../types/safe-page";

// 重新导出 SafePageType 方便外部使用
export { SafePageType } from "../types/safe-page";

// SafePageType 到 HTTP 状态码的映射
const STATUS_CODE_MAP: Record<SafePageType, number> = {
  [SafePageType.SHOP_NOT_FOUND]: 404,
  [SafePageType.SHOP_CLOSED]: 403,
  [SafePageType.PRODUCT_NOT_FOUND]: 404,
};

/**
 * 显示安全页面
 * 抛出 Nuxt 错误，由 error.vue 渲染对应的安全页面
 * @param type 安全页面类型
 * @param options 可选配置
 */
export function showSafePage(
  type: SafePageType,
  options?: {
    /** 追踪 ID，用于风控追踪 */
    trackingId?: string;
  },
): never {
  throw createError({
    statusCode: STATUS_CODE_MAP[type] ?? 404,
    message: type,
    data: {
      type,
      trackingId: options?.trackingId,
    },
  });
}
