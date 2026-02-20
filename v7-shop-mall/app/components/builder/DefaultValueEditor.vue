<script setup lang="ts">
/**
 * 默认值编辑器组件
 * 支持可视化编辑和 JSON 编辑两种模式
 */

import type { VariableType, EnumOption, VariableFieldSchema } from '~/types/data-context'

const props = defineProps<{
  visible: boolean
  type: VariableType
  value?: any
  enumOptions?: EnumOption[]
  itemType?: VariableType
  itemSchema?: VariableFieldSchema[]
  fields?: VariableFieldSchema[]
}>()

const emit = defineEmits<{
  close: []
  save: [value: any]
}>()

const editMode = ref<'visual' | 'json'>('visual')
const localValue = ref<any>(undefined)
const jsonText = ref('')
const jsonError = ref('')

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      localValue.value = props.value !== undefined ? JSON.parse(JSON.stringify(props.value)) : getDefaultValue()
      updateJsonText()
      jsonError.value = ''
      editMode.value = 'visual'
    }
  },
  { immediate: true }
)

function getDefaultValue(): any {
  switch (props.type) {
    case 'string':
    case 'richtext':
    case 'image':
      return ''
    case 'number':
      return 0
    case 'boolean':
      return false
    case 'color':
      return '#3b82f6'
    case 'enum':
      return props.enumOptions?.[0]?.value || ''
    case 'array':
      return []
    case 'object':
      const obj: Record<string, any> = {}
      props.fields?.forEach((field) => {
        obj[field.key] = getFieldDefaultValue(field.type)
      })
      return obj
    default:
      return undefined
  }
}

function getFieldDefaultValue(type: VariableType): any {
  switch (type) {
    case 'string':
    case 'richtext':
    case 'image':
      return ''
    case 'number':
      return 0
    case 'boolean':
      return false
    case 'color':
      return '#3b82f6'
    default:
      return ''
  }
}

function updateJsonText() {
  try {
    jsonText.value = JSON.stringify(localValue.value, null, 2)
  } catch {
    jsonText.value = ''
  }
}

function handleJsonChange(text: string) {
  jsonText.value = text
  try {
    localValue.value = JSON.parse(text)
    jsonError.value = ''
  } catch (e) {
    jsonError.value = 'JSON 格式错误'
  }
}

function formatJson() {
  try {
    const parsed = JSON.parse(jsonText.value)
    jsonText.value = JSON.stringify(parsed, null, 2)
    jsonError.value = ''
  } catch {
    jsonError.value = 'JSON 格式错误，无法格式化'
  }
}

function switchMode(mode: 'visual' | 'json') {
  if (mode === 'json' && editMode.value === 'visual') {
    updateJsonText()
  }
  if (mode === 'visual' && editMode.value === 'json') {
    try {
      localValue.value = JSON.parse(jsonText.value)
      jsonError.value = ''
    } catch {
      jsonError.value = 'JSON 格式错误，无法切换到可视化模式'
      return
    }
  }
  editMode.value = mode
}

function addArrayItem() {
  if (!Array.isArray(localValue.value)) {
    localValue.value = []
  }
  if (props.itemSchema?.length) {
    const newItem: Record<string, any> = {}
    props.itemSchema.forEach((field) => {
      newItem[field.key] = getFieldDefaultValue(field.type)
    })
    localValue.value.push(newItem)
  } else {
    localValue.value.push(getFieldDefaultValue(props.itemType || 'string'))
  }
}

function removeArrayItem(index: number) {
  if (Array.isArray(localValue.value)) {
    localValue.value.splice(index, 1)
  }
}

function updateArrayItem(index: number, value: any) {
  if (Array.isArray(localValue.value)) {
    localValue.value[index] = value
  }
}

function updateArrayItemField(index: number, key: string, value: any) {
  if (Array.isArray(localValue.value) && localValue.value[index]) {
    localValue.value[index][key] = value
  }
}

function updateObjectField(key: string, value: any) {
  if (typeof localValue.value === 'object' && localValue.value !== null) {
    localValue.value[key] = value
  }
}

function handleSave() {
  if (editMode.value === 'json') {
    try {
      localValue.value = JSON.parse(jsonText.value)
    } catch {
      jsonError.value = 'JSON 格式错误，无法保存'
      return
    }
  }
  emit('save', localValue.value)
  emit('close')
}

const simpleTypes: VariableType[] = ['string', 'number', 'boolean', 'color', 'image']
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="emit('close')">
        <div class="value-editor">
          <div class="editor-header">
            <h3 class="editor-title">
              <span class="i-carbon-edit"></span>
              编辑默认值
            </h3>
            <button class="close-btn" @click="emit('close')">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <div class="mode-tabs">
            <button
              class="mode-tab"
              :class="{ active: editMode === 'visual' }"
              @click="switchMode('visual')"
            >
              <span class="i-carbon-view"></span>
              可视化编辑
            </button>
            <button
              class="mode-tab"
              :class="{ active: editMode === 'json' }"
              @click="switchMode('json')"
            >
              <span class="i-carbon-code"></span>
              JSON 编辑
            </button>
          </div>

          <div class="editor-content">
            <!-- Visual Mode -->
            <div v-if="editMode === 'visual'" class="visual-editor">
              <!-- String -->
              <div v-if="type === 'string'" class="field-group">
                <label class="field-label">文本值</label>
                <input
                  v-model="localValue"
                  type="text"
                  class="field-input"
                  placeholder="输入文本"
                />
              </div>

              <!-- Number -->
              <div v-else-if="type === 'number'" class="field-group">
                <label class="field-label">数值</label>
                <input
                  v-model.number="localValue"
                  type="number"
                  class="field-input"
                  placeholder="输入数字"
                />
              </div>

              <!-- Boolean -->
              <div v-else-if="type === 'boolean'" class="field-group">
                <label class="field-label">开关状态</label>
                <div class="toggle-wrapper">
                  <button
                    class="toggle-btn"
                    :class="{ active: localValue }"
                    @click="localValue = !localValue"
                  >
                    <span class="toggle-track">
                      <span class="toggle-thumb"></span>
                    </span>
                    <span class="toggle-text">{{ localValue ? '开启' : '关闭' }}</span>
                  </button>
                </div>
              </div>

              <!-- Color -->
              <div v-else-if="type === 'color'" class="field-group">
                <label class="field-label">颜色</label>
                <div class="color-picker">
                  <input
                    v-model="localValue"
                    type="color"
                    class="color-input"
                  />
                  <input
                    v-model="localValue"
                    type="text"
                    class="color-text"
                    placeholder="#000000"
                  />
                </div>
              </div>

              <!-- Image -->
              <div v-else-if="type === 'image'" class="field-group">
                <label class="field-label">图片 URL</label>
                <input
                  v-model="localValue"
                  type="text"
                  class="field-input"
                  placeholder="输入图片地址"
                />
                <div v-if="localValue" class="image-preview">
                  <img :src="localValue" alt="预览" />
                </div>
              </div>

              <!-- Richtext -->
              <div v-else-if="type === 'richtext'" class="field-group">
                <label class="field-label">富文本内容</label>
                <textarea
                  v-model="localValue"
                  class="field-textarea"
                  placeholder="输入 HTML 内容"
                  rows="6"
                ></textarea>
              </div>

              <!-- Enum -->
              <div v-else-if="type === 'enum'" class="field-group">
                <label class="field-label">选择值</label>
                <div class="enum-options">
                  <button
                    v-for="option in enumOptions"
                    :key="option.value"
                    class="enum-option"
                    :class="{ active: localValue === option.value }"
                    @click="localValue = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>

              <!-- Array -->
              <div v-else-if="type === 'array'" class="field-group">
                <div class="array-header">
                  <label class="field-label">数组元素</label>
                  <button class="add-item-btn" @click="addArrayItem">
                    <span class="i-carbon-add"></span>
                    添加
                  </button>
                </div>
                
                <div v-if="!localValue?.length" class="empty-array">
                  暂无元素，点击添加按钮
                </div>
                
                <div v-else class="array-items">
                  <div
                    v-for="(item, index) in localValue"
                    :key="index"
                    class="array-item"
                  >
                    <div class="item-index">{{ index + 1 }}</div>
                    
                    <!-- Simple array -->
                    <div v-if="!itemSchema?.length" class="item-content simple">
                      <input
                        v-if="itemType === 'string' || itemType === 'image'"
                        :value="item"
                        type="text"
                        class="item-input"
                        @input="updateArrayItem(index, ($event.target as HTMLInputElement).value)"
                      />
                      <input
                        v-else-if="itemType === 'number'"
                        :value="item"
                        type="number"
                        class="item-input"
                        @input="updateArrayItem(index, Number(($event.target as HTMLInputElement).value))"
                      />
                      <div v-else-if="itemType === 'boolean'" class="item-toggle">
                        <button
                          class="mini-toggle"
                          :class="{ active: item }"
                          @click="updateArrayItem(index, !item)"
                        >
                          {{ item ? '开' : '关' }}
                        </button>
                      </div>
                      <div v-else-if="itemType === 'color'" class="item-color">
                        <input
                          :value="item"
                          type="color"
                          class="mini-color"
                          @input="updateArrayItem(index, ($event.target as HTMLInputElement).value)"
                        />
                        <span class="color-value">{{ item }}</span>
                      </div>
                    </div>
                    
                    <!-- Object array -->
                    <div v-else class="item-content object">
                      <div
                        v-for="field in itemSchema"
                        :key="field.key"
                        class="item-field"
                      >
                        <span class="item-field-label">{{ field.label }}</span>
                        <input
                          v-if="field.type === 'string' || field.type === 'image'"
                          :value="item[field.key]"
                          type="text"
                          class="item-field-input"
                          @input="updateArrayItemField(index, field.key, ($event.target as HTMLInputElement).value)"
                        />
                        <input
                          v-else-if="field.type === 'number'"
                          :value="item[field.key]"
                          type="number"
                          class="item-field-input"
                          @input="updateArrayItemField(index, field.key, Number(($event.target as HTMLInputElement).value))"
                        />
                        <button
                          v-else-if="field.type === 'boolean'"
                          class="mini-toggle"
                          :class="{ active: item[field.key] }"
                          @click="updateArrayItemField(index, field.key, !item[field.key])"
                        >
                          {{ item[field.key] ? '开' : '关' }}
                        </button>
                        <div v-else-if="field.type === 'color'" class="mini-color-wrapper">
                          <input
                            :value="item[field.key]"
                            type="color"
                            class="mini-color"
                            @input="updateArrayItemField(index, field.key, ($event.target as HTMLInputElement).value)"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <button class="remove-item-btn" @click="removeArrayItem(index)">
                      <span class="i-carbon-close"></span>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Object -->
              <div v-else-if="type === 'object'" class="field-group">
                <label class="field-label">对象字段</label>
                
                <div v-if="!fields?.length" class="empty-object">
                  未定义字段结构
                </div>
                
                <div v-else class="object-fields">
                  <div
                    v-for="field in fields"
                    :key="field.key"
                    class="object-field"
                  >
                    <label class="object-field-label">{{ field.label }}</label>
                    <input
                      v-if="field.type === 'string' || field.type === 'image'"
                      :value="localValue?.[field.key]"
                      type="text"
                      class="object-field-input"
                      @input="updateObjectField(field.key, ($event.target as HTMLInputElement).value)"
                    />
                    <input
                      v-else-if="field.type === 'number'"
                      :value="localValue?.[field.key]"
                      type="number"
                      class="object-field-input"
                      @input="updateObjectField(field.key, Number(($event.target as HTMLInputElement).value))"
                    />
                    <button
                      v-else-if="field.type === 'boolean'"
                      class="field-toggle"
                      :class="{ active: localValue?.[field.key] }"
                      @click="updateObjectField(field.key, !localValue?.[field.key])"
                    >
                      {{ localValue?.[field.key] ? '开启' : '关闭' }}
                    </button>
                    <div v-else-if="field.type === 'color'" class="field-color">
                      <input
                        :value="localValue?.[field.key]"
                        type="color"
                        class="field-color-input"
                        @input="updateObjectField(field.key, ($event.target as HTMLInputElement).value)"
                      />
                      <span class="field-color-value">{{ localValue?.[field.key] }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- JSON Mode -->
            <div v-else class="json-editor">
              <div class="json-toolbar">
                <button class="format-btn" @click="formatJson">
                  <span class="i-carbon-text-align-left"></span>
                  格式化
                </button>
              </div>
              <textarea
                :value="jsonText"
                class="json-textarea"
                placeholder="输入 JSON"
                spellcheck="false"
                @input="handleJsonChange(($event.target as HTMLTextAreaElement).value)"
              ></textarea>
              <p v-if="jsonError" class="json-error">{{ jsonError }}</p>
            </div>
          </div>

          <div class="editor-footer">
            <button class="btn btn-secondary" @click="emit('close')">
              取消
            </button>
            <button class="btn btn-primary" @click="handleSave">
              <span class="i-carbon-checkmark"></span>
              确定
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(2px);
  z-index: 1002;
}

.value-editor {
  display: flex;
  flex-direction: column;
  width: 520px;
  max-width: 90vw;
  max-height: 80vh;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.8) 0%, rgba(30, 41, 59, 0.4) 100%);
}

.editor-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.editor-title span {
  font-size: 18px;
  color: #3b82f6;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #94a3b8;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #f1f5f9;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.mode-tabs {
  display: flex;
  padding: 12px 20px;
  gap: 8px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.mode-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-tab:hover {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.3);
}

.mode-tab.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.3);
}

.editor-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.visual-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
}

.field-input {
  padding: 10px 14px;
  font-size: 14px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s;
}

.field-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.field-textarea {
  padding: 10px 14px;
  font-size: 14px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
  resize: vertical;
  min-height: 120px;
  transition: all 0.2s;
}

.field-textarea:focus {
  border-color: #3b82f6;
}

.toggle-wrapper {
  display: flex;
}

.toggle-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-track {
  position: relative;
  width: 44px;
  height: 24px;
  background: #475569;
  border-radius: 12px;
  transition: all 0.2s;
}

.toggle-btn.active .toggle-track {
  background: #3b82f6;
}

.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  transition: all 0.2s;
}

.toggle-btn.active .toggle-thumb {
  left: 22px;
}

.toggle-text {
  font-size: 14px;
  color: #e2e8f0;
}

.color-picker {
  display: flex;
  gap: 10px;
}

.color-input {
  width: 48px;
  height: 40px;
  padding: 4px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  cursor: pointer;
}

.color-text {
  flex: 1;
  padding: 10px 14px;
  font-size: 14px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
}

.image-preview {
  margin-top: 8px;
  padding: 8px;
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 8px;
}

.image-preview img {
  max-width: 100%;
  max-height: 150px;
  border-radius: 4px;
}

.enum-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.enum-option {
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

.enum-option:hover {
  color: #e2e8f0;
  border-color: rgba(71, 85, 105, 0.6);
}

.enum-option.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.4);
}

.array-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.add-item-btn {
  display: flex;
  align-items: center;
  gap: 4px;
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

.add-item-btn:hover {
  background: rgba(59, 130, 246, 0.2);
}

.empty-array,
.empty-object {
  padding: 24px;
  font-size: 13px;
  color: #64748b;
  text-align: center;
  background: rgba(15, 23, 42, 0.4);
  border: 1px dashed rgba(71, 85, 105, 0.4);
  border-radius: 8px;
}

.array-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.array-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 8px;
}

.item-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  background: rgba(51, 65, 85, 0.5);
  border-radius: 4px;
  flex-shrink: 0;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-content.simple {
  display: flex;
  align-items: center;
}

.item-content.object {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  outline: none;
}

.item-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.item-field-label {
  width: 80px;
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}

.item-field-input {
  flex: 1;
  padding: 6px 10px;
  font-size: 13px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  outline: none;
}

.mini-toggle {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.mini-toggle.active {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.15);
  border-color: rgba(34, 197, 94, 0.3);
}

.mini-color-wrapper {
  display: flex;
}

.mini-color {
  width: 32px;
  height: 24px;
  padding: 2px;
  background: transparent;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 4px;
  cursor: pointer;
}

.item-color {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-value {
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #94a3b8;
}

.remove-item-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 14px;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.remove-item-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.object-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.object-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.object-field-label {
  font-size: 12px;
  color: #94a3b8;
}

.object-field-input {
  padding: 8px 12px;
  font-size: 13px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  outline: none;
}

.field-toggle {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.field-toggle.active {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.15);
  border-color: rgba(34, 197, 94, 0.3);
}

.field-color {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-color-input {
  width: 40px;
  height: 32px;
  padding: 3px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  cursor: pointer;
}

.field-color-value {
  font-size: 13px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #94a3b8;
}

.json-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.json-toolbar {
  display: flex;
  justify-content: flex-end;
}

.format-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.4);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.format-btn:hover {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.6);
}

.json-textarea {
  width: 100%;
  min-height: 200px;
  padding: 14px;
  font-size: 13px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  line-height: 1.5;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
  resize: vertical;
  transition: all 0.2s;
}

.json-textarea:focus {
  border-color: #3b82f6;
}

.json-error {
  font-size: 12px;
  color: #ef4444;
  margin: 0;
}

.editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.4) 0%, rgba(30, 41, 59, 0.8) 100%);
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  color: #fff;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

.btn-secondary {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.5);
}

.btn-secondary:hover {
  background: rgba(51, 65, 85, 0.8);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .value-editor,
.modal-leave-active .value-editor {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .value-editor,
.modal-leave-to .value-editor {
  transform: scale(0.95);
}
</style>
