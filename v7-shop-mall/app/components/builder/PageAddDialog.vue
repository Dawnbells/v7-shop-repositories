<script setup lang="ts">
/**
 * PageAddDialog - 添加页面/布局弹窗
 */

const props = defineProps<{
  visible: boolean
  type: 'custom' | 'layout'
  layouts?: Array<{ id: string; name: string }>
}>()

const emit = defineEmits<{
  'close': []
  'confirm': [data: { name: string; path?: string; description?: string; layoutId?: string }]
}>()

const formData = reactive({
  name: '',
  path: '',
  description: '',
  layoutId: '',
})

const isCustomPage = computed(() => props.type === 'custom')
const title = computed(() => isCustomPage.value ? '添加自定义页面' : '添加布局')

function resetForm() {
  formData.name = ''
  formData.path = ''
  formData.description = ''
  formData.layoutId = ''
}

function handleClose() {
  resetForm()
  emit('close')
}

function handleConfirm() {
  if (!formData.name.trim()) {
    alert('请输入名称')
    return
  }

  emit('confirm', {
    name: formData.name.trim(),
    path: isCustomPage.value ? formData.path.trim() || undefined : undefined,
    description: !isCustomPage.value ? formData.description.trim() || undefined : undefined,
    layoutId: isCustomPage.value && formData.layoutId ? formData.layoutId : undefined,
  })

  resetForm()
}

watch(() => props.visible, (newVal) => {
  if (!newVal) {
    resetForm()
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="visible" class="dialog-overlay" @click.self="handleClose">
        <div class="dialog-content">
          <div class="dialog-header">
            <h3 class="dialog-title">{{ title }}</h3>
            <button class="dialog-close" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <div class="dialog-body">
            <div class="form-group">
              <label class="form-label">
                名称 <span class="required">*</span>
              </label>
              <input
                v-model="formData.name"
                type="text"
                class="form-input"
                :placeholder="isCustomPage ? '请输入页面名称' : '请输入布局名称'"
                @keyup.enter="handleConfirm"
              />
            </div>

            <div v-if="isCustomPage" class="form-group">
              <label class="form-label">
                路径
                <span class="form-hint">（可选，自动生成）</span>
              </label>
              <div class="input-with-prefix">
                <span class="input-prefix">/</span>
                <input
                  v-model="formData.path"
                  type="text"
                  class="form-input"
                  placeholder="page-name"
                />
              </div>
            </div>

            <div v-if="isCustomPage && layouts?.length" class="form-group">
              <label class="form-label">
                使用布局
                <span class="form-hint">（可选）</span>
              </label>
              <select
                v-model="formData.layoutId"
                class="form-select"
              >
                <option value="">不使用布局</option>
                <option
                  v-for="layout in layouts"
                  :key="layout.id"
                  :value="layout.id"
                >
                  {{ layout.name }}
                </option>
              </select>
            </div>

            <div v-if="!isCustomPage" class="form-group">
              <label class="form-label">
                描述
                <span class="form-hint">（可选）</span>
              </label>
              <textarea
                v-model="formData.description"
                class="form-textarea"
                placeholder="请输入布局描述"
                rows="3"
              ></textarea>
            </div>
          </div>

          <div class="dialog-footer">
            <button class="btn btn-secondary" @click="handleClose">
              取消
            </button>
            <button class="btn btn-primary" @click="handleConfirm">
              确定
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  z-index: 1000;
}

.dialog-content {
  width: 100%;
  max-width: 420px;
  margin: 16px;
  background: #1e293b;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
}

.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
}

.dialog-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 16px;
  color: #94a3b8;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.dialog-close:hover {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.5);
}

.dialog-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #e2e8f0;
}

.required {
  color: #ef4444;
}

.form-hint {
  font-weight: 400;
  color: #64748b;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #f1f5f9;
  background: #0f172a;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
  transition: all 0.15s ease;
}

.form-input:focus,
.form-textarea:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: #64748b;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.form-select {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #f1f5f9;
  background: #0f172a;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
  cursor: pointer;
  transition: all 0.15s ease;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2394a3b8' d='M2.5 4.5l3.5 3.5 3.5-3.5'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 32px;
}

.form-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.form-select option {
  background: #1e293b;
  color: #f1f5f9;
}

.input-with-prefix {
  display: flex;
  align-items: stretch;
}

.input-prefix {
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 14px;
  color: #64748b;
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-right: none;
  border-radius: 8px 0 0 8px;
}

.input-with-prefix .form-input {
  border-radius: 0 8px 8px 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
}

.btn {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-secondary {
  color: #94a3b8;
  background: transparent;
  border: 1px solid rgba(71, 85, 105, 0.5);
}

.btn-secondary:hover {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.5);
}

.btn-primary {
  color: #fff;
  background: #3b82f6;
}

.btn-primary:hover {
  background: #2563eb;
}

/* 动画 */
.dialog-enter-active,
.dialog-leave-active {
  transition: all 0.25s ease;
}

.dialog-enter-active .dialog-content,
.dialog-leave-active .dialog-content {
  transition: all 0.25s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .dialog-content,
.dialog-leave-to .dialog-content {
  opacity: 0;
  transform: scale(0.95) translateY(-10px);
}
</style>
