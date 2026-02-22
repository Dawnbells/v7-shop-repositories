/**
 * 主题类型定义
 */

// 全局样式变量
export interface GlobalStyle {
  // 颜色
  primaryColor: string              // 主色
  secondaryColor: string            // 辅色
  successColor: string              // 成功色
  warningColor: string              // 警告色
  errorColor: string                // 错误色
  backgroundColor: string           // 页面背景色
  surfaceColor: string              // 卡片/表面背景色
  textColor: string                 // 主文字色
  textSecondaryColor: string        // 次要文字色
  borderColor: string               // 边框色

  // 字体
  fontFamily: string                // 主字体
  fontSizeBase: string              // 基础字号
  lineHeight: string                // 行高

  // 圆角
  borderRadiusSmall: string         // 小圆角
  borderRadiusMedium: string        // 中圆角
  borderRadiusLarge: string         // 大圆角

  // 间距
  spacingUnit: string               // 间距基础单位
}

// 创建默认全局样式
export function createDefaultGlobalStyle(): GlobalStyle {
  return {
    primaryColor: '#3b82f6',
    secondaryColor: '#64748b',
    successColor: '#22c55e',
    warningColor: '#f59e0b',
    errorColor: '#ef4444',
    backgroundColor: '#f8fafc',
    surfaceColor: '#ffffff',
    textColor: '#1e293b',
    textSecondaryColor: '#64748b',
    borderColor: '#e2e8f0',
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
    fontSizeBase: '14px',
    lineHeight: '1.5',
    borderRadiusSmall: '4px',
    borderRadiusMedium: '8px',
    borderRadiusLarge: '12px',
    spacingUnit: '8px'
  }
}
