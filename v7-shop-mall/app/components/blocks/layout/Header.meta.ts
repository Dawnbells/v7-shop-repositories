/**
 * Header Block 元数据
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "header",
  name: "页头",
  icon: "i-carbon-application-web",
  category: "layout",
  description: "页头组件，展示网站名称、Logo 和购物车",

  singleton: true,
  allowedPosition: "first",

  propsSchema: [
    {
      key: "contentMaxWidth",
      label: "内容区最大宽度",
      type: "text",
      defaultValue: "1400px",
      placeholder: "1400px",
      description: "设置页头内容区的最大宽度",
      group: "layout",
    },
    {
      key: "sticky",
      label: "吸顶",
      type: "switch",
      defaultValue: false,
      description: "页面滚动时固定在顶部",
    },
    {
      key: "layout",
      label: "布局方式",
      type: "select",
      defaultValue: "left",
      options: [
        { label: "左左右", value: "left" },
        { label: "左中右", value: "center" },
      ],
      description: "控制 Logo 的位置",
    },
    {
      key: "showSiteName",
      label: "显示网站名称",
      type: "switch",
      defaultValue: true,
      description: "是否显示网站名称",
    },
    {
      key: "showCart",
      label: "显示购物车",
      type: "switch",
      defaultValue: true,
      description: "是否显示购物车图标（需全局配置启用购物车功能）",
    },
  ],

  defaultProps: {
    contentMaxWidth: "1400px",
    sticky: false,
    layout: "left",
    showSiteName: true,
    showCart: true,
  },

  styleSchema: [
    {
      key: "height",
      label: "页头高度",
      type: "text",
      defaultValue: "60px",
      placeholder: "如 60px 或 4rem",
      group: "size",
    },
    {
      key: "--header-logo-size",
      label: "Logo大小",
      type: "text",
      defaultValue: "32px",
      placeholder: "如 32px 或 2rem",
      group: "size",
    },
    {
      key: "--header-site-name-size",
      label: "网站名称字号",
      type: "text",
      defaultValue: "18px",
      placeholder: "如 18px 或 1.125rem",
      group: "size",
    },
    {
      key: "--header-cart-size",
      label: "右侧图标大小",
      type: "text",
      defaultValue: "24px",
      placeholder: "如 24px 或 1.5rem",
      group: "size",
    },
    {
      key: "backgroundColor",
      label: "背景颜色",
      type: "color",
      placeholder: "默认使用全局 surfaceColor",
      group: "background",
    },
    {
      key: "--header-text-color",
      label: "字体颜色",
      type: "color",
      placeholder: "默认使用全局 textColor",
      group: "background",
    },
    {
      key: "--header-cart-color",
      label: "购物车颜色",
      type: "color",
      placeholder: "默认使用全局 textColor",
      group: "background",
    },
    {
      key: "backgroundImage",
      label: "背景图片",
      type: "text",
      placeholder: "url(...)",
      group: "background",
    },
    {
      key: "borderBottomWidth",
      label: "底部边框宽度",
      type: "text",
      defaultValue: "1px",
      placeholder: "1px",
      group: "border",
    },
    {
      key: "borderBottomStyle",
      label: "底部边框样式",
      type: "select",
      defaultValue: "solid",
      options: [
        { label: "无", value: "none" },
        { label: "实线", value: "solid" },
        { label: "虚线", value: "dashed" },
        { label: "点线", value: "dotted" },
      ],
      group: "border",
    },
    {
      key: "borderBottomColor",
      label: "底部边框颜色",
      type: "color",
      placeholder: "默认使用全局 borderColor",
      group: "border",
    },
    {
      key: "boxShadow",
      label: "阴影",
      type: "text",
      placeholder: "0 2px 8px rgba(0,0,0,0.1)",
      group: "shadow",
    },
    {
      key: "paddingTop",
      label: "上内边距",
      type: "text",
      defaultValue: "0",
      placeholder: "0",
      group: "padding",
    },
    {
      key: "paddingBottom",
      label: "下内边距",
      type: "text",
      defaultValue: "0",
      placeholder: "0",
      group: "padding",
    },
    {
      key: "paddingLeft",
      label: "左内边距",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "padding",
    },
    {
      key: "paddingRight",
      label: "右内边距",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "padding",
    },
  ],

  eventsSchema: [
    { event: "click", label: "点击", description: "点击页头时触发" },
  ],

  defaultStyle: {
    base: {
      height: "60px",
      "--header-logo-size": "32px",
      "--header-site-name-size": "18px",
      "--header-cart-size": "24px",
      borderBottomWidth: "1px",
      borderBottomStyle: "solid",
      paddingTop: "0",
      paddingBottom: "0",
      paddingLeft: "16px",
      paddingRight: "16px",
    },
    mobile: {
      height: "50px",
      "--header-logo-size": "28px",
      "--header-cart-size": "20px",
      paddingLeft: "12px",
      paddingRight: "12px",
    },
  },

  isContainer: false,
  tags: ["页头", "header", "导航", "logo", "购物车"],
};
