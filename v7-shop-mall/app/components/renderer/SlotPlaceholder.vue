<script setup lang="ts">
/**
 * SlotPlaceholder - 页面插槽占位组件
 * 
 * 注册为 page-slot 类型的 Block 组件
 * - 渲染模式下：由 NodeRenderer 处理，渲染实际页面内容
 * - 编辑器模式下：显示可视化占位符
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
  <div v-if="isInEditor" class="slot-placeholder">
    <div class="slot-placeholder-icon">
      <span class="i-carbon-document"></span>
    </div>
    <span class="slot-placeholder-label">{{ label }}</span>
    <span class="slot-placeholder-hint">页面内容将在此处显示</span>
  </div>
  
  <!-- 渲染模式下，NodeRenderer 会检测 page-slot 类型并处理 -->
  <!-- 此组件本身不需要渲染任何内容 -->
  <slot v-else />
</template>

<style scoped>
.slot-placeholder {
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

.slot-placeholder-icon {
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

.slot-placeholder-label {
  font-size: 14px;
  font-weight: 500;
  color: #475569;
}

.slot-placeholder-hint {
  font-size: 12px;
  color: #94a3b8;
}
</style>
