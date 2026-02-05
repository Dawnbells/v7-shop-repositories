/**
 * 数据上下文类型定义
 * 包含数据绑定、字段结构、变量定义等
 */

import type { GlobalStyle } from './theme'

// ============ 数据字段类型 ============

/**
 * 数据字段类型
 */
export type DataFieldType =
  | "string"
  | "number"
  | "boolean"
  | "object"
  | "array"
  | "image"
  | "richtext";

/**
 * 数据字段结构（用于生成绑定选择器）
 */
export interface DataFieldSchema {
  key: string;                        // 字段路径，如 "title" 或 "images[0].url"
  label: string;                      // 显示名称
  type: DataFieldType;                // 字段类型
  children?: DataFieldSchema[];       // 嵌套字段（object/array 类型）
  required?: boolean;                 // 是否必填
}

// ============ 属性绑定 ============

/**
 * 属性绑定类型
 */
export type PropBindingType = "static" | "binding";

/**
 * 属性绑定配置
 * 属性值可以是静态值或绑定表达式
 */
export interface PropBinding {
  type: PropBindingType;              // 绑定类型
  value: any;                         // 静态值或解析后的值
  expression?: string;                // 绑定表达式，如 "product.title"
}

/**
 * 创建静态属性绑定
 */
export function createStaticBinding(value: any): PropBinding {
  return {
    type: "static",
    value,
  };
}

/**
 * 创建表达式属性绑定
 */
export function createExpressionBinding(expression: string): PropBinding {
  return {
    type: "binding",
    value: undefined,
    expression,
  };
}

/**
 * 判断是否为绑定对象
 */
export function isPropBinding(value: any): value is PropBinding {
  return (
    value &&
    typeof value === "object" &&
    "type" in value &&
    (value.type === "static" || value.type === "binding")
  );
}

// ============ 变量类型 ============

/**
 * 基本变量类型（不可再嵌套）
 * 用于孙类型（子类型的子类型）
 */
export type BasicVariableType =
  | "string"
  | "number"
  | "boolean"
  | "color"
  | "image"
  | "richtext";

/**
 * 所有变量类型
 * 用于顶层变量和子类型
 */
export type VariableType = BasicVariableType | "enum" | "array" | "object";

/**
 * 基本类型列表（用于 UI 选择器）
 */
export const BASIC_VARIABLE_TYPES: BasicVariableType[] = [
  "string",
  "number",
  "boolean",
  "color",
  "image",
  "richtext",
];

/**
 * 所有类型列表（用于 UI 选择器）
 */
export const ALL_VARIABLE_TYPES: VariableType[] = [
  ...BASIC_VARIABLE_TYPES,
  "enum",
  "array",
  "object",
];

/**
 * 枚举选项
 */
export interface EnumOption {
  value: string | number;     // 选项值
  label: string;              // 显示标签
}

/**
 * 变量字段定义（用于 object/array 子字段）
 */
export interface VariableFieldSchema {
  key: string;                // 字段键名
  label: string;              // 显示标签
  type: VariableType;         // 字段类型
  required?: boolean;         // 是否必填
  defaultValue?: any;         // 默认值
  description?: string;       // 描述
  // enum 类型专用
  enumOptions?: EnumOption[]; // 枚举选项列表
  // array 类型专用
  itemType?: VariableType;    // 数组元素类型（简单类型）
  itemSchema?: VariableFieldSchema[]; // 数组元素结构（复杂类型）
  // object 类型专用
  fields?: VariableFieldSchema[]; // 对象子字段
}

/**
 * 多语言默认值配置
 */
export interface I18nDefaultValue {
  languageId: number;         // 语言 ID
  languageCode: string;       // 语言代码（如 en、zh）
  languageName: string;       // 语言名称（如 English、中文）
  value: any;                 // 该语言的默认值
}

/**
 * 自定义变量
 */
export interface CustomVariable {
  key: string;                // 变量键名
  label: string;              // 显示标签
  type: VariableType;         // 变量类型
  defaultValue?: any;         // 默认值
  description?: string;       // 描述
  // 多语言支持
  i18n?: boolean;             // 是否支持多语言
  i18nLanguages?: number[];   // 支持的语言 ID 列表
  i18nDefaults?: I18nDefaultValue[]; // 各语言的默认值
  // enum 类型专用
  enumOptions?: EnumOption[]; // 枚举选项列表
  // array 类型专用
  itemType?: VariableType;    // 数组元素类型（简单类型，如 string、number）
  itemSchema?: VariableFieldSchema[]; // 数组元素结构（复杂类型，如对象数组）
  // object 类型专用
  fields?: VariableFieldSchema[]; // 对象子字段定义
}

// ============ 数据集定义 ============

/**
 * 数据集（全局定义，可被多个页面复用）
 */
export interface DataSet {
  id: string;                         // 唯一标识
  name: string;                       // 数据集名称，如 "产品数据"
  description?: string;               // 描述说明
  schema: DataFieldSchema[];          // 数据字段结构定义
  mockData: Record<string, any>;      // 设计时的 Mock 数据
}

// 预设数据集配置
export interface PresetDataSetConfig {
  dataSetId: string;                    // 数据集 ID
  mockDataOverride?: Record<string, any>; // Mock 数据覆盖
}

// ============ 数据上下文 ============

// 页面数据上下文
export interface PageDataContext {
  preset?: PresetDataSetConfig;         // 预设数据集
  presets?: PresetDataSetConfig[];      // 多个预设数据集
  variables: CustomVariable[];          // 自定义变量
}

// 全局数据上下文
export interface GlobalDataContext {
  presets: PresetDataSetConfig[];       // 全局预设数据集
  variables: CustomVariable[];          // 全局变量
}

// ============ 可绑定字段 ============

/**
 * 可绑定字段信息
 */
export interface BindableField {
  path: string;                         // 字段路径，如 "product.title"
  label: string;                        // 显示名称
  type: DataFieldType;                  // 字段类型
  source: "preset" | "variable" | "global" | "site"; // 数据来源
  dataSetId?: string;                   // 数据集 ID（如果来自数据集）
}

// ============ 站点配置 ============

/**
 * 站点配置值
 * 字段结构由 SITE_CONFIG_SCHEMA 定义
 */
export interface SiteConfig {
  // 基础信息
  siteName?: string;
  logo?: string;
  favicon?: string;

  // SEO 设置
  seoTitle?: string;
  seoDescription?: string;
  seoKeywords?: string;

  // 社交媒体
  facebook?: string;
  twitter?: string;
  instagram?: string;
  youtube?: string;
  tiktok?: string;
  linkedin?: string;

  // 联系方式
  phone?: string;
  email?: string;
  address?: string;

  // 页脚信息
  copyright?: string;
  icp?: string;

  // 功能设置
  enableMultiLanguage?: boolean;
  enableUserReviews?: boolean;
  enableWishlist?: boolean;

  // 全局皮肤样式
  globalStyle?: Partial<GlobalStyle>;

  // 其他自定义字段
  [key: string]: any;
}

/**
 * 多语言站点配置值
 * 格式: { languageId: { fieldKey: value } }
 */
export type SiteConfigI18n = Record<number, Record<string, any>>;

// ============ 变量值存储 ============

/**
 * 变量值存储（宽松类型，键值对形式）
 * 格式: { variableKey: value }
 */
export type VariableValues = Record<string, any>;

/**
 * 多语言变量值存储
 * 格式: { languageId: { variableKey: value } }
 */
export type VariableValuesI18n = Record<number, Record<string, any>>;

/**
 * 完整的变量值数据结构
 * 包含默认值和多语言值
 */
export interface VariableValuesData {
  /** 默认值（非多语言字段或默认语言） */
  default: VariableValues;
  /** 多语言值 */
  i18n?: VariableValuesI18n;
}

/**
 * 完整的站点配置数据结构
 * 包含默认值和多语言值
 */
export interface SiteConfigData {
  /** 默认值（非多语言字段或默认语言） */
  default: SiteConfig;
  /** 多语言值 */
  i18n?: SiteConfigI18n;
}
