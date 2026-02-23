/**
 * Blocks 元数据注册插件
 * 自动扫描并注册 blocks 目录下的组件元数据
 * 运行环境：仅客户端，仅 /builder 路由
 */

import type { ComponentMeta } from '~/types/component-meta'
import { useBlockRegistry } from '~/composables/useBlockRegistry'

// 使用 Vite 的 import.meta.glob 扫描所有 block 元数据文件
// eager: true 表示同步加载
const metaModules = import.meta.glob('~/components/blocks/**/*.meta.ts', { eager: true })

export default defineNuxtPlugin(() => {
  const route = useRoute()
  
  // 仅在 /builder 路由下注册元数据
  // 前台页面不需要元数据，节省内存
  if (!route.path.startsWith('/builder')) {
    return
  }

  const { registerBlockMeta } = useBlockRegistry()

  // 遍历所有扫描到的元数据模块
  for (const [path, module] of Object.entries(metaModules)) {
    if (module) {
      // 获取元数据的 meta 导出
      const meta = (module as any).meta as ComponentMeta | undefined
      if (meta && meta.type) {
        registerBlockMeta(meta)
      }
    }
  }
})
