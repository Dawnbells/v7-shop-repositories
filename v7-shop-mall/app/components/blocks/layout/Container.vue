<script setup lang="ts">
/**
 * Container Block - 容器组件
 * 作为页面/布局的根容器，支持最大宽度限制和居中
 */

interface Props {
  maxWidth?: string
  centered?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  maxWidth: '100%',
  centered: false,
})

const containerStyle = computed(() => ({
  maxWidth: props.maxWidth,
  marginLeft: props.centered ? 'auto' : undefined,
  marginRight: props.centered ? 'auto' : undefined,
}))
</script>

<template>
  <div class="block-container" :style="containerStyle">
    <slot />
  </div>
</template>

<style scoped>
.block-container {
  width: 100%;
  box-sizing: border-box;
}
</style>
