/**
 * 组件元数据类型定义
 * 用于描述组件的可编辑属性和样式
 */

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

// 选项
export interface SelectOption {
  label: string;
  value: any;
}
