/**
 * 绑定解析 composable
 * 用于在客户端渲染时解析组件的属性绑定和样式绑定
 * 
 * 绑定路径前缀映射：
 * - custom.xxx      -> variableValues[xxx]
 * - globalConfig.xxx -> siteConfig.globalConfig[xxx]
 * - siteConfig.xxx   -> siteConfig[xxx]
 * - globalStyle.xxx  -> siteConfig.globalStyle[xxx]
 * - product.xxx    -> inject('productData')[xxx]
 * - article.xxx    -> inject('articleData')[xxx]
 */

import type { ComponentNode, DataBinding } from '~/types/component-meta'
import type { VariableValues } from '~/types/data-context'

/**
 * 绑定解析上下文
 * 包含所有可绑定的数据源
 */
export interface BindingContext {
  /** 自定义变量值 */
  custom: VariableValues
  /** 站点配置（globalConfig 部分） */
  siteConfig: Record<string, any>
  /** 全局样式 */
  globalStyle: Record<string, any>
  /** 产品数据（产品详情页） */
  product?: Record<string, any>
  /** 文章数据（文章页） */
  article?: Record<string, any>
}

/**
 * 绑定解析 composable
 */
export function useBindingResolver() {
  /**
   * 根据路径从对象获取值
   * 支持点分隔的嵌套路径，如 "product.images[0].url"
   */
  function getValueByPath(obj: Record<string, any> | undefined, path: string): any {
    if (!obj || !path) return undefined

    const keys = path.split('.')
    let result: any = obj

    for (const key of keys) {
      if (result == null) return undefined

      // 支持数组索引，如 items[0]
      const arrayMatch = key.match(/^(\w+)\[(\d+)\]$/)
      if (arrayMatch) {
        const [, arrayKey, indexStr] = arrayMatch
        result = result[arrayKey!]?.[parseInt(indexStr!, 10)]
      } else {
        result = result[key]
      }
    }

    return result
  }

  /**
   * 解析单个绑定
   * 根据 variableKey 的前缀确定数据源
   */
  function resolveBinding(binding: DataBinding, context: BindingContext): any {
    const { variableKey } = binding

    let value: any

    if (variableKey.startsWith('custom.')) {
      // 自定义变量：custom.logo -> variableValues.logo
      value = getValueByPath(context.custom, variableKey.slice(7))
    } else if (variableKey.startsWith('globalConfig.')) {
      value = getValueByPath(context.siteConfig, variableKey.slice(13))
    } else if (variableKey.startsWith('siteConfig.')) {
      // 站点配置：siteConfig.siteName -> siteConfig.siteName
      value = getValueByPath(context.siteConfig, variableKey.slice(11))
    } else if (variableKey.startsWith('globalStyle.')) {
      // 全局样式：globalStyle.primaryColor -> globalStyle.primaryColor
      value = getValueByPath(context.globalStyle, variableKey.slice(12))
    } else if (variableKey.startsWith('product.')) {
      // 产品数据：product.title -> productData.title
      value = getValueByPath(context.product, variableKey.slice(8))
    } else if (variableKey.startsWith('article.')) {
      // 文章数据：article.title -> articleData.title
      value = getValueByPath(context.article, variableKey.slice(8))
    } else {
      // 无前缀，尝试直接从上下文根查找
      value = getValueByPath(context as unknown as Record<string, any>, variableKey)
    }

    return value
  }

  /**
   * 解析节点的所有属性绑定
   * @returns 绑定属性的键值对，用于与原始 props 合并
   */
  function resolveNodeBindings(
    node: ComponentNode,
    context: BindingContext
  ): Record<string, any> {
    if (!node.bindings?.length) return {}

    const result: Record<string, any> = {}

    for (const binding of node.bindings) {
      const value = resolveBinding(binding, context)
      // 只有解析出有效值时才覆盖
      if (value !== undefined) {
        result[binding.propKey] = value
      }
    }

    return result
  }

  /**
   * 解析节点的所有样式绑定
   * @returns 绑定样式的键值对，用于与原始 style 合并
   */
  function resolveNodeStyleBindings(
    node: ComponentNode,
    context: BindingContext
  ): Record<string, any> {
    if (!node.styleBindings?.length) return {}

    const result: Record<string, any> = {}

    for (const binding of node.styleBindings) {
      const value = resolveBinding(binding, context)
      if (value !== undefined) {
        result[binding.propKey] = value
      }
    }

    return result
  }

  return {
    getValueByPath,
    resolveBinding,
    resolveNodeBindings,
    resolveNodeStyleBindings,
  }
}
