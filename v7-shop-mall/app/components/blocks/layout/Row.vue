<script setup lang="ts">
/**
 * Row Block - 横向布局组件
 * Flexbox 横向排列容器
 */

interface Props {
  justify?: 'flex-start' | 'center' | 'flex-end' | 'space-between' | 'space-around' | 'space-evenly'
  align?: 'flex-start' | 'center' | 'flex-end' | 'stretch' | 'baseline'
  wrap?: 'nowrap' | 'wrap' | 'wrap-reverse'
  gap?: string
}

const props = withDefaults(defineProps<Props>(), {
  justify: 'flex-start',
  align: 'stretch',
  wrap: 'nowrap',
  gap: '0',
})

const rowStyle = computed(() => ({
  display: 'flex',
  flexDirection: 'row' as const,
  justifyContent: props.justify,
  alignItems: props.align,
  flexWrap: props.wrap,
  gap: props.gap,
}))
</script>

<template>
  <div class="block-row" :style="rowStyle">
    <slot />
  </div>
</template>

<style scoped>
.block-row {
  width: 100%;
  box-sizing: border-box;
}
</style>
