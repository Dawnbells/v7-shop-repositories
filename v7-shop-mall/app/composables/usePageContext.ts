/**
 * 页面上下文 Composable
 * 
 * 在 SSR 时从 event.context.pageContext 读取中间件注入的数据
 * 初始化 usePageTheme 的 useState 状态
 * 客户端通过 hydration payload 自动恢复，无需操作
 */

import type { ThemeConfig } from '~/types/component-meta'
import type { SiteConfig, VariableValues } from '~/types/data-context'
import type { SafePageType } from '~/types/safe-page'

interface PageContext {
  safePageType: SafePageType | null
  trackingId: string | null
  pageTheme: PageThemeContext | null
}

interface PageThemeContext {
  themeConfig: ThemeConfig | null
  siteConfig: SiteConfig
  variableValues: VariableValues
}

export function usePageContext() {
  const { themeConfig, siteConfig, variableValues } = usePageTheme()

  // 安全页面类型
  const safePageType = useState<SafePageType | null>('safePageType', () => null)
  // 追踪 ID
  const trackingId = useState<string | null>('trackingId', () => null)

  // SSR 时：从 event.context.pageContext 读取中间件注入的数据
  if (import.meta.server) {
    const event = useRequestEvent()
    const pageContext = event?.context?.pageContext as PageContext | null | undefined

    // 读取安全页面类型和追踪 ID
    if (pageContext?.safePageType) {
      safePageType.value = pageContext.safePageType
      trackingId.value = pageContext.trackingId ?? null
    }

    const pageTheme = pageContext?.pageTheme as PageThemeContext | null | undefined

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
    safePageType,
    trackingId,
  }
}
