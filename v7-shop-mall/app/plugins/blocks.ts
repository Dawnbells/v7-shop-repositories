/**
 * Blocks 组件注册插件
 * 自动扫描并注册 blocks 目录下的组件
 * 运行环境：SSR + 客户端
 */

import { useBlockRegistry } from '~/composables/useBlockRegistry'

// 使用 Vite 的 import.meta.glob 扫描所有 block 组件
// eager: true 表示同步加载，确保 SSR 时组件可用
const blockModules = import.meta.glob('~/components/blocks/**/*.vue', { eager: true })

export default defineNuxtPlugin(() => {
  const { registerBlock } = useBlockRegistry()

  // 遍历所有扫描到的组件模块
  for (const [path, module] of Object.entries(blockModules)) {
    // 从路径中提取组件类型
    // 例如：~/components/blocks/basic/Text.vue -> text
    // 例如：~/components/blocks/layout/Container.vue -> container
    const type = extractBlockType(path)
    
    if (type && module) {
      // 获取组件的 default 导出
      const component = (module as any).default
      if (component) {
        registerBlock(type, component)
      }
    }
  }
})

/**
 * 从文件路径中提取组件类型
 * @param path 文件路径，如 ~/components/blocks/basic/Text.vue
 * @returns 组件类型，如 text
 */
function extractBlockType(path: string): string | null {
  // 匹配 blocks/xxx/Name.vue 或 blocks/Name.vue
  const match = path.match(/blocks\/(?:[\w-]+\/)?(\w+)\.vue$/)
  if (match && match[1]) {
    // 转换为小写作为类型标识
    return match[1].toLowerCase()
  }
  return null
}
