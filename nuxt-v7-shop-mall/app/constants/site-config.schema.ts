/**
 * 网站预设配置 Schema
 * 定义全局站点配置的字段结构，用于渲染配置表单
 * 
 * 使用与 PropSchema 类似的结构，便于复用表单渲染逻辑
 */

import type { PropEditorType, SelectOption } from "~/types/component-meta";

/**
 * 站点配置字段 Schema
 */
export interface SiteFieldSchema {
  key: string;                    // 字段键名
  label: string;                  // 显示标签
  type: PropEditorType;           // 编辑器类型
  defaultValue?: any;             // 默认值
  placeholder?: string;           // 占位符
  options?: SelectOption[];       // 下拉选项
  group: string;                  // 分组键名
  description?: string;           // 描述说明
  required?: boolean;             // 是否必填
  // 多语言支持
  i18n?: boolean;                 // 是否支持多语言
}

/**
 * 站点配置分组
 */
export interface SiteConfigGroup {
  key: string;                    // 分组键名
  label: string;                  // 分组标签
  icon?: string;                  // 分组图标
  description?: string;           // 分组描述
}

/**
 * 站点配置分组定义
 */
export const SITE_CONFIG_GROUPS: SiteConfigGroup[] = [
  {
    key: "basic",
    label: "基本信息",
    icon: "i-carbon-information",
    description: "网站基本信息配置",
  },
  {
    key: "contact",
    label: "联系方式",
    icon: "i-carbon-phone",
    description: "联系信息配置",
  },
  {
    key: "footer",
    label: "页脚信息",
    icon: "i-carbon-row-collapse",
    description: "页脚显示内容",
  },
  {
    key: "social",
    label: "社交媒体",
    icon: "i-carbon-logo-twitter",
    description: "社交媒体链接",
  },
  {
    key: "seo",
    label: "SEO 设置",
    icon: "i-carbon-search",
    description: "搜索引擎优化",
  },
  {
    key: "features",
    label: "功能设置",
    icon: "i-carbon-settings",
    description: "商城功能开关",
  },
];

/**
 * 站点配置字段 Schema 定义
 */
export const SITE_CONFIG_SCHEMA: SiteFieldSchema[] = [
  // ============ 基本信息 ============
  {
    key: "siteName",
    label: "网站名称",
    type: "text",
    defaultValue: "",
    placeholder: "输入网站/品牌名称",
    group: "basic",
    description: "显示在页头、页脚等位置的网站名称",
    i18n: true,
  },
  {
    key: "logo",
    label: "Logo",
    type: "image",
    defaultValue: "",
    placeholder: "上传或输入 Logo 图片 URL",
    group: "basic",
    description: "网站 Logo 图片",
  },
  {
    key: "logoDark",
    label: "Logo (深色模式)",
    type: "image",
    defaultValue: "",
    placeholder: "深色模式下的 Logo",
    group: "basic",
    description: "可选，深色模式下使用的 Logo",
  },
  {
    key: "favicon",
    label: "网站图标",
    type: "image",
    defaultValue: "",
    placeholder: "Favicon 图标 URL",
    group: "basic",
    description: "浏览器标签页显示的小图标",
  },
  {
    key: "slogan",
    label: "标语",
    type: "text",
    defaultValue: "",
    placeholder: "网站标语或口号",
    group: "basic",
    description: "简短的品牌标语",
    i18n: true,
  },
  {
    key: "description",
    label: "网站描述",
    type: "textarea",
    defaultValue: "",
    placeholder: "简要描述网站内容",
    group: "basic",
    description: "用于 SEO 和品牌展示",
    i18n: true,
  },

  // ============ 联系方式 ============
  {
    key: "contactEmail",
    label: "联系邮箱",
    type: "text",
    defaultValue: "",
    placeholder: "contact@example.com",
    group: "contact",
  },
  {
    key: "contactPhone",
    label: "联系电话",
    type: "text",
    defaultValue: "",
    placeholder: "+86 123 4567 8900",
    group: "contact",
  },
  {
    key: "whatsapp",
    label: "WhatsApp",
    type: "text",
    defaultValue: "",
    placeholder: "WhatsApp 号码",
    group: "contact",
  },
  {
    key: "address",
    label: "地址",
    type: "textarea",
    defaultValue: "",
    placeholder: "公司/店铺地址",
    group: "contact",
    i18n: true,
  },
  {
    key: "businessHours",
    label: "营业时间",
    type: "text",
    defaultValue: "",
    placeholder: "如：周一至周五 9:00-18:00",
    group: "contact",
    i18n: true,
  },

  // ============ 页脚信息 ============
  {
    key: "copyright",
    label: "版权声明",
    type: "text",
    defaultValue: "© 2024 All rights reserved.",
    placeholder: "版权声明文字",
    group: "footer",
    description: "显示在页脚底部的版权信息",
    i18n: true,
  },
  {
    key: "icp",
    label: "ICP 备案号",
    type: "text",
    defaultValue: "",
    placeholder: "如：京ICP备xxxxxxxx号",
    group: "footer",
    description: "中国大陆网站备案号",
  },
  {
    key: "footerLinks",
    label: "页脚链接",
    type: "json",
    defaultValue: [],
    group: "footer",
    description: '格式: [{ "text": "隐私政策", "url": "/privacy" }]',
  },

  // ============ 社交媒体 ============
  {
    key: "facebook",
    label: "Facebook",
    type: "text",
    defaultValue: "",
    placeholder: "Facebook 主页链接",
    group: "social",
  },
  {
    key: "twitter",
    label: "Twitter / X",
    type: "text",
    defaultValue: "",
    placeholder: "Twitter 主页链接",
    group: "social",
  },
  {
    key: "instagram",
    label: "Instagram",
    type: "text",
    defaultValue: "",
    placeholder: "Instagram 主页链接",
    group: "social",
  },
  {
    key: "youtube",
    label: "YouTube",
    type: "text",
    defaultValue: "",
    placeholder: "YouTube 频道链接",
    group: "social",
  },
  {
    key: "tiktok",
    label: "TikTok",
    type: "text",
    defaultValue: "",
    placeholder: "TikTok 主页链接",
    group: "social",
  },
  {
    key: "linkedin",
    label: "LinkedIn",
    type: "text",
    defaultValue: "",
    placeholder: "LinkedIn 主页链接",
    group: "social",
  },
  {
    key: "wechat",
    label: "微信公众号",
    type: "text",
    defaultValue: "",
    placeholder: "微信公众号 ID 或二维码链接",
    group: "social",
  },
  {
    key: "weibo",
    label: "微博",
    type: "text",
    defaultValue: "",
    placeholder: "微博主页链接",
    group: "social",
  },

  // ============ SEO 设置 ============
  {
    key: "seoTitle",
    label: "SEO 标题",
    type: "text",
    defaultValue: "",
    placeholder: "搜索引擎显示的标题",
    group: "seo",
    description: "留空则使用网站名称",
    i18n: true,
  },
  {
    key: "seoDescription",
    label: "SEO 描述",
    type: "textarea",
    defaultValue: "",
    placeholder: "搜索引擎显示的描述",
    group: "seo",
    description: "建议 150-160 字符",
    i18n: true,
  },
  {
    key: "seoKeywords",
    label: "SEO 关键词",
    type: "text",
    defaultValue: "",
    placeholder: "关键词，用逗号分隔",
    group: "seo",
    i18n: true,
  },
  {
    key: "googleAnalytics",
    label: "Google Analytics ID",
    type: "text",
    defaultValue: "",
    placeholder: "如：G-XXXXXXXXXX",
    group: "seo",
  },
  {
    key: "facebookPixel",
    label: "Facebook Pixel ID",
    type: "text",
    defaultValue: "",
    placeholder: "Facebook Pixel 追踪 ID",
    group: "seo",
  },

  // ============ 功能设置 ============
  {
    key: "enableQuantitySelector",
    label: "启用数量选择器",
    type: "switch",
    defaultValue: true,
    group: "features",
    description: "产品页面显示数量选择器",
  },
  {
    key: "enableCart",
    label: "启用购物车功能",
    type: "switch",
    defaultValue: true,
    group: "features",
    description: "启用购物车和加入购物车按钮",
  },
];

/**
 * 获取指定分组的字段
 */
export function getFieldsByGroup(groupKey: string): SiteFieldSchema[] {
  return SITE_CONFIG_SCHEMA.filter((field) => field.group === groupKey);
}

/**
 * 获取所有支持多语言的字段
 */
export function getI18nFields(): SiteFieldSchema[] {
  return SITE_CONFIG_SCHEMA.filter((field) => field.i18n);
}

/**
 * 生成默认的站点配置值
 */
export function createDefaultSiteConfig(): Record<string, any> {
  const config: Record<string, any> = {};
  for (const field of SITE_CONFIG_SCHEMA) {
    if (field.defaultValue !== undefined) {
      config[field.key] = field.defaultValue;
    }
  }
  return config;
}
