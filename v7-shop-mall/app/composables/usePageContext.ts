/**
 * 页面上下文 Composable
 * 
 * 在 SSR 时从 event.context.pageTheme 读取中间件注入的数据
 * 初始化 usePageTheme 的 useState 状态
 * 客户端通过 hydration payload 自动恢复，无需操作
 */

import type { ThemeConfig } from '~/types/component-meta'
import type { SiteConfig, VariableValues } from '~/types/data-context'

interface PageThemeContext {
  themeConfig: ThemeConfig | null
  siteConfig: SiteConfig
  variableValues: VariableValues
}

export function usePageContext() {
  const { themeConfig, siteConfig, variableValues } = usePageTheme()

  // 域名是否存在（店铺是否有效）
  const shopNotFound = useState<boolean>('shopNotFound', () => false)

  // SSR 时：从 event.context 读取中间件注入的数据
  if (import.meta.server) {
    const event = useRequestEvent()

    // 检查域名是否存在
    if (!event?.context?.domain) {
      shopNotFound.value = true
    }

    const pageTheme = event?.context?.pageTheme as PageThemeContext | null | undefined

    if (pageTheme) {
      if (themeConfig.value === null) {
        themeConfig.value = pageTheme.themeConfig
      }
      if (Object.keys(siteConfig.value).length === 0 && pageTheme.siteConfig) {
        siteConfig.value = pageTheme.siteConfig
      }
      if (Object.keys(variableValues.value).length === 0 && pageTheme.variableValues) {
        variableValues.value = pageTheme.variableValues
      }
    }
  }

  return {
    themeConfig,
    siteConfig,
    variableValues,
    shopNotFound,
  }
}
