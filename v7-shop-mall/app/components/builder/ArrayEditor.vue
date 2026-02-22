<script setup lang="ts">
/**
 * ArrayEditor - 数组可视化编辑器
 * 
 * 功能：
 * - 可视化列表展示
 * - 增删改数组元素
 * - 拖拽排序
 * - 支持简单类型数组和对象数组
 */

import type { VariableType, VariableFieldSchema } from '~/types/data-context';

// Props
const props = defineProps<{
  modelValue: any[];
  itemType?: VariableType;        // 简单类型数组的元素类型
  itemSchema?: VariableFieldSchema[];  // 对象数组的元素结构
  disabled?: boolean;
}>();

// Emits
const emit = defineEmits<{
  (e: 'update:modelValue', value: any[]): void;
}>();

// 内部数据（带唯一 ID 用于拖拽）
interface ArrayItemWithId {
  _id: string;
  value: any;
}

const internalItems = ref<ArrayItemWithId[]>([]);

// 同步外部值到内部
watch(
  () => props.modelValue,
  (newValue) => {
    if (!Array.isArray(newValue)) {
      internalItems.value = [];
      return;
    }
    // 保留已有的 _id，避免重新渲染
    const newItems: ArrayItemWithId[] = newValue.map((value, index) => {
      const existing = internalItems.value[index];
      if (existing && JSON.stringify(existing.value) === JSON.stringify(value)) {
        return existing;
      }
      return { _id: generateId(), value };
    });
    internalItems.value = newItems;
  },
  { immediate: true, deep: true }
);

// 同步内部值到外部
function emitUpdate() {
  const values = internalItems.value.map(item => item.value);
  emit('update:modelValue', values);
}

// 生成唯一 ID
function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

// 判断是否为对象数组
const isObjectArray = computed(() => {
  return props.itemSchema && props.itemSchema.length > 0;
});

// 获取元素类型
const elementType = computed<VariableType>(() => {
  if (isObjectArray.value) return 'object';
  return props.itemType || 'string';
});

// 添加元素
function addItem() {
  const newValue = getDefaultValue();
  internalItems.value.push({ _id: generateId(), value: newValue });
  emitUpdate();
}

// 删除元素
function removeItem(index: number) {
  internalItems.value.splice(index, 1);
  emitUpdate();
}

// 复制元素
function duplicateItem(index: number) {
  const item = internalItems.value[index];
  const newItem = { _id: generateId(), value: JSON.parse(JSON.stringify(item.value)) };
  internalItems.value.splice(index + 1, 0, newItem);
  emitUpdate();
}

// 上移元素
function moveUp(index: number) {
  if (index <= 0) return;
  const temp = internalItems.value[index];
  internalItems.value[index] = internalItems.value[index - 1];
  internalItems.value[index - 1] = temp;
  emitUpdate();
}

// 下移元素
function moveDown(index: number) {
  if (index >= internalItems.value.length - 1) return;
  const temp = internalItems.value[index];
  internalItems.value[index] = internalItems.value[index + 1];
  internalItems.value[index + 1] = temp;
  emitUpdate();
}

// 更新元素值
function updateItemValue(index: number, value: any) {
  internalItems.value[index].value = value;
  emitUpdate();
}

// 更新对象元素的字段值
function updateFieldValue(index: number, fieldKey: string, value: any) {
  if (!internalItems.value[index].value || typeof internalItems.value[index].value !== 'object') {
    internalItems.value[index].value = {};
  }
  internalItems.value[index].value[fieldKey] = value;
  emitUpdate();
}

// 获取默认值
function getDefaultValue(): any {
  if (isObjectArray.value && props.itemSchema) {
    const obj: Record<string, any> = {};
    for (const field of props.itemSchema) {
      obj[field.key] = getFieldDefaultValue(field.type);
    }
    return obj;
  }
  return getFieldDefaultValue(elementType.value);
}

// 根据类型获取默认值
function getFieldDefaultValue(type: VariableType): any {
  switch (type) {
    case 'string':
    case 'image':
    case 'richtext':
    case 'color':
      return '';
    case 'number':
      return 0;
    case 'boolean':
      return false;
    case 'array':
      return [];
    case 'object':
      return {};
    case 'enum':
      return '';
    default:
      return '';
  }
}

// 获取元素显示标题
function getItemTitle(item: ArrayItemWithId, index: number): string {
  if (isObjectArray.value && props.itemSchema) {
    // 尝试用第一个字段的值作为标题
    const firstField = props.itemSchema[0];
    if (firstField && item.value && item.value[firstField.key]) {
      const val = item.value[firstField.key];
      if (typeof val === 'string' && val.length > 0) {
        return val.length > 30 ? val.substring(0, 30) + '...' : val;
      }
    }
  }
  return `第 ${index + 1} 项`;
}

// 展开/折叠状态
const expandedItems = ref<Set<string>>(new Set());

function toggleExpand(id: string) {
  if (expandedItems.value.has(id)) {
    expandedItems.value.delete(id);
  } else {
    expandedItems.value.add(id);
  }
}

function isExpanded(id: string): boolean {
  return expandedItems.value.has(id);
}

// 全部展开/折叠
function expandAll() {
  internalItems.value.forEach(item => expandedItems.value.add(item._id));
}

function collapseAll() {
  expandedItems.value.clear();
}
</script>

<template>
  <div class="array-editor">
    <!-- 工具栏 -->
    <div class="editor-toolbar">
      <span class="item-count">共 {{ internalItems.length }} 项</span>
      <div class="toolbar-actions">
        <button v-if="isObjectArray && internalItems.length > 0" class="toolbar-btn" @click="expandAll">
          <span class="i-carbon-expand-all"></span>
        </button>
        <button v-if="isObjectArray && internalItems.length > 0" class="toolbar-btn" @click="collapseAll">
          <span class="i-carbon-collapse-all"></span>
        </button>
        <button class="toolbar-btn add" :disabled="disabled" @click="addItem">
          <span class="i-carbon-add"></span>
          添加
        </button>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="internalItems.length === 0" class="empty-state">
      <span class="i-carbon-list text-2xl text-gray-600 mb-2"></span>
      <p>暂无数据</p>
      <button class="add-first-btn" :disabled="disabled" @click="addItem">
        <span class="i-carbon-add"></span>
        添加第一项
      </button>
    </div>

    <!-- 数组列表 -->
    <div v-else class="items-list">
      <div
        v-for="(item, index) in internalItems"
        :key="item._id"
        class="array-item"
        :class="{ expanded: isExpanded(item._id) }"
      >
        <!-- 项目头部 -->
        <div class="item-header" @click="isObjectArray && toggleExpand(item._id)">
          <div class="item-handle">
            <span class="i-carbon-draggable text-gray-500"></span>
          </div>
          <span v-if="isObjectArray" class="expand-icon">
            <span :class="isExpanded(item._id) ? 'i-carbon-chevron-down' : 'i-carbon-chevron-right'"></span>
          </span>
          <span class="item-title">{{ getItemTitle(item, index) }}</span>
          <div class="item-actions">
            <button class="action-btn" title="上移" :disabled="disabled || index === 0" @click.stop="moveUp(index)">
              <span class="i-carbon-arrow-up"></span>
            </button>
            <button class="action-btn" title="下移" :disabled="disabled || index === internalItems.length - 1" @click.stop="moveDown(index)">
              <span class="i-carbon-arrow-down"></span>
            </button>
            <button class="action-btn" title="复制" :disabled="disabled" @click.stop="duplicateItem(index)">
              <span class="i-carbon-copy"></span>
            </button>
            <button class="action-btn danger" title="删除" :disabled="disabled" @click.stop="removeItem(index)">
              <span class="i-carbon-trash-can"></span>
            </button>
          </div>
        </div>

        <!-- 项目内容 -->
        <div v-if="!isObjectArray || isExpanded(item._id)" class="item-content">
          <!-- 对象数组：渲染字段 -->
          <template v-if="isObjectArray && itemSchema">
            <div v-for="field in itemSchema" :key="field.key" class="field-row">
              <label class="field-label">{{ field.label }}</label>
              
              <!-- 文本 -->
              <input
                v-if="field.type === 'string'"
                type="text"
                class="field-input"
                :value="item.value?.[field.key] || ''"
                :disabled="disabled"
                @input="updateFieldValue(index, field.key, ($event.target as HTMLInputElement).value)"
              />

              <!-- 数字 -->
              <input
                v-else-if="field.type === 'number'"
                type="number"
                class="field-input"
                :value="item.value?.[field.key] || 0"
                :disabled="disabled"
                @input="updateFieldValue(index, field.key, Number(($event.target as HTMLInputElement).value))"
              />

              <!-- 布尔 -->
              <label v-else-if="field.type === 'boolean'" class="switch">
                <input
                  type="checkbox"
                  :checked="item.value?.[field.key] || false"
                  :disabled="disabled"
                  @change="updateFieldValue(index, field.key, ($event.target as HTMLInputElement).checked)"
                />
                <span class="switch-slider"></span>
              </label>

              <!-- 颜色 -->
              <div v-else-if="field.type === 'color'" class="color-input">
                <input
                  type="color"
                  :value="item.value?.[field.key] || '#000000'"
                  :disabled="disabled"
                  @input="updateFieldValue(index, field.key, ($event.target as HTMLInputElement).value)"
                />
                <input
                  type="text"
                  class="field-input"
                  :value="item.value?.[field.key] || ''"
                  :disabled="disabled"
                  placeholder="#000000"
                  @input="updateFieldValue(index, field.key, ($event.target as HTMLInputElement).value)"
                />
              </div>

              <!-- 图片 -->
              <div v-else-if="field.type === 'image'" class="image-field">
                <input
                  type="text"
                  class="field-input"
                  :value="item.value?.[field.key] || ''"
                  :disabled="disabled"
                  placeholder="图片 URL"
                  @input="updateFieldValue(index, field.key, ($event.target as HTMLInputElement).value)"
                />
                <div v-if="item.value?.[field.key]" class="image-preview-small">
                  <img :src="item.value[field.key]" alt="" />
                </div>
              </div>

              <!-- 富文本 -->
              <textarea
                v-else-if="field.type === 'richtext'"
                class="field-textarea"
                :value="item.value?.[field.key] || ''"
                :disabled="disabled"
                rows="3"
                @input="updateFieldValue(index, field.key, ($event.target as HTMLTextAreaElement).value)"
              ></textarea>

              <!-- 枚举 -->
              <select
                v-else-if="field.type === 'enum' && field.enumOptions"
                class="field-input"
                :value="item.value?.[field.key] || ''"
                :disabled="disabled"
                @change="updateFieldValue(index, field.key, ($event.target as HTMLSelectElement).value)"
              >
                <option value="">请选择</option>
                <option v-for="opt in field.enumOptions" :key="String(opt.value)" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>

              <!-- 默认文本 -->
              <input
                v-else
                type="text"
                class="field-input"
                :value="item.value?.[field.key] || ''"
                :disabled="disabled"
                @input="updateFieldValue(index, field.key, ($event.target as HTMLInputElement).value)"
              />
            </div>
          </template>

          <!-- 简单类型数组：单个输入 -->
          <template v-else>
            <!-- 文本 -->
            <input
              v-if="elementType === 'string'"
              type="text"
              class="field-input full"
              :value="item.value || ''"
              :disabled="disabled"
              @input="updateItemValue(index, ($event.target as HTMLInputElement).value)"
            />

            <!-- 数字 -->
            <input
              v-else-if="elementType === 'number'"
              type="number"
              class="field-input full"
              :value="item.value || 0"
              :disabled="disabled"
              @input="updateItemValue(index, Number(($event.target as HTMLInputElement).value))"
            />

            <!-- 布尔 -->
            <label v-else-if="elementType === 'boolean'" class="switch">
              <input
                type="checkbox"
                :checked="item.value || false"
                :disabled="disabled"
                @change="updateItemValue(index, ($event.target as HTMLInputElement).checked)"
              />
              <span class="switch-slider"></span>
            </label>

            <!-- 颜色 -->
            <div v-else-if="elementType === 'color'" class="color-input">
              <input
                type="color"
                :value="item.value || '#000000'"
                :disabled="disabled"
                @input="updateItemValue(index, ($event.target as HTMLInputElement).value)"
              />
              <input
                type="text"
                class="field-input"
                :value="item.value || ''"
                :disabled="disabled"
                placeholder="#000000"
                @input="updateItemValue(index, ($event.target as HTMLInputElement).value)"
              />
            </div>

            <!-- 图片 -->
            <div v-else-if="elementType === 'image'" class="image-field full">
              <input
                type="text"
                class="field-input"
                :value="item.value || ''"
                :disabled="disabled"
                placeholder="图片 URL"
                @input="updateItemValue(index, ($event.target as HTMLInputElement).value)"
              />
              <div v-if="item.value" class="image-preview-small">
                <img :src="item.value" alt="" />
              </div>
            </div>

            <!-- 富文本 -->
            <textarea
              v-else-if="elementType === 'richtext'"
              class="field-textarea full"
              :value="item.value || ''"
              :disabled="disabled"
              rows="3"
              @input="updateItemValue(index, ($event.target as HTMLTextAreaElement).value)"
            ></textarea>

            <!-- 默认文本 -->
            <input
              v-else
              type="text"
              class="field-input full"
              :value="item.value || ''"
              :disabled="disabled"
              @input="updateItemValue(index, ($event.target as HTMLInputElement).value)"
            />
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.array-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 工具栏 */
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background-color: #0f172a;
  border-radius: 6px;
}

.item-count {
  font-size: 12px;
  color: #64748b;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  font-size: 12px;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.toolbar-btn:hover:not(:disabled) {
  color: #e2e8f0;
  background-color: #334155;
}

.toolbar-btn.add {
  color: #3b82f6;
}

.toolbar-btn.add:hover:not(:disabled) {
  background-color: rgba(59, 130, 246, 0.1);
}

.toolbar-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px;
  color: #64748b;
  text-align: center;
}

.add-first-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 16px;
  font-size: 13px;
  color: #3b82f6;
  background: none;
  border: 1px dashed #3b82f6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-first-btn:hover:not(:disabled) {
  background-color: rgba(59, 130, 246, 0.1);
}

/* 列表 */
.items-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.array-item {
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  overflow: hidden;
}

.array-item.expanded {
  border-color: #3b82f6;
}

/* 项目头部 */
.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.item-header:hover {
  background-color: #1e293b;
}

.item-handle {
  cursor: grab;
}

.expand-icon {
  color: #64748b;
  font-size: 14px;
}

.item-title {
  flex: 1;
  font-size: 13px;
  color: #e2e8f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-actions {
  display: flex;
  gap: 2px;
}

.action-btn {
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
}

.action-btn:hover:not(:disabled) {
  color: #e2e8f0;
  background-color: #334155;
}

.action-btn.danger:hover:not(:disabled) {
  color: #ef4444;
  background-color: rgba(239, 68, 68, 0.1);
}

.action-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* 项目内容 */
.item-content {
  padding: 12px;
  border-top: 1px solid #334155;
  background-color: #1e293b;
}

/* 字段行 */
.field-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.field-row:last-child {
  margin-bottom: 0;
}

.field-label {
  font-size: 12px;
  color: #94a3b8;
}

.field-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
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

.field-input.full {
  margin-top: 0;
}

.field-textarea {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  color: #e2e8f0;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  outline: none;
  resize: vertical;
  min-height: 60px;
}

.field-textarea:focus {
  border-color: #3b82f6;
}

/* 颜色输入 */
.color-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-input input[type="color"] {
  width: 36px;
  height: 36px;
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
  gap: 8px;
}

.image-field.full .field-input {
  width: 100%;
}

.image-preview-small {
  width: 60px;
  height: 60px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #334155;
}

.image-preview-small img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
</style>
