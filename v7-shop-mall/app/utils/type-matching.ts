/**
 * 数据绑定类型匹配工具
 * 用于判断属性类型与变量类型是否兼容
 */

import type { PropEditorType } from '~/types/component-meta'
import type { VariableType } from '~/types/data-context'

/**
 * 可绑定数据源分组类型
 */
export type DataSourceGroup = 'variable' | 'siteConfig' | 'globalStyle'

/**
 * 可绑定数据源接口
 * 统一表示自定义变量和全局配置
 */
export interface BindableDataSource {
  key: string              // 完整键名（如 globalConfig.siteName 或变量 key）
  label: string            // 显示名称
  type: VariableType       // 数据类型
  group: DataSourceGroup   // 数据来源分组
  groupLabel: string       // 来源显示名称
  description?: string     // 描述说明
}

/**
 * 数据源分组配置
 */
export const DATA_SOURCE_GROUP_CONFIG: Record<DataSourceGroup, { label: string; icon: string }> = {
  variable: { label: '自定义变量', icon: 'i-carbon-variable' },
  siteConfig: { label: '全局配置', icon: 'i-carbon-settings' },
  globalStyle: { label: '全局样式', icon: 'i-carbon-color-palette' },
}

/**
 * PropEditorType 到兼容 VariableType 的映射
 */
const TYPE_COMPATIBILITY_MAP: Record<PropEditorType, VariableType[]> = {
  text: ['string'],
  textarea: ['string'],
  number: ['number'],
  switch: ['boolean'],
  color: ['color', 'string'],
  image: ['image', 'string'],
  richtext: ['richtext', 'string'],
  select: ['string', 'number', 'enum'],
  radio: ['string', 'number', 'enum'],
  json: ['object', 'array'],
  icon: ['string'],
}

/**
 * 获取与属性类型兼容的变量类型列表
 * @param propType 属性编辑器类型
 * @returns 兼容的变量类型数组
 */
export function getCompatibleVariableTypes(propType: PropEditorType): VariableType[] {
  return TYPE_COMPATIBILITY_MAP[propType] || ['string']
}

/**
 * 判断属性类型与变量类型是否兼容
 * @param propType 属性编辑器类型
 * @param variableType 变量类型
 * @returns 是否兼容
 */
export function isTypeCompatible(
  propType: PropEditorType,
  variableType: VariableType
): boolean {
  const compatibleTypes = getCompatibleVariableTypes(propType)
  return compatibleTypes.includes(variableType)
}

/**
 * 将 PropEditorType 转换为对应的 VariableType
 * 用于将 SiteConfigSchema 的 type 转换为 VariableType
 * @param propType 属性编辑器类型
 * @returns 对应的变量类型
 */
export function propTypeToVariableType(propType: PropEditorType): VariableType {
  const mapping: Record<PropEditorType, VariableType> = {
    text: 'string',
    textarea: 'string',
    number: 'number',
    switch: 'boolean',
    color: 'color',
    image: 'image',
    richtext: 'richtext',
    select: 'string',
    radio: 'string',
    json: 'object',
    icon: 'string',
  }
  return mapping[propType] || 'string'
}

/**
 * 过滤出与属性类型兼容的数据源
 * @param sources 所有数据源
 * @param propType 属性编辑器类型
 * @returns 兼容的数据源列表
 */
export function filterCompatibleSources(
  sources: BindableDataSource[],
  propType: PropEditorType
): BindableDataSource[] {
  return sources.filter(source => isTypeCompatible(propType, source.type))
}

/**
 * 按分组对数据源进行分组
 * @param sources 数据源列表
 * @returns 分组后的数据源
 */
export function groupDataSources(
  sources: BindableDataSource[]
): Record<DataSourceGroup, BindableDataSource[]> {
  const grouped: Record<DataSourceGroup, BindableDataSource[]> = {
    variable: [],
    siteConfig: [],
    globalStyle: [],
  }

  for (const source of sources) {
    if (grouped[source.group]) {
      grouped[source.group].push(source)
    }
  }

  return grouped
}
