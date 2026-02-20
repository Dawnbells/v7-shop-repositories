<script setup lang="ts">
/**
 * 默认值编辑器组件
 * 支持可视化编辑和多语言配置
 */

import type { VariableType, EnumOption, VariableFieldSchema, I18nDefaultValue } from '~/types/data-context'

interface LanguageItem {
  id: number
  code: string
  name: string
  cname: string
}

const props = defineProps<{
  visible: boolean
  type: VariableType
  value?: any
  i18n?: boolean
  i18nLanguages?: number[]
  i18nDefaults?: I18nDefaultValue[]
  enumOptions?: EnumOption[]
  itemType?: VariableType
  itemSchema?: VariableFieldSchema[]
  fields?: VariableFieldSchema[]
}>()

const emit = defineEmits<{
  close: []
  save: [data: {
    defaultValue: any
    i18n: boolean
    i18nLanguages: number[]
    i18nDefaults: I18nDefaultValue[]
  }]
}>()

// 语言列表
const languageList = ref<LanguageItem[]>([])
const languageLoading = ref(false)

// 本地编辑状态
const localI18n = ref(false)
const localDefaultValue = ref<any>(null)
const localLanguages = ref<number[]>([])
const localDefaults = ref<I18nDefaultValue[]>([])

// 当前选中的语言 Tab
const activeLanguageTab = ref<number | null>(null)

// 获取语言列表
async function fetchLanguages() {
  if (languageList.value.length > 0) return

  languageLoading.value = true
  try {
    const data = await $fetch<LanguageItem[]>('/api/languages/list')
    languageList.value = data || []
  } catch (error) {
    console.error('获取语言列表失败:', error)
    languageList.value = []
  } finally {
    languageLoading.value = false
  }
}

// 监听弹窗打开，初始化本地状态
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      fetchLanguages()
      localI18n.value = props.i18n || false
      localDefaultValue.value = deepClone(props.value) ?? getDefaultValue()
      localLanguages.value = props.i18nLanguages ? [...props.i18nLanguages] : []
      localDefaults.value = deepClone(props.i18nDefaults) || []

      if (localLanguages.value.length > 0) {
        activeLanguageTab.value = localLanguages.value[0] ?? null
      } else {
        activeLanguageTab.value = null
      }
    }
  },
  { immediate: true }
)

function deepClone<T>(obj: T): T {
  if (obj === null || obj === undefined) return obj
  return JSON.parse(JSON.stringify(obj))
}

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

// 切换多语言开关
function toggleI18n(enabled: boolean) {
  localI18n.value = enabled
  if (enabled && localLanguages.value.length === 0) {
    const firstLang = languageList.value[0]
    if (firstLang) {
      localLanguages.value.push(firstLang.id)
      localDefaults.value.push({
        languageId: firstLang.id,
        languageCode: firstLang.code,
        languageName: firstLang.cname || firstLang.name,
        value: deepClone(localDefaultValue.value) ?? getDefaultValue(),
      })
      activeLanguageTab.value = firstLang.id
    }
  }
}

// 切换语言选中状态
function toggleLanguage(language: LanguageItem) {
  const index = localLanguages.value.indexOf(language.id)

  if (index > -1) {
    localLanguages.value.splice(index, 1)
    const defaultIndex = localDefaults.value.findIndex(d => d.languageId === language.id)
    if (defaultIndex > -1) {
      localDefaults.value.splice(defaultIndex, 1)
    }
    if (activeLanguageTab.value === language.id) {
      activeLanguageTab.value = localLanguages.value[0] ?? null
    }
  } else {
    localLanguages.value.push(language.id)
    localDefaults.value.push({
      languageId: language.id,
      languageCode: language.code,
      languageName: language.cname || language.name,
      value: getDefaultValue(),
    })
    if (localLanguages.value.length === 1) {
      activeLanguageTab.value = language.id
    }
  }
}

function isLanguageSelected(languageId: number): boolean {
  return localLanguages.value.includes(languageId)
}

function getLanguageDefaultValue(languageId: number): any {
  const item = localDefaults.value.find(d => d.languageId === languageId)
  return item?.value
}

function setLanguageDefaultValue(languageId: number, value: any) {
  const item = localDefaults.value.find(d => d.languageId === languageId)
  if (item) {
    item.value = value
  }
}

// 选中的语言列表
const selectedLanguages = computed(() => {
  return localLanguages.value
    .map(id => languageList.value.find(lang => lang.id === id))
    .filter((lang): lang is LanguageItem => !!lang)
})

// 当前编辑的值
const currentEditValue = computed({
  get() {
    if (localI18n.value && activeLanguageTab.value) {
      return getLanguageDefaultValue(activeLanguageTab.value)
    }
    return localDefaultValue.value
  },
  set(value: any) {
    if (localI18n.value && activeLanguageTab.value) {
      setLanguageDefaultValue(activeLanguageTab.value, value)
    } else {
      localDefaultValue.value = value
    }
  }
})

// 数组操作
function addArrayItem() {
  const arr = currentEditValue.value as any[] || []
  if (props.itemSchema && props.itemSchema.length > 0) {
    const newItem: Record<string, any> = {}
    props.itemSchema.forEach(f => {
      newItem[f.key] = getFieldDefaultValue(f.type)
    })
    arr.push(newItem)
  } else {
    arr.push(getFieldDefaultValue(props.itemType || 'string'))
  }
  currentEditValue.value = arr
}

function removeArrayItem(index: number) {
  const arr = currentEditValue.value as any[] || []
  arr.splice(index, 1)
  currentEditValue.value = [...arr]
}

function updateArrayItem(index: number, value: any) {
  const arr = currentEditValue.value as any[] || []
  arr[index] = value
  currentEditValue.value = [...arr]
}

function updateArrayItemField(index: number, fieldKey: string, value: any) {
  const arr = currentEditValue.value as any[] || []
  if (arr[index]) {
    arr[index][fieldKey] = value
    currentEditValue.value = [...arr]
  }
}

function updateObjectField(fieldKey: string, value: any) {
  const obj = currentEditValue.value as Record<string, any> || {}
  obj[fieldKey] = value
  currentEditValue.value = { ...obj }
}

function handleSave() {
  emit('save', {
    defaultValue: localDefaultValue.value,
    i18n: localI18n.value,
    i18nLanguages: localLanguages.value,
    i18nDefaults: localDefaults.value.filter(d => localLanguages.value.includes(d.languageId)),
  })
  emit('close')
}

function handleClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="handleClose">
        <div class="value-editor">
          <div class="editor-header">
            <h3 class="editor-title">
              <span class="i-carbon-settings-adjust"></span>
              默认值配置
            </h3>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <div class="editor-content">
            <!-- 多语言开关和语言选择 -->
            <div class="i18n-toggle-section">
              <label class="i18n-toggle-label">
                <input
                  type="checkbox"
                  :checked="localI18n"
                  @change="toggleI18n(($event.target as HTMLInputElement).checked)"
                />
                <span class="i-carbon-earth"></span>
                <span>启用多语言</span>
              </label>

              <!-- 语言多选 -->
              <div v-if="localI18n && !languageLoading && languageList.length > 0" class="language-select-inline">
                <span class="select-label">选择语言：</span>
                <div class="language-checkboxes-inline">
                  <label
                    v-for="lang in languageList"
                    :key="lang.id"
                    class="language-checkbox-item"
                    :class="{ selected: isLanguageSelected(lang.id) }"
                  >
                    <input
                      type="checkbox"
                      :checked="isLanguageSelected(lang.id)"
                      @change="toggleLanguage(lang)"
                    />
                    <span>{{ lang.cname || lang.name }}</span>
                  </label>
                </div>
              </div>
            </div>

            <!-- 加载状态 -->
            <div v-if="localI18n && languageLoading" class="loading-state">
              <span class="i-carbon-circle-dash animate-spin"></span>
              <span>加载语言列表...</span>
            </div>

            <!-- 空状态 -->
            <div v-else-if="localI18n && languageList.length === 0" class="empty-config">
              暂无可用语言，请先配置语言
            </div>

            <!-- 主内容区域 -->
            <div
              v-else
              class="editor-container"
              :class="{ 'with-sidebar': localI18n && selectedLanguages.length > 0 }"
            >
              <!-- 左侧语言 Tab -->
              <div v-if="localI18n && selectedLanguages.length > 0" class="language-sidebar">
                <div class="sidebar-title">语言</div>
                <div class="language-tabs-vertical">
                  <button
                    v-for="lang in selectedLanguages"
                    :key="lang.id"
                    class="language-tab-vertical"
                    :class="{ active: activeLanguageTab === lang.id }"
                    @click="activeLanguageTab = lang.id"
                  >
                    <span class="tab-name">{{ lang.cname || lang.name }}</span>
                    <span class="tab-code">{{ lang.code }}</span>
                  </button>
                </div>
              </div>

              <!-- 右侧编辑区域 -->
              <div class="value-editor-section">
                <div v-if="localI18n && selectedLanguages.length === 0" class="empty-hint">
                  请先选择至少一种语言
                </div>

                <template v-else>
                  <!-- String -->
                  <template v-if="type === 'string'">
                    <label class="field-label">文本值</label>
                    <input
                      v-model="currentEditValue"
                      type="text"
                      class="field-input"
                      placeholder="输入文本"
                    />
                  </template>

                  <!-- Number -->
                  <template v-else-if="type === 'number'">
                    <label class="field-label">数值</label>
                    <input
                      v-model.number="currentEditValue"
                      type="number"
                      class="field-input"
                      placeholder="输入数字"
                    />
                  </template>

                  <!-- Boolean -->
                  <template v-else-if="type === 'boolean'">
                    <label class="field-label">开关状态</label>
                    <div class="toggle-wrapper">
                      <button
                        class="toggle-btn"
                        :class="{ active: currentEditValue }"
                        @click="currentEditValue = !currentEditValue"
                      >
                        <span class="toggle-track">
                          <span class="toggle-thumb"></span>
                        </span>
                        <span class="toggle-text">{{ currentEditValue ? '开启' : '关闭' }}</span>
                      </button>
                    </div>
                  </template>

                  <!-- Color -->
                  <template v-else-if="type === 'color'">
                    <label class="field-label">颜色</label>
                    <div class="color-picker">
                      <input
                        v-model="currentEditValue"
                        type="color"
                        class="color-input"
                      />
                      <input
                        v-model="currentEditValue"
                        type="text"
                        class="color-text"
                        placeholder="#000000"
                      />
                    </div>
                  </template>

                  <!-- Image -->
                  <template v-else-if="type === 'image'">
                    <label class="field-label">图片 URL</label>
                    <input
                      v-model="currentEditValue"
                      type="text"
                      class="field-input"
                      placeholder="输入图片地址"
                    />
                    <div v-if="currentEditValue" class="image-preview">
                      <img :src="currentEditValue" alt="预览" />
                    </div>
                  </template>

                  <!-- Richtext -->
                  <template v-else-if="type === 'richtext'">
                    <label class="field-label">富文本内容</label>
                    <textarea
                      v-model="currentEditValue"
                      class="field-textarea"
                      placeholder="输入 HTML 内容"
                      rows="6"
                    ></textarea>
                  </template>

                  <!-- Enum -->
                  <template v-else-if="type === 'enum'">
                    <label class="field-label">选择值</label>
                    <div class="enum-options">
                      <button
                        v-for="option in enumOptions"
                        :key="String(option.value)"
                        class="enum-option"
                        :class="{ active: currentEditValue === option.value }"
                        @click="currentEditValue = option.value"
                      >
                        <span class="option-check">
                          <span v-if="currentEditValue === option.value" class="i-carbon-checkmark"></span>
                        </span>
                        <span class="option-label">{{ option.label }}</span>
                        <span class="option-value">{{ option.value }}</span>
                      </button>
                    </div>
                  </template>

                  <!-- Array -->
                  <template v-else-if="type === 'array'">
                    <div class="array-header">
                      <label class="field-label">数组元素</label>
                      <button class="add-item-btn" @click="addArrayItem">
                        <span class="i-carbon-add"></span>
                        添加
                      </button>
                    </div>

                    <div v-if="!currentEditValue?.length" class="empty-array">
                      <span class="i-carbon-list"></span>
                      <span>暂无元素，点击添加按钮</span>
                    </div>

                    <div v-else class="array-items">
                      <div
                        v-for="(item, index) in currentEditValue"
                        :key="index"
                        class="array-item"
                      >
                        <div class="item-header">
                          <span class="item-index">{{ index + 1 }}</span>
                          <button class="remove-item-btn" @click="removeArrayItem(index)">
                            <span class="i-carbon-close"></span>
                          </button>
                        </div>

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
                      </div>
                    </div>
                  </template>

                  <!-- Object -->
                  <template v-else-if="type === 'object'">
                    <label class="field-label">对象字段</label>

                    <div v-if="!fields?.length" class="empty-object">
                      <span class="i-carbon-json"></span>
                      <span>未定义字段结构</span>
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
                          :value="currentEditValue?.[field.key]"
                          type="text"
                          class="object-field-input"
                          @input="updateObjectField(field.key, ($event.target as HTMLInputElement).value)"
                        />
                        <input
                          v-else-if="field.type === 'number'"
                          :value="currentEditValue?.[field.key]"
                          type="number"
                          class="object-field-input"
                          @input="updateObjectField(field.key, Number(($event.target as HTMLInputElement).value))"
                        />
                        <button
                          v-else-if="field.type === 'boolean'"
                          class="field-toggle"
                          :class="{ active: currentEditValue?.[field.key] }"
                          @click="updateObjectField(field.key, !currentEditValue?.[field.key])"
                        >
                          {{ currentEditValue?.[field.key] ? '开启' : '关闭' }}
                        </button>
                        <div v-else-if="field.type === 'color'" class="field-color">
                          <input
                            :value="currentEditValue?.[field.key]"
                            type="color"
                            class="field-color-input"
                            @input="updateObjectField(field.key, ($event.target as HTMLInputElement).value)"
                          />
                          <span class="field-color-value">{{ currentEditValue?.[field.key] }}</span>
                        </div>
                      </div>
                    </div>
                  </template>
                </template>
              </div>
            </div>
          </div>

          <div class="editor-footer">
            <button class="btn btn-secondary" @click="handleClose">
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
  width: 700px;
  max-width: 90vw;
  max-height: 85vh;
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
  padding: 20px 24px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.8) 0%, rgba(30, 41, 59, 0.4) 100%);
}

.editor-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.editor-title span {
  font-size: 20px;
  color: #3b82f6;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #94a3b8;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #f1f5f9;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.editor-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* 多语言开关区域 */
.i18n-toggle-section {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  margin-bottom: 20px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 12px;
}

.i18n-toggle-label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
  color: #e2e8f0;
  cursor: pointer;
}

.i18n-toggle-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: #3b82f6;
  cursor: pointer;
}

.i18n-toggle-label .i-carbon-earth {
  font-size: 18px;
  color: #3b82f6;
}

.language-select-inline {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.select-label {
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
}

.language-checkboxes-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.language-checkbox-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: #94a3b8;
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.language-checkbox-item:hover {
  color: #e2e8f0;
  border-color: rgba(71, 85, 105, 0.6);
}

.language-checkbox-item.selected {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.4);
}

.language-checkbox-item input[type="checkbox"] {
  display: none;
}

/* 加载和空状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px;
  font-size: 14px;
  color: #94a3b8;
}

.loading-state .i-carbon-circle-dash {
  font-size: 20px;
}

.empty-config,
.empty-hint {
  padding: 32px;
  text-align: center;
  font-size: 14px;
  color: #64748b;
  background: rgba(15, 23, 42, 0.4);
  border: 1px dashed rgba(71, 85, 105, 0.4);
  border-radius: 12px;
}

/* 编辑器容器 */
.editor-container {
  display: flex;
  gap: 20px;
}

.editor-container.with-sidebar {
  min-height: 300px;
}

/* 语言侧边栏 */
.language-sidebar {
  width: 140px;
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
  padding-left: 4px;
}

.language-tabs-vertical {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.language-tab-vertical {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 12px 14px;
  font-size: 14px;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.language-tab-vertical:hover {
  color: #e2e8f0;
  border-color: rgba(71, 85, 105, 0.6);
}

.language-tab-vertical.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.4);
}

.tab-name {
  font-weight: 500;
}

.tab-code {
  font-size: 11px;
  color: #64748b;
  font-family: 'Monaco', 'Menlo', monospace;
}

.language-tab-vertical.active .tab-code {
  color: #60a5fa;
}

/* 值编辑区域 */
.value-editor-section {
  flex: 1;
  min-width: 0;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  margin-bottom: 10px;
}

.field-input {
  width: 100%;
  padding: 12px 16px;
  font-size: 14px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 10px;
  outline: none;
  transition: all 0.2s;
}

.field-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.field-textarea {
  width: 100%;
  padding: 12px 16px;
  font-size: 14px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 10px;
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
  gap: 14px;
  padding: 12px 18px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-track {
  position: relative;
  width: 48px;
  height: 26px;
  background: #475569;
  border-radius: 13px;
  transition: all 0.2s;
}

.toggle-btn.active .toggle-track {
  background: #3b82f6;
}

.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  transition: all 0.2s;
}

.toggle-btn.active .toggle-thumb {
  left: 25px;
}

.toggle-text {
  font-size: 14px;
  font-weight: 500;
  color: #e2e8f0;
}

.color-picker {
  display: flex;
  gap: 12px;
}

.color-input {
  width: 52px;
  height: 44px;
  padding: 4px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 10px;
  cursor: pointer;
}

.color-text {
  flex: 1;
  padding: 12px 16px;
  font-size: 14px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 10px;
  outline: none;
}

.image-preview {
  margin-top: 12px;
  padding: 12px;
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 10px;
}

.image-preview img {
  max-width: 100%;
  max-height: 160px;
  border-radius: 6px;
}

.enum-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.enum-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  font-size: 14px;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
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

.option-check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 14px;
  color: #fff;
  background: rgba(51, 65, 85, 0.5);
  border-radius: 50%;
  flex-shrink: 0;
}

.enum-option.active .option-check {
  background: #3b82f6;
}

.option-label {
  flex: 1;
  font-weight: 500;
}

.option-value {
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #64748b;
}

.array-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.add-item-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-item-btn:hover {
  background: rgba(59, 130, 246, 0.2);
}

.empty-array,
.empty-object {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px;
  font-size: 14px;
  color: #64748b;
  text-align: center;
  background: rgba(15, 23, 42, 0.4);
  border: 1px dashed rgba(71, 85, 105, 0.4);
  border-radius: 12px;
}

.empty-array span:first-child,
.empty-object span:first-child {
  font-size: 32px;
  color: #475569;
}

.array-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.array-item {
  padding: 16px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 12px;
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.item-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
  border-radius: 6px;
}

.item-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.item-content.simple {
  flex-direction: row;
  align-items: center;
}

.item-input {
  flex: 1;
  padding: 10px 14px;
  font-size: 14px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
}

.item-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-field-label {
  width: 100px;
  font-size: 13px;
  color: #94a3b8;
  flex-shrink: 0;
}

.item-field-input {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  outline: none;
}

.mini-toggle {
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 6px;
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
  width: 36px;
  height: 28px;
  padding: 2px;
  background: transparent;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  cursor: pointer;
}

.item-color {
  display: flex;
  align-items: center;
  gap: 10px;
}

.color-value {
  font-size: 13px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #94a3b8;
}

.remove-item-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-size: 14px;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.remove-item-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.object-fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.object-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.object-field-label {
  font-size: 13px;
  color: #94a3b8;
}

.object-field-input {
  padding: 10px 14px;
  font-size: 14px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
}

.field-toggle {
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 8px;
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
  gap: 12px;
}

.field-color-input {
  width: 44px;
  height: 36px;
  padding: 3px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  cursor: pointer;
}

.field-color-value {
  font-size: 14px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #94a3b8;
}

.editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.4) 0%, rgba(30, 41, 59, 0.8) 100%);
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 20px;
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

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-spin {
  animation: spin 1s linear infinite;
}
</style>
