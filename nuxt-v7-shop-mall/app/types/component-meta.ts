/**
 * 组件元数据类型定义
 * 用于描述组件的可编辑属性和样式
 */

import type { EventTrigger } from "./events";
import type { ResponsiveStyle } from "./schema";

// 组件分类
export type ComponentCategory =
  | "basic"
  | "layout"
  | "business"
  | "marketing"
  | "form";

// 属性编辑器类型
export type PropEditorType =
  | "text" // 单行文本
  | "textarea" // 多行文本
  | "number" // 数字
  | "switch" // 开关
  | "select" // 下拉选择
  | "radio" // 单选
  | "color" // 颜色选择器
  | "image" // 图片上传
  | "richtext" // 富文本
  | "json" // JSON 编辑器
  | "icon"; // 图标选择器

// 样式编辑器类型
export type StyleEditorType =
  | "size" // 尺寸输入（带单位）
  | "spacing" // 间距（margin/padding）
  | "color" // 颜色
  | "select" // 下拉选择
  | "slider" // 滑块
  | "border" // 边框
  | "shadow" // 阴影
  | "font"; // 字体

// 选项
export interface SelectOption {
  label: string;
  value: any;
}

// 校验规则
export interface ValidationRule {
  type: "required" | "min" | "max" | "pattern" | "custom";
  value?: any;
  message: string;
}

// 属性 Schema
export interface PropSchema {
  key: string; // 属性键名
  label: string; // 显示标签
  type: PropEditorType; // 编辑器类型
  defaultValue?: any; // 默认值
  placeholder?: string; // 占位符
  options?: SelectOption[]; // 下拉选项
  min?: number; // 最小值（number 类型常用）
  max?: number; // 最大值（number 类型常用）
  step?: number; // 步进值（number 类型常用）
  rules?: ValidationRule[]; // 校验规则
  group?: string; // 分组名称
  description?: string; // 描述说明
  visible?: (props: Record<string, any>) => boolean; // 条件显示
}

// 样式分组
export type StyleGroup =
  | "size"
  | "spacing"
  | "typography"
  | "background"
  | "border"
  | "effect";

// 数据编辑器类型
export type DataEditorType =
  | "list" // 列表编辑器（如通知栏的 items）
  | "tree" // 树形编辑器（如菜单）
  | "table" // 表格编辑器
  | "keyValue"; // 键值对编辑器

// 数据项字段定义
export interface DataItemField {
  key: string; // 字段键名
  label: string; // 显示标签
  type: "text" | "icon" | "image" | "number" | "switch" | "select"; // 字段类型
  required?: boolean; // 是否必填
  defaultValue?: any; // 默认值
  placeholder?: string; // 占位符
  options?: SelectOption[]; // 下拉选项（select 类型）
  suggestions?: string[]; // 建议值列表（如图标建议）
}

// 数据 Schema（用于定义组件的数据编辑配置）
export interface DataSchema {
  key: string; // 对应 props 中的属性键名
  label: string; // 显示标签
  type: DataEditorType; // 编辑器类型
  description?: string; // 描述说明
  itemFields: DataItemField[]; // 数据项的字段定义
  defaultItem?: Record<string, any>; // 新增项的默认值
  minItems?: number; // 最小项数
  maxItems?: number; // 最大项数
  bindingHint?: string; // 数据绑定时的提示信息
}

// 样式 Schema
export interface StyleSchema {
  key: string; // CSS 属性名
  label: string; // 显示标签
  type: StyleEditorType; // 编辑器类型
  defaultValue?: any; // 默认值
  options?: SelectOption[]; // 下拉选项
  unit?: string; // 单位，如 'px', '%', 'rem'
  min?: number; // 最小值
  max?: number; // 最大值
  step?: number; // 步进值
  group?: StyleGroup; // 分组
  responsive?: boolean; // 是否支持响应式配置
}

// 组件元数据
export interface ComponentMeta {
  type: string; // 组件类型标识
  name: string; // 显示名称
  icon: string; // 图标（UnoCSS 图标类名）
  category: ComponentCategory; // 分类
  description?: string; // 组件描述

  // 属性和样式定义
  propsSchema: PropSchema[]; // 可编辑属性定义
  styleSchema: StyleSchema[]; // 可编辑样式定义
  dataSchema?: DataSchema; // 数据编辑定义（可选，用于复杂数据结构的编辑）

  // 事件支持
  supportEvents: EventTrigger[]; // 支持的事件触发方式

  // 默认值
  defaultProps: Record<string, any>; // 默认属性
  defaultStyle: ResponsiveStyle; // 默认样式

  // 容器相关
  isContainer?: boolean; // 是否为容器组件
  allowChildren?: string[]; // 允许的子组件类型（空数组表示允许所有）
  maxChildren?: number; // 最大子组件数量

  // 布局专用
  layoutOnly?: boolean; // 是否仅在布局编辑时可用
}

// 组件分类标签
export const COMPONENT_CATEGORY_LABELS: Record<ComponentCategory, string> = {
  basic: "基础组件",
  layout: "布局组件",
  business: "业务组件",
  marketing: "营销组件",
  form: "表单组件",
};

// 样式分组标签
export const STYLE_GROUP_LABELS: Record<StyleGroup, string> = {
  size: "尺寸",
  spacing: "间距",
  typography: "文字",
  background: "背景",
  border: "边框",
  effect: "效果",
};
