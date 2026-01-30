import { readFileSync } from "fs";
import { resolve } from "path";
import { SafePageType } from "~/types/page-context";
import type { H3Event } from "h3";

// 缓存安全页面 HTML
let safePageHtml: string | null = null;

/**
 * 获取安全页面 HTML
 * @param type 安全页面类型
 */
export function getSafePageHtml(type: SafePageType = SafePageType.SHOP_CLOSED): string {
  if (!safePageHtml) {
    const safePath = resolve(process.cwd(), "public/safe-page.html");
    safePageHtml = readFileSync(safePath, "utf-8");
  }
  // 注入页面类型到 body 的 data-type 属性
  return safePageHtml!.replace("<body>", `<body data-type="${type}">`);
}

/**
 * 显示安全页面并结束响应
 * 调用后会中断后续中间件的执行
 * @param event H3 事件对象
 * @param type 安全页面类型
 * @param options 可选配置
 */
export function showSafePage(
  event: H3Event,
  type: SafePageType,
  options?: {
    /** 追踪 ID，会注入到页面中 */
    trackingId?: string;
  }
): void {
  let html = getSafePageHtml(type);

  // 注入追踪 ID
  if (options?.trackingId) {
    html = html.replace(
      "</body>",
      `<div style="display:none" data-pd="${options.trackingId}"></div></body>`
    );
  }

  // 标记事件已处理，中断后续中间件
  event._handled = true;

  event.node.res.setHeader("Content-Type", "text/html; charset=utf-8");
  event.node.res.setHeader("X-Robots-Tag", "noindex, nofollow");
  event.node.res.statusCode = 200;
  event.node.res.end(html);
}
