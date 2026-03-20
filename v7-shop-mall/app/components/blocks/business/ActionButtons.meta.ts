/**
 * ActionButtons Block 元数据
 * 购买操作按钮组件 - 加入购物车和立即购买
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "actionbuttons",
  name: "购买按钮",
  icon: "i-carbon-shopping-cart",
  category: "business",
  description: "购买操作按钮，包含加入购物车和立即购买功能",

  allowedPages: ["product-detail"],

  propsSchema: [
    {
      key: "layout",
      label: "按钮布局",
      type: "select",
      defaultValue: "horizontal",
      options: [
        { label: "横向排列", value: "horizontal" },
        { label: "纵向排列", value: "vertical" },
      ],
      description: "按钮的排列方式",
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
      description: "按钮的尺寸大小",
    },
    {
      key: "fullWidth",
      label: "按钮撑满",
      type: "switch",
      defaultValue: true,
      description: "按钮是否撑满容器宽度",
    },
    {
      key: "showAddToCart",
      label: "显示加购按钮",
      type: "switch",
      defaultValue: true,
      description: "是否显示加入购物车按钮（需同时启用全局购物车功能）",
    },
    {
      key: "addToCartText",
      label: "加购按钮文本",
      type: "text",
      defaultValue: "",
      placeholder: "加入购物车",
      description: "加入购物车按钮的显示文本",
      showIf: "showAddToCart === true",
    },
    {
      key: "buyNowText",
      label: "购买按钮文本",
      type: "text",
      defaultValue: "",
      placeholder: "立即购买",
      description: "立即购买按钮的显示文本",
    },
  ],

  defaultProps: {
    layout: "horizontal",
    buttonSize: "medium",
    fullWidth: true,
    showAddToCart: true,
    addToCartText: "",
    buyNowText: "",
  },

  styleSchema: [
    // 容器样式
    {
      key: "--action-btn-padding",
      label: "容器内边距",
      type: "text",
      defaultValue: "16px 0",
      placeholder: "16px 0",
      group: "layout",
    },
    {
      key: "--action-btn-gap",
      label: "按钮间距",
      type: "text",
      defaultValue: "12px",
      placeholder: "12px",
      group: "layout",
    },
    // 按钮通用样式
    {
      key: "--action-btn-radius",
      label: "按钮圆角",
      type: "text",
      defaultValue: "8px",
      placeholder: "8px",
      group: "button",
    },
    {
      key: "--action-btn-font-size",
      label: "按钮字号",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "button",
    },
    {
      key: "--action-btn-font-weight",
      label: "按钮字重",
      type: "select",
      defaultValue: "500",
      options: [
        { label: "正常", value: "400" },
        { label: "中等", value: "500" },
        { label: "粗体", value: "600" },
      ],
      group: "button",
    },
    {
      key: "--action-btn-inner-padding",
      label: "按钮内边距",
      type: "text",
      defaultValue: "12px 24px",
      placeholder: "12px 24px",
      group: "button",
    },
    // 主按钮样式（立即购买）
    {
      key: "--action-btn-primary-bg",
      label: "主按钮背景色",
      type: "color",
      placeholder: "默认使用全局 primaryColor",
      group: "primary",
    },
    {
      key: "--action-btn-primary-color",
      label: "主按钮文字色",
      type: "color",
      defaultValue: "#ffffff",
      placeholder: "#ffffff",
      group: "primary",
    },
    {
      key: "--action-btn-primary-hover-bg",
      label: "主按钮悬停背景",
      type: "color",
      placeholder: "默认使用全局 primaryColor 深色",
      group: "primary",
    },
    // 次按钮样式（加入购物车）
    {
      key: "--action-btn-secondary-bg",
      label: "次按钮背景色",
      type: "color",
      defaultValue: "#f3f4f6",
      placeholder: "#f3f4f6",
      group: "secondary",
    },
    {
      key: "--action-btn-secondary-color",
      label: "次按钮文字色",
      type: "color",
      defaultValue: "#374151",
      placeholder: "#374151",
      group: "secondary",
    },
    {
      key: "--action-btn-secondary-border",
      label: "次按钮边框色",
      type: "color",
      defaultValue: "#e5e7eb",
      placeholder: "#e5e7eb",
      group: "secondary",
    },
    {
      key: "--action-btn-secondary-hover-bg",
      label: "次按钮悬停背景",
      type: "color",
      defaultValue: "#e5e7eb",
      placeholder: "#e5e7eb",
      group: "secondary",
    },
    // 图标样式
    {
      key: "--action-btn-icon-size",
      label: "图标尺寸",
      type: "text",
      defaultValue: "20px",
      placeholder: "20px",
      group: "icon",
    },
  ],

  defaultStyle: {
    base: {
      "--action-btn-padding": "16px 0",
      "--action-btn-gap": "12px",
      "--action-btn-radius": "8px",
      "--action-btn-font-size": "16px",
      "--action-btn-font-weight": "500",
      "--action-btn-inner-padding": "12px 24px",
      "--action-btn-primary-color": "#ffffff",
      "--action-btn-secondary-bg": "#f3f4f6",
      "--action-btn-secondary-color": "#374151",
      "--action-btn-secondary-border": "#e5e7eb",
      "--action-btn-secondary-hover-bg": "#e5e7eb",
      "--action-btn-icon-size": "20px",
    },
    mobile: {
      "--action-btn-gap": "8px",
      "--action-btn-inner-padding": "14px 20px",
    },
  },

  eventsSchema: [
    {
      event: "addToCart",
      label: "加入购物车",
      description: "点击加入购物车按钮时触发",
    },
    {
      event: "buyNow",
      label: "立即购买",
      description: "点击立即购买按钮时触发",
    },
  ],

  isContainer: false,
  tags: [
    "购买",
    "按钮",
    "购物车",
    "下单",
    "action",
    "button",
    "cart",
    "buy",
    "checkout",
  ],
};
