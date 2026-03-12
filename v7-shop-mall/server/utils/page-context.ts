/**
 * 页面上下文工具函数
 * 提供类型安全的 context 操作
 */

import type { H3Event } from 'h3'
import type { PageContext, PartialPageContext } from '../types/page-context'
import { createEmptyPageContext } from '../types/page-context'

const PAGE_CONTEXT_KEY = 'pageContext'

/**
 * 获取页面上下文（部分）
 * 用于中间件阶段，域名相关字段可能为 null
 * 如果不存在则创建空的上下文
 */
export function getPartialPageContext(event: H3Event): PartialPageContext {
  if (!event.context[PAGE_CONTEXT_KEY]) {
    event.context[PAGE_CONTEXT_KEY] = createEmptyPageContext()
  }
  return event.context[PAGE_CONTEXT_KEY] as PartialPageContext
}

/**
 * 获取页面上下文（完整）
 * 用于 01-domain.ts 验证通过后，域名相关字段已确保非 null
 * 如果不存在则创建空的上下文
 */
export function getPageContext(event: H3Event): PageContext {
  if (!event.context[PAGE_CONTEXT_KEY]) {
    event.context[PAGE_CONTEXT_KEY] = createEmptyPageContext()
  }
  return event.context[PAGE_CONTEXT_KEY] as PageContext
}

/**
 * 更新页面上下文
 * 合并传入的更新到现有上下文
 */
export function updatePageContext(
  event: H3Event,
  updates: Partial<PartialPageContext>
): void {
  const ctx = getPartialPageContext(event)
  Object.assign(ctx, updates)
}
