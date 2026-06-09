/**
 * 数据上下文类型定义
 * 包含变量定义、变量类型、变量值存储等
 */

import type { GlobalStyle } from './theme'

// ============ 数据字段类型 ============

/**
 * 数据字段类型（用于预设数据和可绑定字段）
 */
export type DataFieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'object'
  | 'array'
  | 'image'
  | 'richtext'

// ============ 变量类型 ============

/**
 * 基本变量类型（不可再嵌套）
 * 用于孙类型（子类型的子类型）
 */
export type BasicVariableType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'color'
  | 'image'
  | 'richtext'

/**
 * 所有变量类型
 * 用于顶层变量和子类型
 */
export type VariableType = BasicVariableType | 'enum' | 'array' | 'object'

/**
 * 基本类型列表（用于 UI 选择器）
 */
export const BASIC_VARIABLE_TYPES: BasicVariableType[] = [
  'string',
  'number',
  'boolean',
  'color',
  'image',
  'richtext',
]

/**
 * 所有类型列表（用于 UI 选择器）
 */
export const ALL_VARIABLE_TYPES: VariableType[] = [
  ...BASIC_VARIABLE_TYPES,
  'enum',
  'array',
  'object',
]

// ============ 枚举选项 ============

/**
 * 枚举选项
 */
export interface EnumOption {
  value: string | number
  label: string
}

// ============ 变量字段定义 ============

/**
 * 变量字段定义（用于 object/array 子字段）
 */
export interface VariableFieldSchema {
  key: string
  label: string
  type: VariableType
  required?: boolean
  defaultValue?: any
  description?: string
  enumOptions?: EnumOption[]
  itemType?: VariableType
  itemSchema?: VariableFieldSchema[]
  fields?: VariableFieldSchema[]
}

// ============ 多语言支持 ============

/**
 * 多语言默认值配置
 */
export interface I18nDefaultValue {
  languageId: number
  languageCode: string
  languageName: string
  value: any
}

// ============ 自定义变量 ============

/**
 * 自定义变量
 */
export interface CustomVariable {
  key: string
  label: string
  type: VariableType
  defaultValue?: any
  description?: string
  i18n?: boolean
  i18nLanguages?: number[]
  i18nDefaults?: I18nDefaultValue[]
  enumOptions?: EnumOption[]
  itemType?: VariableType
  itemSchema?: VariableFieldSchema[]
  fields?: VariableFieldSchema[]
}

// ============ 变量值存储 ============

/**
 * 变量值存储（宽松类型，键值对形式）
 * 格式: { variableKey: value }
 */
export type VariableValues = Record<string, any>

/**
 * 多语言变量值存储
 * 格式: { languageId: { variableKey: value } }
 */
export type VariableValuesI18n = Record<number, Record<string, any>>

/**
 * 完整的变量值数据结构
 * 包含默认值和多语言值
 */
export interface VariableValuesData {
  default: VariableValues
  i18n?: VariableValuesI18n
}

// ============ 站点配置 ============

/**
 * 全局配置（业务配置）
 * 包含基本信息、联系方式、页脚、社交媒体、SEO、功能设置等
 */
export interface GlobalConfig {
  siteName?: string
  logo?: string
  favicon?: string
  browserTabTitle?: string
  slogan?: string
  description?: string
  contactEmail?: string
  contactPhone?: string
  whatsapp?: string
  address?: string
  businessHours?: string
  copyright?: string
  icp?: string
  newsletterTitle?: string
  facebook?: string
  twitter?: string
  instagram?: string
  youtube?: string
  tiktok?: string
  linkedin?: string
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
  enableQuantitySelector?: boolean
  enableCart?: boolean
  cartMode?: 'mall' | 'single'
  allowCustomAddress?: boolean
  rtlMode?: boolean
  [key: string]: any
}

/**
 * 站点配置（宽松类型，键值对形式）
 * 格式: { globalConfig: {...}, globalStyle: {...} }
 * - globalConfig: 业务配置（基本信息、联系方式、SEO等）
 * - globalStyle: 样式配置（颜色、字体、圆角、间距等）
 */
export interface SiteConfig {
  [key: string]: any
  globalConfig?: GlobalConfig
  globalStyle?: Partial<GlobalStyle>
}

/**
 * 多语言站点配置存储
 * 格式: { languageId: { configKey: value } }
 */
export type SiteConfigI18n = Record<number, Record<string, any>>
