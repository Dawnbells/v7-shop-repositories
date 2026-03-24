<script setup lang="ts">
/**
 * 主题导出弹窗组件
 * 支持选择性导出页面、布局、站点配置、变量等
 */

import type { ExportOptions, ThemeRawData } from '~/utils/theme-export'
import {
  exportThemeAsJson,
  downloadJson,
  generateExportFilename,
  calculateDataSize,
  formatFileSize,
} from '~/utils/theme-export'

const props = defineProps<{
  visible: boolean
  themeData: ThemeRawData | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'exported'): void
}>()

// 导出选项
const exportOptions = ref<ExportOptions>({
  pages: true,
  layouts: true,
  siteConfig: true,
  variableSchema: true,
  variableValues: true,
})

// 是否正在导出
const isExporting = ref(false)

// 选项配置
const optionItems = [
  { key: 'pages', label: '页面配置', icon: 'i-carbon-document', description: '所有页面的组件结构和配置' },
  { key: 'layouts', label: '布局配置', icon: 'i-carbon-template', description: '布局模板的组件结构' },
  { key: 'siteConfig', label: '站点配置', icon: 'i-carbon-settings', description: '全局样式、品牌信息等' },
  { key: 'variableSchema', label: '变量定义', icon: 'i-carbon-parameter', description: '自定义变量的结构定义' },
  { key: 'variableValues', label: '变量值', icon: 'i-carbon-data-table', description: '变量的实际值' },
] as const

// 计算各部分数据大小
const dataSizes = computed(() => {
  if (!props.themeData) return {}
  return {
    pages: formatFileSize(calculateDataSize(props.themeData.pages)),
    layouts: formatFileSize(calculateDataSize(props.themeData.layouts)),
    siteConfig: formatFileSize(calculateDataSize(props.themeData.siteConfig)),
    variableSchema: formatFileSize(calculateDataSize(props.themeData.variableSchema)),
    variableValues: formatFileSize(calculateDataSize(props.themeData.variableValues)),
  }
})

// 计算预估导出大小
const estimatedSize = computed(() => {
  if (!props.themeData) return '0 B'
  let totalSize = 0
  if (exportOptions.value.pages) {
    totalSize += calculateDataSize(props.themeData.pages)
  }
  if (exportOptions.value.layouts) {
    totalSize += calculateDataSize(props.themeData.layouts)
  }
  if (exportOptions.value.siteConfig) {
    totalSize += calculateDataSize(props.themeData.siteConfig)
  }
  if (exportOptions.value.variableSchema) {
    totalSize += calculateDataSize(props.themeData.variableSchema)
  }
  if (exportOptions.value.variableValues) {
    totalSize += calculateDataSize(props.themeData.variableValues)
  }
  // 加上元数据开销
  totalSize += 200
  return formatFileSize(totalSize)
})

// 是否有选中的选项
const hasSelection = computed(() => {
  return Object.values(exportOptions.value).some(v => v)
})

// 全选/取消全选
function toggleAll() {
  const allSelected = Object.values(exportOptions.value).every(v => v)
  const newValue = !allSelected
  exportOptions.value = {
    pages: newValue,
    layouts: newValue,
    siteConfig: newValue,
    variableSchema: newValue,
    variableValues: newValue,
  }
}

// 执行导出
async function handleExport() {
  if (!props.themeData || !hasSelection.value) return

  isExporting.value = true

  try {
    const jsonContent = exportThemeAsJson(props.themeData, exportOptions.value)
    const filename = generateExportFilename('theme-export')
    downloadJson(jsonContent, filename)
    emit('exported')
    emit('close')
  } catch (error: any) {
    console.error('[ExportModal] 导出失败:', error)
    alert('导出失败: ' + (error.message || '未知错误'))
  } finally {
    isExporting.value = false
  }
}

// 关闭弹窗
function handleClose() {
  emit('close')
}

// 重置选项
watch(() => props.visible, (visible) => {
  if (visible) {
    exportOptions.value = {
      pages: true,
      layouts: true,
      siteConfig: true,
      variableSchema: true,
      variableValues: true,
    }
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="handleClose">
        <div class="export-modal">
          <!-- 头部 -->
          <div class="modal-header">
            <h2 class="modal-title">
              <span class="i-carbon-export"></span>
              导出主题配置
            </h2>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- 内容区 -->
          <div class="modal-content">
            <!-- 选项说明 -->
            <div class="section-header">
              <span class="section-title">选择导出内容</span>
              <button class="toggle-all-btn" @click="toggleAll">
                {{ Object.values(exportOptions).every(v => v) ? '取消全选' : '全选' }}
              </button>
            </div>

            <!-- 选项列表 -->
            <div class="option-list">
              <label
                v-for="item in optionItems"
                :key="item.key"
                class="option-item"
                :class="{ checked: exportOptions[item.key] }"
              >
                <input
                  v-model="exportOptions[item.key]"
                  type="checkbox"
                  class="option-checkbox"
                />
                <span class="option-icon" :class="item.icon"></span>
                <div class="option-info">
                  <span class="option-label">{{ item.label }}</span>
                  <span class="option-desc">{{ item.description }}</span>
                </div>
                <span class="option-size">{{ dataSizes[item.key] || '-' }}</span>
              </label>
            </div>

            <!-- 预估大小 -->
            <div class="size-preview">
              <span class="i-carbon-document-download"></span>
              <span>预估文件大小：</span>
              <strong>{{ estimatedSize }}</strong>
            </div>
          </div>

          <!-- 底部 -->
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="handleClose">
              取消
            </button>
            <button
              class="btn btn-primary"
              :disabled="!hasSelection || isExporting"
              @click="handleExport"
            >
              <span v-if="isExporting" class="i-carbon-circle-dash animate-spin mr-1"></span>
              <span v-else class="i-carbon-download mr-1"></span>
              {{ isExporting ? '导出中...' : '导出 JSON' }}
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
.export-modal {
  background: #1e293b;
  border-radius: 12px;
  width: 480px;
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

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
}

.toggle-all-btn {
  font-size: 12px;
  color: #3b82f6;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}

.toggle-all-btn:hover {
  background: rgba(59, 130, 246, 0.1);
}

/* 选项列表 */
.option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.option-item:hover {
  background: rgba(51, 65, 85, 0.5);
  border-color: rgba(71, 85, 105, 0.6);
}

.option-item.checked {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.4);
}

.option-checkbox {
  width: 18px;
  height: 18px;
  accent-color: #3b82f6;
  cursor: pointer;
}

.option-icon {
  font-size: 18px;
  color: #64748b;
}

.option-item.checked .option-icon {
  color: #3b82f6;
}

.option-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.option-label {
  font-size: 14px;
  font-weight: 500;
  color: #e2e8f0;
}

.option-desc {
  font-size: 12px;
  color: #64748b;
}

.option-size {
  font-size: 12px;
  color: #94a3b8;
  font-family: monospace;
}

/* 预估大小 */
.size-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px 14px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 8px;
  font-size: 13px;
  color: #94a3b8;
}

.size-preview strong {
  color: #3b82f6;
  font-weight: 600;
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

.modal-enter-active .export-modal,
.modal-leave-active .export-modal {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .export-modal,
.modal-leave-to .export-modal {
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
