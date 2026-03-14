<script setup lang="ts">
/**
 * PropertyPanel - 右侧属性面板
 * 用于编辑选中组件的属性，支持数据绑定（类型匹配 + 全局数据）
 */

import type { ComponentMeta, DataBinding, PropEditorType, StyleSchema } from '~/types/component-meta'
import { useCanvasState } from '~/composables/useCanvasState'
import { useBlockRegistry } from '~/composables/useBlockRegistry'
import { useThemeSchema } from '~/composables/useThemeSchema'
import { SITE_CONFIG_SCHEMA } from '~/constants/site-config.schema'
import {
  getBindableFieldsForPageType,
  dataFieldTypeToVariableType,
  getPresetDataSet,
  type BindableFieldExt,
} from '~/constants/preset-datasets'
import {
  type BindableDataSource,
  propTypeToVariableType,
  filterCompatibleSources,
} from '~/utils/type-matching'

interface Props {
  pageType?: string
  presetIds?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  pageType: 'landing',
  presetIds: () => [],
})

type TabType = 'props' | 'style' | 'action'

// 样式设备类型
type StyleDeviceType = 'base' | 'desktop' | 'tablet' | 'mobile'

// 设备选项配置
const styleDeviceOptions: { key: StyleDeviceType; label: string; icon: string }[] = [
  { key: 'base', label: '通用', icon: 'i-carbon-settings' },
  { key: 'desktop', label: 'PC', icon: 'i-carbon-laptop' },
  { key: 'tablet', label: '平板', icon: 'i-carbon-tablet' },
  { key: 'mobile', label: '移动', icon: 'i-carbon-mobile' },
]

// 样式分组配置（用于显示分组标题）
const STYLE_GROUP_LABELS: Record<string, string> = {
  size: '尺寸',
  margin: '外边距',
  padding: '内边距',
  background: '背景',
  text: '文字',
  border: '边框',
  layout: '布局',
  effect: '效果',
}

const activeTab = ref<TabType>('props')
const activeStyleDevice = ref<StyleDeviceType>('base')

const tabs: { key: TabType; label: string; icon: string }[] = [
  { key: 'props', label: '属性', icon: 'i-carbon-settings-adjust' },
  { key: 'style', label: '样式', icon: 'i-carbon-paint-brush' },
  { key: 'action', label: '交互', icon: 'i-carbon-touch-interaction' }
]

// 获取画布状态
const { selectedNode, selectedNodeId, updateNode } = useCanvasState()
const { getBlockMeta } = useBlockRegistry()
const { variableSchema } = useThemeSchema()

// 是否有选中组件
const hasSelection = computed(() => !!selectedNode.value)

// 选中组件的元数据
const blockMeta = computed<ComponentMeta | null>(() => {
  if (!selectedNode.value) return null
  return getBlockMeta(selectedNode.value.type) || null
})

// 组件显示名称
const componentName = computed(() => {
  if (!selectedNode.value) return ''
  return selectedNode.value.name || blockMeta.value?.name || selectedNode.value.type
})

// 根据页面类型或自定义预设 ID 获取预设字段
function getPresetFieldsForPage(): BindableFieldExt[] {
  // 自定义页面使用 presetIds
  if (props.pageType === 'custom' && props.presetIds.length > 0) {
    const fields: BindableFieldExt[] = []
    for (const presetId of props.presetIds) {
      const preset = getPresetDataSet(presetId)
      if (preset) {
        fields.push(...preset.fields)
      }
    }
    return fields
  }
  // 其他页面类型使用固定映射
  return getBindableFieldsForPageType(props.pageType)
}

// 合并所有可绑定数据源
const allDataSources = computed<BindableDataSource[]>(() => {
  const sources: BindableDataSource[] = []

  // 1. 页面预设数据（根据页面类型或自定义预设 ID 动态获取）
  const presetFields = getPresetFieldsForPage()
  for (const field of presetFields) {
    sources.push({
      key: field.path,
      label: field.label,
      type: dataFieldTypeToVariableType(field.type),
      group: 'preset',
      groupLabel: '页面预设数据',
      description: field.description,
      category: field.category,
      categoryLabel: field.categoryLabel,
    })
  }

  // 2. 自定义变量
  for (const variable of variableSchema.value || []) {
    sources.push({
      key: `custom.${variable.key}`,
      label: variable.label,
      type: variable.type,
      group: 'variable',
      groupLabel: '自定义变量',
      description: variable.description,
    })
  }

  // 3. 全局配置（siteConfig）和全局皮肤变量（globalStyle）
  for (const field of SITE_CONFIG_SCHEMA) {
    const isGlobalStyle = field.group.startsWith('globalStyle')
    sources.push({
      key: field.key,
      label: field.label,
      type: propTypeToVariableType(field.type),
      group: isGlobalStyle ? 'globalStyle' : 'siteConfig',
      groupLabel: isGlobalStyle ? '全局皮肤' : '全局配置',
      description: field.description,
    })
  }

  return sources
})

// 属性面板数据源：页面预设 + 全局配置 + 自定义变量
const propDataSources = computed(() =>
  allDataSources.value.filter(s => s.group === 'preset' || s.group === 'variable' || s.group === 'siteConfig')
)

// 样式面板数据源：页面预设 + 全局皮肤 + 自定义变量
const styleDataSources = computed(() =>
  allDataSources.value.filter(s => s.group === 'preset' || s.group === 'variable' || s.group === 'globalStyle')
)

// 根据属性类型获取兼容的数据源（属性面板使用）
function getCompatibleSourcesForProp(propType: PropEditorType): BindableDataSource[] {
  return filterCompatibleSources(propDataSources.value, propType)
}

// 根据属性类型获取兼容的数据源（样式面板使用）
function getCompatibleSourcesForStyle(propType: PropEditorType): BindableDataSource[] {
  return filterCompatibleSources(styleDataSources.value, propType)
}

// 获取属性的绑定配置
function getBindingForProp(propKey: string): DataBinding | null {
  if (!selectedNode.value?.bindings) return null
  return selectedNode.value.bindings.find(b => b.propKey === propKey) || null
}

// 更新组件属性
function updateProp(key: string, value: any) {
  if (!selectedNodeId.value) return
  updateNode(selectedNodeId.value, {
    props: { [key]: value }
  })
}

// 更新属性绑定
function updateBinding(propKey: string, binding: DataBinding | null) {
  if (!selectedNodeId.value || !selectedNode.value) return
  
  const currentBindings = selectedNode.value.bindings || []
  let newBindings: DataBinding[]
  
  if (binding) {
    // 添加或更新绑定
    const existingIndex = currentBindings.findIndex(b => b.propKey === propKey)
    if (existingIndex >= 0) {
      newBindings = [...currentBindings]
      newBindings[existingIndex] = binding
    } else {
      newBindings = [...currentBindings, binding]
    }
  } else {
    // 移除绑定
    newBindings = currentBindings.filter(b => b.propKey !== propKey)
  }
  
  // 直接更新 bindings 字段
  if (selectedNode.value) {
    selectedNode.value.bindings = newBindings
  }
}

// 从组件 meta 获取样式 schema
const styleSchema = computed<StyleSchema[]>(() => {
  return blockMeta.value?.styleSchema || []
})

// 获取样式 schema 中的所有分组
const styleGroups = computed<string[]>(() => {
  const groups = new Set<string>()
  for (const style of styleSchema.value) {
    if (style.group) {
      groups.add(style.group)
    }
  }
  return Array.from(groups)
})

// 获取分组显示名称
function getStyleGroupLabel(groupKey: string): string {
  return STYLE_GROUP_LABELS[groupKey] || groupKey
}

// 根据分组获取样式属性
function getStylePropsByGroup(groupKey: string): StyleSchema[] {
  return styleSchema.value.filter(style => style.group === groupKey)
}

// 获取没有分组的样式属性
const ungroupedStyles = computed<StyleSchema[]>(() => {
  return styleSchema.value.filter(style => !style.group)
})

// 获取样式值（根据当前选中的设备类型）
function getStyleValue(styleKey: string): any {
  if (!selectedNode.value?.style) return undefined
  const deviceStyles = selectedNode.value.style[activeStyleDevice.value]
  return deviceStyles?.[styleKey]
}

// 获取通用样式值（用于非通用设备的继承提示）
function getBaseStyleValue(styleKey: string): any {
  if (!selectedNode.value?.style?.base) return undefined
  return selectedNode.value.style.base[styleKey]
}

// 是否显示继承提示（非通用设备且当前设备无值）
function shouldShowInheritedHint(styleKey: string): boolean {
  if (activeStyleDevice.value === 'base') return false
  const currentValue = getStyleValue(styleKey)
  return currentValue === undefined || currentValue === null || currentValue === ''
}

// 获取继承值（非通用设备时返回通用值）
function getInheritedValue(styleKey: string): any {
  if (activeStyleDevice.value === 'base') return undefined
  return getBaseStyleValue(styleKey)
}

// 获取样式的绑定配置
function getBindingForStyle(styleKey: string): DataBinding | null {
  if (!selectedNode.value?.styleBindings) return null
  return selectedNode.value.styleBindings.find(b => b.propKey === styleKey) || null
}

// 更新样式值（根据当前选中的设备类型）
function updateStyle(key: string, value: any) {
  if (!selectedNodeId.value || !selectedNode.value) return
  
  const currentStyle = selectedNode.value.style || {}
  const currentDeviceStyle = currentStyle[activeStyleDevice.value] || {}
  
  updateNode(selectedNodeId.value, {
    style: {
      ...currentStyle,
      [activeStyleDevice.value]: {
        ...currentDeviceStyle,
        [key]: value
      }
    }
  })
}

// 更新样式绑定
function updateStyleBinding(styleKey: string, binding: DataBinding | null) {
  if (!selectedNodeId.value || !selectedNode.value) return
  
  const currentBindings = selectedNode.value.styleBindings || []
  let newBindings: DataBinding[]
  
  if (binding) {
    // 添加或更新绑定
    const existingIndex = currentBindings.findIndex(b => b.propKey === styleKey)
    if (existingIndex >= 0) {
      newBindings = [...currentBindings]
      newBindings[existingIndex] = binding
    } else {
      newBindings = [...currentBindings, binding]
    }
  } else {
    // 移除绑定
    newBindings = currentBindings.filter(b => b.propKey !== styleKey)
  }
  
  // 直接更新 styleBindings 字段
  if (selectedNode.value) {
    selectedNode.value.styleBindings = newBindings
  }
}

// 重置组件为默认配置
function resetToDefault() {
  if (!selectedNodeId.value || !blockMeta.value) return
  
  const defaultProps = blockMeta.value.defaultProps || {}
  const defaultStyle = blockMeta.value.defaultStyle || {}
  
  updateNode(selectedNodeId.value, {
    props: defaultProps,
    style: defaultStyle,
  })
  
  // 清除绑定
  if (selectedNode.value) {
    selectedNode.value.bindings = []
    selectedNode.value.styleBindings = []
  }
}
</script>

<template>
  <div class="property-panel">
    <div class="panel-header">
      <span v-if="blockMeta?.icon" :class="blockMeta.icon" class="panel-icon"></span>
      <span v-else class="i-carbon-settings panel-icon"></span>
      <span class="panel-title">{{ hasSelection ? componentName : '属性' }}</span>
      <button 
        v-if="hasSelection" 
        class="reset-btn" 
        title="恢复默认配置"
        @click="resetToDefault"
      >
        <span class="i-carbon-reset"></span>
      </button>
    </div>

    <!-- Tab 切换 -->
    <div class="panel-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="panel-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <span :class="tab.icon"></span>
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <!-- 内容区域 -->
    <div class="panel-content">
      <!-- 无选中状态 -->
      <div v-if="!hasSelection" class="empty-state">
        <div class="empty-icon">
          <span class="i-carbon-cursor-1"></span>
        </div>
        <p class="empty-text">选择一个组件以编辑属性</p>
      </div>

      <!-- 属性面板 -->
      <div v-else-if="activeTab === 'props'" class="tab-content">
        <template v-if="blockMeta?.propsSchema?.length">
          <div class="props-list">
            <BuilderPropertyField
              v-for="prop in blockMeta.propsSchema"
              :key="prop.key"
              :schema="prop"
              :model-value="selectedNode?.props[prop.key]"
              :binding="getBindingForProp(prop.key)"
              :data-sources="getCompatibleSourcesForProp(prop.type)"
              @update:model-value="updateProp(prop.key, $event)"
              @update:binding="updateBinding(prop.key, $event)"
            />
          </div>
        </template>
        <div v-else class="empty-state small">
          <span class="i-carbon-settings-adjust empty-icon-small"></span>
          <p class="empty-text">该组件暂无可编辑属性</p>
        </div>
      </div>

      <!-- 样式面板 -->
      <div v-else-if="activeTab === 'style'" class="tab-content style-tab-content">
        <!-- 设备切换器（悬浮在右上角，不占用布局空间） -->
        <div v-if="styleSchema.length" class="device-switcher-float">
          <button
            v-for="device in styleDeviceOptions"
            :key="device.key"
            class="device-btn"
            :class="{ active: activeStyleDevice === device.key }"
            :title="device.label"
            @click="activeStyleDevice = device.key"
          >
            <span :class="device.icon"></span>
          </button>
        </div>
        <template v-if="styleSchema.length">
          <!-- 有分组的样式 -->
          <template v-for="group in styleGroups" :key="group">
            <div class="property-section" v-if="getStylePropsByGroup(group).length > 0">
              <div class="section-header">
                <span class="section-title">{{ getStyleGroupLabel(group) }}</span>
              </div>
              <div class="props-list">
                <BuilderPropertyField
                  v-for="styleProp in getStylePropsByGroup(group)"
                  :key="styleProp.key"
                  :schema="styleProp"
                  :model-value="getStyleValue(styleProp.key)"
                  :binding="getBindingForStyle(styleProp.key)"
                  :data-sources="getCompatibleSourcesForStyle(styleProp.type)"
                  :inherited-value="getInheritedValue(styleProp.key)"
                  :show-inherited-hint="shouldShowInheritedHint(styleProp.key)"
                  @update:model-value="updateStyle(styleProp.key, $event)"
                  @update:binding="updateStyleBinding(styleProp.key, $event)"
                />
              </div>
            </div>
          </template>
          <!-- 无分组的样式 -->
          <div v-if="ungroupedStyles.length > 0" class="props-list">
            <BuilderPropertyField
              v-for="styleProp in ungroupedStyles"
              :key="styleProp.key"
              :schema="styleProp"
              :model-value="getStyleValue(styleProp.key)"
              :binding="getBindingForStyle(styleProp.key)"
              :data-sources="getCompatibleSourcesForStyle(styleProp.type)"
              :inherited-value="getInheritedValue(styleProp.key)"
              :show-inherited-hint="shouldShowInheritedHint(styleProp.key)"
              @update:model-value="updateStyle(styleProp.key, $event)"
              @update:binding="updateStyleBinding(styleProp.key, $event)"
            />
          </div>
        </template>
        <div v-else class="empty-state small">
          <span class="i-carbon-paint-brush empty-icon-small"></span>
          <p class="empty-text">该组件暂无可编辑样式</p>
        </div>
      </div>

      <!-- 交互面板 -->
      <div v-else-if="activeTab === 'action'" class="tab-content">
        <template v-if="blockMeta?.eventsSchema?.length">
          <div class="events-list">
            <div
              v-for="eventSchema in blockMeta.eventsSchema"
              :key="eventSchema.event"
              class="event-item"
            >
              <div class="event-header">
                <span class="event-name">{{ eventSchema.label }}</span>
                <span v-if="eventSchema.description" class="event-desc">{{ eventSchema.description }}</span>
              </div>
              <div class="event-actions">
                <button class="add-action-btn">
                  <span class="i-carbon-add"></span>
                  添加动作
                </button>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="empty-state small">
          <span class="i-carbon-touch-interaction empty-icon-small"></span>
          <p class="empty-text">该组件暂无可配置事件</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.property-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e293b;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.panel-icon {
  font-size: 18px;
  color: #8b5cf6;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
}

.reset-btn {
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 16px;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.reset-btn:hover {
  color: #f1f5f9;
  background: rgba(51, 65, 85, 0.5);
}

/* Tab 切换 */
.panel-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  background: rgba(15, 23, 42, 0.3);
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.panel-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  justify-content: center;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.panel-tab:hover {
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
}

.panel-tab.active {
  color: #f1f5f9;
  background: rgba(51, 65, 85, 0.5);
}

.panel-tab span:first-child {
  font-size: 14px;
}

/* 内容区域 */
.panel-content {
  position: relative;
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.tab-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.style-tab-content {
  position: relative;
}

/* 设备切换器（悬浮右上角，不占用布局空间） */
.device-switcher-float {
  position: sticky;
  top: -12px;
  z-index: 10;
  display: flex;
  gap: 2px;
  padding: 3px;
  margin-left: auto;
  margin-bottom: -32px;
  width: fit-content;
  background: rgba(15, 23, 42, 0.9);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 6px;
  backdrop-filter: blur(8px);
}

.device-switcher-float .device-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  font-size: 14px;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.device-switcher-float .device-btn:hover {
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
}

.device-switcher-float .device-btn.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.15);
}

/* 属性列表 */
.props-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-state.small {
  padding: 32px 24px;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  font-size: 28px;
  color: #475569;
  background: rgba(51, 65, 85, 0.3);
  border-radius: 16px;
}

.empty-icon-small {
  font-size: 32px;
  color: #475569;
  margin-bottom: 12px;
}

.empty-text {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

/* 属性区块 */
.property-section {
  background: rgba(15, 23, 42, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.2);
  border-radius: 8px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: rgba(51, 65, 85, 0.2);
  border-bottom: 1px solid rgba(71, 85, 105, 0.2);
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.property-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 12px;
}

.property-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
}

.property-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.property-label {
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
}

.property-input-group {
  display: flex;
  gap: 4px;
}

.property-input {
  flex: 1;
  min-width: 0;
  padding: 6px 8px;
  font-size: 12px;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
  transition: border-color 0.15s ease;
}

.property-input:focus {
  border-color: rgba(59, 130, 246, 0.5);
}

.property-unit {
  width: 56px;
  padding: 6px 4px;
  font-size: 11px;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
  cursor: pointer;
}

/* 边距编辑器 */
.spacing-editor {
  padding: 16px;
}

.spacing-box {
  position: relative;
  width: 100%;
  aspect-ratio: 2 / 1;
  min-height: 100px;
  border: 2px dashed rgba(71, 85, 105, 0.5);
  border-radius: 8px;
}

.spacing-input {
  position: absolute;
  width: 40px;
  padding: 4px;
  font-size: 11px;
  text-align: center;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
}

.spacing-input.top {
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
}

.spacing-input.right {
  top: 50%;
  right: -20px;
  transform: translateY(-50%);
}

.spacing-input.bottom {
  bottom: -12px;
  left: 50%;
  transform: translateX(-50%);
}

.spacing-input.left {
  top: 50%;
  left: -20px;
  transform: translateY(-50%);
}

.spacing-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.spacing-label {
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
}

/* 颜色选择器 */
.color-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.color-preview {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 2px solid rgba(71, 85, 105, 0.5);
  cursor: pointer;
}

.color-input {
  flex: 1;
  padding: 6px 8px;
  font-size: 12px;
  font-family: monospace;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
}

/* 事件列表 */
.events-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.event-item {
  background: rgba(15, 23, 42, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.2);
  border-radius: 8px;
  overflow: hidden;
}

.event-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: rgba(51, 65, 85, 0.2);
  border-bottom: 1px solid rgba(71, 85, 105, 0.2);
}

.event-name {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
}

.event-desc {
  font-size: 11px;
  color: #64748b;
}

.event-actions {
  padding: 12px;
}

.add-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.1);
  border: 1px dashed rgba(139, 92, 246, 0.3);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.add-action-btn:hover {
  background: rgba(139, 92, 246, 0.2);
  border-color: rgba(139, 92, 246, 0.5);
}

/* 自定义滚动条 */
.panel-content::-webkit-scrollbar {
  width: 6px;
}

.panel-content::-webkit-scrollbar-track {
  background: transparent;
}

.panel-content::-webkit-scrollbar-thumb {
  background: rgba(71, 85, 105, 0.5);
  border-radius: 3px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: rgba(71, 85, 105, 0.8);
}
</style>
