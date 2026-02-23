<script setup lang="ts">
/**
 * PropertyField - 属性字段编辑器
 * 根据 PropSchema.type 渲染对应的编辑器组件
 * 支持静态值和数据绑定两种模式（带分组显示）
 */

import type { PropSchema, DataBinding } from '~/types/component-meta'
import {
  type BindableDataSource,
  type DataSourceGroup,
  DATA_SOURCE_GROUP_CONFIG,
  groupDataSources,
} from '~/utils/type-matching'

interface Props {
  schema: PropSchema
  modelValue: any
  binding?: DataBinding | null
  dataSources?: BindableDataSource[]
}

const props = withDefaults(defineProps<Props>(), {
  binding: null,
  dataSources: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: any]
  'update:binding': [binding: DataBinding | null]
}>()

// 是否处于绑定模式
const isBindingMode = computed(() => !!props.binding)

// 分组后的数据源
const groupedSources = computed(() => groupDataSources(props.dataSources))

// 获取有数据的分组
const activeGroups = computed(() => {
  const groups: DataSourceGroup[] = []
  if (groupedSources.value.variable.length > 0) groups.push('variable')
  if (groupedSources.value.siteConfig.length > 0) groups.push('siteConfig')
  if (groupedSources.value.globalStyle.length > 0) groups.push('globalStyle')
  return groups
})

// 当前绑定的变量 key
const bindingVariableKey = computed({
  get: () => props.binding?.variableKey || '',
  set: (key: string) => {
    if (key) {
      emit('update:binding', {
        propKey: props.schema.key,
        variableKey: key,
      })
    } else {
      emit('update:binding', null)
    }
  }
})

// 切换绑定模式
function toggleBindingMode() {
  if (isBindingMode.value) {
    emit('update:binding', null)
  } else {
    // 如果有可用数据源，默认选择第一个
    if (props.dataSources.length > 0) {
      emit('update:binding', {
        propKey: props.schema.key,
        variableKey: props.dataSources[0].key,
      })
    }
  }
}

// 本地值，用于双向绑定
const localValue = computed({
  get: () => props.modelValue ?? props.schema.defaultValue ?? '',
  set: (value) => emit('update:modelValue', value)
})

// 处理数字输入
function handleNumberInput(event: Event) {
  const target = event.target as HTMLInputElement
  const value = target.value === '' ? undefined : Number(target.value)
  emit('update:modelValue', value)
}

// 处理开关切换
function handleSwitchToggle() {
  emit('update:modelValue', !localValue.value)
}

// 获取绑定变量的显示名称
const boundVariableName = computed(() => {
  if (!props.binding) return ''
  const source = props.dataSources.find(s => s.key === props.binding?.variableKey)
  return source?.label || props.binding.variableKey
})

// 获取绑定变量所属分组
const boundVariableGroup = computed(() => {
  if (!props.binding) return ''
  const source = props.dataSources.find(s => s.key === props.binding?.variableKey)
  return source?.groupLabel || ''
})

// 是否有可用数据源
const hasDataSources = computed(() => props.dataSources.length > 0)

// 获取分组配置
function getGroupConfig(group: DataSourceGroup) {
  return DATA_SOURCE_GROUP_CONFIG[group]
}
</script>

<template>
  <div class="property-field">
    <div class="field-header">
      <label class="field-label">
        {{ schema.label }}
        <span v-if="schema.required" class="required-mark">*</span>
      </label>
      
      <!-- 绑定切换按钮 -->
      <button
        v-if="hasDataSources"
        type="button"
        class="binding-toggle"
        :class="{ active: isBindingMode }"
        :title="isBindingMode ? '切换为静态值' : '绑定数据'"
        @click="toggleBindingMode"
      >
        <span class="i-carbon-link" />
      </button>
    </div>
    
    <div class="field-control">
      <!-- 绑定模式：显示分组变量选择器 -->
      <div v-if="isBindingMode" class="binding-selector">
        <select v-model="bindingVariableKey" class="binding-select">
          <option value="" disabled>选择数据源</option>
          <template v-for="group in activeGroups" :key="group">
            <optgroup :label="getGroupConfig(group).label">
              <option
                v-for="source in groupedSources[group]"
                :key="source.key"
                :value="source.key"
              >
                {{ source.label }}
              </option>
            </optgroup>
          </template>
        </select>
        <div class="binding-info">
          <span class="binding-hint">已绑定: {{ boundVariableName }}</span>
          <span v-if="boundVariableGroup" class="binding-group-tag">{{ boundVariableGroup }}</span>
        </div>
      </div>

      <!-- 静态值模式 -->
      <template v-else>
        <!-- 单行文本 -->
        <input
          v-if="schema.type === 'text'"
          v-model="localValue"
          type="text"
          class="field-input"
          :placeholder="schema.placeholder"
        />

      <!-- 多行文本 -->
      <textarea
        v-else-if="schema.type === 'textarea'"
        v-model="localValue"
        class="field-textarea"
        :placeholder="schema.placeholder"
        rows="3"
      />

      <!-- 数字 -->
      <input
        v-else-if="schema.type === 'number'"
        :value="localValue"
        type="number"
        class="field-input"
        :placeholder="schema.placeholder"
        :min="schema.min"
        :max="schema.max"
        :step="schema.step || 1"
        @input="handleNumberInput"
      />

      <!-- 开关 -->
      <button
        v-else-if="schema.type === 'switch'"
        type="button"
        class="field-switch"
        :class="{ active: localValue }"
        @click="handleSwitchToggle"
      >
        <span class="switch-thumb" />
      </button>

      <!-- 下拉选择 -->
      <select
        v-else-if="schema.type === 'select'"
        v-model="localValue"
        class="field-select"
      >
        <option v-if="schema.placeholder" value="" disabled>
          {{ schema.placeholder }}
        </option>
        <option
          v-for="option in schema.options"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </option>
      </select>

      <!-- 单选 -->
      <div v-else-if="schema.type === 'radio'" class="field-radio-group">
        <label
          v-for="option in schema.options"
          :key="option.value"
          class="field-radio"
          :class="{ active: localValue === option.value }"
        >
          <input
            v-model="localValue"
            type="radio"
            :value="option.value"
            class="radio-input"
          />
          <span class="radio-label">{{ option.label }}</span>
        </label>
      </div>

      <!-- 颜色选择器 -->
      <div v-else-if="schema.type === 'color'" class="field-color">
        <div
          class="color-preview"
          :style="{ background: localValue || '#ffffff' }"
        />
        <input
          v-model="localValue"
          type="text"
          class="color-input"
          :placeholder="schema.placeholder || '#ffffff'"
        />
        <input
          v-model="localValue"
          type="color"
          class="color-picker-native"
        />
      </div>

      <!-- 图片上传（简化版，显示URL输入） -->
      <div v-else-if="schema.type === 'image'" class="field-image">
        <input
          v-model="localValue"
          type="text"
          class="field-input"
          :placeholder="schema.placeholder || '输入图片URL'"
        />
        <div v-if="localValue" class="image-preview">
          <img :src="localValue" alt="preview" />
        </div>
      </div>

      <!-- 富文本（简化版，使用 textarea） -->
      <textarea
        v-else-if="schema.type === 'richtext'"
        v-model="localValue"
        class="field-textarea richtext"
        :placeholder="schema.placeholder"
        rows="4"
      />

      <!-- JSON 编辑器（简化版，使用 textarea） -->
      <textarea
        v-else-if="schema.type === 'json'"
        v-model="localValue"
        class="field-textarea json"
        :placeholder="schema.placeholder || '{}'"
        rows="4"
      />

      <!-- 图标选择器（简化版，显示输入框） -->
      <div v-else-if="schema.type === 'icon'" class="field-icon">
        <span v-if="localValue" :class="localValue" class="icon-preview" />
        <input
          v-model="localValue"
          type="text"
          class="field-input"
          :placeholder="schema.placeholder || 'i-carbon-xxx'"
        />
      </div>

        <!-- 默认：文本输入 -->
        <input
          v-else
          v-model="localValue"
          type="text"
          class="field-input"
          :placeholder="schema.placeholder"
        />
      </template>
    </div>

    <!-- 描述说明 -->
    <p v-if="schema.description" class="field-description">
      {{ schema.description }}
    </p>
  </div>
</template>

<style scoped>
.property-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.field-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

.required-mark {
  color: #ef4444;
}

/* 绑定切换按钮 */
.binding-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 14px;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.binding-toggle:hover {
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
}

.binding-toggle.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.3);
}

/* 绑定选择器 */
.binding-selector {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  background: rgba(59, 130, 246, 0.05);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 6px;
}

.binding-icon {
  display: none;
}

.binding-select {
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 6px;
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24'%3E%3Cpath fill='%233b82f6' d='M7 10l5 5 5-5z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  padding-right: 28px;
}

.binding-select:focus {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.binding-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.binding-hint {
  font-size: 11px;
  color: #3b82f6;
}

.binding-group-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  font-size: 10px;
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.1);
  border-radius: 4px;
}

.field-control {
  width: 100%;
}

/* 通用输入框样式 */
.field-input,
.field-textarea,
.field-select {
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 6px;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.field-input:focus,
.field-textarea:focus,
.field-select:focus {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.field-textarea {
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
}

.field-textarea.richtext,
.field-textarea.json {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
}

.field-select {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24'%3E%3Cpath fill='%2394a3b8' d='M7 10l5 5 5-5z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  padding-right: 28px;
}

/* 开关样式 */
.field-switch {
  position: relative;
  width: 44px;
  height: 24px;
  padding: 2px;
  background: rgba(71, 85, 105, 0.5);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.field-switch.active {
  background: #3b82f6;
}

.switch-thumb {
  display: block;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.field-switch.active .switch-thumb {
  transform: translateX(20px);
}

/* 单选组样式 */
.field-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.field-radio {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  font-size: 12px;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.field-radio:hover {
  background: rgba(51, 65, 85, 0.5);
}

.field-radio.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.3);
}

.radio-input {
  display: none;
}

/* 颜色选择器样式 */
.field-color {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-preview {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 2px solid rgba(71, 85, 105, 0.5);
  flex-shrink: 0;
}

.color-input {
  flex: 1;
  padding: 8px 10px;
  font-size: 13px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 6px;
  outline: none;
}

.color-input:focus {
  border-color: rgba(59, 130, 246, 0.5);
}

.color-picker-native {
  position: absolute;
  left: 0;
  top: 0;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  opacity: 0;
}

.field-color {
  position: relative;
}

/* 图片预览 */
.field-image {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-preview {
  width: 100%;
  max-height: 120px;
  overflow: hidden;
  border-radius: 6px;
  border: 1px solid rgba(71, 85, 105, 0.3);
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 图标选择器 */
.field-icon {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-preview {
  font-size: 20px;
  color: #94a3b8;
  flex-shrink: 0;
}

.field-icon .field-input {
  flex: 1;
}

/* 描述说明 */
.field-description {
  margin: 0;
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}
</style>
