/**
 * 页面主题数据 Composable（SSR 安全）
 * 
 * 使用 useState 实现请求级作用域的状态管理
 * 数据由 usePageContext() 从 event.context.pageTheme 初始化
 * 客户端通过 hydration payload 自动获取，不会重新请求
 */

import type { ThemeConfig, PageData, LayoutData, PageType } from '~/types/component-meta'
import type { SiteConfig, VariableValues, GlobalConfig } from '~/types/data-context'
import type { GlobalStyle } from '~/types/theme'

export function usePageTheme() {
  // SSR 安全：useState 在服务端是请求级作用域的
  const themeConfig = useState<ThemeConfig | null>('pageThemeConfig', () => null)
  const siteConfig = useState<SiteConfig>('pageSiteConfig', () => ({}))
  const variableValues = useState<VariableValues>('pageVariableValues', () => ({}))

  // 全局样式
  const globalStyle = computed<Partial<GlobalStyle>>(() => {
    return siteConfig.value?.globalStyle || {}
  })

  // 全局配置
  const globalConfig = computed<Partial<GlobalConfig>>(() => {
    return siteConfig.value?.globalConfig || {}
  })

  // 全局样式转 CSS 变量
  const cssVariables = computed(() => {
    const style = globalStyle.value
    return {
      '--primary-color': style.primaryColor || '#3b82f6',
      '--secondary-color': style.secondaryColor || '#64748b',
      '--success-color': style.successColor || '#22c55e',
      '--warning-color': style.warningColor || '#f59e0b',
      '--error-color': style.errorColor || '#ef4444',
      '--background-color': style.backgroundColor || '#f8fafc',
      '--surface-color': style.surfaceColor || '#ffffff',
      '--text-color': style.textColor || '#1e293b',
      '--text-secondary-color': style.textSecondaryColor || '#64748b',
      '--border-color': style.borderColor || '#e2e8f0',
      '--font-family': style.fontFamily || 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
      '--font-size-base': style.fontSizeBase || '14px',
      '--line-height': style.lineHeight || '1.5',
      '--border-radius-small': style.borderRadiusSmall || '4px',
      '--border-radius-medium': style.borderRadiusMedium || '8px',
      '--border-radius-large': style.borderRadiusLarge || '12px',
      '--spacing-unit': style.spacingUnit || '8px',
    }
  })

  /**
   * 按页面类型查找页面 schema
   */
  function getPageSchema(pageType: PageType): PageData | undefined {
    if (!themeConfig.value?.pages) return undefined
    return themeConfig.value.pages.find(page => page.type === pageType)
  }

  /**
   * 按 ID 查找布局 schema
   */
  function getLayoutSchema(layoutId: string): LayoutData | undefined {
    if (!themeConfig.value?.layouts) return undefined
    return themeConfig.value.layouts.find(layout => layout.id === layoutId)
  }

  /**
   * 检查是否有主题配置
   */
  const hasThemeConfig = computed(() => !!themeConfig.value)

  return {
    // 状态
    themeConfig,
    siteConfig,
    variableValues,

    // 计算属性
    globalStyle,
    globalConfig,
    cssVariables,
    hasThemeConfig,

    // 方法
    getPageSchema,
    getLayoutSchema,
  }
}
