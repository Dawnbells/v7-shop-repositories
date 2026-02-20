<script setup lang="ts">
/**
 * 变量管理弹窗组件
 * 用于管理主题自定义变量（添加、编辑、删除）
 */

import type { CustomVariable, VariableType } from '~/types/data-context'

defineProps<{
  visible: boolean
  variables: CustomVariable[]
}>()

const emit = defineEmits<{
  close: []
  add: []
  edit: [variable: CustomVariable]
  delete: [key: string]
}>()

const variableTypes: Array<{ value: VariableType; label: string; icon: string }> = [
  { value: 'string', label: '文本', icon: 'i-carbon-text-font' },
  { value: 'number', label: '数字', icon: 'i-carbon-hashtag' },
  { value: 'boolean', label: '开关', icon: 'i-carbon-toggle-off' },
  { value: 'color', label: '颜色', icon: 'i-carbon-color-palette' },
  { value: 'image', label: '图片', icon: 'i-carbon-image' },
  { value: 'richtext', label: '富文本', icon: 'i-carbon-text-align-left' },
  { value: 'enum', label: '枚举', icon: 'i-carbon-list-checked' },
  { value: 'array', label: '数组', icon: 'i-carbon-list' },
  { value: 'object', label: '对象', icon: 'i-carbon-json' },
]

function getTypeInfo(type: VariableType) {
  return variableTypes.find(t => t.value === type) || variableTypes[0]
}

function getVariableExtraInfo(variable: CustomVariable): string {
  if (variable.type === 'enum' && variable.enumOptions) {
    return `${variable.enumOptions.length} 个选项`
  }
  if (variable.type === 'array') {
    if (variable.itemSchema && variable.itemSchema.length > 0) {
      return `对象数组 (${variable.itemSchema.length} 个字段)`
    }
    if (variable.itemType) {
      return `${getTypeInfo(variable.itemType).label}数组`
    }
  }
  if (variable.type === 'object' && variable.fields) {
    return `${variable.fields.length} 个字段`
  }
  return ''
}

function handleDelete(key: string, label: string) {
  if (confirm(`确定要删除变量「${label}」吗？`)) {
    emit('delete', key)
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="emit('close')">
        <div class="variable-manager">
          <div class="manager-header">
            <h2 class="manager-title">
              <span class="i-carbon-parameter"></span>
              变量管理
            </h2>
            <button class="close-btn" @click="emit('close')">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <div class="manager-content">
            <div class="variable-list">
              <div v-if="variables.length === 0" class="empty-state">
                <span class="i-carbon-parameter empty-icon"></span>
                <p class="empty-title">暂无自定义变量</p>
                <p class="empty-hint">点击下方按钮添加变量</p>
              </div>

              <div
                v-for="variable in variables"
                :key="variable.key"
                class="variable-item"
              >
                <div class="variable-info">
                  <div class="variable-type-icon">
                    <span :class="getTypeInfo(variable.type).icon"></span>
                  </div>
                  <div class="variable-details">
                    <div class="variable-name">
                      <span class="variable-label">{{ variable.label }}</span>
                      <span class="variable-key">site.{{ variable.key }}</span>
                    </div>
                    <div v-if="variable.description" class="variable-desc">
                      {{ variable.description }}
                    </div>
                    <div class="variable-meta">
                      <span class="meta-tag">{{ getTypeInfo(variable.type).label }}</span>
                      <span v-if="getVariableExtraInfo(variable)" class="meta-tag extra">
                        {{ getVariableExtraInfo(variable) }}
                      </span>
                      <span v-if="variable.i18n" class="meta-tag i18n">多语言</span>
                    </div>
                  </div>
                </div>
                <div class="variable-actions">
                  <button
                    class="action-btn"
                    title="编辑"
                    @click="emit('edit', variable)"
                  >
                    <span class="i-carbon-edit"></span>
                  </button>
                  <button
                    class="action-btn danger"
                    title="删除"
                    @click="handleDelete(variable.key, variable.label)"
                  >
                    <span class="i-carbon-trash-can"></span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="manager-footer">
            <button class="btn btn-primary" @click="emit('add')">
              <span class="i-carbon-add"></span>
              添加变量
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
  z-index: 1000;
}

.variable-manager {
  display: flex;
  flex-direction: column;
  width: 600px;
  max-width: 90vw;
  max-height: 80vh;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.manager-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.8) 0%, rgba(30, 41, 59, 0.4) 100%);
}

.manager-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.manager-title span {
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

.manager-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.variable-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: #475569;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: #94a3b8;
  margin: 0 0 8px;
}

.empty-hint {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.variable-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.8) 0%, rgba(15, 23, 42, 0.6) 100%);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 12px;
  transition: all 0.2s;
}

.variable-item:hover {
  border-color: rgba(59, 130, 246, 0.4);
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.9) 0%, rgba(15, 23, 42, 0.7) 100%);
}

.variable-info {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.variable-type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  font-size: 20px;
  color: #3b82f6;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15) 0%, rgba(59, 130, 246, 0.05) 100%);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 10px;
  flex-shrink: 0;
}

.variable-details {
  flex: 1;
  min-width: 0;
}

.variable-name {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.variable-label {
  font-size: 15px;
  font-weight: 500;
  color: #f1f5f9;
}

.variable-key {
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #64748b;
  padding: 2px 6px;
  background: rgba(51, 65, 85, 0.5);
  border-radius: 4px;
}

.variable-desc {
  font-size: 13px;
  color: #64748b;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.variable-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.meta-tag {
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.6);
  border-radius: 4px;
}

.meta-tag.extra {
  color: #60a5fa;
  background: rgba(59, 130, 246, 0.15);
}

.meta-tag.i18n {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.15);
}

.variable-actions {
  display: flex;
  gap: 6px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 16px;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.3);
}

.action-btn.danger:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.manager-footer {
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
  transform: translateY(-1px);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .variable-manager,
.modal-leave-active .variable-manager {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .variable-manager,
.modal-leave-to .variable-manager {
  transform: scale(0.95);
}
</style>
