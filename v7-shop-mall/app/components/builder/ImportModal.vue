<script setup lang="ts">
/**
 * 主题导入弹窗组件
 * 支持导入 JSON 文件，验证并预览导入内容
 */

import type { ThemeImportData, ValidationResult } from '~/utils/theme-export'
import {
  importThemeFromJson,
  validateImportData,
  readFileAsText,
  formatFileSize,
} from '~/utils/theme-export'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'import', data: ThemeImportData): void
}>()

// 文件输入引用
const fileInputRef = ref<HTMLInputElement | null>(null)

// 状态
const selectedFile = ref<File | null>(null)
const isValidating = ref(false)
const isImporting = ref(false)
const validationResult = ref<ValidationResult | null>(null)
const importData = ref<ThemeImportData | null>(null)

// 拖拽状态
const isDragging = ref(false)

// 导入内容预览
const importPreview = computed(() => {
  if (!importData.value) return null
  const { includes, data, exportedAt } = importData.value
  return {
    exportedAt: exportedAt ? new Date(exportedAt).toLocaleString() : '未知',
    items: [
      { key: 'pages', label: '页面配置', included: includes.pages, count: data.pages?.length || 0 },
      { key: 'layouts', label: '布局配置', included: includes.layouts, count: data.layouts?.length || 0 },
      { key: 'siteConfig', label: '站点配置', included: includes.siteConfig, hasData: !!data.siteConfig && Object.keys(data.siteConfig).length > 0 },
      { key: 'variableSchema', label: '变量定义', included: includes.variableSchema, count: data.variableSchema?.length || 0 },
      { key: 'variableValues', label: '变量值', included: includes.variableValues, hasData: !!data.variableValues && Object.keys(data.variableValues).length > 0 },
    ],
  }
})

// 触发文件选择
function triggerFileSelect() {
  fileInputRef.value?.click()
}

// 处理文件选择
async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    await processFile(file)
  }
  // 重置 input，允许重复选择同一文件
  input.value = ''
}

// 处理拖拽
function handleDragOver(event: DragEvent) {
  event.preventDefault()
  isDragging.value = true
}

function handleDragLeave() {
  isDragging.value = false
}

async function handleDrop(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false
  const file = event.dataTransfer?.files[0]
  if (file) {
    await processFile(file)
  }
}

// 处理文件
async function processFile(file: File) {
  // 验证文件类型
  if (!file.name.endsWith('.json')) {
    validationResult.value = {
      valid: false,
      errors: ['请选择 .json 格式的文件'],
      warnings: [],
    }
    return
  }

  selectedFile.value = file
  isValidating.value = true
  validationResult.value = null
  importData.value = null

  try {
    const content = await readFileAsText(file)
    const data = importThemeFromJson(content)
    const result = validateImportData(data)

    validationResult.value = result
    if (result.valid && result.data) {
      importData.value = result.data
    }
  } catch (error: any) {
    validationResult.value = {
      valid: false,
      errors: ['文件解析失败: ' + (error.message || '无效的 JSON 格式')],
      warnings: [],
    }
  } finally {
    isValidating.value = false
  }
}

// 清除选择
function clearSelection() {
  selectedFile.value = null
  validationResult.value = null
  importData.value = null
}

// 执行导入
function handleImport() {
  if (!importData.value) return

  isImporting.value = true

  try {
    emit('import', importData.value)
    emit('close')
  } catch (error: any) {
    console.error('[ImportModal] 导入失败:', error)
    alert('导入失败: ' + (error.message || '未知错误'))
  } finally {
    isImporting.value = false
  }
}

// 关闭弹窗
function handleClose() {
  clearSelection()
  emit('close')
}

// 重置状态
watch(() => props.visible, (visible) => {
  if (!visible) {
    clearSelection()
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="handleClose">
        <div class="import-modal">
          <!-- 头部 -->
          <div class="modal-header">
            <h2 class="modal-title">
              <span class="i-carbon-upload"></span>
              导入主题配置
            </h2>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- 内容区 -->
          <div class="modal-content">
            <!-- 警告提示 -->
            <div class="warning-tip">
              <span class="i-carbon-warning"></span>
              导入后将覆盖当前编辑器的对应配置，此操作不可撤销
            </div>

            <!-- 文件选择区 -->
            <div
              v-if="!selectedFile"
              class="file-drop-zone"
              :class="{ dragging: isDragging }"
              @dragover="handleDragOver"
              @dragleave="handleDragLeave"
              @drop="handleDrop"
              @click="triggerFileSelect"
            >
              <input
                ref="fileInputRef"
                type="file"
                accept=".json"
                class="file-input"
                @change="handleFileSelect"
              />
              <span class="i-carbon-document-add drop-icon"></span>
              <p class="drop-text">点击选择文件或拖拽文件到此处</p>
              <p class="drop-hint">支持 .json 格式</p>
            </div>

            <!-- 已选文件 -->
            <div v-else class="selected-file">
              <div class="file-info">
                <span class="i-carbon-document file-icon"></span>
                <div class="file-details">
                  <span class="file-name">{{ selectedFile.name }}</span>
                  <span class="file-size">{{ formatFileSize(selectedFile.size) }}</span>
                </div>
                <button class="remove-btn" @click="clearSelection">
                  <span class="i-carbon-close"></span>
                </button>
              </div>

              <!-- 验证中 -->
              <div v-if="isValidating" class="validation-status validating">
                <span class="i-carbon-circle-dash animate-spin"></span>
                <span>正在验证文件...</span>
              </div>

              <!-- 验证失败 -->
              <div v-else-if="validationResult && !validationResult.valid" class="validation-status error">
                <span class="i-carbon-warning-alt"></span>
                <div class="error-list">
                  <p v-for="(error, index) in validationResult.errors" :key="index">{{ error }}</p>
                </div>
              </div>

              <!-- 验证成功 -->
              <div v-else-if="validationResult && validationResult.valid" class="validation-status success">
                <span class="i-carbon-checkmark-filled"></span>
                <span>文件验证通过</span>
              </div>

              <!-- 警告信息 -->
              <div v-if="validationResult?.warnings?.length" class="warning-list">
                <div v-for="(warning, index) in validationResult.warnings" :key="index" class="warning-item">
                  <span class="i-carbon-warning"></span>
                  <span>{{ warning }}</span>
                </div>
              </div>

              <!-- 导入预览 -->
              <div v-if="importPreview" class="import-preview">
                <div class="preview-header">
                  <span class="preview-title">导入内容预览</span>
                  <span class="preview-time">导出时间：{{ importPreview.exportedAt }}</span>
                </div>
                <div class="preview-items">
                  <div
                    v-for="item in importPreview.items"
                    :key="item.key"
                    class="preview-item"
                    :class="{ included: item.included }"
                  >
                    <span class="item-status">
                      <span v-if="item.included" class="i-carbon-checkmark"></span>
                      <span v-else class="i-carbon-close"></span>
                    </span>
                    <span class="item-label">{{ item.label }}</span>
                    <span v-if="'count' in item" class="item-count">
                      {{ item.included ? `${item.count} 项` : '未包含' }}
                    </span>
                    <span v-else class="item-count">
                      {{ item.included && item.hasData ? '有数据' : '未包含' }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部 -->
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="handleClose">
              取消
            </button>
            <button
              class="btn btn-primary"
              :disabled="!importData || isImporting"
              @click="handleImport"
            >
              <span v-if="isImporting" class="i-carbon-circle-dash animate-spin mr-1"></span>
              <span v-else class="i-carbon-upload mr-1"></span>
              {{ isImporting ? '导入中...' : '确认导入' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 弹窗遮罩 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

/* 弹窗主体 */
.import-modal {
  background: #1e293b;
  border-radius: 12px;
  width: 520px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(71, 85, 105, 0.5);
}

/* 头部 */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
}

.modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

/* 内容区 */
.modal-content {
  padding: 20px;
  overflow-y: auto;
}

/* 警告提示 */
.warning-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 8px;
  font-size: 13px;
  color: #fbbf24;
  margin-bottom: 16px;
}

/* 文件拖放区 */
.file-drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  border: 2px dashed rgba(71, 85, 105, 0.5);
  border-radius: 12px;
  background: rgba(51, 65, 85, 0.2);
  cursor: pointer;
  transition: all 0.2s;
}

.file-drop-zone:hover,
.file-drop-zone.dragging {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
}

.file-input {
  display: none;
}

.drop-icon {
  font-size: 48px;
  color: #64748b;
  margin-bottom: 12px;
}

.file-drop-zone:hover .drop-icon,
.file-drop-zone.dragging .drop-icon {
  color: #3b82f6;
}

.drop-text {
  font-size: 14px;
  color: #94a3b8;
  margin: 0 0 4px;
}

.drop-hint {
  font-size: 12px;
  color: #64748b;
  margin: 0;
}

/* 已选文件 */
.selected-file {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 8px;
}

.file-icon {
  font-size: 24px;
  color: #3b82f6;
}

.file-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #e2e8f0;
}

.file-size {
  font-size: 12px;
  color: #64748b;
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.remove-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

/* 验证状态 */
.validation-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
}

.validation-status.validating {
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  color: #60a5fa;
}

.validation-status.error {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
}

.validation-status.success {
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.3);
  color: #4ade80;
}

.error-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.error-list p {
  margin: 0;
}

/* 警告列表 */
.warning-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.warning-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 6px;
  font-size: 12px;
  color: #fbbf24;
}

/* 导入预览 */
.import-preview {
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 8px;
  overflow: hidden;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: rgba(51, 65, 85, 0.3);
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.preview-title {
  font-size: 13px;
  font-weight: 500;
  color: #e2e8f0;
}

.preview-time {
  font-size: 11px;
  color: #64748b;
}

.preview-items {
  padding: 8px;
}

.preview-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  transition: background 0.2s;
}

.preview-item:hover {
  background: rgba(51, 65, 85, 0.3);
}

.preview-item.included .item-status {
  color: #4ade80;
}

.preview-item:not(.included) .item-status {
  color: #64748b;
}

.item-label {
  flex: 1;
  font-size: 13px;
  color: #94a3b8;
}

.preview-item.included .item-label {
  color: #e2e8f0;
}

.item-count {
  font-size: 12px;
  color: #64748b;
}

.preview-item.included .item-count {
  color: #94a3b8;
}

/* 底部 */
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-secondary {
  background: rgba(51, 65, 85, 0.5);
  color: #94a3b8;
  border: 1px solid rgba(71, 85, 105, 0.5);
}

.btn-secondary:hover {
  background: rgba(51, 65, 85, 0.8);
  color: #e2e8f0;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .import-modal,
.modal-leave-active .import-modal {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .import-modal,
.modal-leave-to .import-modal {
  transform: scale(0.95);
}

.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
