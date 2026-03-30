/**
 * Blocks 元数据注册插件
 * 自动扫描并注册 blocks 目录下的组件元数据
 * 运行环境：仅客户端，仅 /builder 路由
 * 使用懒加载，meta 代码仅在使用 builder 时加载
 */

import type { ComponentMeta } from '~/types/component-meta'
import { useBlockRegistry } from '~/composables/useBlockRegistry'

const metaModules = import.meta.glob<{ meta: ComponentMeta }>(
  '~/components/blocks/**/*.meta.ts',
)

export default defineNuxtPlugin(async () => {
  const route = useRoute()

  if (!route.path.startsWith('/builder')) {
    return
  }

  const { registerBlockMeta } = useBlockRegistry()

  const results = await Promise.all(
    Object.entries(metaModules).map(async ([, loader]) => {
      const mod = await loader()
      return mod
    }),
  )

  for (const mod of results) {
    const meta = mod?.meta
    if (meta?.type) {
      registerBlockMeta(meta)
    }
  }
})
