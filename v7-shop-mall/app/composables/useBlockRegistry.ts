/**
 * Block 组件注册表
 * 管理可渲染组件和组件元数据的注册与获取
 */

import type { Component } from 'vue'
import type { ComponentMeta } from '~/types/component-meta'

// 组件注册表（SSR + 客户端共享）
const blockComponents = new Map<string, Component>()

// 元数据注册表（仅 /builder 客户端使用）
const blockMetas = new Map<string, ComponentMeta>()

/**
 * Block 组件注册表 composable
 */
export function useBlockRegistry() {
  /**
   * 注册组件
   * @param type 组件类型标识
   * @param component Vue 组件
   */
  function registerBlock(type: string, component: Component) {
    blockComponents.set(type, component)
  }

  /**
   * 批量注册组件
   * @param blocks 组件映射 { type: component }
   */
  function registerBlocks(blocks: Record<string, Component>) {
    for (const [type, component] of Object.entries(blocks)) {
      registerBlock(type, component)
    }
  }

  /**
   * 获取组件
   * @param type 组件类型标识
   * @returns Vue 组件或 undefined
   */
  function getBlock(type: string): Component | undefined {
    return blockComponents.get(type)
  }

  /**
   * 检查组件是否已注册
   * @param type 组件类型标识
   */
  function hasBlock(type: string): boolean {
    return blockComponents.has(type)
  }

  /**
   * 获取所有已注册的组件类型
   */
  function getAllBlockTypes(): string[] {
    return Array.from(blockComponents.keys())
  }

  /**
   * 注册组件元数据
   * @param meta 组件元数据
   */
  function registerBlockMeta(meta: ComponentMeta) {
    blockMetas.set(meta.type, meta)
  }

  /**
   * 批量注册组件元数据
   * @param metas 元数据数组
   */
  function registerBlockMetas(metas: ComponentMeta[]) {
    for (const meta of metas) {
      registerBlockMeta(meta)
    }
  }

  /**
   * 获取组件元数据
   * @param type 组件类型标识
   * @returns 组件元数据或 undefined
   */
  function getBlockMeta(type: string): ComponentMeta | undefined {
    return blockMetas.get(type)
  }

  /**
   * 检查元数据是否已注册
   * @param type 组件类型标识
   */
  function hasBlockMeta(type: string): boolean {
    return blockMetas.has(type)
  }

  /**
   * 获取所有已注册的组件元数据
   * @returns 元数据数组
   */
  function getAllBlockMetas(): ComponentMeta[] {
    return Array.from(blockMetas.values())
  }

  /**
   * 按分类获取组件元数据
   * @param category 组件分类
   * @returns 该分类下的元数据数组
   */
  function getBlockMetasByCategory(category: ComponentMeta['category']): ComponentMeta[] {
    return getAllBlockMetas().filter(meta => meta.category === category)
  }

  /**
   * 获取分组后的组件元数据
   * @returns 按分类分组的元数据
   */
  function getGroupedBlockMetas(): Record<string, ComponentMeta[]> {
    const grouped: Record<string, ComponentMeta[]> = {}
    for (const meta of getAllBlockMetas()) {
      if (!grouped[meta.category]) {
        grouped[meta.category] = []
      }
      grouped[meta.category].push(meta)
    }
    return grouped
  }

  return {
    // 组件相关
    registerBlock,
    registerBlocks,
    getBlock,
    hasBlock,
    getAllBlockTypes,
    // 元数据相关
    registerBlockMeta,
    registerBlockMetas,
    getBlockMeta,
    hasBlockMeta,
    getAllBlockMetas,
    getBlockMetasByCategory,
    getGroupedBlockMetas,
  }
}
