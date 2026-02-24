/**
 * 数据绑定类型匹配工具
 * 用于判断属性类型与变量类型是否兼容
 */

import type { DataFieldType, BindableField } from '~/types/data-context'

// 从 preset-datasets 导入类型
import type { BindableFieldExt, BindableFieldSource } from '~/constants/preset-datasets'

/**
 * 变量类型
 */
export type VariableType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'color'
  | 'image'
  | 'richtext'
  | 'enum'
  | 'array'
  | 'object'

/**
 * 属性编辑器类型
 */
export type PropEditorType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'switch'
  | 'color'
  | 'image'
  | 'richtext'
  | 'select'
  | 'radio'
  | 'json'
  | 'icon'

/**
 * 可绑定数据源分组类型
 */
export type DataSourceGroup = 'preset' | 'variable' | 'siteConfig' | 'globalStyle'

/**
 * 可绑定数据源接口
 */
export interface BindableDataSource {
  key: string              // 完整键名
  label: string            // 显示名称
  type: VariableType       // 数据类型
  group: DataSourceGroup   // 数据来源分组
  groupLabel: string       // 来源显示名称
  description?: string     // 描述说明
  category?: string        // 分类键
  categoryLabel?: string   // 分类显示名称
}

/**
 * 数据源分组配置
 */
export const DATA_SOURCE_GROUP_CONFIG: Record<DataSourceGroup, { label: string; icon: string; order: number }> = {
  preset: { label: '页面预设数据', icon: 'i-carbon-data-base', order: 1 },
  variable: { label: '自定义变量', icon: 'i-carbon-variable', order: 2 },
  siteConfig: { label: '全局配置', icon: 'i-carbon-settings', order: 3 },
  globalStyle: { label: '全局样式', icon: 'i-carbon-color-palette', order: 4 },
}

/**
 * 分类分组结构
 */
export interface CategoryGroup {
  key: string
  label: string
  sources: BindableDataSource[]
}

/**
 * 数据源分组结构（带层级）
 */
export interface SourceGroupHierarchy {
  group: DataSourceGroup
  label: string
  icon: string
  categories: CategoryGroup[]
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
 */
export function getCompatibleVariableTypes(propType: PropEditorType): VariableType[] {
  return TYPE_COMPATIBILITY_MAP[propType] || ['string']
}

/**
 * 判断属性类型与变量类型是否兼容
 */
export function isTypeCompatible(
  propType: PropEditorType,
  variableType: VariableType
): boolean {
  const compatibleTypes = getCompatibleVariableTypes(propType)
  return compatibleTypes.includes(variableType)
}

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

/**
 * 过滤出与属性类型兼容的数据源
 */
export function filterCompatibleSources(
  sources: BindableDataSource[],
  propType: PropEditorType
): BindableDataSource[] {
  return sources.filter(source => isTypeCompatible(propType, source.type))
}

/**
 * 按分组对数据源进行分组
 */
export function groupDataSources(
  sources: BindableDataSource[]
): Record<DataSourceGroup, BindableDataSource[]> {
  const grouped: Record<DataSourceGroup, BindableDataSource[]> = {
    preset: [],
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

/**
 * 按数据源和分类进行层级分组
 */
export function groupDataSourcesHierarchically(
  sources: BindableDataSource[]
): SourceGroupHierarchy[] {
  const groupMap = new Map<DataSourceGroup, Map<string, BindableDataSource[]>>()

  for (const source of sources) {
    if (!groupMap.has(source.group)) {
      groupMap.set(source.group, new Map())
    }
    const categoryMap = groupMap.get(source.group)!
    const categoryKey = source.category || 'other'
    if (!categoryMap.has(categoryKey)) {
      categoryMap.set(categoryKey, [])
    }
    categoryMap.get(categoryKey)!.push(source)
  }

  const result: SourceGroupHierarchy[] = []

  const sortedGroups = Array.from(groupMap.keys()).sort((a, b) => {
    return (DATA_SOURCE_GROUP_CONFIG[a]?.order || 99) - (DATA_SOURCE_GROUP_CONFIG[b]?.order || 99)
  })

  for (const group of sortedGroups) {
    const categoryMap = groupMap.get(group)!
    const config = DATA_SOURCE_GROUP_CONFIG[group]

    const categories: CategoryGroup[] = []
    for (const [categoryKey, categorySources] of categoryMap) {
      const firstSource = categorySources[0]
      categories.push({
        key: categoryKey,
        label: firstSource?.categoryLabel || categoryKey,
        sources: categorySources,
      })
    }

    result.push({
      group,
      label: config.label,
      icon: config.icon,
      categories,
    })
  }

  return result
}
