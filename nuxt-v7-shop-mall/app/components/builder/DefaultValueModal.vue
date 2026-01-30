<script setup lang="ts">
/**
 * 默认值配置弹窗组件
 * 用于配置变量的默认值，支持多语言 Tab 切换
 * 支持所有变量类型：string、number、boolean、color、image、richtext、enum、array、object
 */

import type {
  VariableType,
  I18nDefaultValue,
  EnumOption,
  VariableFieldSchema,
} from "~/types/data-context";

// 语言项类型
interface LanguageItem {
  id: number;
  code: string;
  name: string;
  cname: string;
}

// Props
const props = defineProps<{
  modelValue: boolean;
  variableType: VariableType;
  defaultValue: any;
  i18n: boolean;
  i18nLanguages: number[];
  i18nDefaults: I18nDefaultValue[];
  // 类型相关配置
  enumOptions?: EnumOption[];
  itemType?: VariableType;
  itemSchema?: VariableFieldSchema[];
  fields?: VariableFieldSchema[];
}>();

// Emits
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "save", data: {
    defaultValue: any;
    i18n: boolean;
    i18nLanguages: number[];
    i18nDefaults: I18nDefaultValue[];
  }): void;
}>();

// 语言列表
const languageList = ref<LanguageItem[]>([]);
const languageLoading = ref(false);

// 本地编辑状态
const localI18n = ref(false);
const localDefaultValue = ref<any>(null);
const localLanguages = ref<number[]>([]);
const localDefaults = ref<I18nDefaultValue[]>([]);

// 当前选中的语言 Tab
const activeLanguageTab = ref<number | null>(null);

// 获取语言列表
async function fetchLanguages() {
  if (languageList.value.length > 0) return;
  
  languageLoading.value = true;
  try {
    const data = await $fetch<LanguageItem[]>("/api/languages/list");
    languageList.value = data || [];
  } catch (error) {
    console.error("获取语言列表失败:", error);
    languageList.value = [];
  } finally {
    languageLoading.value = false;
  }
}

// 监听弹窗打开，初始化本地状态
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      fetchLanguages();
      // 深拷贝 props 数据到本地状态
      localI18n.value = props.i18n;
      localDefaultValue.value = deepClone(props.defaultValue);
      localLanguages.value = [...props.i18nLanguages];
      localDefaults.value = deepClone(props.i18nDefaults);
      
      // 设置默认激活的语言 Tab
      if (localLanguages.value.length > 0) {
        activeLanguageTab.value = localLanguages.value[0] ?? null;
      } else {
        activeLanguageTab.value = null;
      }
    }
  },
  { immediate: true }
);

// 深拷贝
function deepClone<T>(obj: T): T {
  if (obj === null || obj === undefined) return obj;
  return JSON.parse(JSON.stringify(obj));
}

// 根据类型获取默认值
function getDefaultValueByType(type: VariableType): any {
  switch (type) {
    case "string":
      return "";
    case "number":
      return 0;
    case "boolean":
      return false;
    case "color":
      return "#000000";
    case "image":
      return "";
    case "richtext":
      return "";
    case "enum":
      return props.enumOptions?.[0]?.value ?? "";
    case "array":
      return [];
    case "object":
      // 根据 fields 生成默认对象
      const obj: Record<string, any> = {};
      props.fields?.forEach(f => {
        obj[f.key] = getDefaultValueByType(f.type);
      });
      return obj;
    default:
      return "";
  }
}

// 切换多语言开关
function toggleI18n(enabled: boolean) {
  localI18n.value = enabled;
  if (enabled && localLanguages.value.length === 0) {
    // 启用多语言时，如果没有选择语言，默认选第一个
    const firstLang = languageList.value[0];
    if (firstLang) {
      localLanguages.value.push(firstLang.id);
      localDefaults.value.push({
        languageId: firstLang.id,
        languageCode: firstLang.code,
        languageName: firstLang.cname || firstLang.name,
        value: deepClone(localDefaultValue.value) ?? getDefaultValueByType(props.variableType),
      });
      activeLanguageTab.value = firstLang.id;
    }
  }
}

// 切换语言选中状态
function toggleLanguage(language: LanguageItem) {
  const index = localLanguages.value.indexOf(language.id);
  
  if (index > -1) {
    // 取消选中：移除语言和对应的默认值
    localLanguages.value.splice(index, 1);
    const defaultIndex = localDefaults.value.findIndex(d => d.languageId === language.id);
    if (defaultIndex > -1) {
      localDefaults.value.splice(defaultIndex, 1);
    }
    // 如果取消的是当前激活的 Tab，切换到第一个
    if (activeLanguageTab.value === language.id) {
      activeLanguageTab.value = localLanguages.value[0] ?? null;
    }
  } else {
    // 选中：添加语言和默认值条目
    localLanguages.value.push(language.id);
    localDefaults.value.push({
      languageId: language.id,
      languageCode: language.code,
      languageName: language.cname || language.name,
      value: getDefaultValueByType(props.variableType),
    });
    // 如果是第一个选中的语言，激活它
    if (localLanguages.value.length === 1) {
      activeLanguageTab.value = language.id;
    }
  }
}

// 检查语言是否被选中
function isLanguageSelected(languageId: number): boolean {
  return localLanguages.value.includes(languageId);
}

// 获取语言的默认值
function getLanguageDefaultValue(languageId: number): any {
  const item = localDefaults.value.find(d => d.languageId === languageId);
  return item?.value;
}

// 设置语言的默认值
function setLanguageDefaultValue(languageId: number, value: any) {
  const item = localDefaults.value.find(d => d.languageId === languageId);
  if (item) {
    item.value = value;
  }
}

// 选中的语言列表（按选择顺序）
const selectedLanguages = computed(() => {
  return localLanguages.value
    .map(id => languageList.value.find(lang => lang.id === id))
    .filter((lang): lang is LanguageItem => !!lang);
});

// 当前激活语言的信息
const activeLanguage = computed(() => {
  if (!activeLanguageTab.value) return null;
  return languageList.value.find(lang => lang.id === activeLanguageTab.value);
});

// 当前编辑的值（多语言时为当前 Tab 的值，否则为普通默认值）
const currentEditValue = computed({
  get() {
    if (localI18n.value && activeLanguageTab.value) {
      return getLanguageDefaultValue(activeLanguageTab.value);
    }
    return localDefaultValue.value;
  },
  set(value: any) {
    if (localI18n.value && activeLanguageTab.value) {
      setLanguageDefaultValue(activeLanguageTab.value, value);
    } else {
      localDefaultValue.value = value;
    }
  }
});

// 关闭弹窗
function handleClose() {
  emit("update:modelValue", false);
}

// 保存配置
function handleSave() {
  emit("save", {
    defaultValue: localDefaultValue.value,
    i18n: localI18n.value,
    i18nLanguages: localLanguages.value,
    i18nDefaults: localDefaults.value.filter(d => localLanguages.value.includes(d.languageId)),
  });
  emit("update:modelValue", false);
}

// ============ 数组操作 ============

function addArrayItem() {
  const arr = currentEditValue.value as any[] || [];
  if (props.itemSchema && props.itemSchema.length > 0) {
    // 复杂类型：添加对象
    const newItem: Record<string, any> = {};
    props.itemSchema.forEach(f => {
      newItem[f.key] = getDefaultValueByType(f.type);
    });
    arr.push(newItem);
  } else {
    // 简单类型
    arr.push(getDefaultValueByType(props.itemType || "string"));
  }
  currentEditValue.value = arr;
}

function removeArrayItem(index: number) {
  const arr = currentEditValue.value as any[] || [];
  arr.splice(index, 1);
  currentEditValue.value = [...arr];
}

function updateArrayItem(index: number, value: any) {
  const arr = currentEditValue.value as any[] || [];
  arr[index] = value;
  currentEditValue.value = [...arr];
}

function updateArrayItemField(index: number, fieldKey: string, value: any) {
  const arr = currentEditValue.value as any[] || [];
  if (arr[index]) {
    arr[index][fieldKey] = value;
    currentEditValue.value = [...arr];
  }
}

// ============ 对象操作 ============

function updateObjectField(fieldKey: string, value: any) {
  const obj = currentEditValue.value as Record<string, any> || {};
  obj[fieldKey] = value;
  currentEditValue.value = { ...obj };
}

// 获取类型显示名称
function getTypeLabel(type: VariableType): string {
  const typeLabels: Record<VariableType, string> = {
    string: "文本",
    number: "数字",
    boolean: "开关",
    color: "颜色",
    image: "图片",
    richtext: "富文本",
    enum: "枚举",
    array: "数组",
    object: "对象",
  };
  return typeLabels[type] || type;
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-overlay" @click.self="handleClose">
        <div class="default-value-modal">
          <!-- 头部 -->
          <div class="modal-header">
            <h3 class="modal-title">
              <span class="i-carbon-settings-adjust"></span>
              默认值配置
            </h3>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- 内容 -->
          <div class="modal-content">
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
              
              <!-- 语言多选（启用多语言时显示） -->
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

            <!-- 多语言加载/空状态 -->
            <div v-if="localI18n && languageLoading" class="loading-state">
              <span class="i-carbon-circle-dash animate-spin"></span>
              <span>加载语言列表...</span>
            </div>
            
            <div v-else-if="localI18n && languageList.length === 0" class="empty-config">
              暂无可用语言，请先在数据库中配置语言
            </div>

            <!-- 主内容区域：多语言时左右布局 -->
            <div 
              v-else
              class="editor-container"
              :class="{ 'with-sidebar': localI18n && selectedLanguages.length > 0 }"
            >
              <!-- 左侧语言 Tab（垂直） -->
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
                <!-- 简单类型：文本 -->
                <template v-if="variableType === 'string'">
                  <input
                    v-model="currentEditValue"
                    type="text"
                    class="property-input"
                    placeholder="输入默认文本"
                  />
                </template>

                <!-- 简单类型：数字 -->
                <template v-else-if="variableType === 'number'">
                  <input
                    v-model.number="currentEditValue"
                    type="number"
                    class="property-input"
                    placeholder="输入默认数字"
                  />
                </template>

                <!-- 简单类型：开关 -->
                <template v-else-if="variableType === 'boolean'">
                  <label class="switch">
                    <input
                      type="checkbox"
                      :checked="currentEditValue"
                      @change="currentEditValue = ($event.target as HTMLInputElement).checked"
                    />
                    <span class="switch-slider"></span>
                  </label>
                </template>

                <!-- 简单类型：颜色 -->
                <template v-else-if="variableType === 'color'">
                  <div class="color-input">
                    <input
                      type="color"
                      :value="currentEditValue || '#000000'"
                      @input="currentEditValue = ($event.target as HTMLInputElement).value"
                    />
                    <input
                      v-model="currentEditValue"
                      type="text"
                      class="property-input"
                      placeholder="#000000"
                    />
                  </div>
                </template>

                <!-- 简单类型：图片 -->
                <template v-else-if="variableType === 'image'">
                  <input
                    v-model="currentEditValue"
                    type="text"
                    class="property-input"
                    placeholder="输入图片 URL"
                  />
                </template>

                <!-- 简单类型：富文本 -->
                <template v-else-if="variableType === 'richtext'">
                  <textarea
                    v-model="currentEditValue"
                    class="property-input property-textarea"
                    placeholder="输入富文本内容"
                  ></textarea>
                </template>

                <!-- 枚举类型 -->
                <template v-else-if="variableType === 'enum'">
                  <select v-model="currentEditValue" class="property-input">
                    <option value="">-- 选择默认值 --</option>
                    <option
                      v-for="opt in enumOptions"
                      :key="String(opt.value)"
                      :value="opt.value"
                    >
                      {{ opt.label }} ({{ opt.value }})
                    </option>
                  </select>
                </template>

                <!-- 数组类型 -->
                <template v-else-if="variableType === 'array'">
                  <div class="array-editor">
                    <div class="array-hint">配置数组的默认元素</div>
                    
                    <!-- 简单类型数组 -->
                    <template v-if="!itemSchema || itemSchema.length === 0">
                      <div class="array-items">
                        <div
                          v-for="(item, index) in (currentEditValue as any[] || [])"
                          :key="index"
                          class="array-item-row"
                        >
                          <span class="array-item-index">{{ index + 1 }}</span>
                          <!-- 文本/图片 -->
                          <input
                            v-if="itemType === 'string' || itemType === 'image'"
                            :value="item"
                            type="text"
                            class="property-input"
                            :placeholder="itemType === 'image' ? '图片 URL' : '文本值'"
                            @input="updateArrayItem(index, ($event.target as HTMLInputElement).value)"
                          />
                          <!-- 数字 -->
                          <input
                            v-else-if="itemType === 'number'"
                            :value="item"
                            type="number"
                            class="property-input"
                            placeholder="数字值"
                            @input="updateArrayItem(index, Number(($event.target as HTMLInputElement).value))"
                          />
                          <!-- 颜色 -->
                          <div v-else-if="itemType === 'color'" class="color-input compact">
                            <input
                              :value="item"
                              type="color"
                              @input="updateArrayItem(index, ($event.target as HTMLInputElement).value)"
                            />
                            <input
                              :value="item"
                              type="text"
                              class="property-input"
                              placeholder="#000000"
                              @input="updateArrayItem(index, ($event.target as HTMLInputElement).value)"
                            />
                          </div>
                          <!-- 开关 -->
                          <label v-else-if="itemType === 'boolean'" class="switch compact">
                            <input
                              :checked="item"
                              type="checkbox"
                              @change="updateArrayItem(index, ($event.target as HTMLInputElement).checked)"
                            />
                            <span class="switch-slider"></span>
                          </label>
                          <!-- 富文本 -->
                          <textarea
                            v-else-if="itemType === 'richtext'"
                            :value="item"
                            class="property-input property-textarea compact"
                            placeholder="富文本内容"
                            @input="updateArrayItem(index, ($event.target as HTMLTextAreaElement).value)"
                          ></textarea>
                          <button class="remove-item-btn" @click="removeArrayItem(index)">
                            <span class="i-carbon-close"></span>
                          </button>
                        </div>
                      </div>
                    </template>

                    <!-- 复杂类型数组（对象数组） -->
                    <template v-else>
                      <div class="array-items">
                        <div
                          v-for="(item, index) in (currentEditValue as any[] || [])"
                          :key="index"
                          class="array-object-item"
                        >
                          <div class="array-object-header">
                            <span class="array-object-index">元素 {{ index + 1 }}</span>
                            <button class="remove-item-btn" @click="removeArrayItem(index)">
                              <span class="i-carbon-close"></span>
                            </button>
                          </div>
                          <div class="array-object-fields">
                            <div
                              v-for="field in itemSchema"
                              :key="field.key"
                              class="object-field"
                            >
                              <label>{{ field.label }}</label>
                              <!-- 文本/图片 -->
                              <input
                                v-if="field.type === 'string' || field.type === 'image'"
                                :value="item[field.key]"
                                type="text"
                                class="property-input"
                                :placeholder="field.label"
                                @input="updateArrayItemField(index, field.key, ($event.target as HTMLInputElement).value)"
                              />
                              <!-- 数字 -->
                              <input
                                v-else-if="field.type === 'number'"
                                :value="item[field.key]"
                                type="number"
                                class="property-input"
                                @input="updateArrayItemField(index, field.key, Number(($event.target as HTMLInputElement).value))"
                              />
                              <!-- 颜色 -->
                              <div v-else-if="field.type === 'color'" class="color-input compact">
                                <input
                                  :value="item[field.key]"
                                  type="color"
                                  @input="updateArrayItemField(index, field.key, ($event.target as HTMLInputElement).value)"
                                />
                                <input
                                  :value="item[field.key]"
                                  type="text"
                                  class="property-input"
                                  @input="updateArrayItemField(index, field.key, ($event.target as HTMLInputElement).value)"
                                />
                              </div>
                              <!-- 开关 -->
                              <label v-else-if="field.type === 'boolean'" class="switch compact">
                                <input
                                  :checked="item[field.key]"
                                  type="checkbox"
                                  @change="updateArrayItemField(index, field.key, ($event.target as HTMLInputElement).checked)"
                                />
                                <span class="switch-slider"></span>
                              </label>
                              <!-- 富文本 -->
                              <textarea
                                v-else-if="field.type === 'richtext'"
                                :value="item[field.key]"
                                class="property-input property-textarea compact"
                                :placeholder="field.label"
                                @input="updateArrayItemField(index, field.key, ($event.target as HTMLTextAreaElement).value)"
                              ></textarea>
                            </div>
                          </div>
                        </div>
                      </div>
                    </template>

                    <button class="add-item-btn" @click="addArrayItem">
                      <span class="i-carbon-add"></span>
                      添加元素
                    </button>
                  </div>
                </template>

                <!-- 对象类型 -->
                <template v-else-if="variableType === 'object'">
                  <div class="object-editor">
                    <div class="object-hint">配置对象各字段的默认值</div>
                    <div class="object-fields">
                      <div
                        v-for="field in fields"
                        :key="field.key"
                        class="object-field"
                      >
                        <label>{{ field.label }}</label>
                        <!-- 文本/图片 -->
                        <input
                          v-if="field.type === 'string' || field.type === 'image'"
                          :value="(currentEditValue as Record<string, any>)?.[field.key]"
                          type="text"
                          class="property-input"
                          :placeholder="field.label"
                          @input="updateObjectField(field.key, ($event.target as HTMLInputElement).value)"
                        />
                        <!-- 数字 -->
                        <input
                          v-else-if="field.type === 'number'"
                          :value="(currentEditValue as Record<string, any>)?.[field.key]"
                          type="number"
                          class="property-input"
                          @input="updateObjectField(field.key, Number(($event.target as HTMLInputElement).value))"
                        />
                        <!-- 颜色 -->
                        <div v-else-if="field.type === 'color'" class="color-input compact">
                          <input
                            :value="(currentEditValue as Record<string, any>)?.[field.key]"
                            type="color"
                            @input="updateObjectField(field.key, ($event.target as HTMLInputElement).value)"
                          />
                          <input
                            :value="(currentEditValue as Record<string, any>)?.[field.key]"
                            type="text"
                            class="property-input"
                            @input="updateObjectField(field.key, ($event.target as HTMLInputElement).value)"
                          />
                        </div>
                        <!-- 开关 -->
                        <label v-else-if="field.type === 'boolean'" class="switch compact">
                          <input
                            :checked="(currentEditValue as Record<string, any>)?.[field.key]"
                            type="checkbox"
                            @change="updateObjectField(field.key, ($event.target as HTMLInputElement).checked)"
                          />
                          <span class="switch-slider"></span>
                        </label>
                        <!-- 富文本 -->
                        <textarea
                          v-else-if="field.type === 'richtext'"
                          :value="(currentEditValue as Record<string, any>)?.[field.key]"
                          class="property-input property-textarea compact"
                          :placeholder="field.label"
                          @input="updateObjectField(field.key, ($event.target as HTMLTextAreaElement).value)"
                        ></textarea>
                      </div>
                    </div>
                  </div>
                </template>
              </template>
            </div>
            </div>
          </div>

          <!-- 底部 -->
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="handleClose">取消</button>
            <button class="btn btn-primary" @click="handleSave">
              <span class="i-carbon-checkmark mr-1"></span>
              保存
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

.default-value-modal {
  display: flex;
  flex-direction: column;
  width: 1080px;
  max-width: 90vw;
  max-height: 85vh;
  background-color: #1e293b;
  border-radius: 12px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #334155;
}

.modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #e2e8f0;
}

.modal-title .i-carbon-settings-adjust {
  color: #3b82f6;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #e2e8f0;
  background-color: #334155;
}

.modal-content {
  flex: 1;
  overflow: hidden;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid #334155;
}

/* ============ 多语言开关 ============ */

.i18n-toggle-section {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #334155;
}

.i18n-toggle-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #e2e8f0;
  cursor: pointer;
  flex-shrink: 0;
}

.i18n-toggle-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: #22c55e;
  cursor: pointer;
}

.i18n-toggle-label .i-carbon-earth {
  font-size: 18px;
  color: #22c55e;
}

/* 语言选择（内联） */
.language-select-inline {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.select-label {
  font-size: 12px;
  color: #64748b;
  flex-shrink: 0;
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
  padding: 4px 10px;
  font-size: 12px;
  color: #94a3b8;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.language-checkbox-item:hover {
  border-color: #475569;
}

.language-checkbox-item.selected {
  color: #e2e8f0;
  background-color: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
}

.language-checkbox-item input[type="checkbox"] {
  width: 12px;
  height: 12px;
  accent-color: #3b82f6;
  cursor: pointer;
}

/* ============ 编辑器容器（左右布局） ============ */

.editor-container {
  display: flex;
  gap: 0;
  flex: 1;
  min-height: 0;
}

.editor-container.with-sidebar {
  border: 1px solid #334155;
  border-radius: 8px;
  overflow: hidden;
}

/* ============ 左侧语言侧边栏 ============ */

.language-sidebar {
  width: 160px;
  flex-shrink: 0;
  background-color: #0f172a;
  border-right: 1px solid #334155;
  display: flex;
  flex-direction: column;
}

.sidebar-title {
  padding: 12px 16px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid #334155;
}

.language-tabs-vertical {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow-y: auto;
}

.language-tab-vertical {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 13px;
  color: #94a3b8;
  background: none;
  border: none;
  border-left: 3px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.language-tab-vertical:hover {
  color: #e2e8f0;
  background-color: rgba(255, 255, 255, 0.02);
}

.language-tab-vertical.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
  border-left-color: #3b82f6;
}

.language-tab-vertical .tab-name {
  font-weight: 500;
}

.language-tab-vertical .tab-code {
  font-size: 11px;
  color: #64748b;
}

.language-tab-vertical.active .tab-code {
  color: #60a5fa;
}

/* ============ 默认值编辑区域 ============ */

.value-editor-section {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  min-height: 0;
}

.editor-container:not(.with-sidebar) .value-editor-section {
  padding: 0;
  overflow-y: auto;
}

/* ============ 自定义滚动条样式 ============ */

.value-editor-section::-webkit-scrollbar,
.language-tabs-vertical::-webkit-scrollbar {
  width: 6px;
}

.value-editor-section::-webkit-scrollbar-track,
.language-tabs-vertical::-webkit-scrollbar-track {
  background: transparent;
}

.value-editor-section::-webkit-scrollbar-thumb,
.language-tabs-vertical::-webkit-scrollbar-thumb {
  background-color: #334155;
  border-radius: 3px;
}

.value-editor-section::-webkit-scrollbar-thumb:hover,
.language-tabs-vertical::-webkit-scrollbar-thumb:hover {
  background-color: #475569;
}

/* Firefox 滚动条 */
.value-editor-section,
.language-tabs-vertical {
  scrollbar-width: thin;
  scrollbar-color: #334155 transparent;
}

.empty-hint {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #64748b;
  background-color: #0f172a;
  border: 1px dashed #334155;
  border-radius: 8px;
}

/* ============ 数组编辑器 ============ */

.array-editor,
.object-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.array-hint,
.object-hint {
  font-size: 12px;
  color: #64748b;
}

.array-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.array-item-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.array-item-index {
  width: 24px;
  font-size: 12px;
  color: #64748b;
  text-align: center;
  flex-shrink: 0;
}

.array-item-row .property-input {
  flex: 1;
}

.array-item-row .color-input.compact {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 数组对象元素 */
.array-object-item {
  padding: 12px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
}

.array-object-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.array-object-index {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

.array-object-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* ============ 对象编辑器 ============ */

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

.object-field label {
  font-size: 12px;
  color: #94a3b8;
}

/* ============ 表单元素 ============ */

.property-input {
  width: 100%;
  min-width: 0;
  padding: 8px 12px;
  font-size: 14px;
  background-color: #0f172a;
  border: 1px solid #475569;
  border-radius: 6px;
  color: #e2e8f0;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.property-input:focus {
  border-color: #3b82f6;
}

.property-input::placeholder {
  color: #64748b;
}

.property-textarea {
  min-height: 80px;
  resize: vertical;
}

.property-textarea.compact {
  min-height: 60px;
}

/* 颜色输入 */
.color-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-input input[type="color"] {
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}

.color-input .property-input {
  flex: 1;
}

.color-input.compact input[type="color"] {
  width: 32px;
  height: 32px;
}

/* 开关 */
.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background-color: #334155;
  border-radius: 24px;
  transition: 0.2s;
}

.switch-slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  border-radius: 50%;
  transition: 0.2s;
}

.switch input:checked + .switch-slider {
  background-color: #3b82f6;
}

.switch input:checked + .switch-slider:before {
  transform: translateX(20px);
}

.switch.compact {
  width: 40px;
  height: 22px;
}

.switch.compact .switch-slider:before {
  height: 16px;
  width: 16px;
}

.switch.compact input:checked + .switch-slider:before {
  transform: translateX(18px);
}

/* 按钮 */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background-color: #2563eb;
}

.btn-secondary {
  background-color: #334155;
  color: #e2e8f0;
}

.btn-secondary:hover {
  background-color: #475569;
}

.add-item-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #3b82f6;
  background: none;
  border: 1px dashed #3b82f6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-item-btn:hover {
  background-color: rgba(59, 130, 246, 0.1);
}

.remove-item-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 14px;
  color: #64748b;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.remove-item-btn:hover {
  color: #ef4444;
  background-color: rgba(239, 68, 68, 0.1);
}

/* 状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  font-size: 13px;
  color: #64748b;
}

.loading-state .animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.empty-config {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #64748b;
  background-color: #0f172a;
  border: 1px dashed #334155;
  border-radius: 8px;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .default-value-modal,
.modal-leave-active .default-value-modal {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .default-value-modal,
.modal-leave-to .default-value-modal {
  transform: scale(0.95);
}
</style>
