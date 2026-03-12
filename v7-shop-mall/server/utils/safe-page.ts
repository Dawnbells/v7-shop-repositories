import type { H3Event } from "h3";
import { SafePageType } from "../types/safe-page";
import { updatePageContext } from "./page-context";

// 重新导出 SafePageType 方便外部使用
export { SafePageType } from "../types/safe-page";

/**
 * 设置安全页面类型
 * 设置后，前端 app.vue 会根据 safePageType 渲染 SafePage 组件
 * @param event H3 事件对象
 * @param type 安全页面类型
 * @param options 可选配置
 */
export function showSafePage(
  event: H3Event,
  type: SafePageType,
  options?: {
    /** 追踪 ID，用于风控追踪 */
    trackingId?: string;
  },
): void {
  updatePageContext(event, {
    safePageType: type,
    trackingId: options?.trackingId ?? null,
  });
}
