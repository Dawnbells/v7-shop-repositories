/**
 * 页面主题皮肤 Composable（SSR 安全）
 *
 * 提供主题配置、全局样式、CSS 变量等主题皮肤相关功能
 * 数据从 usePageContext 获取（由中间件注入）
 * 客户端通过 hydration payload 自动获取，不会重新请求
 */

import type { PageData, LayoutData, PageType } from "~/types/component-meta";
import type { GlobalConfig } from "~/types/data-context";
import type { GlobalStyle } from "~/types/theme";

export function usePageTheme() {
  // 从 usePageContext 获取主题数据
  const { themeConfig, siteConfig, variableValues } = usePageContext();

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
