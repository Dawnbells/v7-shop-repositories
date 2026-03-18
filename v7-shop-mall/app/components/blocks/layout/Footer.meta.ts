/**
 * Footer Block 元数据
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "footer",
  name: "页脚",
  icon: "i-carbon-row-collapse",
  category: "layout",
  description: "页脚组件，展示联系方式、社交媒体、协议链接和版权信息",

  singleton: true,
  allowedPosition: "last",

  propsSchema: [
    {
      key: "layout",
      label: "布局方式",
      type: "select",
      defaultValue: "standard",
      options: [
        { label: "简单布局", value: "simple" },
        { label: "标准布局", value: "standard" },
        { label: "多列布局", value: "columns" },
      ],
      description: "简单：仅显示协议链接和版权；标准/多列：显示完整内容",
    },
    {
      key: "showContact",
      label: "显示联系方式",
      type: "switch",
      defaultValue: true,
      description: "显示邮箱、电话、WhatsApp、地址等联系信息",
    },
    {
      key: "showSocial",
      label: "显示社交媒体",
      type: "switch",
      defaultValue: true,
      description: "显示社交媒体图标链接",
    },
    {
      key: "showProtocol",
      label: "显示协议链接",
      type: "switch",
      defaultValue: true,
      description: "显示协议分组和文章链接",
    },
    {
      key: "showCopyright",
      label: "显示版权信息",
      type: "switch",
      defaultValue: true,
      description: "显示版权声明和 ICP 备案号",
    },
  ],

  defaultProps: {
    layout: "standard",
    showContact: true,
    showSocial: true,
    showProtocol: true,
    showCopyright: true,
  },

  styleSchema: [
    {
      key: "backgroundColor",
      label: "背景颜色",
      type: "color",
      placeholder: "默认使用全局 surfaceColor",
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
      key: "--footer-text-color",
      label: "文字颜色",
      type: "color",
      placeholder: "默认使用全局 textColor",
      group: "color",
    },
    {
      key: "--footer-link-color",
      label: "链接颜色",
      type: "color",
      placeholder: "默认使用全局 primaryColor",
      group: "color",
    },
    {
      key: "--footer-social-bg",
      label: "社交图标背景",
      type: "color",
      placeholder: "rgba(0, 0, 0, 0.05)",
      group: "color",
    },
    {
      key: "borderTopWidth",
      label: "顶部边框宽度",
      type: "text",
      defaultValue: "1px",
      placeholder: "1px",
      group: "border",
    },
    {
      key: "borderTopStyle",
      label: "顶部边框样式",
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
      key: "borderTopColor",
      label: "顶部边框颜色",
      type: "color",
      placeholder: "默认使用全局 borderColor",
      group: "border",
    },
    {
      key: "paddingTop",
      label: "上内边距",
      type: "text",
      defaultValue: "32px",
      placeholder: "32px",
      group: "padding",
    },
    {
      key: "paddingBottom",
      label: "下内边距",
      type: "text",
      defaultValue: "24px",
      placeholder: "24px",
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
    { event: "click", label: "点击", description: "点击页脚时触发" },
  ],

  defaultStyle: {
    base: {
      borderTopWidth: "1px",
      borderTopStyle: "solid",
      paddingTop: "32px",
      paddingBottom: "24px",
      paddingLeft: "16px",
      paddingRight: "16px",
    },
    mobile: {
      paddingTop: "24px",
      paddingBottom: "16px",
      paddingLeft: "12px",
      paddingRight: "12px",
    },
  },

  isContainer: false,
  tags: ["页脚", "footer", "版权", "联系方式", "社交媒体", "协议"],
};
