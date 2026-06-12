/**
 * Reviews Block 元数据
 * 用户评价 - 可配置的评论列表，含评分汇总、星级、买家秀与已验证徽章
 */

import type { ComponentMeta } from "~/types/component-meta";

const SAMPLE_REVIEWS = JSON.stringify(
  [
    {
      name: "张女士",
      rating: 5,
      date: "2026-05-18",
      content: "用了两周效果很好，测量很方便，家里老人也会用，物流也快。",
      images: [],
      verified: true,
    },
    {
      name: "李先生",
      rating: 4.5,
      date: "2026-05-12",
      content: "包装严实，做工不错，客服回复及时，推荐购买。",
      images: [],
      verified: true,
    },
    {
      name: "王女士",
      rating: 4,
      date: "2026-05-03",
      content: "第二次回购了，送家人很合适。",
      images: [],
      verified: false,
    },
  ],
  null,
  2,
);

export const meta: ComponentMeta = {
  type: "reviews",
  name: "用户评价",
  icon: "i-carbon-star",
  category: "marketing",
  description: "可配置的评论列表：评分汇总、星级、买家秀图片与已验证购买徽章",

  propsSchema: [
    {
      key: "showTitle",
      label: "显示标题",
      type: "switch",
      defaultValue: true,
      group: "title",
    },
    {
      key: "title",
      label: "标题文本",
      type: "text",
      defaultValue: "用户评价",
      placeholder: "例如：Opiniones de clientes",
      group: "title",
      showIf: "showTitle === true",
    },
    {
      key: "reviews",
      label: "评论数据",
      type: "json",
      defaultValue: SAMPLE_REVIEWS,
      placeholder:
        '[{"name":"张女士","rating":5,"date":"2026-05-18","content":"很好用","images":["路径或URL"],"avatar":"","verified":true}]',
      description:
        "JSON 数组，每条评论字段：name 姓名、rating 星级(0-5 支持小数)、date 日期、content 内容、images 图片列表、avatar 头像(留空显示首字母)、verified 是否已验证",
      group: "data",
    },
    {
      key: "showSummary",
      label: "显示评分汇总",
      type: "switch",
      defaultValue: true,
      description: "平均分与评论数自动按评论数据计算",
      group: "display",
    },
    {
      key: "totalCount",
      label: "评论总数（手动）",
      type: "text",
      defaultValue: "",
      placeholder: "例如：1,238（留空自动按评论条数）",
      description: "汇总行显示的总数，可与实际列出的评论条数不同，支持任意格式如 1.2k+",
      group: "display",
      showIf: "showSummary === true",
    },
    {
      key: "showDates",
      label: "显示日期",
      type: "switch",
      defaultValue: true,
      group: "display",
    },
    {
      key: "showImages",
      label: "显示买家秀图片",
      type: "switch",
      defaultValue: true,
      group: "display",
    },
    {
      key: "verifiedText",
      label: "已验证徽章文案",
      type: "text",
      defaultValue: "已验证购买",
      placeholder: "例如：Compra verificada",
      group: "display",
    },
    {
      key: "layout",
      label: "排列方式",
      type: "select",
      defaultValue: "list",
      options: [
        { label: "列表（单列）", value: "list" },
        { label: "网格（双列）", value: "grid" },
      ],
      group: "display",
    },
    {
      key: "maxVisible",
      label: "默认显示条数",
      type: "number",
      defaultValue: 0,
      min: 0,
      max: 100,
      step: 1,
      description: "0 表示全部显示；大于 0 时超出部分折叠为「查看更多」",
      group: "display",
    },
    {
      key: "loadMoreText",
      label: "查看更多文案",
      type: "text",
      defaultValue: "查看更多评价",
      placeholder: "例如：Ver más opiniones",
      group: "display",
    },
  ],

  defaultProps: {
    showTitle: true,
    title: "用户评价",
    reviews: SAMPLE_REVIEWS,
    showSummary: true,
    totalCount: "",
    showDates: true,
    showImages: true,
    verifiedText: "已验证购买",
    layout: "list",
    maxVisible: 0,
    loadMoreText: "查看更多评价",
  },

  styleSchema: [
    { key: "--reviews-max-width", label: "最大宽度", type: "text", placeholder: "960px", group: "size" },
    { key: "--reviews-padding", label: "内边距", type: "text", placeholder: "16px", group: "size" },
    { key: "--reviews-gap", label: "区块间距", type: "text", placeholder: "16px", group: "size" },
    { key: "--reviews-card-gap", label: "卡片间距", type: "text", placeholder: "12px", group: "size" },
    { key: "--reviews-title-size", label: "标题字号", type: "text", placeholder: "20px", group: "text" },
    { key: "--reviews-title-color", label: "标题颜色", type: "color", group: "text" },
    { key: "--reviews-content-color", label: "内容颜色", type: "color", group: "text" },
    { key: "--reviews-star-color", label: "星级颜色", type: "color", defaultValue: "#f59e0b", group: "star" },
    { key: "--reviews-star-empty-color", label: "空星颜色", type: "color", defaultValue: "#e5e7eb", group: "star" },
    { key: "--reviews-verified-color", label: "已验证徽章颜色", type: "color", defaultValue: "#10b981", group: "star" },
    { key: "--reviews-card-bg", label: "卡片背景", type: "color", defaultValue: "#fafafa", group: "card" },
    { key: "--reviews-card-border", label: "卡片边框", type: "text", placeholder: "1px solid #f0f0f0", group: "card" },
    { key: "--reviews-card-radius", label: "卡片圆角", type: "text", placeholder: "10px", group: "card" },
    { key: "--reviews-image-size", label: "买家秀图片尺寸", type: "text", placeholder: "88px", group: "card" },
  ],

  defaultStyle: {
    base: {},
  },

  isContainer: false,
  tags: ["评价", "评论", "买家秀", "晒单", "口碑", "reviews", "comments", "rating", "testimonial"],
}
