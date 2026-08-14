/**
 * ArticleDetail Block 元数据
 * 文章详情组件 - 展示文章标题、更新时间、描述和正文内容
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "articledetail",
  name: "文章详情",
  icon: "i-carbon-document",
  category: "business",
  description: "展示文章标题、更新时间、描述和正文内容",

  allowedPages: ["article"],

  propsSchema: [
    {
      key: "showDate",
      label: "显示更新时间",
      type: "switch",
      defaultValue: true,
      description: "是否在标题下方显示文章更新时间",
      group: "meta",
    },
    {
      key: "dateFormat",
      label: "日期格式",
      type: "select",
      defaultValue: "auto",
      options: [
        { label: "跟随语言 · 长格式（2026年8月12日 / August 12, 2026）", value: "auto" },
        { label: "跟随语言 · 简写（2026年8月12日 / Aug 12, 2026）", value: "auto-medium" },
        { label: "跟随语言 · 数字（2026/8/12 / 12.08.2026）", value: "auto-numeric" },
        { label: "国际标准 ISO（2026-08-12）", value: "iso" },
        { label: "日/月/年（12/08/2026）", value: "dmy" },
        { label: "月/日/年（08/12/2026）", value: "mdy" },
      ],
      description: "“跟随语言”会按站点当前语言自动本地化，其余为固定的国际通用格式",
      showIf: "showDate === true",
      group: "meta",
    },
    {
      key: "showDateLabel",
      label: "显示“更新时间”文字",
      type: "switch",
      defaultValue: false,
      description: "在日期前显示当前语言的“更新时间”标签",
      showIf: "showDate === true",
      group: "meta",
    },
  ],

  defaultProps: {
    showDate: true,
    dateFormat: "auto",
    showDateLabel: false,
  },

  styleSchema: [
    // 布局样式
    {
      key: "--article-max-width",
      label: "最大宽度",
      type: "text",
      defaultValue: "800px",
      placeholder: "800px",
      group: "layout",
    },
    {
      key: "--article-padding",
      label: "内边距",
      type: "text",
      defaultValue: "40px 24px",
      placeholder: "40px 24px",
      group: "layout",
    },
    // 标题样式
    {
      key: "--article-title-size",
      label: "标题字号",
      type: "text",
      defaultValue: "28px",
      placeholder: "28px",
      group: "title",
    },
    {
      key: "--article-title-weight",
      label: "标题字重",
      type: "select",
      defaultValue: "700",
      options: [
        { label: "正常", value: "400" },
        { label: "中等", value: "500" },
        { label: "粗体", value: "600" },
        { label: "加粗", value: "700" },
      ],
      group: "title",
    },
    {
      key: "--article-title-color",
      label: "标题颜色",
      type: "color",
      defaultValue: "#1f2937",
      placeholder: "#1f2937",
      group: "title",
    },
    {
      key: "--article-title-margin",
      label: "标题下边距",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "title",
    },
    // 元信息样式
    {
      key: "--article-meta-size",
      label: "元信息字号",
      type: "text",
      defaultValue: "14px",
      placeholder: "14px",
      group: "meta",
    },
    {
      key: "--article-meta-color",
      label: "元信息颜色",
      type: "color",
      defaultValue: "#6b7280",
      placeholder: "#6b7280",
      group: "meta",
    },
    {
      key: "--article-meta-margin",
      label: "元信息下边距",
      type: "text",
      defaultValue: "20px",
      placeholder: "20px",
      group: "meta",
    },
    // 描述样式
    {
      key: "--article-desc-size",
      label: "描述字号",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "description",
    },
    {
      key: "--article-desc-color",
      label: "描述颜色",
      type: "color",
      defaultValue: "#4b5563",
      placeholder: "#4b5563",
      group: "description",
    },
    {
      key: "--article-desc-margin",
      label: "描述下边距",
      type: "text",
      defaultValue: "24px",
      placeholder: "24px",
      group: "description",
    },
    {
      key: "--article-border-color",
      label: "分割线颜色",
      type: "color",
      defaultValue: "#e5e7eb",
      placeholder: "#e5e7eb",
      group: "description",
    },
    // 正文样式
    {
      key: "--article-content-size",
      label: "正文字号",
      type: "text",
      defaultValue: "16px",
      placeholder: "16px",
      group: "content",
    },
    {
      key: "--article-content-color",
      label: "正文颜色",
      type: "color",
      defaultValue: "#374151",
      placeholder: "#374151",
      group: "content",
    },
    {
      key: "--article-heading-color",
      label: "正文标题颜色",
      type: "color",
      defaultValue: "#1f2937",
      placeholder: "#1f2937",
      group: "content",
    },
    {
      key: "--article-link-color",
      label: "链接颜色",
      type: "color",
      defaultValue: "#2563eb",
      placeholder: "#2563eb",
      group: "content",
    },
  ],

  defaultStyle: {
    base: {
      "--article-max-width": "800px",
      "--article-padding": "40px 24px",
      "--article-title-size": "28px",
      "--article-title-weight": "700",
      "--article-title-color": "#1f2937",
      "--article-title-margin": "16px",
      "--article-meta-size": "14px",
      "--article-meta-color": "#6b7280",
      "--article-meta-margin": "20px",
      "--article-desc-size": "16px",
      "--article-desc-color": "#4b5563",
      "--article-desc-margin": "24px",
      "--article-border-color": "#e5e7eb",
      "--article-content-size": "16px",
      "--article-content-color": "#374151",
      "--article-heading-color": "#1f2937",
      "--article-link-color": "#2563eb",
    },
    mobile: {
      "--article-padding": "24px 16px",
      "--article-title-size": "24px",
      "--article-meta-margin": "16px",
      "--article-desc-size": "15px",
      "--article-content-size": "15px",
    },
  },

  isContainer: false,
  tags: ["文章", "详情", "内容", "博客", "article", "detail", "content", "blog"],
};
