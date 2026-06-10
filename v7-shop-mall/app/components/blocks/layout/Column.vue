<script setup lang="ts">
/**
 * Column Block - 纵向布局组件
 * Flexbox 纵向排列容器
 */

// 前台渲染时 meta 不加载，容器标记必须声明在组件上（NodeRenderer 依赖它渲染子节点）
defineOptions({ isContainer: true })

interface Props {
  justify?: 'flex-start' | 'center' | 'flex-end' | 'space-between' | 'space-around' | 'space-evenly'
  align?: 'flex-start' | 'center' | 'flex-end' | 'stretch' | 'baseline'
  gap?: string
}

const props = withDefaults(defineProps<Props>(), {
  justify: 'flex-start',
  align: 'stretch',
  gap: '0',
})

const columnStyle = computed(() => ({
  display: 'flex',
  flexDirection: 'column' as const,
  justifyContent: props.justify,
  alignItems: props.align,
  gap: props.gap,
}))
</script>

<template>
  <div class="block-column" :style="columnStyle">
    <slot />
  </div>
</template>

<style scoped>
.block-column {
  width: 100%;
  box-sizing: border-box;
}
</style>
