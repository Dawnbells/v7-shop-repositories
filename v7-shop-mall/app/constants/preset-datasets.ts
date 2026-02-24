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

import type { DataFieldType, VariableType } from '~/types/data-context'

// ============ 可绑定字段定义 ============

/**
 * 数据来源类型
 */
export type BindableFieldSource = 'preset' | 'variable' | 'siteConfig' | 'globalStyle'

/**
 * 可绑定字段（扩展版，包含分类信息）
 */
export interface BindableFieldExt {
  path: string              // 绑定路径，如 "product.title"
  label: string             // 显示名称
  type: DataFieldType       // 字段类型
  source: BindableFieldSource   // 数据来源
  category?: string         // 分类键（用于层级分组），如 "product"、"domain"
  categoryLabel?: string    // 分类显示名称，如 "产品信息"
  description?: string      // 字段描述
  presetId?: string         // 所属预设数据集 ID
}

// ============ 预设数据集定义 ============

/**
 * 预设数据集
 */
export interface PresetDataSet {
  id: string                      // 唯一标识
  name: string                    // 显示名称
  description?: string            // 描述
  icon?: string                   // 图标类名
  fields: BindableFieldExt[]      // 可绑定字段列表
  mockData: Record<string, any>   // 编辑器预览用的 Mock 数据
}

// ============ 产品数据集 ============

const PRODUCT_FIELDS: BindableFieldExt[] = [
  { path: 'product.id', label: '产品 ID', type: 'number', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.spuId', label: '产品 SPU ID', type: 'number', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.title', label: '产品标题', type: 'string', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.merchandise', label: '商品名称', type: 'string', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.introduction', label: '产品介绍', type: 'string', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.summary', label: '产品摘要', type: 'string', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.sellPrice', label: '销售价格', type: 'number', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.originPrice', label: '原价', type: 'number', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.isMultiSpecs', label: '是否多规格', type: 'boolean', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.images', label: '产品图片列表', type: 'array', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.images[0].relativePath', label: '首张图片路径', type: 'image', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
  { path: 'product.specifications', label: '产品规格列表', type: 'array', source: 'preset', category: 'product', categoryLabel: '产品信息', presetId: 'product' },
]

const PRODUCT_MOCK_DATA = {
  product: {
    id: 12345,
    spuId: 67890,
    title: 'Sample Product Title',
    merchandise: 'Sample Merchandise',
    introduction: 'This is a sample product introduction for preview.',
    summary: 'Sample product summary',
    sellPrice: 99.99,
    originPrice: 129.99,
    isMultiSpecs: false,
    images: [
      { id: 1, relativePath: '/mock/product-1.jpg', name: 'product-1.jpg' },
      { id: 2, relativePath: '/mock/product-2.jpg', name: 'product-2.jpg' },
    ],
    specifications: [
      { id: 1, skuId: 1001, sellPrice: 99.99, originPrice: 129.99, stockQuantity: 100, attributes: [{ name: 'Color', value: 'Black' }] },
    ],
  },
}

export const PRESET_PRODUCT: PresetDataSet = {
  id: 'product',
  name: '产品数据',
  description: '产品详情页的产品信息',
  icon: 'i-carbon-product',
  fields: PRODUCT_FIELDS,
  mockData: PRODUCT_MOCK_DATA,
}

// ============ 文章数据集 ============

const ARTICLE_FIELDS: BindableFieldExt[] = [
  { path: 'article.id', label: '文章 ID', type: 'number', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
  { path: 'article.title', label: '文章标题', type: 'string', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
  { path: 'article.name', label: '文章名称', type: 'string', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
  { path: 'article.description', label: '文章描述', type: 'string', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
  { path: 'article.content', label: '文章内容', type: 'richtext', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
  { path: 'article.author', label: '作者', type: 'string', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
  { path: 'article.publishedAt', label: '发布时间', type: 'string', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
  { path: 'article.coverImage', label: '封面图片', type: 'image', source: 'preset', category: 'article', categoryLabel: '文章信息', presetId: 'article' },
]

const ARTICLE_MOCK_DATA = {
  article: {
    id: 1001,
    title: 'Sample Article Title',
    name: 'sample-article',
    description: 'This is a sample article description for preview.',
    content: '<p>This is the article content with <strong>rich text</strong> formatting.</p>',
    author: 'John Doe',
    publishedAt: '2024-01-15',
    coverImage: '/mock/article-cover.jpg',
  },
}

export const PRESET_ARTICLE: PresetDataSet = {
  id: 'article',
  name: '文章数据',
  description: '文章详情页的文章信息',
  icon: 'i-carbon-document',
  fields: ARTICLE_FIELDS,
  mockData: ARTICLE_MOCK_DATA,
}

// ============ 页面上下文数据集 ============

const PAGE_CONTEXT_FIELDS: BindableFieldExt[] = [
  // 域名信息
  { path: 'pageContext.domain.fullName', label: '完整域名', type: 'string', source: 'preset', category: 'domain', categoryLabel: '域名信息', presetId: 'pageContext' },
  { path: 'pageContext.domain.name', label: '域名名称', type: 'string', source: 'preset', category: 'domain', categoryLabel: '域名信息', presetId: 'pageContext' },
  
  // 国家信息
  { path: 'pageContext.country.code', label: '国家代码', type: 'string', source: 'preset', category: 'country', categoryLabel: '国家信息', presetId: 'pageContext' },
  { path: 'pageContext.country.name', label: '国家名称', type: 'string', source: 'preset', category: 'country', categoryLabel: '国家信息', presetId: 'pageContext' },
  { path: 'pageContext.country.phonePrefix', label: '电话前缀', type: 'string', source: 'preset', category: 'country', categoryLabel: '国家信息', presetId: 'pageContext' },
  
  // 货币信息
  { path: 'pageContext.currency.code', label: '货币代码', type: 'string', source: 'preset', category: 'currency', categoryLabel: '货币信息', presetId: 'pageContext' },
  { path: 'pageContext.currency.name', label: '货币名称', type: 'string', source: 'preset', category: 'currency', categoryLabel: '货币信息', presetId: 'pageContext' },
  { path: 'pageContext.currency.symbol', label: '货币符号', type: 'string', source: 'preset', category: 'currency', categoryLabel: '货币信息', presetId: 'pageContext' },
  { path: 'pageContext.currency.exchangeRate', label: '汇率', type: 'number', source: 'preset', category: 'currency', categoryLabel: '货币信息', presetId: 'pageContext' },
  
  // 语言信息
  { path: 'pageContext.languages[0].code', label: '当前语言代码', type: 'string', source: 'preset', category: 'language', categoryLabel: '语言信息', presetId: 'pageContext' },
  { path: 'pageContext.languages[0].name', label: '当前语言名称', type: 'string', source: 'preset', category: 'language', categoryLabel: '语言信息', presetId: 'pageContext' },
  
  // 公司信息
  { path: 'pageContext.company.name', label: '公司名称', type: 'string', source: 'preset', category: 'company', categoryLabel: '公司信息', presetId: 'pageContext' },
  { path: 'pageContext.company.domain', label: '公司域名', type: 'string', source: 'preset', category: 'company', categoryLabel: '公司信息', presetId: 'pageContext' },
]

const PAGE_CONTEXT_MOCK_DATA = {
  pageContext: {
    domain: {
      id: 1,
      fullName: 'shop.example.com',
      name: 'shop',
      type: 'COMPANY',
      status: 'VALID',
      companyId: 1,
    },
    country: {
      id: 1,
      code: 'US',
      name: 'United States',
      continentCode: 'NA',
      phonePrefix: '+1',
      requiredEmail: true,
      requiredPhone: false,
    },
    currency: {
      id: 1,
      code: 'USD',
      name: 'US Dollar',
      symbol: '$',
      exchangeRate: 1.0,
      fractionDigits: 2,
    },
    languages: [
      { id: 1, code: 'en', name: 'English', cname: '英语' },
    ],
    company: {
      id: 1,
      name: 'Sample Company',
      domain: 'example.com',
    },
  },
}

export const PRESET_PAGE_CONTEXT: PresetDataSet = {
  id: 'pageContext',
  name: '页面上下文',
  description: '所有页面共享的上下文信息（域名、国家、货币、语言等）',
  icon: 'i-carbon-globe',
  fields: PAGE_CONTEXT_FIELDS,
  mockData: PAGE_CONTEXT_MOCK_DATA,
}

// ============ 落地页数据集 ============

const LANDING_SPECIFIC_FIELDS: BindableFieldExt[] = [
  { path: 'landing.landingProductId', label: '落地页产品 ID', type: 'number', source: 'preset', category: 'landing', categoryLabel: '落地页信息', presetId: 'landing' },
  { path: 'landing.protocolGroups', label: '协议链接分组', type: 'array', source: 'preset', category: 'landing', categoryLabel: '落地页信息', presetId: 'landing' },
]

const LANDING_MOCK_DATA = {
  landing: {
    landingProductId: 12345,
    protocolGroups: [
      {
        title: '服务协议',
        links: [
          { text: '隐私政策', url: '/privacy' },
          { text: '服务条款', url: '/terms' },
        ],
      },
    ],
  },
  ...PRODUCT_MOCK_DATA,
  ...PAGE_CONTEXT_MOCK_DATA,
}

export const PRESET_LANDING: PresetDataSet = {
  id: 'landing',
  name: '落地页数据',
  description: '落地页特有的数据（包含产品信息和页面上下文）',
  icon: 'i-carbon-rocket',
  fields: [
    ...LANDING_SPECIFIC_FIELDS,
    ...PRODUCT_FIELDS.map(f => ({ ...f, presetId: 'landing' })),
  ],
  mockData: LANDING_MOCK_DATA,
}

// ============ 预设数据集注册表 ============

/**
 * 所有预设数据集
 */
export const PRESET_DATASETS: Record<string, PresetDataSet> = {
  product: PRESET_PRODUCT,
  article: PRESET_ARTICLE,
  pageContext: PRESET_PAGE_CONTEXT,
  landing: PRESET_LANDING,
}

/**
 * 获取预设数据集列表
 */
export function getPresetDataSetList(): PresetDataSet[] {
  return Object.values(PRESET_DATASETS)
}

/**
 * 根据 ID 获取预设数据集
 */
export function getPresetDataSet(id: string): PresetDataSet | undefined {
  return PRESET_DATASETS[id]
}

// ============ 页面类型与预设数据集映射 ============

/**
 * 页面类型对应的预设数据集 ID 列表
 * - 内置页面类型自动关联对应的预设
 * - 自定义页面可以选择需要的预设
 */
export const PAGE_TYPE_PRESETS: Record<string, string[]> = {
  home: ['pageContext'],
  product: ['product', 'pageContext'],
  article: ['article', 'pageContext'],
  landing: ['landing', 'pageContext'],
  checkout: ['product', 'pageContext'],
  cart: ['pageContext'],
  search: ['pageContext'],
  category: ['pageContext'],
  custom: [], // 自定义页面默认无预设，可手动选择
}

/**
 * 根据页面类型获取预设数据集列表
 */
export function getPresetsForPageType(pageType: string): PresetDataSet[] {
  const presetIds = PAGE_TYPE_PRESETS[pageType] || PAGE_TYPE_PRESETS.custom
  return presetIds
    .map(id => PRESET_DATASETS[id])
    .filter((p): p is PresetDataSet => !!p)
}

/**
 * 根据页面类型获取所有可绑定字段
 */
export function getBindableFieldsForPageType(pageType: string): BindableFieldExt[] {
  const presets = getPresetsForPageType(pageType)
  const fields: BindableFieldExt[] = []
  
  for (const preset of presets) {
    fields.push(...preset.fields)
  }
  
  return fields
}

/**
 * 根据页面类型获取合并的 Mock 数据
 */
export function getMockDataForPageType(pageType: string): Record<string, any> {
  const presets = getPresetsForPageType(pageType)
  let mockData: Record<string, any> = {}
  
  for (const preset of presets) {
    mockData = { ...mockData, ...preset.mockData }
  }
  
  return mockData
}

// ============ 转换工具函数 ============

/**
 * DataFieldType 到 VariableType 的映射
 */
export function dataFieldTypeToVariableType(fieldType: DataFieldType): VariableType {
  const mapping: Record<DataFieldType, VariableType> = {
    string: 'string',
    number: 'number',
    boolean: 'boolean',
    object: 'object',
    array: 'array',
    image: 'image',
    richtext: 'richtext',
  }
  return mapping[fieldType] || 'string'
}
