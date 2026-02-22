<script setup lang="ts">
/**
 * VariableInput - 通用变量输入组件
 * 
 * 根据变量 schema 自动渲染对应类型的输入控件
 * 支持所有变量类型：string, number, boolean, color, image, richtext, enum, array, object
 */

import type { CustomVariable, VariableType } from '~/types/data-context';
import { useIframeAuth } from '~/composables/useIframeAuth';

// 获取图片 URL 构建函数
const { buildImageUrl } = useIframeAuth();

// Props
const props = defineProps<{
  variable: CustomVariable;        // 变量定义
  modelValue: any;                 // 当前值
  disabled?: boolean;
}>();

// Emits
const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void;
}>();

// 更新值
function updateValue(value: any) {
  emit('update:modelValue', value);
}

// 获取类型图标
function getTypeIcon(type: VariableType): string {
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

// 打开图片选择器
function openImagePicker() {
  showImagePicker.value = true;
}

// 处理图片选择 - 只保存 relativePath，渲染时再拼接 imageBaseUrl
function handleImageSelect(images: any[]) {
  if (images.length > 0) {
    const image = images[0];
    const url = image.relativePath || '';
    updateValue(url);
  }
  showImagePicker.value = false;
}

// 处理多图选择（用于图片数组）- 只保存 relativePath
function handleMultiImageSelect(images: any[]) {
  const urls = images.map(img => img.relativePath || '').filter(Boolean);
  if (Array.isArray(props.modelValue)) {
    updateValue([...props.modelValue, ...urls]);
  } else {
    updateValue(urls);
  }
  showImagePicker.value = false;
}

// 判断是否为图片数组
const isImageArray = computed(() => {
  return props.variable.type === 'array' && props.variable.itemType === 'image';
});

// 预设颜色板
const presetColors = [
  '#ef4444', '#f97316', '#f59e0b', '#eab308', '#84cc16',
  '#22c55e', '#10b981', '#14b8a6', '#06b6d4', '#0ea5e9',
  '#3b82f6', '#6366f1', '#8b5cf6', '#a855f7', '#d946ef',
  '#ec4899', '#f43f5e', '#000000', '#ffffff', '#6b7280',
];
</script>

<template>
  <div class="variable-input">
    <!-- 文本输入 -->
    <input
      v-if="variable.type === 'string'"
      type="text"
      class="input-text"
      :value="modelValue || ''"
      :disabled="disabled"
      placeholder="输入文本"
      @input="updateValue(($event.target as HTMLInputElement).value)"
    />

    <!-- 数字输入 -->
    <input
      v-else-if="variable.type === 'number'"
      type="number"
      class="input-text"
      :value="modelValue ?? 0"
      :disabled="disabled"
      placeholder="输入数字"
      @input="updateValue(Number(($event.target as HTMLInputElement).value))"
    />

    <!-- 布尔开关 -->
    <div v-else-if="variable.type === 'boolean'" class="switch-row">
      <label class="switch">
        <input
          type="checkbox"
          :checked="modelValue || false"
          :disabled="disabled"
          @change="updateValue(($event.target as HTMLInputElement).checked)"
        />
        <span class="switch-slider"></span>
      </label>
      <span class="switch-status">{{ modelValue ? '是' : '否' }}</span>
    </div>

    <!-- 颜色选择 -->
    <div v-else-if="variable.type === 'color'" class="color-section">
      <div class="color-input">
        <input
          type="color"
          :value="modelValue || '#000000'"
          :disabled="disabled"
          @input="updateValue(($event.target as HTMLInputElement).value)"
        />
        <input
          type="text"
          class="input-text"
          :value="modelValue || ''"
          :disabled="disabled"
          placeholder="#000000"
          @input="updateValue(($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="color-presets">
        <button
          v-for="color in presetColors"
          :key="color"
          class="preset-color"
          :style="{ backgroundColor: color }"
          :class="{ active: modelValue === color }"
          :disabled="disabled"
          @click="updateValue(color)"
        ></button>
      </div>
    </div>

    <!-- 图片选择 -->
    <div v-else-if="variable.type === 'image'" class="image-section">
      <div class="image-input-row">
        <input
          type="text"
          class="input-text"
          :value="modelValue || ''"
          :disabled="disabled"
          placeholder="图片 URL"
          @input="updateValue(($event.target as HTMLInputElement).value)"
        />
        <button class="pick-btn" :disabled="disabled" @click="openImagePicker">
          <span class="i-carbon-image-search"></span>
          选择图片
        </button>
      </div>
      <div v-if="modelValue" class="image-preview">
        <img :src="buildImageUrl(modelValue)" alt="预览" />
        <button class="clear-image" :disabled="disabled" @click="updateValue('')">
          <span class="i-carbon-close"></span>
        </button>
      </div>
    </div>

    <!-- 富文本 -->
    <div v-else-if="variable.type === 'richtext'" class="richtext-section">
      <textarea
        class="input-textarea"
        :value="modelValue || ''"
        :disabled="disabled"
        placeholder="输入内容"
        rows="5"
        @input="updateValue(($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <div class="richtext-hint">
        <span class="i-carbon-information"></span>
        支持 HTML 格式
      </div>
    </div>

    <!-- 枚举选择 -->
    <select
      v-else-if="variable.type === 'enum' && variable.enumOptions"
      class="input-select"
      :value="modelValue || ''"
      :disabled="disabled"
      @change="updateValue(($event.target as HTMLSelectElement).value)"
    >
      <option value="">请选择</option>
      <option v-for="opt in variable.enumOptions" :key="String(opt.value)" :value="opt.value">
        {{ opt.label }}
      </option>
    </select>

    <!-- 数组编辑 -->
    <div v-else-if="variable.type === 'array'" class="array-section">
      <!-- 图片数组特殊处理 -->
      <div v-if="isImageArray" class="image-array">
        <div class="image-array-toolbar">
          <button class="add-image-btn" :disabled="disabled" @click="showImagePicker = true">
            <span class="i-carbon-add"></span>
            添加图片
          </button>
        </div>
        <div v-if="modelValue && modelValue.length > 0" class="image-array-grid">
          <div
            v-for="(url, index) in modelValue"
            :key="index"
            class="image-array-item"
          >
            <img :src="buildImageUrl(url)" alt="" />
            <button
              class="remove-image"
              :disabled="disabled"
              @click="updateValue(modelValue.filter((_: any, i: number) => i !== index))"
            >
              <span class="i-carbon-close"></span>
            </button>
          </div>
        </div>
        <div v-else class="empty-images">
          暂无图片
        </div>
      </div>

      <!-- 通用数组编辑器 -->
      <BuilderArrayEditor
        v-else
        :model-value="modelValue || []"
        :item-type="variable.itemType"
        :item-schema="variable.itemSchema"
        :disabled="disabled"
        @update:model-value="updateValue($event)"
      />
    </div>

    <!-- 对象编辑 -->
    <div v-else-if="variable.type === 'object' && variable.fields" class="object-section">
      <BuilderObjectEditor
        :model-value="modelValue || {}"
        :fields="variable.fields"
        :disabled="disabled"
        @update:model-value="updateValue($event)"
      />
    </div>

    <!-- 未知类型：默认文本输入 -->
    <input
      v-else
      type="text"
      class="input-text"
      :value="modelValue || ''"
      :disabled="disabled"
      @input="updateValue(($event.target as HTMLInputElement).value)"
    />

    <!-- 图片选择器弹窗 -->
    <BuilderImagePicker
      :visible="showImagePicker"
      :multiple="isImageArray"
      :max-count="isImageArray ? 20 : 1"
      @close="showImagePicker = false"
      @select="isImageArray ? handleMultiImageSelect($event) : handleImageSelect($event)"
    />
  </div>
</template>

<style scoped>
.variable-input {
  width: 100%;
}

/* 文本输入 */
.input-text {
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

.input-text:focus {
  border-color: #3b82f6;
}

.input-text::placeholder {
  color: #64748b;
}

.input-text:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 文本域 */
.input-textarea {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #e2e8f0;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  outline: none;
  resize: vertical;
  min-height: 100px;
  transition: border-color 0.2s;
}

.input-textarea:focus {
  border-color: #3b82f6;
}

.input-textarea::placeholder {
  color: #64748b;
}

/* 下拉选择 */
.input-select {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #e2e8f0;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  outline: none;
  cursor: pointer;
  transition: border-color 0.2s;
}

.input-select:focus {
  border-color: #3b82f6;
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

/* 颜色区域 */
.color-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

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

.color-input .input-text {
  flex: 1;
}

.color-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preset-color {
  width: 24px;
  height: 24px;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.preset-color:hover {
  transform: scale(1.1);
}

.preset-color.active {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3);
}

.preset-color:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 图片区域 */
.image-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.image-input-row {
  display: flex;
  gap: 8px;
}

.image-input-row .input-text {
  flex: 1;
}

.pick-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
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
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #334155;
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.clear-image {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: white;
  background-color: rgba(0, 0, 0, 0.6);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-image:hover {
  background-color: #ef4444;
}

/* 富文本区域 */
.richtext-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.richtext-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

/* 数组区域 */
.array-section {
  width: 100%;
}

/* 图片数组 */
.image-array {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.image-array-toolbar {
  display: flex;
  justify-content: flex-start;
}

.add-image-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  color: #3b82f6;
  background: none;
  border: 1px dashed #3b82f6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-image-btn:hover:not(:disabled) {
  background-color: rgba(59, 130, 246, 0.1);
}

.image-array-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 10px;
}

.image-array-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #334155;
}

.image-array-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-image {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 12px;
  color: white;
  background-color: rgba(0, 0, 0, 0.6);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
}

.image-array-item:hover .remove-image {
  opacity: 1;
}

.remove-image:hover {
  background-color: #ef4444;
}

.empty-images {
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: #64748b;
  background-color: #0f172a;
  border: 1px dashed #334155;
  border-radius: 6px;
}

/* 对象区域 */
.object-section {
  width: 100%;
  padding: 12px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
}
</style>
