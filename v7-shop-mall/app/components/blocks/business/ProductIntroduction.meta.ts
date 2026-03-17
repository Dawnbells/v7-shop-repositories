/**
 * ProductIntroduction Block 元数据
 * 商品详情组件 - 解析并渲染商品 introduction 字段中的图片和内容
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "productintroduction",
  name: "商品详情",
  icon: "i-carbon-document",
  category: "business",
  description: "展示商品详情图片和描述内容，图片自适应宽高比显示",

  allowedPages: ["product-detail"],

  propsSchema: [],

  defaultProps: {},

  styleSchema: [
    {
      key: "--intro-max-width",
      label: "最大宽度",
      type: "text",
      defaultValue: "100%",
      placeholder: "100%",
      group: "layout",
    },
    {
      key: "--intro-image-gap",
      label: "图片间距",
      type: "text",
      defaultValue: "1px",
      placeholder: "1px",
      group: "layout",
    },
    {
      key: "--intro-html-padding",
      label: "文字内边距",
      type: "text",
      defaultValue: "0 0",
      placeholder: "0 0",
      group: "content",
    },
    {
      key: "--intro-html-font-size",
      label: "文字字号",
      type: "text",
      defaultValue: "14px",
      placeholder: "14px",
      group: "content",
    },
    {
      key: "--intro-html-line-height",
      label: "文字行高",
      type: "text",
      defaultValue: "1.6",
      placeholder: "1.6",
      group: "content",
    },
    {
      key: "--intro-html-color",
      label: "文字颜色",
      type: "color",
      placeholder: "默认使用全局 textColor",
      group: "content",
    },
  ],

  defaultStyle: {
    base: {
      "--intro-max-width": "100%",
      "--intro-image-gap": "1px",
      "--intro-html-padding": "0 0",
      "--intro-html-font-size": "14px",
      "--intro-html-line-height": "1.6",
    },
    mobile: {
      "--intro-html-font-size": "13px",
    },
  },

  isContainer: false,
  tags: ["商品", "详情", "介绍", "图片", "product", "introduction", "detail"],
};
