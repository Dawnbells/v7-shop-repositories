/**
 * 服务端 PageContext 工具函数
 * 用于在中间件中初始化和更新 pageContext
 */

import type { H3Event } from "h3";
import type { PageContext } from "~/types/page-context";

/**
 * 获取当前请求的 pageContext
 * @param event H3 事件对象
 */
export function getPageContext(event: H3Event): PageContext {
  if (!event.context.pageContext) {
    event.context.pageContext = {};
  }
  return event.context.pageContext as PageContext;
}

/**
 * 更新 pageContext（合并更新）
 * @param event H3 事件对象
 * @param updates 要更新的字段
 */
export function updatePageContext(
  event: H3Event,
  updates: Partial<PageContext>
): PageContext {
  const current = getPageContext(event);
  const updated = {
    ...current,
    ...updates,
  };
  event.context.pageContext = updated;
  return updated;
}

/**
 * 设置 pageContext 的某个字段
 * @param event H3 事件对象
 * @param key 字段名
 * @param value 字段值
 */
export function setPageContextField<K extends keyof PageContext>(
  event: H3Event,
  key: K,
  value: PageContext[K]
): void {
  const current = getPageContext(event);
  current[key] = value;
  event.context.pageContext = current;
}
