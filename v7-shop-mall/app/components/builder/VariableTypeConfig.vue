<script setup lang="ts">
/**
 * 变量类型配置组件
 * 用于配置 enum/array/object 类型的详细设置
 */

import type { VariableType, EnumOption, VariableFieldSchema } from '~/types/data-context'

const props = defineProps<{
  type: VariableType
  enumOptions?: EnumOption[]
  itemType?: VariableType
  itemSchema?: VariableFieldSchema[]
  fields?: VariableFieldSchema[]
}>()

const emit = defineEmits<{
  change: [config: {
    enumOptions?: EnumOption[]
    itemType?: VariableType
    itemSchema?: VariableFieldSchema[]
    fields?: VariableFieldSchema[]
  }]
}>()

const simpleTypes: Array<{ value: VariableType; label: string }> = [
  { value: 'string', label: '文本' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '开关' },
  { value: 'color', label: '颜色' },
  { value: 'image', label: '图片' },
]

const arrayMode = ref<'simple' | 'object'>('simple')

watch(
  () => props.itemSchema,
  (schema) => {
    if (schema && schema.length > 0) {
      arrayMode.value = 'object'
    }
  },
  { immediate: true }
)

function addEnumOption() {
  const newOptions = [...(props.enumOptions || [])]
  const newKey = `option_${newOptions.length + 1}`
  newOptions.push({ value: newKey, label: `选项 ${newOptions.length + 1}` })
  emit('change', { enumOptions: newOptions })
}

function updateEnumOption(index: number, field: 'value' | 'label', value: string) {
  const newOptions = [...(props.enumOptions || [])]
  newOptions[index] = { ...newOptions[index], [field]: value }
  emit('change', { enumOptions: newOptions })
}

function removeEnumOption(index: number) {
  const newOptions = [...(props.enumOptions || [])]
  newOptions.splice(index, 1)
  emit('change', { enumOptions: newOptions })
}

function updateItemType(type: VariableType) {
  emit('change', { itemType: type, itemSchema: [] })
}

function switchArrayMode(mode: 'simple' | 'object') {
  arrayMode.value = mode
  if (mode === 'simple') {
    emit('change', { itemType: props.itemType || 'string', itemSchema: [] })
  } else {
    emit('change', { itemSchema: props.itemSchema?.length ? props.itemSchema : [] })
  }
}

function addItemSchemaField() {
  const newSchema = [...(props.itemSchema || [])]
  const newKey = `field_${newSchema.length + 1}`
  newSchema.push({ key: newKey, label: `字段 ${newSchema.length + 1}`, type: 'string' })
  emit('change', { itemSchema: newSchema })
}

function updateItemSchemaField(index: number, field: keyof VariableFieldSchema, value: any) {
  const newSchema = [...(props.itemSchema || [])]
  newSchema[index] = { ...newSchema[index], [field]: value }
  emit('change', { itemSchema: newSchema })
}

function removeItemSchemaField(index: number) {
  const newSchema = [...(props.itemSchema || [])]
  newSchema.splice(index, 1)
  emit('change', { itemSchema: newSchema })
}

function addObjectField() {
  const newFields = [...(props.fields || [])]
  const newKey = `field_${newFields.length + 1}`
  newFields.push({ key: newKey, label: `字段 ${newFields.length + 1}`, type: 'string' })
  emit('change', { fields: newFields })
}

function updateObjectField(index: number, field: keyof VariableFieldSchema, value: any) {
  const newFields = [...(props.fields || [])]
  newFields[index] = { ...newFields[index], [field]: value }
  emit('change', { fields: newFields })
}

function removeObjectField(index: number) {
  const newFields = [...(props.fields || [])]
  newFields.splice(index, 1)
  emit('change', { fields: newFields })
}
</script>

<template>
  <div class="type-config">
    <!-- Enum Configuration -->
    <div v-if="type === 'enum'" class="config-section">
      <div class="config-header">
        <span class="config-label">枚举选项</span>
        <button class="add-btn" @click="addEnumOption">
          <span class="i-carbon-add"></span>
          添加选项
        </button>
      </div>
      
      <div v-if="!enumOptions?.length" class="empty-hint">
        暂无选项，点击上方按钮添加
      </div>
      
      <div v-else class="option-list">
        <div
          v-for="(option, index) in enumOptions"
          :key="index"
          class="option-item"
        >
          <div class="option-inputs">
            <input
              :value="option.value"
              type="text"
              class="option-input"
              placeholder="值"
              @input="updateEnumOption(index, 'value', ($event.target as HTMLInputElement).value)"
            />
            <input
              :value="option.label"
              type="text"
              class="option-input"
              placeholder="显示名称"
              @input="updateEnumOption(index, 'label', ($event.target as HTMLInputElement).value)"
            />
          </div>
          <button class="remove-btn" @click="removeEnumOption(index)">
            <span class="i-carbon-close"></span>
          </button>
        </div>
      </div>
    </div>

    <!-- Array Configuration -->
    <div v-if="type === 'array'" class="config-section">
      <div class="config-tabs">
        <button
          class="tab-btn"
          :class="{ active: arrayMode === 'simple' }"
          @click="switchArrayMode('simple')"
        >
          简单数组
        </button>
        <button
          class="tab-btn"
          :class="{ active: arrayMode === 'object' }"
          @click="switchArrayMode('object')"
        >
          对象数组
        </button>
      </div>

      <div v-if="arrayMode === 'simple'" class="simple-array-config">
        <label class="config-label">元素类型</label>
        <div class="type-buttons">
          <button
            v-for="t in simpleTypes"
            :key="t.value"
            class="type-btn"
            :class="{ active: itemType === t.value }"
            @click="updateItemType(t.value)"
          >
            {{ t.label }}
          </button>
        </div>
      </div>

      <div v-else class="object-array-config">
        <div class="config-header">
          <span class="config-label">对象字段</span>
          <button class="add-btn" @click="addItemSchemaField">
            <span class="i-carbon-add"></span>
            添加字段
          </button>
        </div>
        
        <div v-if="!itemSchema?.length" class="empty-hint">
          暂无字段，点击上方按钮添加
        </div>
        
        <div v-else class="field-list">
          <div
            v-for="(field, index) in itemSchema"
            :key="index"
            class="field-item"
          >
            <div class="field-inputs">
              <input
                :value="field.key"
                type="text"
                class="field-input"
                placeholder="键名"
                @input="updateItemSchemaField(index, 'key', ($event.target as HTMLInputElement).value)"
              />
              <input
                :value="field.label"
                type="text"
                class="field-input"
                placeholder="显示名称"
                @input="updateItemSchemaField(index, 'label', ($event.target as HTMLInputElement).value)"
              />
              <select
                :value="field.type"
                class="field-select"
                @change="updateItemSchemaField(index, 'type', ($event.target as HTMLSelectElement).value)"
              >
                <option v-for="t in simpleTypes" :key="t.value" :value="t.value">
                  {{ t.label }}
                </option>
              </select>
            </div>
            <button class="remove-btn" @click="removeItemSchemaField(index)">
              <span class="i-carbon-close"></span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Object Configuration -->
    <div v-if="type === 'object'" class="config-section">
      <div class="config-header">
        <span class="config-label">对象字段</span>
        <button class="add-btn" @click="addObjectField">
          <span class="i-carbon-add"></span>
          添加字段
        </button>
      </div>
      
      <div v-if="!fields?.length" class="empty-hint">
        暂无字段，点击上方按钮添加
      </div>
      
      <div v-else class="field-list">
        <div
          v-for="(field, index) in fields"
          :key="index"
          class="field-item"
        >
          <div class="field-inputs">
            <input
              :value="field.key"
              type="text"
              class="field-input"
              placeholder="键名"
              @input="updateObjectField(index, 'key', ($event.target as HTMLInputElement).value)"
            />
            <input
              :value="field.label"
              type="text"
              class="field-input"
              placeholder="显示名称"
              @input="updateObjectField(index, 'label', ($event.target as HTMLInputElement).value)"
            />
            <select
              :value="field.type"
              class="field-select"
              @change="updateObjectField(index, 'type', ($event.target as HTMLSelectElement).value)"
            >
              <option v-for="t in simpleTypes" :key="t.value" :value="t.value">
                {{ t.label }}
              </option>
            </select>
          </div>
          <button class="remove-btn" @click="removeObjectField(index)">
            <span class="i-carbon-close"></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.type-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.config-label {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-btn:hover {
  background: rgba(59, 130, 246, 0.2);
  border-color: rgba(59, 130, 246, 0.5);
}

.empty-hint {
  padding: 24px;
  font-size: 13px;
  color: #64748b;
  text-align: center;
  background: rgba(15, 23, 42, 0.4);
  border: 1px dashed rgba(71, 85, 105, 0.4);
  border-radius: 8px;
}

.option-list,
.field-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item,
.field-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-inputs {
  display: flex;
  flex: 1;
  gap: 8px;
}

.field-inputs {
  display: flex;
  flex: 1;
  gap: 8px;
}

.option-input,
.field-input {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  outline: none;
  transition: all 0.2s;
}

.option-input:focus,
.field-input:focus {
  border-color: #3b82f6;
}

.field-select {
  width: 100px;
  padding: 8px 10px;
  font-size: 13px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  outline: none;
  cursor: pointer;
  transition: all 0.2s;
}

.field-select:focus {
  border-color: #3b82f6;
}

.field-select option {
  background: #1e293b;
  color: #f1f5f9;
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 14px;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.remove-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.config-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: rgba(15, 23, 42, 0.5);
  border-radius: 8px;
}

.tab-btn {
  flex: 1;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #e2e8f0;
}

.tab-btn.active {
  color: #f1f5f9;
  background: rgba(59, 130, 246, 0.2);
}

.simple-array-config {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.type-btn {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.type-btn:hover {
  color: #e2e8f0;
  border-color: rgba(71, 85, 105, 0.6);
}

.type-btn.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.4);
}

.object-array-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
