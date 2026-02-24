<script setup lang="ts">
/**
 * Grid Block - 网格布局组件
 * CSS Grid 网格容器
 */

interface Props {
  columns?: string | number
  rows?: string
  gap?: string
  columnGap?: string
  rowGap?: string
  justifyItems?: 'start' | 'center' | 'end' | 'stretch'
  alignItems?: 'start' | 'center' | 'end' | 'stretch'
}

const props = withDefaults(defineProps<Props>(), {
  columns: 3,
  rows: 'auto',
  gap: '0',
  columnGap: '',
  rowGap: '',
  justifyItems: 'stretch',
  alignItems: 'stretch',
})

const gridStyle = computed(() => {
  let columnsValue: string

  if (typeof props.columns === 'number') {
    columnsValue = `repeat(${props.columns}, 1fr)`
  } else if (/^\d+$/.test(props.columns)) {
    columnsValue = `repeat(${props.columns}, 1fr)`
  } else {
    columnsValue = props.columns
  }

  return {
    display: 'grid',
    gridTemplateColumns: columnsValue,
    gridTemplateRows: props.rows,
    gap: props.gap,
    columnGap: props.columnGap || undefined,
    rowGap: props.rowGap || undefined,
    justifyItems: props.justifyItems,
    alignItems: props.alignItems,
  }
})
</script>

<template>
  <div class="block-grid" :style="gridStyle">
    <slot />
  </div>
</template>

<style scoped>
.block-grid {
  width: 100%;
  box-sizing: border-box;
}
</style>
