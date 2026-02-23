<script setup lang="ts">
/**
 * Text Block - 文本组件
 * 用于显示文本内容，支持富文本
 */

interface Props {
  content?: string
  tag?: 'p' | 'span' | 'div' | 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6'
  align?: 'left' | 'center' | 'right' | 'justify'
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  tag: 'p',
  align: 'left',
})

const textStyle = computed(() => ({
  textAlign: props.align,
}))
</script>

<template>
  <component
    :is="tag"
    class="block-text"
    :style="textStyle"
    v-html="content"
  />
</template>

<style scoped>
.block-text {
  margin: 0;
  word-wrap: break-word;
}
</style>
