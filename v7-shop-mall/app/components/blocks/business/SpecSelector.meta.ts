/**
 * SpecSelector Block 元数据
 * 规格选择组件 - 按钮式规格选择，支持图片显示
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "specselector",
  name: "规格选择",
  icon: "i-carbon-list-checked",
  category: "business",
  description: "商品规格选择组件，仅在多规格商品下显示",

  allowedPages: ["product-detail"],

  propsSchema: [
    {
      key: "showImage",
      label: "显示图片",
      type: "switch",
      defaultValue: true,
      description: "是否显示规格图片",
    },
    {
      key: "showPriceInfo",
      label: "显示底部价格",
      type: "switch",
      defaultValue: true,
      description: "是否在规格选择下方显示选中规格的价格和库存信息",
    },
    {
      key: "imageSize",
      label: "图片尺寸",
      type: "text",
      defaultValue: "40px",
      placeholder: "40px",
      description: "规格图片的尺寸",
      showIf: "showImage === true",
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
      description: "规格按钮的尺寸",
    },
  ],

  defaultProps: {
    showImage: true,
    imageSize: "40px",
    buttonSize: "medium",
    showPriceInfo: true,
  },

  styleSchema: [
    // 容器样式
    {
      key: "--spec-padding",
      label: "内边距",
      type: "text",
      defaultValue: "16px 0",
      placeholder: "16px 0",
      group: "layout",
    },
    // 分组样式
    {
      key: "--spec-group-gap",
      label: "分组间距",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "layout",
    },
    {
      key: "--spec-label-size",
      label: "标签字号",
      type: "text",
      defaultValue: "14px",
      placeholder: "14px",
      group: "label",
    },
    {
      key: "--spec-label-weight",
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
      key: "--spec-label-color",
      label: "标签颜色",
      type: "color",
      defaultValue: "#374151",
      placeholder: "#374151",
      group: "label",
    },
    {
      key: "--spec-label-gap",
      label: "标签下间距",
      type: "text",
      defaultValue: "8px",
      placeholder: "8px",
      group: "label",
    },
    // 选项样式
    {
      key: "--spec-option-gap",
      label: "选项间距",
      type: "text",
      defaultValue: "8px",
      placeholder: "8px",
      group: "option",
    },
    {
      key: "--spec-option-padding",
      label: "选项内边距",
      type: "text",
      defaultValue: "8px 16px",
      placeholder: "8px 16px",
      group: "option",
    },
    {
      key: "--spec-option-radius",
      label: "选项圆角",
      type: "text",
      defaultValue: "6px",
      placeholder: "6px",
      group: "option",
    },
    {
      key: "--spec-option-border-color",
      label: "选项边框色",
      type: "color",
      defaultValue: "#e5e7eb",
      placeholder: "#e5e7eb",
      group: "option",
    },
    {
      key: "--spec-option-bg",
      label: "选项背景色",
      type: "color",
      defaultValue: "#ffffff",
      placeholder: "#ffffff",
      group: "option",
    },
    {
      key: "--spec-option-color",
      label: "选项文字色",
      type: "color",
      defaultValue: "#374151",
      placeholder: "#374151",
      group: "option",
    },
    {
      key: "--spec-option-font-size",
      label: "选项字号",
      type: "text",
      defaultValue: "14px",
      placeholder: "14px",
      group: "option",
    },
    // 选中样式
    {
      key: "--spec-option-selected-border",
      label: "选中边框色",
      type: "color",
      placeholder: "默认使用全局 primaryColor",
      group: "selected",
    },
    {
      key: "--spec-option-selected-bg",
      label: "选中背景色",
      type: "color",
      placeholder: "rgba(59, 130, 246, 0.05)",
      group: "selected",
    },
    {
      key: "--spec-option-selected-color",
      label: "选中文字色",
      type: "color",
      placeholder: "默认使用全局 primaryColor",
      group: "selected",
    },
    // 价格样式
    {
      key: "--spec-price-gap",
      label: "价格区间距",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "price",
    },
    {
      key: "--spec-price-size",
      label: "价格字号",
      type: "text",
      defaultValue: "20px",
      placeholder: "20px",
      group: "price",
    },
    {
      key: "--spec-price-weight",
      label: "价格字重",
      type: "select",
      defaultValue: "700",
      options: [
        { label: "中等", value: "500" },
        { label: "粗体", value: "600" },
        { label: "加粗", value: "700" },
      ],
      group: "price",
    },
    {
      key: "--spec-price-color",
      label: "价格颜色",
      type: "color",
      placeholder: "默认使用全局 primaryColor",
      group: "price",
    },
    {
      key: "--spec-origin-price-size",
      label: "原价字号",
      type: "text",
      defaultValue: "14px",
      placeholder: "14px",
      group: "price",
    },
    {
      key: "--spec-origin-price-color",
      label: "原价颜色",
      type: "color",
      defaultValue: "#9ca3af",
      placeholder: "#9ca3af",
      group: "price",
    },
  ],

  defaultStyle: {
    base: {
      "--spec-padding": "16px 0",
      "--spec-group-gap": "16px",
      "--spec-label-size": "14px",
      "--spec-label-weight": "500",
      "--spec-label-color": "#374151",
      "--spec-label-gap": "8px",
      "--spec-option-gap": "8px",
      "--spec-option-padding": "8px 16px",
      "--spec-option-radius": "6px",
      "--spec-option-border-color": "#e5e7eb",
      "--spec-option-bg": "#ffffff",
      "--spec-option-color": "#374151",
      "--spec-option-font-size": "14px",
      "--spec-price-gap": "16px",
      "--spec-price-size": "20px",
      "--spec-price-weight": "700",
      "--spec-origin-price-size": "14px",
      "--spec-origin-price-color": "#9ca3af",
    },
    mobile: {
      "--spec-option-padding": "6px 12px",
      "--spec-option-font-size": "13px",
    },
  },

  eventsSchema: [
    {
      event: "specChange",
      label: "规格变更",
      description: "选择规格时触发",
    },
  ],

  isContainer: false,
  tags: ["规格", "选择", "SKU", "属性", "spec", "selector", "variant"],
};
