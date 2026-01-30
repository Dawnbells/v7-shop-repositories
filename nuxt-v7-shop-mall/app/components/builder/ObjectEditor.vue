<script setup lang="ts">
/**
 * ObjectEditor - 对象结构化编辑器
 * 
 * 功能：
 * - 根据 fields schema 渲染结构化表单
 * - 支持各种字段类型
 * - 支持嵌套对象和数组
 */

import type { VariableFieldSchema } from '~/types/data-context';

// Props
const props = defineProps<{
  modelValue: Record<string, any>;
  fields: VariableFieldSchema[];
  disabled?: boolean;
}>();

// Emits
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void;
}>();

// 更新字段值
function updateFieldValue(fieldKey: string, value: any) {
  const newValue = { ...props.modelValue, [fieldKey]: value };
  emit('update:modelValue', newValue);
}

// 获取字段值
function getFieldValue(fieldKey: string): any {
  return props.modelValue?.[fieldKey];
}

// 获取类型图标
function getTypeIcon(type: string): string {
  const icons: Record<string, string> = {
    string: 'i-carbon-text-font',
    number: 'i-carbon-hashtag',
    boolean: 'i-carbon-toggle-off',
    color: 'i-carbon-color-palette',
    image: 'i-carbon-image',
    richtext: 'i-carbon-text-align-left',
    enum: 'i-carbon-list-checked',
    array: 'i-carbon-list',
    object: 'i-carbon-json',
  };
  return icons[type] || 'i-carbon-unknown';
}

// ImagePicker 状态
const showImagePicker = ref(false);
const currentImageField = ref<string | null>(null);

// 打开图片选择器
function openImagePicker(fieldKey: string) {
  currentImageField.value = fieldKey;
  showImagePicker.value = true;
}

// 处理图片选择
function handleImageSelect(images: any[]) {
  if (currentImageField.value && images.length > 0) {
    const image = images[0];
    const url = image.absolutionPath || image.relativePath || '';
    updateFieldValue(currentImageField.value, url);
  }
  showImagePicker.value = false;
  currentImageField.value = null;
}
</script>

<template>
  <div class="object-editor">
    <div v-if="!fields || fields.length === 0" class="empty-state">
      <span class="i-carbon-json text-2xl text-gray-600 mb-2"></span>
      <p>未定义字段结构</p>
    </div>

    <div v-else class="fields-list">
      <div v-for="field in fields" :key="field.key" class="field-item">
        <div class="field-header">
          <span :class="getTypeIcon(field.type)" class="field-icon"></span>
          <label class="field-label">
            {{ field.label }}
            <span v-if="field.required" class="required">*</span>
          </label>
        </div>

        <div class="field-content">
          <!-- 文本 -->
          <input
            v-if="field.type === 'string'"
            type="text"
            class="field-input"
            :value="getFieldValue(field.key) || ''"
            :disabled="disabled"
            :placeholder="field.description || '输入文本'"
            @input="updateFieldValue(field.key, ($event.target as HTMLInputElement).value)"
          />

          <!-- 数字 -->
          <input
            v-else-if="field.type === 'number'"
            type="number"
            class="field-input"
            :value="getFieldValue(field.key) ?? 0"
            :disabled="disabled"
            :placeholder="field.description || '输入数字'"
            @input="updateFieldValue(field.key, Number(($event.target as HTMLInputElement).value))"
          />

          <!-- 布尔 -->
          <div v-else-if="field.type === 'boolean'" class="switch-row">
            <label class="switch">
              <input
                type="checkbox"
                :checked="getFieldValue(field.key) || false"
                :disabled="disabled"
                @change="updateFieldValue(field.key, ($event.target as HTMLInputElement).checked)"
              />
              <span class="switch-slider"></span>
            </label>
            <span class="switch-status">
              {{ getFieldValue(field.key) ? '是' : '否' }}
            </span>
          </div>

          <!-- 颜色 -->
          <div v-else-if="field.type === 'color'" class="color-input">
            <input
              type="color"
              :value="getFieldValue(field.key) || '#000000'"
              :disabled="disabled"
              @input="updateFieldValue(field.key, ($event.target as HTMLInputElement).value)"
            />
            <input
              type="text"
              class="field-input"
              :value="getFieldValue(field.key) || ''"
              :disabled="disabled"
              placeholder="#000000"
              @input="updateFieldValue(field.key, ($event.target as HTMLInputElement).value)"
            />
          </div>

          <!-- 图片 -->
          <div v-else-if="field.type === 'image'" class="image-field">
            <div class="image-input-row">
              <input
                type="text"
                class="field-input"
                :value="getFieldValue(field.key) || ''"
                :disabled="disabled"
                placeholder="图片 URL"
                @input="updateFieldValue(field.key, ($event.target as HTMLInputElement).value)"
              />
              <button class="pick-btn" :disabled="disabled" @click="openImagePicker(field.key)">
                <span class="i-carbon-image-search"></span>
                选择
              </button>
            </div>
            <div v-if="getFieldValue(field.key)" class="image-preview">
              <img :src="getFieldValue(field.key)" alt="" />
            </div>
          </div>

          <!-- 富文本 -->
          <textarea
            v-else-if="field.type === 'richtext'"
            class="field-textarea"
            :value="getFieldValue(field.key) || ''"
            :disabled="disabled"
            :placeholder="field.description || '输入内容'"
            rows="4"
            @input="updateFieldValue(field.key, ($event.target as HTMLTextAreaElement).value)"
          ></textarea>

          <!-- 枚举 -->
          <select
            v-else-if="field.type === 'enum' && field.enumOptions"
            class="field-input"
            :value="getFieldValue(field.key) || ''"
            :disabled="disabled"
            @change="updateFieldValue(field.key, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">请选择</option>
            <option v-for="opt in field.enumOptions" :key="String(opt.value)" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>

          <!-- 数组（递归） -->
          <div v-else-if="field.type === 'array'" class="nested-editor">
            <ArrayEditor
              :model-value="getFieldValue(field.key) || []"
              :item-type="field.itemType"
              :item-schema="field.itemSchema"
              :disabled="disabled"
              @update:model-value="updateFieldValue(field.key, $event)"
            />
          </div>

          <!-- 对象（递归） -->
          <div v-else-if="field.type === 'object' && field.fields" class="nested-editor">
            <ObjectEditor
              :model-value="getFieldValue(field.key) || {}"
              :fields="field.fields"
              :disabled="disabled"
              @update:model-value="updateFieldValue(field.key, $event)"
            />
          </div>

          <!-- 默认文本 -->
          <input
            v-else
            type="text"
            class="field-input"
            :value="getFieldValue(field.key) || ''"
            :disabled="disabled"
            @input="updateFieldValue(field.key, ($event.target as HTMLInputElement).value)"
          />
        </div>

        <p v-if="field.description && field.type !== 'string' && field.type !== 'richtext'" class="field-desc">
          {{ field.description }}
        </p>
      </div>
    </div>

    <!-- 图片选择器 -->
    <ImagePicker
      :visible="showImagePicker"
      :multiple="false"
      @close="showImagePicker = false"
      @select="handleImageSelect"
    />
  </div>
</template>

<style scoped>
.object-editor {
  display: flex;
  flex-direction: column;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 20px;
  color: #64748b;
  text-align: center;
  background-color: #0f172a;
  border-radius: 8px;
}

/* 字段列表 */
.fields-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-icon {
  font-size: 16px;
  color: #3b82f6;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
}

.required {
  color: #ef4444;
  margin-left: 2px;
}

.field-content {
  display: flex;
  flex-direction: column;
}

.field-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #e2e8f0;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  outline: none;
  transition: border-color 0.2s;
}

.field-input:focus {
  border-color: #3b82f6;
}

.field-input::placeholder {
  color: #64748b;
}

.field-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.field-textarea {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #e2e8f0;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  outline: none;
  resize: vertical;
  min-height: 80px;
  transition: border-color 0.2s;
}

.field-textarea:focus {
  border-color: #3b82f6;
}

.field-textarea::placeholder {
  color: #64748b;
}

.field-desc {
  font-size: 12px;
  color: #64748b;
  margin: 4px 0 0;
}

/* 开关行 */
.switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.switch-status {
  font-size: 13px;
  color: #94a3b8;
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

/* 颜色输入 */
.color-input {
  display: flex;
  align-items: center;
  gap: 10px;
}

.color-input input[type="color"] {
  width: 44px;
  height: 44px;
  padding: 0;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}

.color-input .field-input {
  flex: 1;
}

/* 图片字段 */
.image-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.image-input-row {
  display: flex;
  gap: 8px;
}

.image-input-row .field-input {
  flex: 1;
}

.pick-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  font-size: 13px;
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
  border: 1px solid #3b82f6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.pick-btn:hover:not(:disabled) {
  background-color: rgba(59, 130, 246, 0.2);
}

.pick-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.image-preview {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #334155;
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 嵌套编辑器 */
.nested-editor {
  padding: 12px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
}
</style>
