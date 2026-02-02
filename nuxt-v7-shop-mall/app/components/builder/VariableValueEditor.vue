<script setup lang="ts">
/**
 * 变量值设置组件
 * 用于编辑站点配置和用户自定义变量的实际值
 * 
 * Tab 1: 全局配置 - 根据 SITE_CONFIG_SCHEMA 渲染表单
 * Tab 2: 用户变量 - 使用 VariableInput 组件渲染值编辑器
 * 
 * 支持所有变量类型的可视化输入：
 * - string, number, boolean, color
 * - image（支持从资源库选择）
 * - richtext（支持富文本编辑）
 * - enum（下拉选择）
 * - array（可视化列表编辑）
 * - object（结构化表单编辑）
 */

import type { CustomVariable } from "~/types/data-context";
import {
  SITE_CONFIG_SCHEMA,
  SITE_CONFIG_GROUPS,
  type SiteFieldSchema,
} from "~/constants/site-config.schema";

// 获取图片 URL 构建函数
const { buildImageUrl } = useIframeAuth();

// Props
defineProps<{
  visible: boolean;
}>();

// Emits
const emit = defineEmits<{
  (e: "close"): void;
}>();

// 主题状态
const {
  variableSchema,
  siteConfig,
  variableValues,
  updateSiteConfig,
  updateVariableValue,
} = useThemeSchema();

// 当前 Tab
const activeTab = ref<"site" | "variables">("site");

// 当前选中的站点配置分组
const activeSiteGroup = ref<string>("basic");

// 获取分组的字段
function getGroupFields(groupKey: string): SiteFieldSchema[] {
  return SITE_CONFIG_SCHEMA.filter((field) => field.group === groupKey);
}

// 获取站点配置值
function getSiteValue(key: string): any {
  return siteConfig.value[key] ?? "";
}

// 获取变量值
function getVarValue(key: string): any {
  return variableValues.value[key] ?? "";
}

// 更新站点配置
function handleSiteConfigChange(key: string, value: any) {
  updateSiteConfig(key, value);
}

// 更新变量值
function handleVariableChange(key: string, value: any) {
  updateVariableValue(key, value);
}

// 获取变量类型图标
function getTypeIcon(type: string): string {
  const icons: Record<string, string> = {
    string: "i-carbon-text-font",
    number: "i-carbon-hashtag",
    boolean: "i-carbon-toggle-off",
    color: "i-carbon-color-palette",
    image: "i-carbon-image",
    richtext: "i-carbon-text-align-left",
    enum: "i-carbon-list-checked",
    array: "i-carbon-list",
    object: "i-carbon-json",
  };
  return icons[type] || "i-carbon-unknown";
}

// 关闭弹窗
function handleClose() {
  emit("close");
}

// ImagePicker 状态（站点配置图片字段使用）
const showImagePicker = ref(false);
const currentImageField = ref<string | null>(null);

// 打开图片选择器
function openImagePicker(fieldKey: string) {
  currentImageField.value = fieldKey;
  showImagePicker.value = true;
}

// 处理图片选择 - 只保存 relativePath，渲染时再拼接 imageBaseUrl
function handleImageSelect(images: any[]) {
  if (currentImageField.value && images.length > 0) {
    const image = images[0];
    const url = image.relativePath || '';
    handleSiteConfigChange(currentImageField.value, url);
  }
  showImagePicker.value = false;
  currentImageField.value = null;
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="handleClose">
        <div class="value-editor">
          <!-- 头部 -->
          <div class="editor-header">
            <h3 class="editor-title">
              <span class="i-carbon-settings-adjust"></span>
              变量值设置
            </h3>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- Tab 切换 -->
          <div class="tab-bar">
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'site' }"
              @click="activeTab = 'site'"
            >
              <span class="i-carbon-globe"></span>
              全局配置
            </button>
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'variables' }"
              @click="activeTab = 'variables'"
            >
              <span class="i-carbon-data-vis-4"></span>
              用户变量
              <span v-if="variableSchema.length > 0" class="badge">
                {{ variableSchema.length }}
              </span>
            </button>
          </div>

          <!-- 内容区 -->
          <div class="editor-content">
            <!-- 全局配置 Tab - 左侧垂直 Tab 布局 -->
            <div v-if="activeTab === 'site'" class="site-config-panel">
              <!-- 左侧分组 Tab -->
              <div class="site-group-tabs">
                <button
                  v-for="group in SITE_CONFIG_GROUPS"
                  :key="group.key"
                  class="site-group-tab"
                  :class="{ active: activeSiteGroup === group.key }"
                  @click="activeSiteGroup = group.key"
                >
                  <span :class="group.icon || 'i-carbon-folder'"></span>
                  <span class="tab-label">{{ group.label }}</span>
                </button>
              </div>

              <!-- 右侧字段内容 -->
              <div class="site-group-content">
                <div
                  v-for="field in getGroupFields(activeSiteGroup)"
                  :key="field.key"
                  class="field-row"
                >
                  <label class="field-label">
                    {{ field.label }}
                    <span v-if="field.i18n" class="i18n-badge">多语言</span>
                  </label>

                  <!-- 文本输入 -->
                  <input
                    v-if="field.type === 'text'"
                    type="text"
                    class="field-input"
                    :value="getSiteValue(field.key)"
                    :placeholder="field.placeholder"
                    @input="handleSiteConfigChange(field.key, ($event.target as HTMLInputElement).value)"
                  />

                  <!-- 多行文本 -->
                  <textarea
                    v-else-if="field.type === 'textarea'"
                    class="field-textarea"
                    :value="getSiteValue(field.key)"
                    :placeholder="field.placeholder"
                    rows="3"
                    @input="handleSiteConfigChange(field.key, ($event.target as HTMLTextAreaElement).value)"
                  ></textarea>

                  <!-- 图片（增强：支持选择图片） -->
                  <div v-else-if="field.type === 'image'" class="image-input">
                    <div class="image-input-row">
                      <input
                        type="text"
                        class="field-input"
                        :value="getSiteValue(field.key)"
                        :placeholder="field.placeholder || '输入图片 URL'"
                        @input="handleSiteConfigChange(field.key, ($event.target as HTMLInputElement).value)"
                      />
                      <button class="pick-btn" @click="openImagePicker(field.key)">
                        <span class="i-carbon-image-search"></span>
                        选择
                      </button>
                    </div>
                    <div
                      v-if="getSiteValue(field.key)"
                      class="image-preview"
                    >
                      <img :src="buildImageUrl(getSiteValue(field.key))" alt="预览" />
                    </div>
                  </div>

                  <!-- JSON -->
                  <textarea
                    v-else-if="field.type === 'json'"
                    class="field-textarea json-input"
                    :value="JSON.stringify(getSiteValue(field.key) || [], null, 2)"
                    :placeholder="field.description"
                    rows="4"
                    @input="
                      try {
                        handleSiteConfigChange(field.key, JSON.parse(($event.target as HTMLTextAreaElement).value));
                      } catch {}
                    "
                  ></textarea>

                  <!-- 开关 (switch) -->
                  <div v-else-if="field.type === 'switch'" class="switch-row">
                    <label class="switch">
                      <input
                        type="checkbox"
                        :checked="getSiteValue(field.key) ?? field.defaultValue"
                        @change="handleSiteConfigChange(field.key, ($event.target as HTMLInputElement).checked)"
                      />
                      <span class="switch-slider"></span>
                    </label>
                    <span class="switch-status">
                      {{ (getSiteValue(field.key) ?? field.defaultValue) ? '已启用' : '已禁用' }}
                    </span>
                  </div>

                  <!-- 描述 -->
                  <p v-if="field.description" class="field-desc">
                    {{ field.description }}
                  </p>
                </div>

                <!-- 空状态 -->
                <div
                  v-if="getGroupFields(activeSiteGroup).length === 0"
                  class="empty-group"
                >
                  暂无配置项
                </div>
              </div>
            </div>

            <!-- 用户变量 Tab（使用新的 VariableInput 组件） -->
            <div v-else-if="activeTab === 'variables'" class="variables-panel">
              <!-- 变量列表 -->
              <div v-if="variableSchema.length > 0" class="variables-list">
                <div
                  v-for="variable in variableSchema"
                  :key="variable.key"
                  class="variable-item"
                >
                  <div class="variable-header">
                    <span :class="getTypeIcon(variable.type)" class="type-icon"></span>
                    <div class="variable-info">
                      <span class="variable-label">{{ variable.label }}</span>
                      <span class="variable-key">{{ variable.key }}</span>
                    </div>
                    <span v-if="variable.i18n" class="i18n-badge">多语言</span>
                  </div>

                  <!-- 使用 VariableInput 组件渲染输入 -->
                  <div class="variable-value">
                    <VariableInput
                      :variable="variable"
                      :model-value="getVarValue(variable.key)"
                      @update:model-value="handleVariableChange(variable.key, $event)"
                    />
                  </div>

                  <p v-if="variable.description" class="variable-desc">
                    {{ variable.description }}
                  </p>
                </div>
              </div>

              <!-- 空状态 -->
              <div v-else class="empty-state">
                <span class="i-carbon-data-vis-4 empty-icon"></span>
                <p class="empty-title">暂无自定义变量</p>
                <p class="empty-desc">请先在"变量管理"中定义变量</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 图片选择器（站点配置使用） -->
    <ImagePicker
      :visible="showImagePicker"
      :multiple="false"
      @close="showImagePicker = false"
      @select="handleImageSelect"
    />
  </Teleport>
</template>

<style scoped>
/* 弹窗遮罩 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

/* 编辑器容器 */
.value-editor {
  width: 100%;
  max-width: 900px;
  max-height: 85vh;
  background-color: #1e293b;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 头部 */
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #334155;
  flex-shrink: 0;
}

.editor-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #f1f5f9;
}

.editor-title span {
  font-size: 22px;
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
  color: #f1f5f9;
  background-color: #334155;
}

.close-btn span {
  font-size: 20px;
}

/* Tab 栏 */
.tab-bar {
  display: flex;
  gap: 4px;
  padding: 12px 20px;
  border-bottom: 1px solid #334155;
  flex-shrink: 0;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 500;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #e2e8f0;
  background-color: #334155;
}

.tab-btn.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

.tab-btn span:first-child {
  font-size: 18px;
}

.badge {
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.2);
  border-radius: 10px;
}

/* 内容区 */
.editor-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 20px;
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.editor-content::-webkit-scrollbar {
  width: 8px;
}

.editor-content::-webkit-scrollbar-track {
  background: transparent;
}

.editor-content::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.editor-content::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

/* 站点配置面板 - 左右布局 */
.site-config-panel {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

/* 左侧分组 Tab */
.site-group-tabs {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 140px;
  flex-shrink: 0;
  padding-right: 12px;
  border-right: 1px solid #334155;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.site-group-tabs::-webkit-scrollbar {
  width: 6px;
}

.site-group-tabs::-webkit-scrollbar-track {
  background: transparent;
}

.site-group-tabs::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 3px;
}

.site-group-tabs::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

.site-group-tab {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.site-group-tab:hover {
  color: #e2e8f0;
  background-color: #334155;
}

.site-group-tab.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

.site-group-tab span:first-child {
  font-size: 18px;
  flex-shrink: 0;
}

.tab-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 右侧字段内容 */
.site-group-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding-right: 8px;
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.site-group-content::-webkit-scrollbar {
  width: 8px;
}

.site-group-content::-webkit-scrollbar-track {
  background: transparent;
}

.site-group-content::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.site-group-content::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

/* 字段行 */
.field-row {
  margin-bottom: 16px;
}

.field-row:last-child {
  margin-bottom: 0;
}

.field-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
}

.i18n-badge {
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 600;
  color: #22c55e;
  background-color: rgba(34, 197, 94, 0.15);
  border-radius: 4px;
}

.field-input,
.field-textarea,
.field-select {
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

.field-input:focus,
.field-textarea:focus,
.field-select:focus {
  border-color: #3b82f6;
}

.field-input::placeholder,
.field-textarea::placeholder {
  color: #64748b;
}

.field-textarea {
  resize: vertical;
  min-height: 80px;
}

.json-input {
  font-family: "Fira Code", "Monaco", monospace;
  font-size: 12px;
}

.field-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: #64748b;
}

/* 图片输入（增强） */
.image-input {
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

.pick-btn:hover {
  background-color: rgba(59, 130, 246, 0.2);
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

/* 变量列表 */
.variables-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.variable-item {
  padding: 16px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
}

.variable-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.type-icon {
  font-size: 20px;
  color: #3b82f6;
}

.variable-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.variable-label {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.variable-key {
  font-size: 12px;
  color: #64748b;
  font-family: "Fira Code", monospace;
}

.variable-value {
  margin-top: 8px;
}

.variable-desc {
  margin: 12px 0 0;
  font-size: 12px;
  color: #64748b;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: #475569;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #94a3b8;
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.empty-group {
  padding: 20px;
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

/* 用户变量面板 */
.variables-panel {
  height: 100%;
  overflow-y: auto;
  padding-right: 8px;
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.variables-panel::-webkit-scrollbar {
  width: 8px;
}

.variables-panel::-webkit-scrollbar-track {
  background: transparent;
}

.variables-panel::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.variables-panel::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

/* 弹窗动画 */
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
