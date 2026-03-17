/**
 * QuantitySelector Block 元数据
 * 购买数量选择组件 - 支持增减按钮和直接输入
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "quantityselector",
  name: "数量选择",
  icon: "i-carbon-add-alt",
  category: "business",
  description: "购买数量选择组件，由全局配置控制是否显示",

  allowedPages: ["product-detail"],

  propsSchema: [
    {
      key: "showLabel",
      label: "显示标签",
      type: "switch",
      defaultValue: true,
      description: "是否显示数量标签",
    },
    {
      key: "label",
      label: "标签文本",
      type: "text",
      defaultValue: "数量",
      placeholder: "数量",
      description: "数量选择器的标签文本",
      showIf: "showLabel === true",
    },
    {
      key: "min",
      label: "最小数量",
      type: "number",
      defaultValue: 1,
      min: 1,
      max: 100,
      description: "允许选择的最小数量",
    },
    {
      key: "max",
      label: "最大数量",
      type: "number",
      defaultValue: 999,
      min: 1,
      max: 9999,
      description: "允许选择的最大数量（实际会受库存限制）",
    },
    {
      key: "buttonSize",
      label: "按钮尺寸",
      type: "select",
      defaultValue: "medium",
      options: [
        { label: "小", value: "small" },
        { label: "中", value: "medium" },
        { label: "大", value: "large" },
      ],
      description: "增减按钮的尺寸",
    },
  ],

  defaultProps: {
    showLabel: true,
    label: "数量",
    min: 1,
    max: 999,
    buttonSize: "medium",
  },

  styleSchema: [
    // 容器样式
    {
      key: "--quantity-padding",
      label: "内边距",
      type: "text",
      defaultValue: "12px 0",
      placeholder: "12px 0",
      group: "layout",
    },
    {
      key: "--quantity-gap",
      label: "标签间距",
      type: "text",
      defaultValue: "12px",
      placeholder: "12px",
      group: "layout",
    },
    // 标签样式
    {
      key: "--quantity-label-size",
      label: "标签字号",
      type: "text",
      defaultValue: "14px",
      placeholder: "14px",
      group: "label",
    },
    {
      key: "--quantity-label-weight",
      label: "标签字重",
      type: "select",
      defaultValue: "500",
      options: [
        { label: "正常", value: "400" },
        { label: "中等", value: "500" },
        { label: "粗体", value: "600" },
      ],
      group: "label",
    },
    {
      key: "--quantity-label-color",
      label: "标签颜色",
      type: "color",
      defaultValue: "#374151",
      placeholder: "#374151",
      group: "label",
    },
    // 控件样式
    {
      key: "--quantity-border-color",
      label: "边框颜色",
      type: "color",
      defaultValue: "#e5e7eb",
      placeholder: "#e5e7eb",
      group: "controls",
    },
    {
      key: "--quantity-radius",
      label: "圆角",
      type: "text",
      defaultValue: "6px",
      placeholder: "6px",
      group: "controls",
    },
    {
      key: "--quantity-bg",
      label: "背景色",
      type: "color",
      defaultValue: "#ffffff",
      placeholder: "#ffffff",
      group: "controls",
    },
    // 按钮样式
    {
      key: "--quantity-btn-bg",
      label: "按钮背景色",
      type: "color",
      defaultValue: "#f9fafb",
      placeholder: "#f9fafb",
      group: "button",
    },
    {
      key: "--quantity-btn-color",
      label: "按钮文字色",
      type: "color",
      defaultValue: "#374151",
      placeholder: "#374151",
      group: "button",
    },
    {
      key: "--quantity-btn-hover-bg",
      label: "按钮悬停背景",
      type: "color",
      defaultValue: "#f3f4f6",
      placeholder: "#f3f4f6",
      group: "button",
    },
    // 输入框样式
    {
      key: "--quantity-input-width",
      label: "输入框宽度",
      type: "text",
      defaultValue: "60px",
      placeholder: "60px",
      group: "input",
    },
    {
      key: "--quantity-input-size",
      label: "输入框字号",
      type: "text",
      defaultValue: "14px",
      placeholder: "14px",
      group: "input",
    },
    {
      key: "--quantity-input-weight",
      label: "输入框字重",
      type: "select",
      defaultValue: "500",
      options: [
        { label: "正常", value: "400" },
        { label: "中等", value: "500" },
        { label: "粗体", value: "600" },
      ],
      group: "input",
    },
    {
      key: "--quantity-input-color",
      label: "输入框文字色",
      type: "color",
      defaultValue: "#1f2937",
      placeholder: "#1f2937",
      group: "input",
    },
    {
      key: "--quantity-input-bg",
      label: "输入框背景色",
      type: "color",
      defaultValue: "#ffffff",
      placeholder: "#ffffff",
      group: "input",
    },
  ],

  defaultStyle: {
    base: {
      "--quantity-padding": "12px 0",
      "--quantity-gap": "12px",
      "--quantity-label-size": "14px",
      "--quantity-label-weight": "500",
      "--quantity-label-color": "#374151",
      "--quantity-border-color": "#e5e7eb",
      "--quantity-radius": "6px",
      "--quantity-bg": "#ffffff",
      "--quantity-btn-bg": "#f9fafb",
      "--quantity-btn-color": "#374151",
      "--quantity-btn-hover-bg": "#f3f4f6",
      "--quantity-input-width": "60px",
      "--quantity-input-size": "14px",
      "--quantity-input-weight": "500",
      "--quantity-input-color": "#1f2937",
      "--quantity-input-bg": "#ffffff",
    },
    mobile: {
      "--quantity-gap": "8px",
      "--quantity-input-width": "52px",
    },
  },
  isContainer: false,
  tags: ["数量", "选择", "购买", "quantity", "selector", "number", "stepper"],
};
