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
  key: string; // 字段键名
  label: string; // 显示标签
  type: PropEditorType; // 编辑器类型
  defaultValue?: any; // 默认值
  placeholder?: string; // 占位符
  options?: SelectOption[]; // 下拉选项
  group: string; // 分组键名
  description?: string; // 描述说明
  required?: boolean; // 是否必填
  i18n?: boolean; // 是否支持多语言
  showIf?: string; // 条件显示表达式，如 "globalConfig.enableCart === true"
}

/**
 * 站点配置分组
 */
export interface SiteConfigGroup {
  key: string; // 分组键名
  label: string; // 分组标签
  icon?: string; // 分组图标
  description?: string; // 分组描述
}

/**
 * 站点配置分组定义
 */
export const SITE_CONFIG_GROUPS: SiteConfigGroup[] = [
  {
    key: "globalConfig.basic",
    label: "基本信息",
    icon: "i-carbon-information",
    description: "网站基本信息配置",
  },
  {
    key: "globalConfig.contact",
    label: "联系方式",
    icon: "i-carbon-phone",
    description: "联系信息配置",
  },
  {
    key: "globalConfig.footer",
    label: "页脚信息",
    icon: "i-carbon-row-collapse",
    description: "页脚显示内容",
  },
  {
    key: "globalConfig.social",
    label: "社交媒体",
    icon: "i-carbon-logo-twitter",
    description: "社交媒体链接",
  },
  {
    key: "globalConfig.seo",
    label: "SEO 设置",
    icon: "i-carbon-search",
    description: "搜索引擎优化",
  },
  {
    key: "globalConfig.features",
    label: "功能设置",
    icon: "i-carbon-settings",
    description: "商城功能开关",
  },
  {
    key: "globalStyle.color",
    label: "颜色",
    icon: "i-carbon-color-palette",
    description: "颜色配置",
  },
  {
    key: "globalStyle.font",
    label: "字体",
    icon: "i-carbon-text-font",
    description: "字体配置",
  },
  {
    key: "globalStyle.radius",
    label: "圆角",
    icon: "i-carbon-crop",
    description: "圆角配置",
  },
  {
    key: "globalStyle.spacing",
    label: "间距",
    icon: "i-carbon-fit-to-screen",
    description: "间距配置",
  },
];

/**
 * 站点配置字段 Schema 定义
 */
export const SITE_CONFIG_SCHEMA: SiteFieldSchema[] = [
  // ============ 基本信息 (globalConfig.basic) ============
  {
    key: "globalConfig.siteName",
    label: "网站名称",
    type: "text",
    defaultValue: "",
    placeholder: "输入网站/品牌名称",
    group: "globalConfig.basic",
    description: "显示在页头、页脚等位置的网站名称",
  },
  {
    key: "globalConfig.logo",
    label: "Logo",
    type: "image",
    defaultValue: "",
    placeholder: "上传或输入 Logo 图片 URL",
    group: "globalConfig.basic",
    description: "网站 Logo 图片",
  },
  {
    key: "globalConfig.favicon",
    label: "网站图标",
    type: "image",
    defaultValue: "",
    placeholder: "Favicon 图标 URL",
    group: "globalConfig.basic",
    description: "浏览器标签页显示的小图标",
  },
  {
    key: "globalConfig.browserTabTitle",
    label: "浏览器标签标题",
    type: "text",
    defaultValue: "",
    placeholder: "如：Vapsolo",
    group: "globalConfig.basic",
    description: "浏览器标签页标题后缀，格式：页面标题 - {此值}",
  },
  {
    key: "globalConfig.slogan",
    label: "标语",
    type: "text",
    defaultValue: "",
    placeholder: "网站标语或口号",
    group: "globalConfig.basic",
    description: "简短的品牌标语",
  },
  {
    key: "globalConfig.description",
    label: "网站描述",
    type: "textarea",
    defaultValue: "",
    placeholder: "简要描述网站内容",
    group: "globalConfig.basic",
    description: "用于 SEO 和品牌展示",
  },

  // ============ 联系方式 (globalConfig.contact) ============
  {
    key: "globalConfig.contactEmail",
    label: "联系邮箱",
    type: "text",
    defaultValue: "",
    placeholder: "contact@example.com",
    group: "globalConfig.contact",
  },
  {
    key: "globalConfig.contactPhone",
    label: "联系电话",
    type: "text",
    defaultValue: "",
    placeholder: "+86 123 4567 8900",
    group: "globalConfig.contact",
  },
  {
    key: "globalConfig.whatsapp",
    label: "WhatsApp",
    type: "text",
    defaultValue: "",
    placeholder: "WhatsApp 号码",
    group: "globalConfig.contact",
  },
  {
    key: "globalConfig.address",
    label: "地址",
    type: "textarea",
    defaultValue: "",
    placeholder: "公司/店铺地址",
    group: "globalConfig.contact",
  },
  {
    key: "globalConfig.businessHours",
    label: "营业时间",
    type: "text",
    defaultValue: "",
    placeholder: "如：周一至周五 9:00-18:00",
    group: "globalConfig.contact",
  },

  // ============ 页脚信息 (globalConfig.footer) ============
  {
    key: "globalConfig.copyright",
    label: "版权声明",
    type: "text",
    defaultValue: "© 2024 All rights reserved.",
    placeholder: "版权声明文字",
    group: "globalConfig.footer",
    description: "显示在页脚底部的版权信息",
  },
  {
    key: "globalConfig.icp",
    label: "ICP 备案号",
    type: "text",
    defaultValue: "",
    placeholder: "如：京ICP备xxxxxxxx号",
    group: "globalConfig.footer",
    description: "中国大陆网站备案号",
  },
  {
    key: "globalConfig.newsletterTitle",
    label: "邮件订阅标题",
    type: "text",
    defaultValue: "订阅我们的新闻",
    placeholder: "如：订阅获取最新优惠",
    group: "globalConfig.footer",
    description: "页脚邮件订阅区域的标题文字",
  },

  // ============ 社交媒体 (globalConfig.social) ============
  {
    key: "globalConfig.facebook",
    label: "Facebook",
    type: "text",
    defaultValue: "",
    placeholder: "Facebook 主页链接",
    group: "globalConfig.social",
  },
  {
    key: "globalConfig.twitter",
    label: "Twitter / X",
    type: "text",
    defaultValue: "",
    placeholder: "Twitter 主页链接",
    group: "globalConfig.social",
  },
  {
    key: "globalConfig.instagram",
    label: "Instagram",
    type: "text",
    defaultValue: "",
    placeholder: "Instagram 主页链接",
    group: "globalConfig.social",
  },
  {
    key: "globalConfig.youtube",
    label: "YouTube",
    type: "text",
    defaultValue: "",
    placeholder: "YouTube 频道链接",
    group: "globalConfig.social",
  },
  {
    key: "globalConfig.tiktok",
    label: "TikTok",
    type: "text",
    defaultValue: "",
    placeholder: "TikTok 主页链接",
    group: "globalConfig.social",
  },
  {
    key: "globalConfig.linkedin",
    label: "LinkedIn",
    type: "text",
    defaultValue: "",
    placeholder: "LinkedIn 主页链接",
    group: "globalConfig.social",
  },

  // ============ SEO 设置 (globalConfig.seo) ============
  {
    key: "globalConfig.seoTitle",
    label: "SEO 标题",
    type: "text",
    defaultValue: "",
    placeholder: "搜索引擎显示的标题",
    group: "globalConfig.seo",
    description: "留空则使用网站名称",
  },
  {
    key: "globalConfig.seoDescription",
    label: "SEO 描述",
    type: "textarea",
    defaultValue: "",
    placeholder: "搜索引擎显示的描述",
    group: "globalConfig.seo",
    description: "建议 150-160 字符",
  },
  {
    key: "globalConfig.seoKeywords",
    label: "SEO 关键词",
    type: "text",
    defaultValue: "",
    placeholder: "关键词，用逗号分隔",
    group: "globalConfig.seo",
  },

  // ============ 功能设置 (globalConfig.features) ============
  {
    key: "globalConfig.enableQuantitySelector",
    label: "启用数量选择器",
    type: "switch",
    defaultValue: true,
    group: "globalConfig.features",
    description: "产品页面显示数量选择器",
  },
  {
    key: "globalConfig.enableCart",
    label: "启用购物车功能",
    type: "switch",
    defaultValue: true,
    group: "globalConfig.features",
    description: "启用购物车和加入购物车按钮",
  },
  {
    key: "globalConfig.cartMode",
    label: "购物车模式",
    type: "toggle",
    defaultValue: "single",
    options: [
      { label: "商城模式", value: "mall" },
      { label: "单页模式", value: "single" },
    ],
    group: "globalConfig.features",
    description: "商城模式：所有商品共享购物车；单页模式：每个商品独立购物车",
    showIf: "globalConfig.enableCart === true",
  },
  {
    key: "globalConfig.allowCustomAddress",
    label: "允许手动输入地址",
    type: "switch",
    defaultValue: false,
    group: "globalConfig.features",
    description: "当地址库无匹配时，允许用户手动输入地址信息",
  },

  // ============ 全局皮肤 (globalStyle) ============
  // 颜色
  {
    key: "globalStyle.primaryColor",
    label: "主色",
    type: "color",
    defaultValue: "#3b82f6",
    group: "globalStyle.color",
    description: "品牌主色调",
  },
  {
    key: "globalStyle.secondaryColor",
    label: "辅色",
    type: "color",
    defaultValue: "#64748b",
    group: "globalStyle.color",
    description: "次要颜色",
  },
  {
    key: "globalStyle.successColor",
    label: "成功色",
    type: "color",
    defaultValue: "#22c55e",
    group: "globalStyle.color",
    description: "成功状态颜色",
  },
  {
    key: "globalStyle.warningColor",
    label: "警告色",
    type: "color",
    defaultValue: "#f59e0b",
    group: "globalStyle.color",
    description: "警告状态颜色",
  },
  {
    key: "globalStyle.errorColor",
    label: "错误色",
    type: "color",
    defaultValue: "#ef4444",
    group: "globalStyle.color",
    description: "错误状态颜色",
  },
  {
    key: "globalStyle.backgroundColor",
    label: "页面背景色",
    type: "color",
    defaultValue: "#f8fafc",
    group: "globalStyle.color",
    description: "页面背景颜色",
  },
  {
    key: "globalStyle.surfaceColor",
    label: "表面背景色",
    type: "color",
    defaultValue: "#ffffff",
    group: "globalStyle.color",
    description: "卡片等表面元素背景色",
  },
  {
    key: "globalStyle.textColor",
    label: "主文字色",
    type: "color",
    defaultValue: "#1e293b",
    group: "globalStyle.color",
    description: "主要文字颜色",
  },
  {
    key: "globalStyle.textSecondaryColor",
    label: "次要文字色",
    type: "color",
    defaultValue: "#64748b",
    group: "globalStyle.color",
    description: "次要文字颜色",
  },
  {
    key: "globalStyle.borderColor",
    label: "边框色",
    type: "color",
    defaultValue: "#e2e8f0",
    group: "globalStyle.color",
    description: "边框颜色",
  },

  // 字体
  {
    key: "globalStyle.fontFamily",
    label: "字体",
    type: "text",
    defaultValue: "Inter, -apple-system, BlinkMacSystemFont, sans-serif",
    placeholder: "字体名称",
    group: "globalStyle.font",
    description: "网站主字体",
  },
  {
    key: "globalStyle.fontSizeBase",
    label: "基础字号",
    type: "text",
    defaultValue: "14px",
    placeholder: "如：14px",
    group: "globalStyle.font",
    description: "基础字体大小",
  },
  {
    key: "globalStyle.lineHeight",
    label: "行高",
    type: "text",
    defaultValue: "1.5",
    placeholder: "如：1.5",
    group: "globalStyle.font",
    description: "文字行高",
  },

  // 圆角
  {
    key: "globalStyle.borderRadiusSmall",
    label: "小圆角",
    type: "text",
    defaultValue: "4px",
    placeholder: "如：4px",
    group: "globalStyle.radius",
    description: "小尺寸元素圆角",
  },
  {
    key: "globalStyle.borderRadiusMedium",
    label: "中圆角",
    type: "text",
    defaultValue: "8px",
    placeholder: "如：8px",
    group: "globalStyle.radius",
    description: "中等尺寸元素圆角",
  },
  {
    key: "globalStyle.borderRadiusLarge",
    label: "大圆角",
    type: "text",
    defaultValue: "12px",
    placeholder: "如：12px",
    group: "globalStyle.radius",
    description: "大尺寸元素圆角",
  },

  // 间距
  {
    key: "globalStyle.spacingUnit",
    label: "间距单位",
    type: "text",
    defaultValue: "8px",
    placeholder: "如：8px",
    group: "globalStyle.spacing",
    description: "基础间距单位",
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
 * 设置嵌套对象的值
 * @param obj 目标对象
 * @param path 路径，如 "globalStyle.primaryColor"
 * @param value 值
 */
function setNestedValue(
  obj: Record<string, any>,
  path: string,
  value: any
): void {
  const keys = path.split(".");
  let current = obj;

  for (let i = 0; i < keys.length - 1; i++) {
    const key = keys[i]!;
    if (!(key in current) || typeof current[key] !== "object") {
      current[key] = {};
    }
    current = current[key];
  }

  const lastKey = keys[keys.length - 1]!;
  current[lastKey] = value;
}

/**
 * 生成默认的站点配置值
 */
export function createDefaultSiteConfig(): Record<string, any> {
  const config: Record<string, any> = {};
  for (const field of SITE_CONFIG_SCHEMA) {
    if (field.defaultValue !== undefined) {
      setNestedValue(config, field.key, field.defaultValue);
    }
  }
  return config;
}
