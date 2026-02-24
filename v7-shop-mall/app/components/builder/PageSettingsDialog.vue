<script setup lang="ts">
/**
 * PageSettingsDialog - 页面设置弹窗
 * 用于修改已创建页面的布局
 */

const props = defineProps<{
  visible: boolean
  pageName: string
  currentLayoutId?: string
  layouts: Array<{ id: string; name: string }>
}>()

const emit = defineEmits<{
  'close': []
  'confirm': [layoutId: string | undefined]
}>()

const selectedLayoutId = ref('')

watch(() => props.visible, (newVal) => {
  if (newVal) {
    selectedLayoutId.value = props.currentLayoutId || ''
  }
})

function handleClose() {
  emit('close')
}

function handleConfirm() {
  emit('confirm', selectedLayoutId.value || undefined)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="visible" class="dialog-overlay" @click.self="handleClose">
        <div class="dialog-content">
          <div class="dialog-header">
            <h3 class="dialog-title">页面设置</h3>
            <button class="dialog-close" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <div class="dialog-body">
            <div class="form-group">
              <label class="form-label">页面名称</label>
              <div class="page-name-display">{{ pageName }}</div>
            </div>

            <div class="form-group">
              <label class="form-label">使用布局</label>
              <select
                v-model="selectedLayoutId"
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
              <p class="form-description">
                选择布局后，页面内容将嵌入到布局的插槽中
              </p>
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

.page-name-display {
  padding: 10px 12px;
  font-size: 14px;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
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

.form-description {
  margin: 8px 0 0;
  font-size: 12px;
  color: #64748b;
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
