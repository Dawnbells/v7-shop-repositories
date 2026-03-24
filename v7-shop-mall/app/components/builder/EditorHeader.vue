<script setup lang="ts">
/**
 * EditorHeader - 编辑器顶部工具栏
 */

defineProps<{
  themeName?: string
  contextInfo?: string
  hasUnsavedChanges?: boolean
  isSaving?: boolean
}>()

const emit = defineEmits<{
  close: []
  save: []
  openTemplates: []
  openVariables: []
  openVariableValues: []
  openExport: []
  openImport: []
}>()
</script>

<template>
  <header class="editor-header">
    <!-- 左侧：关闭按钮 + 主题信息 -->
    <div class="header-left">
      <button 
        class="close-btn" 
        title="关闭编辑器"
        @click="emit('close')"
      >
        <span class="i-carbon-close"></span>
      </button>
      
      <div class="theme-info">
        <h1 class="theme-name">{{ themeName || '主题编辑器' }}</h1>
        <span v-if="contextInfo" class="context-badge">
          {{ contextInfo }}
        </span>
        <span v-if="hasUnsavedChanges" class="unsaved-badge">
          <span class="pulse-dot"></span>
          未保存
        </span>
      </div>
    </div>

    <!-- 右侧：操作按钮组 -->
    <div class="header-right">
      <button class="btn btn-ghost" @click="emit('openTemplates')">
        <span class="i-carbon-template"></span>
        <span class="btn-text">应用模板</span>
      </button>
      
      <button class="btn btn-ghost" @click="emit('openVariables')">
        <span class="i-carbon-parameter"></span>
        <span class="btn-text">变量管理</span>
      </button>
      
      <button class="btn btn-ghost" @click="emit('openVariableValues')">
        <span class="i-carbon-settings-adjust"></span>
        <span class="btn-text">变量值</span>
      </button>

      <div class="divider"></div>

      <button class="btn btn-ghost" @click="emit('openImport')">
        <span class="i-carbon-upload"></span>
        <span class="btn-text">导入</span>
      </button>

      <button class="btn btn-ghost" @click="emit('openExport')">
        <span class="i-carbon-download"></span>
        <span class="btn-text">导出</span>
      </button>

      <div class="divider"></div>
      
      <button 
        class="btn btn-primary"
        :disabled="isSaving"
        @click="emit('save')"
      >
        <span v-if="isSaving" class="i-carbon-circle-dash spinning"></span>
        <span v-else class="i-carbon-save"></span>
        <span class="btn-text">{{ isSaving ? '保存中...' : '保存' }}</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  background: linear-gradient(180deg, #1e293b 0%, #1a2332 100%);
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
  backdrop-filter: blur(8px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: #94a3b8;
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
}

.close-btn:hover {
  color: #f1f5f9;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.close-btn:active {
  transform: scale(0.95);
}

.theme-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.theme-name {
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.context-badge {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  background: linear-gradient(135deg, rgba(51, 65, 85, 0.8) 0%, rgba(51, 65, 85, 0.5) 100%);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 6px;
}

.unsaved-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #fbbf24;
  background: rgba(245, 158, 11, 0.15);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 6px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background: #fbbf24;
  border-radius: 50%;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.2);
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.divider {
  width: 1px;
  height: 24px;
  background: rgba(71, 85, 105, 0.5);
  margin: 0 8px;
}

.btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-text {
  white-space: nowrap;
}

.btn-ghost {
  color: #94a3b8;
  background: transparent;
  border: 1px solid transparent;
}

.btn-ghost:hover {
  color: #f1f5f9;
  background: rgba(51, 65, 85, 0.5);
  border-color: rgba(71, 85, 105, 0.5);
}

.btn-ghost:active {
  transform: translateY(1px);
}

.btn-primary {
  color: #fff;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
  transform: translateY(-1px);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 响应式：小屏隐藏按钮文字 */
@media (max-width: 1200px) {
  .btn-text {
    display: none;
  }
  
  .btn {
    padding: 8px 10px;
  }
}
</style>
