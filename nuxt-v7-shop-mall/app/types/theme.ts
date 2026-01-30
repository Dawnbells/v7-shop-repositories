/**
 * 主题类型定义
 */

import type { CustomPageSchema, LayoutSchema, PageSchema } from './schema'
import type { GlobalDataContext } from './data-context'

// 主题状态
export type ThemeStatus = 'draft' | 'published' | 'archived'

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

// 页面配置集合
export interface ThemePages {
  layouts: LayoutSchema[]           // 布局列表
  home: PageSchema                  // 首页 - 必选
  product: PageSchema               // 商品落地页 - 必选
  orderResult: PageSchema           // 订单结果页 - 必选
  article: PageSchema               // 文章协议页 - 必选
  checkout?: PageSchema             // 收银台 - 可选
  custom: CustomPageSchema[]        // 自定义页面 - 可多个
}

// i18n 变量值存储格式：{ pageKey: { locale: { varKey: value } } }
export type I18nValues = Record<string, Record<string, Record<string, any>>>

// 主题 Schema
export interface ThemeSchema {
  id: string
  name: string                      // 主题名称
  description?: string              // 主题描述
  version: string                   // 版本号
  status: ThemeStatus               // 状态
  thumbnail?: string                // 缩略图

  // 全局样式
  globalStyle: GlobalStyle

  // 全局数据配置
  globalData?: GlobalDataContext    // 全局数据上下文

  // i18n 变量值（格式：{ pageKey: { locale: { varKey: value } } }）
  i18nValues?: I18nValues           // i18n 变量的值

  // 页面配置
  pages: ThemePages

  // 时间戳
  createdAt: string
  updatedAt: string
}

// 必选页面类型
export const REQUIRED_PAGE_TYPES = ['home', 'product', 'orderResult', 'article'] as const
export type RequiredPageType = typeof REQUIRED_PAGE_TYPES[number]

// 可选页面类型
export const OPTIONAL_PAGE_TYPES = ['checkout'] as const
export type OptionalPageType = typeof OPTIONAL_PAGE_TYPES[number]

// 页面类型标签
export const PAGE_TYPE_LABELS: Record<RequiredPageType | OptionalPageType, string> = {
  home: '首页',
  product: '商品页',
  orderResult: '订单结果',
  article: '文章页',
  checkout: '收银台'
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

// 创建默认布局（空布局，需要手动拖拽添加 Page Slot）
export function createDefaultLayout(id: string): LayoutSchema {
  return {
    id: `${id}-layout-default`,
    name: 'default',
    components: []
  }
}

// 创建空主题
export function createEmptyTheme(id: string, name: string): ThemeSchema {
  const now = new Date().toISOString()

  return {
    id,
    name,
    version: '1.0.0',
    status: 'draft',
    globalStyle: createDefaultGlobalStyle(),
    globalData: {
      presets: [],
      variables: []
    },
    pages: {
      layouts: [createDefaultLayout(id)],
      home: {
        id: `${id}-home`,
        name: '首页',
        pageType: 'home',
        components: [],
        meta: { title: '首页' }
      },
      product: {
        id: `${id}-product`,
        name: '商品页',
        pageType: 'product',
        components: [],
        meta: { title: '商品详情' }
      },
      orderResult: {
        id: `${id}-order-result`,
        name: '订单结果',
        pageType: 'order-result',
        components: [],
        meta: { title: '订单结果' }
      },
      article: {
        id: `${id}-article`,
        name: '文章页',
        pageType: 'article',
        components: [],
        meta: { title: '文章详情' }
      },
      custom: []
    },
    createdAt: now,
    updatedAt: now
  }
}
