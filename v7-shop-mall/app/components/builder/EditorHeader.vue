<script setup lang="ts">
/**
 * EditorHeader - 主题编辑器标题组件
 *
 * 提供编辑器顶部标题区，包含：
 * - 关闭按钮
 * - 主题名称和上下文信息
 * - 未保存标识
 * - 右侧操作按钮（模板、变量管理、变量值、保存等）
 */

import { useIframeAuth } from "@/composables/base/useIframeAuth";
import TemplateSelectModal from "./TemplateSelectModal.vue";
import VariableManager from "./VariableManager.vue";

defineOptions({
  name: "EditorHeader",
});

// 从认证中获取上下文信息
const { contextName, isTemplateMode } = useIframeAuth();

// 主题名称（暂存）
const themeName = ref("未命名主题");

// 未保存状态（暂存）
const hasUnsavedChanges = ref(false);

// 保存状态（暂存）
const isSaving = ref(false);

// 保存消息（暂存）
const saveMessage = ref<{ type: string; text: string } | null>(null);

// 模板选择弹窗状态
const showTemplateSelect = ref(false);

// 变量管理弹窗状态
const showVariableManager = ref(false);

// 关闭事件（暂不实现）
function handleClose() {
  console.log("Close editor");
}

// 保存事件（暂不实现）
function handleSave() {
  console.log("Save");
}

// 应用模板事件（暂不实现）
function handleApplyTemplate(data: {
  themeConfig: any;
  variableSchema: any[];
  siteConfig: any;
  variableValues: any;
}) {
  console.log("Apply template:", data);
  showTemplateSelect.value = false;
  saveMessage.value = { type: "success", text: "模板应用成功" };
  setTimeout(() => (saveMessage.value = null), 3000);
}

// 显示模板选择
function openTemplateSelect() {
  showTemplateSelect.value = true;
}

// 显示变量管理
function openVariableManager() {
  showVariableManager.value = true;
}

// 显示变量值编辑（暂不实现）

// 显示变量值编辑（暂不实现）
function showVariableValueEditor() {
  console.log("Show variable value editor");
}
</script>

<template>
  <header class="editor-header">
    <!-- 左侧：关闭按钮 + 标题信息 -->
    <div class="header-left">
      <button class="close-btn" title="关闭编辑器" @click="handleClose">
        <span class="i-carbon-close"></span>
      </button>
      <h1 class="theme-name">{{ themeName }}</h1>
      <span v-if="contextName" class="context-info">{{ contextName }}</span>
      <span v-if="hasUnsavedChanges" class="unsaved-badge">未保存</span>
    </div>

    <!-- 中间：预留 -->
    <div class="header-center"></div>

    <!-- 右侧：操作按钮 -->
    <div class="header-right">
      <!-- 保存状态消息 -->
      <Transition name="fade">
        <span v-if="saveMessage" class="save-message" :class="saveMessage.type">
          {{ saveMessage.text }}
        </span>
      </Transition>

      <button class="btn btn-secondary" @click="openTemplateSelect">
        <span class="i-carbon-template mr-1"></span>
        应用模板
      </button>
      <button class="btn btn-secondary" @click="openVariableManager">
        <span class="i-carbon-parameter mr-1"></span>
        变量管理
      </button>
      <button class="btn btn-secondary" @click="showVariableValueEditor">
        <span class="i-carbon-settings-adjust mr-1"></span>
        变量值
      </button>
      <button class="btn btn-primary" :disabled="isSaving" @click="handleSave">
        <span
          v-if="isSaving"
          class="i-carbon-circle-dash animate-spin mr-1"
        ></span>
        <span v-else class="i-carbon-save mr-1"></span>
        {{ isSaving ? "保存中..." : "保存" }}
      </button>
    </div>
  </header>

  <!-- 应用模板弹窗 -->
  <TemplateSelectModal
    :visible="showTemplateSelect"
    @close="showTemplateSelect = false"
    @apply="handleApplyTemplate"
  />

  <!-- 变量管理弹窗 -->
  <VariableManager
    :visible="showVariableManager"
    @close="showVariableManager = false"
  />
</template>

<style scoped>
/* 顶部工具栏 */
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: #94a3b8;
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  background-color: #334155;
  color: #f1f5f9;
}

.theme-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
}

.unsaved-badge {
  padding: 2px 8px;
  font-size: 12px;
  background-color: #f59e0b;
  color: #1e293b;
  border-radius: 4px;
}

.context-info {
  padding: 4px 10px;
  font-size: 12px;
  color: #94a3b8;
  background-color: #334155;
  border-radius: 4px;
}

.header-center {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 保存状态消息 */
.save-message {
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 6px;
  font-weight: 500;
}

.save-message.success {
  background-color: rgba(34, 197, 94, 0.2);
  color: #22c55e;
}

.save-message.error {
  background-color: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 旋转动画 */
.animate-spin {
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

/* 按钮基础样式 */
.btn {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-secondary {
  color: #e2e8f0;
  background-color: #334155;
}

.btn-secondary:hover {
  background-color: #475569;
}

.btn-primary {
  color: white;
  background-color: #3b82f6;
}

.btn-primary:hover {
  background-color: #2563eb;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.mr-1 {
  margin-right: 4px;
}
</style>
