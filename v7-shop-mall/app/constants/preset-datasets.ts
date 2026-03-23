/**
 * 预设数据集定义
 *
 * 定义系统中所有可用的预设数据集，包括：
 * - 产品数据：产品详情页的产品信息
 * - 文章数据：文章详情页的文章信息
 * - 页面上下文：所有页面共享的上下文信息（域名、国家、货币等）
 * - 落地页数据：落地页特有的数据
 *
 * 每个预设数据集包含：
 * - 可绑定字段列表（用于编辑器属性绑定）
 * - Mock 数据（用于编辑器预览）
 */

import type { DataFieldType, VariableType } from "~/types/data-context";

// ============ 可绑定字段定义 ============

/**
 * 数据来源类型
 */
export type BindableFieldSource =
  | "preset"
  | "variable"
  | "siteConfig"
  | "globalStyle";

/**
 * 可绑定字段（扩展版，包含分类信息）
 */
export interface BindableFieldExt {
  path: string; // 绑定路径，如 "product.title"
  label: string; // 显示名称
  type: DataFieldType; // 字段类型
  source: BindableFieldSource; // 数据来源
  category?: string; // 分类键（用于层级分组），如 "product"、"domain"
  categoryLabel?: string; // 分类显示名称，如 "产品信息"
  description?: string; // 字段描述
  presetId?: string; // 所属预设数据集 ID
}

// ============ 预设数据集定义 ============

/**
 * 预设数据集
 */
export interface PresetDataSet {
  id: string; // 唯一标识
  name: string; // 显示名称
  description?: string; // 描述
  icon?: string; // 图标类名
  fields: BindableFieldExt[]; // 可绑定字段列表
  mockData: Record<string, any>; // 编辑器预览用的 Mock 数据
}

// ============ 产品数据集 ============

const PRODUCT_FIELDS: BindableFieldExt[] = [
  {
    path: "product.id",
    label: "产品 ID",
    type: "number",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.spuId",
    label: "产品 SPU ID",
    type: "number",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.title",
    label: "产品标题",
    type: "string",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.merchandise",
    label: "商品名称",
    type: "string",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.introduction",
    label: "产品介绍",
    type: "string",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.summary",
    label: "产品摘要",
    type: "string",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.sellPrice",
    label: "销售价格",
    type: "number",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.originPrice",
    label: "原价",
    type: "number",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.isMultiSpecs",
    label: "是否多规格",
    type: "boolean",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.images",
    label: "产品图片列表",
    type: "array",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.images[0].relativePath",
    label: "首张图片路径",
    type: "image",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
  {
    path: "product.specifications",
    label: "产品规格列表",
    type: "array",
    source: "preset",
    category: "product",
    categoryLabel: "产品信息",
    presetId: "product",
  },
];

const PRODUCT_MOCK_DATA = {
  product: {
    id: 12345,
    spuId: 67890,
    title: "Sample Product Title",
    merchandise: "Sample Merchandise",
    introduction: "This is a sample product introduction for preview.",
    summary: "Sample product summary",
    sellPrice: 99.99,
    originPrice: 129.99,
    isMultiSpecs: false,
    images: [
      { id: 1, relativePath: "/mock/product-1.svg", name: "product-1.svg" },
      { id: 2, relativePath: "/mock/product-2.svg", name: "product-2.svg" },
    ],
    specifications: [
      {
        id: 1,
        skuId: 1001,
        sellPrice: 99.99,
        originPrice: 129.99,
        stockQuantity: 100,
        attributes: [{ name: "Color", value: "Black" }],
      },
    ],
  },
};

/**
 * 编辑器预览用的完整商品信息 Mock 数据
 * 与 ProductInfo 接口完全匹配
 */
export const PRODUCT_INFO_MOCK = {
  id: 12345,
  spuId: 67890,
  skuId: null,
  countryId: 1,
  languageId: 1,
  title: "M12™ Green 360° třírovinný laser s automatickým nivelováním",
  summary: "Profesionální laserový měřicí přístroj s vysokou přesností ≤ 0,2 mm",
  introduction: "<p>This is a sample product introduction for preview.</p>",
  merchandise: "Sample Merchandise",
  waybillProductName: "Laser Level",
  sellPrice: 99.99,
  originPrice: 129.99,
  costPrice: 50.00,
  isTaxable: true,
  taxationMethod: "PERCENTAGE",
  fixedTaxAmount: null,
  taxAmountThreshold: null,
  taxQuantityThreshold: 0,
  taxPerBase: null,
  barcode: "1234567890123",
  stockQuantity: 100,
  linkStock: false,
  isMultiSpecs: true,
  videoFileId: null,
  botShowSpuId: null,
  riskUserShowSpuId: null,
  blacklistedUserShowSpuId: null,
  images: [
    {
      id: 1,
      relativePath: "/mock/product-1.svg",
      name: "product-1.svg",
      width: 800,
      height: 800,
      suffix: "svg",
      fileSize: 102400,
      mediaType: "image/svg+xml",
      mediaState: "VALID",
    },
    {
      id: 2,
      relativePath: "/mock/product-2.svg",
      name: "product-2.svg",
      width: 800,
      height: 800,
      suffix: "svg",
      fileSize: 98304,
      mediaType: "image/svg+xml",
      mediaState: "VALID",
    },
    {
      id: 3,
      relativePath: "/mock/product-3.svg",
      name: "product-3.svg",
      width: 800,
      height: 800,
      suffix: "svg",
      fileSize: 95000,
      mediaType: "image/svg+xml",
      mediaState: "VALID",
    },
    {
      id: 4,
      relativePath: "/mock/product-4.svg",
      name: "product-4.svg",
      width: 800,
      height: 800,
      suffix: "svg",
      fileSize: 92000,
      mediaType: "image/svg+xml",
      mediaState: "VALID",
    },
  ],
  specifications: [
    {
      id: 1,
      sid: null,
      skuId: 1001,
      sellPrice: 99.99,
      originPrice: 129.99,
      costPrice: 50.00,
      barcode: "1234567890123-001",
      stockQuantity: 50,
      linkStock: false,
      specificationImageId: null,
      specImagePath: "/mock/spec-black.svg",
      attributes: [
        { name: "颜色", value: "黑色", imagePath: "/mock/color-black.svg" },
        { name: "尺寸", value: "S" },
      ],
    },
    {
      id: 2,
      sid: null,
      skuId: 1002,
      sellPrice: 109.99,
      originPrice: 139.99,
      costPrice: 55.00,
      barcode: "1234567890123-002",
      stockQuantity: 30,
      linkStock: false,
      specificationImageId: null,
      specImagePath: "/mock/spec-red.svg",
      attributes: [
        { name: "颜色", value: "红色", imagePath: "/mock/color-red.svg" },
        { name: "尺寸", value: "S" },
      ],
    },
    {
      id: 3,
      sid: null,
      skuId: 1003,
      sellPrice: 119.99,
      originPrice: 149.99,
      costPrice: 60.00,
      barcode: "1234567890123-003",
      stockQuantity: 20,
      linkStock: false,
      specificationImageId: null,
      specImagePath: "/mock/spec-blue.svg",
      attributes: [
        { name: "颜色", value: "蓝色", imagePath: "/mock/color-blue.svg" },
        { name: "尺寸", value: "M" },
      ],
    },
    {
      id: 4,
      sid: null,
      skuId: 1004,
      sellPrice: 129.99,
      originPrice: 159.99,
      costPrice: 65.00,
      barcode: "1234567890123-004",
      stockQuantity: 0,
      linkStock: false,
      specificationImageId: null,
      specImagePath: "/mock/spec-black-l.svg",
      attributes: [
        { name: "颜色", value: "黑色", imagePath: "/mock/color-black.svg" },
        { name: "尺寸", value: "L" },
      ],
    },
  ],
  introductionData: [
    {
      type: "image" as const,
      id: 1,
      src: "/mock/product-1.svg",
      width: 800,
      height: 800,
      aspectRatio: 1,
    },
    {
      type: "html" as const,
      content:
        "<p><strong>Product Features:</strong></p><ul><li>High precision measurement</li><li>360 degree laser coverage</li><li>Auto-leveling technology</li></ul>",
    },
    {
      type: "image" as const,
      id: 2,
      src: "/mock/product-2.svg",
      width: 800,
      height: 800,
      aspectRatio: 1,
    },
    {
      type: "html" as const,
      content:
        "<p><strong>Specifications:</strong></p><p>Model: M12 Green 360</p><p>Accuracy: 0.2mm/m</p><p>Range: Up to 30m</p>",
    },
  ],
};

/**
 * 编辑器预览用的货币 Mock 数据
 */
export const CURRENCY_MOCK = {
  id: 1,
  code: "USD",
  name: "US Dollar",
  symbol: "$",
  exchangeRate: 1.0,
  fractionDigits: 2,
};

export const PRESET_PRODUCT: PresetDataSet = {
  id: "product",
  name: "产品数据",
  description: "产品详情页的产品信息",
  icon: "i-carbon-product",
  fields: PRODUCT_FIELDS,
  mockData: PRODUCT_MOCK_DATA,
};

// ============ 文章数据集 ============

const ARTICLE_FIELDS: BindableFieldExt[] = [
  {
    path: "article.id",
    label: "文章 ID",
    type: "number",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
  {
    path: "article.title",
    label: "文章标题",
    type: "string",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
  {
    path: "article.name",
    label: "文章名称",
    type: "string",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
  {
    path: "article.description",
    label: "文章描述",
    type: "string",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
  {
    path: "article.content",
    label: "文章内容",
    type: "richtext",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
  {
    path: "article.author",
    label: "作者",
    type: "string",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
  {
    path: "article.publishedAt",
    label: "发布时间",
    type: "string",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
  {
    path: "article.coverImage",
    label: "封面图片",
    type: "image",
    source: "preset",
    category: "article",
    categoryLabel: "文章信息",
    presetId: "article",
  },
];

const ARTICLE_MOCK_DATA = {
  article: {
    id: 1001,
    title: "Sample Article Title",
    name: "sample-article",
    description: "This is a sample article description for preview.",
    content:
      "<p>This is the article content with <strong>rich text</strong> formatting.</p>",
    author: "John Doe",
    publishedAt: "2024-01-15",
    coverImage: "/mock/article-cover.svg",
  },
};

export const PRESET_ARTICLE: PresetDataSet = {
  id: "article",
  name: "文章数据",
  description: "文章详情页的文章信息",
  icon: "i-carbon-document",
  fields: ARTICLE_FIELDS,
  mockData: ARTICLE_MOCK_DATA,
};

// ============ 页面上下文数据集 ============

const PAGE_CONTEXT_FIELDS: BindableFieldExt[] = [
  // 域名信息
  {
    path: "pageContext.domain.fullName",
    label: "完整域名",
    type: "string",
    source: "preset",
    category: "domain",
    categoryLabel: "域名信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.domain.name",
    label: "域名名称",
    type: "string",
    source: "preset",
    category: "domain",
    categoryLabel: "域名信息",
    presetId: "pageContext",
  },

  // 国家信息
  {
    path: "pageContext.country.code",
    label: "国家代码",
    type: "string",
    source: "preset",
    category: "country",
    categoryLabel: "国家信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.country.name",
    label: "国家名称",
    type: "string",
    source: "preset",
    category: "country",
    categoryLabel: "国家信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.country.phonePrefix",
    label: "电话前缀",
    type: "string",
    source: "preset",
    category: "country",
    categoryLabel: "国家信息",
    presetId: "pageContext",
  },

  // 货币信息
  {
    path: "pageContext.currency.code",
    label: "货币代码",
    type: "string",
    source: "preset",
    category: "currency",
    categoryLabel: "货币信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.currency.name",
    label: "货币名称",
    type: "string",
    source: "preset",
    category: "currency",
    categoryLabel: "货币信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.currency.symbol",
    label: "货币符号",
    type: "string",
    source: "preset",
    category: "currency",
    categoryLabel: "货币信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.currency.exchangeRate",
    label: "汇率",
    type: "number",
    source: "preset",
    category: "currency",
    categoryLabel: "货币信息",
    presetId: "pageContext",
  },

  // 语言信息
  {
    path: "pageContext.languages[0].code",
    label: "当前语言代码",
    type: "string",
    source: "preset",
    category: "language",
    categoryLabel: "语言信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.languages[0].name",
    label: "当前语言名称",
    type: "string",
    source: "preset",
    category: "language",
    categoryLabel: "语言信息",
    presetId: "pageContext",
  },

  // 公司信息
  {
    path: "pageContext.company.name",
    label: "公司名称",
    type: "string",
    source: "preset",
    category: "company",
    categoryLabel: "公司信息",
    presetId: "pageContext",
  },
  {
    path: "pageContext.company.domain",
    label: "公司域名",
    type: "string",
    source: "preset",
    category: "company",
    categoryLabel: "公司信息",
    presetId: "pageContext",
  },
];

const PAGE_CONTEXT_MOCK_DATA = {
  pageContext: {
    domain: {
      id: 1,
      fullName: "shop.example.com",
      name: "shop",
      type: "COMPANY",
      status: "VALID",
      companyId: 1,
    },
    country: {
      id: 1,
      code: "US",
      name: "United States",
      continentCode: "NA",
      phonePrefix: "+1",
      requiredEmail: true,
      requiredPhone: false,
    },
    currency: {
      id: 1,
      code: "USD",
      name: "US Dollar",
      symbol: "$",
      exchangeRate: 1.0,
      fractionDigits: 2,
    },
    languages: [{ id: 1, code: "en", name: "English", cname: "英语" }],
    company: {
      id: 1,
      name: "Sample Company",
      domain: "example.com",
    },
  },
};

export const PRESET_PAGE_CONTEXT: PresetDataSet = {
  id: "pageContext",
  name: "页面上下文",
  description: "所有页面共享的上下文信息（域名、国家、货币、语言等）",
  icon: "i-carbon-globe",
  fields: PAGE_CONTEXT_FIELDS,
  mockData: PAGE_CONTEXT_MOCK_DATA,
};

// ============ 落地页数据集 ============

const LANDING_SPECIFIC_FIELDS: BindableFieldExt[] = [
  {
    path: "landing.landingProductId",
    label: "落地页产品 ID",
    type: "number",
    source: "preset",
    category: "landing",
    categoryLabel: "落地页信息",
    presetId: "landing",
  },
  {
    path: "landing.protocolGroups",
    label: "协议链接分组",
    type: "array",
    source: "preset",
    category: "landing",
    categoryLabel: "落地页信息",
    presetId: "landing",
  },
];

const LANDING_MOCK_DATA = {
  landing: {
    landingProductId: 12345,
    protocolGroups: [
      {
        title: "服务协议",
        links: [
          { text: "隐私政策", url: "/privacy" },
          { text: "服务条款", url: "/terms" },
        ],
      },
    ],
  },
  ...PRODUCT_MOCK_DATA,
  ...PAGE_CONTEXT_MOCK_DATA,
};

export const PRESET_LANDING: PresetDataSet = {
  id: "landing",
  name: "落地页数据",
  description: "落地页特有的数据（包含产品信息和页面上下文）",
  icon: "i-carbon-rocket",
  fields: [
    ...LANDING_SPECIFIC_FIELDS,
    ...PRODUCT_FIELDS.map((f) => ({ ...f, presetId: "landing" })),
  ],
  mockData: LANDING_MOCK_DATA,
};

// ============ 预设数据集注册表 ============

/**
 * 所有预设数据集
 */
export const PRESET_DATASETS: Record<string, PresetDataSet> = {
  product: PRESET_PRODUCT,
  article: PRESET_ARTICLE,
  pageContext: PRESET_PAGE_CONTEXT,
  landing: PRESET_LANDING,
};

/**
 * 获取预设数据集列表
 */
export function getPresetDataSetList(): PresetDataSet[] {
  return Object.values(PRESET_DATASETS);
}

/**
 * 根据 ID 获取预设数据集
 */
export function getPresetDataSet(id: string): PresetDataSet | undefined {
  return PRESET_DATASETS[id];
}

// ============ 页面类型与预设数据集映射 ============

/**
 * 页面类型对应的预设数据集 ID 列表
 * - 内置页面类型自动关联对应的预设
 * - 自定义页面可以选择需要的预设
 */
export const PAGE_TYPE_PRESETS: Record<string, string[]> = {
  home: ["pageContext"],
  product: ["product", "pageContext"],
  article: ["article", "pageContext"],
  landing: ["landing", "pageContext"],
  checkout: ["product", "pageContext"],
  cart: ["pageContext"],
  search: ["pageContext"],
  category: ["pageContext"],
  custom: [], // 自定义页面默认无预设，可手动选择
};

/**
 * 根据页面类型获取预设数据集列表
 */
export function getPresetsForPageType(pageType: string): PresetDataSet[] {
  const presetIds = PAGE_TYPE_PRESETS[pageType] || PAGE_TYPE_PRESETS.custom;
  return presetIds!
    .map((id) => PRESET_DATASETS[id])
    .filter((p): p is PresetDataSet => !!p);
}

/**
 * 根据页面类型获取所有可绑定字段
 */
export function getBindableFieldsForPageType(
  pageType: string,
): BindableFieldExt[] {
  const presets = getPresetsForPageType(pageType);
  const fields: BindableFieldExt[] = [];

  for (const preset of presets) {
    fields.push(...preset.fields);
  }

  return fields;
}

/**
 * 根据页面类型获取合并的 Mock 数据
 */
export function getMockDataForPageType(pageType: string): Record<string, any> {
  const presets = getPresetsForPageType(pageType);
  let mockData: Record<string, any> = {};

  for (const preset of presets) {
    mockData = { ...mockData, ...preset.mockData };
  }

  return mockData;
}

// ============ 组件专用 Mock 数据 ============

/**
 * HeroBanner 组件 Mock 数据
 * 用于首页顶部海报轮播预览
 */
export const HERO_BANNER_MOCK = {
  items: [
    {
      src: "/mock/banner-1.svg",
      alt: "Spring Collection",
      link: "/collection/spring",
    },
    {
      src: "/mock/banner-2.svg",
      alt: "New Arrivals",
      link: "/new-arrivals",
    },
    {
      src: "/mock/banner-3.svg",
      alt: "Special Offer",
      link: "/sale",
    },
  ],
};

/**
 * HeroSection 海报横幅 Mock 数据
 */
export const HERO_SECTION_MOCK = {
  preset: "classic",
  backgroundImage: "/mock/hero-bg.svg",
  eyebrow: "Breezy looks made for sunny days.",
  title: "LUXETTE'S STYLE",
  subtitle: "",
  buttonText: "Buy now",
  buttonLink: "/",
  buttonStyle: "solid",
  contentAlign: "center",
  contentPosition: "center",
  overlayType: "gradient",
  overlayColor: "#000000",
  overlayOpacity: 30,
};

/**
 * Checkout 收银台组件 Mock 数据
 * 用于收银台页面预览
 */
export const CHECKOUT_MOCK = {
  items: [
    {
      id: 1,
      title: "M12™ Green 360° Laser Level",
      image: "/mock/product-1.svg",
      spec: "黑色 / S",
      quantity: 2,
      price: 99.99,
    },
    {
      id: 2,
      title: "Professional Measuring Tool Kit",
      image: "/mock/product-2.svg",
      spec: "红色 / M",
      quantity: 1,
      price: 149.99,
    },
  ],
  subtotal: 349.97,
  shippingFee: 0,
  discount: 20.0,
  total: 329.97,
  itemCount: 3,
};

/**
 * OrderResult 订单结果组件 Mock 数据
 * 用于订单结果页面预览
 */
export const ORDER_RESULT_MOCK = {
  orderId: "ORD-2024-001234",
  amount: 329.97,
  recipient: "John Doe",
  phone: "+1 *** *** 1234",
  email: "j***@example.com",
  address: "123 Main St, *** City, CA 90001",
  paymentMethod: "Credit Card",
  paymentStatus: "PAID",
  orderTime: "2024-01-15 14:30:00",
};

/**
 * Site Config 站点配置 Mock 数据
 * 用于 Header/Footer 等全局组件预览
 */
export const SITE_CONFIG_MOCK = {
  siteName: "V7 Shop",
  logo: "/mock/logo.svg",
  contact: {
    email: "support@v7shop.com",
    phone: "+1 (800) 123-4567",
    address: "123 Commerce Street, San Francisco, CA 94102",
  },
  social: [
    {
      platform: "facebook",
      url: "https://facebook.com/v7shop",
      icon: "/mock/social-facebook.svg",
    },
    {
      platform: "instagram",
      url: "https://instagram.com/v7shop",
      icon: "/mock/social-instagram.svg",
    },
    {
      platform: "twitter",
      url: "https://twitter.com/v7shop",
      icon: "/mock/social-twitter.svg",
    },
    {
      platform: "youtube",
      url: "https://youtube.com/v7shop",
      icon: "/mock/social-youtube.svg",
    },
    {
      platform: "tiktok",
      url: "https://tiktok.com/@v7shop",
      icon: "/mock/social-tiktok.svg",
    },
  ],
  protocols: [
    { text: "Privacy Policy", url: "/privacy" },
    { text: "Terms of Service", url: "/terms" },
    { text: "Refund Policy", url: "/refund" },
    { text: "Shipping Policy", url: "/shipping" },
  ],
  copyright: "© 2024 V7 Shop. All rights reserved.",
  paymentIcons: [
    "/mock/payment-visa.svg",
    "/mock/payment-mastercard.svg",
    "/mock/payment-paypal.svg",
    "/mock/payment-amex.svg",
  ],
};

/**
 * SpecSelector 规格选择器组件 Mock 数据
 * 用于规格选择器预览
 */
export const SPEC_SELECTOR_MOCK = {
  specifications: PRODUCT_INFO_MOCK.specifications,
  selectedSpec: PRODUCT_INFO_MOCK.specifications[0],
};

/**
 * QuantitySelector 数量选择器组件 Mock 数据
 * 用于数量选择器预览
 */
export const QUANTITY_SELECTOR_MOCK = {
  quantity: 1,
  min: 1,
  max: 99,
  stock: 50,
};

/**
 * ActionButtons 购买按钮组件 Mock 数据
 * 用于购买按钮预览
 */
export const ACTION_BUTTONS_MOCK = {
  canAddToCart: true,
  canBuyNow: true,
  isLoading: false,
  stock: 50,
};

/**
 * 文章信息 Mock 数据（与 ArticleInfo 接口匹配）
 * 用于文章详情页编辑器预览
 */
export const ARTICLE_INFO_MOCK = {
  title: "Sample Article Title - How to Choose the Right Product",
  description: "This is a sample article description for preview. Learn how to make the best purchasing decisions.",
  content: `
    <h2>Introduction</h2>
    <p>Welcome to our comprehensive guide on choosing the right product. This article will help you understand the key factors to consider.</p>
    <p><img src="/mock/article-cover.svg" alt="Article Cover" style="max-width: 100%; border-radius: 8px;" /></p>
    <h2>Key Considerations</h2>
    <p>When selecting a product, consider the following aspects:</p>
    <ul>
      <li><strong>Quality</strong> - Always prioritize quality over price</li>
      <li><strong>Features</strong> - Make sure it meets your requirements</li>
      <li><strong>Reviews</strong> - Check what other customers say</li>
    </ul>
    <h2>Conclusion</h2>
    <p>Making an informed decision is crucial. Take your time to research and compare options before making a purchase.</p>
  `,
  updateTime: "2024-01-15 10:30:00",
};

/**
 * 订单结果信息 Mock 数据（与 OrderResultInfo 接口匹配）
 * 用于订单结果页编辑器预览
 */
export const ORDER_RESULT_INFO_MOCK = {
  id: 12345,
  totalAmount: "329.97",
  currencySymbol: "$",
  currencyCode: "USD",
  firstName: "John Doe",
  phone: "+1 *** *** 1234",
  email: "j***@example.com",
  address: "123 Main St, *** City, CA 90001",
  paymentMethod: "Credit Card",
  paymentStatus: "PAID",
  orderTime: "2024-01-15 14:30:00",
};

/**
 * 统一的编辑器预览 Mock 数据
 * 包含所有组件所需的 mock 数据
 */
export const EDITOR_PREVIEW_MOCK = {
  // 核心数据
  productInfo: PRODUCT_INFO_MOCK,
  currency: CURRENCY_MOCK,

  // 组件专用数据
  heroBanner: HERO_BANNER_MOCK,
  checkout: CHECKOUT_MOCK,
  orderResult: ORDER_RESULT_MOCK,
  siteConfig: SITE_CONFIG_MOCK,
  specSelector: SPEC_SELECTOR_MOCK,
  quantitySelector: QUANTITY_SELECTOR_MOCK,
  actionButtons: ACTION_BUTTONS_MOCK,

  // 预设数据集
  ...PRODUCT_MOCK_DATA,
  ...ARTICLE_MOCK_DATA,
  ...PAGE_CONTEXT_MOCK_DATA,
};

// ============ 转换工具函数 ============

/**
 * DataFieldType 到 VariableType 的映射
 */
export function dataFieldTypeToVariableType(
  fieldType: DataFieldType,
): VariableType {
  const mapping: Record<DataFieldType, VariableType> = {
    string: "string",
    number: "number",
    boolean: "boolean",
    object: "object",
    array: "array",
    image: "image",
    richtext: "richtext",
  };
  return mapping[fieldType] || "string";
}
