<script setup lang="ts">
/**
 * PageSlot Block - 页面内容插槽
 * 
 * 在布局中标记页面内容的插入位置
 * - 编辑器模式：显示占位符
 * - 渲染模式：由 NodeRenderer 检测并替换为实际页面内容
 */

interface Props {
  label?: string
}

const props = withDefaults(defineProps<Props>(), {
  label: '页面内容区域',
})

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>('isInEditor', ref(false))
</script>

<template>
  <!-- 编辑器模式显示占位符 -->
  <div class="page-slot" :class="{ 'is-editor': isInEditor }">
    <template v-if="isInEditor">
      <div class="page-slot-placeholder">
        <div class="page-slot-icon">
          <span class="i-carbon-document"></span>
        </div>
        <span class="page-slot-label">{{ label }}</span>
        <span class="page-slot-hint">页面内容将在此处显示</span>
      </div>
    </template>
    
    <!-- 渲染模式下，NodeRenderer 会检测 page-slot 类型并处理 -->
    <slot v-else />
  </div>
</template>

<style scoped>
.page-slot {
  width: 100%;
}

.page-slot.is-editor {
  min-height: 200px;
}

.page-slot-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 200px;
  padding: 40px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 2px dashed #cbd5e1;
  border-radius: 8px;
}

.page-slot-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  font-size: 24px;
  color: #64748b;
  background: #e2e8f0;
  border-radius: 12px;
}

.page-slot-label {
  font-size: 14px;
  font-weight: 500;
  color: #475569;
}

.page-slot-hint {
  font-size: 12px;
  color: #94a3b8;
}
</style>
