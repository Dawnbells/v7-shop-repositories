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

  propsSchema: [
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
    {
      key: "height",
      label: "页头高度",
      type: "text",
      defaultValue: "60px",
      placeholder: "如 60px 或 4rem",
      description: "页头的高度",
    },
  ],

  defaultProps: {
    layout: "left",
    showSiteName: true,
    showCart: true,
    height: "60px",
  },

  styleSchema: [
    {
      key: "backgroundColor",
      label: "背景颜色",
      type: "color",
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
      placeholder: "1px",
      group: "border",
    },
    {
      key: "borderBottomStyle",
      label: "底部边框样式",
      type: "select",
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
      key: "paddingLeft",
      label: "左内边距",
      type: "text",
      placeholder: "16px",
      group: "padding",
    },
    {
      key: "paddingRight",
      label: "右内边距",
      type: "text",
      placeholder: "16px",
      group: "padding",
    },
  ],

  eventsSchema: [
    { event: "click", label: "点击", description: "点击页头时触发" },
  ],

  defaultStyle: {
    base: {
      backgroundColor: "#ffffff",
      borderBottomWidth: "1px",
      borderBottomStyle: "solid",
      borderBottomColor: "#e2e8f0",
      paddingLeft: "16px",
      paddingRight: "16px",
    },
  },

  isContainer: false,
  tags: ["页头", "header", "导航", "logo", "购物车"],
};
