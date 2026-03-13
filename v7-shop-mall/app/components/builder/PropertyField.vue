<script setup lang="ts">
/**
 * PropertyField - 属性字段编辑器
 * 根据 PropSchema.type 渲染对应的编辑器组件
 * 支持静态值和数据绑定两种模式（带分组显示）
 */

import type { PropSchema, DataBinding } from '~/types/component-meta'
import type { BindableDataSource } from '~/utils/type-matching'

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
    // 进入绑定模式，但不默认选择，让用户通过选择器选择
    emit('update:binding', {
      propKey: props.schema.key,
      variableKey: '',
    })
  }
}

// 处理变量选择
function handleVariableSelect(source: BindableDataSource) {
  emit('update:binding', {
    propKey: props.schema.key,
    variableKey: source.key,
  })
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

// 是否有可用数据源
const hasDataSources = computed(() => props.dataSources.length > 0)

// ============ 自定义下拉框逻辑 ============
const isSelectOpen = ref(false)
const selectTriggerRef = ref<HTMLElement | null>(null)
const selectPopoverRef = ref<HTMLElement | null>(null)
const selectPopoverStyle = ref({
  top: '0px',
  left: '0px',
  width: '0px',
})

// 当前选中的选项标签
const selectedOptionLabel = computed(() => {
  if (!props.schema.options) return ''
  const option = props.schema.options.find(opt => opt.value === localValue.value)
  return option?.label || ''
})

// 打开下拉框
function openSelect() {
  if (!selectTriggerRef.value) return
  
  const rect = selectTriggerRef.value.getBoundingClientRect()
  const viewportHeight = window.innerHeight
  const popoverMaxHeight = 240
  
  let top = rect.bottom + 4
  
  // 如果下方空间不足，向上弹出
  if (top + popoverMaxHeight > viewportHeight - 16) {
    top = rect.top - popoverMaxHeight - 4
    if (top < 16) {
      top = 16
    }
  }
  
  selectPopoverStyle.value = {
    top: `${top}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
  }
  
  isSelectOpen.value = true
}

// 关闭下拉框
function closeSelect() {
  isSelectOpen.value = false
}

// 选择选项
function selectOption(value: any) {
  localValue.value = value
  closeSelect()
}

// 点击外部关闭
function handleSelectClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  const isInsideTrigger = selectTriggerRef.value?.contains(target)
  const isInsidePopover = selectPopoverRef.value?.contains(target)
  
  if (!isInsideTrigger && !isInsidePopover) {
    closeSelect()
  }
}

// 监听点击外部
onMounted(() => {
  document.addEventListener('click', handleSelectClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleSelectClickOutside)
})
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
      <!-- 绑定模式：显示层级变量选择器 -->
      <div v-if="isBindingMode" class="binding-selector">
        <BuilderVariableSelector
          v-model="bindingVariableKey"
          :data-sources="dataSources"
          placeholder="选择数据源..."
          @select="handleVariableSelect"
        />
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
      <div v-else-if="schema.type === 'select'" class="field-select-wrapper">
        <button
          ref="selectTriggerRef"
          type="button"
          class="field-select-trigger"
          :class="{ open: isSelectOpen }"
          @click.stop="isSelectOpen ? closeSelect() : openSelect()"
        >
          <span v-if="selectedOptionLabel" class="select-value">{{ selectedOptionLabel }}</span>
          <span v-else class="select-placeholder">{{ schema.placeholder || '请选择' }}</span>
          <span class="select-arrow i-carbon-chevron-down" />
        </button>
        
        <!-- 下拉弹出层 -->
        <Teleport to="body">
          <div
            v-if="isSelectOpen"
            ref="selectPopoverRef"
            class="select-popover"
            :style="selectPopoverStyle"
            @click.stop
          >
            <div class="select-options">
              <button
                v-for="option in schema.options"
                :key="option.value"
                type="button"
                class="select-option"
                :class="{ active: localValue === option.value }"
                @click="selectOption(option.value)"
              >
                <span class="option-label">{{ option.label }}</span>
                <span v-if="localValue === option.value" class="option-check i-carbon-checkmark" />
              </button>
            </div>
          </div>
        </Teleport>
      </div>

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
  padding: 8px;
  background: rgba(59, 130, 246, 0.05);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 6px;
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

/* 自定义下拉框样式 */
.field-select-wrapper {
  position: relative;
  width: 100%;
}

.field-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.field-select-trigger:hover {
  border-color: rgba(71, 85, 105, 0.5);
}

.field-select-trigger:focus {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
  outline: none;
}

.field-select-trigger.open {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.select-value {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-placeholder {
  flex: 1;
  color: #64748b;
}

.select-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: #64748b;
  transition: transform 0.2s ease;
}

.field-select-trigger.open .select-arrow {
  transform: rotate(180deg);
}

/* 下拉弹出层 */
.select-popover {
  position: fixed;
  z-index: 9999;
  max-height: 240px;
  background: #1e293b;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.05);
  overflow: hidden;
  animation: selectPopoverFadeIn 0.15s ease-out;
}

@keyframes selectPopoverFadeIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.select-options {
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(100, 116, 139, 0.4) transparent;
}

.select-options::-webkit-scrollbar {
  width: 6px;
}

.select-options::-webkit-scrollbar-track {
  background: transparent;
}

.select-options::-webkit-scrollbar-thumb {
  background: rgba(100, 116, 139, 0.4);
  border-radius: 3px;
}

.select-options::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 116, 139, 0.6);
}

.select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  color: #cbd5e1;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.select-option:hover {
  background: rgba(59, 130, 246, 0.1);
  color: #e2e8f0;
}

.select-option.active {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.option-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-check {
  flex-shrink: 0;
  font-size: 14px;
  color: #3b82f6;
  margin-left: 8px;
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
