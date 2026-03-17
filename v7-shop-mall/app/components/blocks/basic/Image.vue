<script setup lang="ts">
/**
 * Image Block - 图片组件
 * 支持 CDN 路径自动转换和加载失败降级
 */

interface Props {
  src?: string
  alt?: string
  fallback?: boolean
  objectFit?: 'contain' | 'cover' | 'fill' | 'none' | 'scale-down'
  loading?: 'lazy' | 'eager'
}

const props = withDefaults(defineProps<Props>(), {
  src: '',
  alt: '',
  fallback: true,
  objectFit: 'cover',
  loading: 'lazy',
})

const { currentUrl, handleError } = useImageWithFallback(computed(() => props.src))

const imageStyle = computed(() => ({
  objectFit: props.objectFit,
}))

function onError(event: Event) {
  if (props.fallback) {
    handleError(event)
  }
}
</script>

<template>
  <img
    v-if="currentUrl"
    class="block-image"
    :src="currentUrl"
    :alt="alt"
    :loading="loading"
    :style="imageStyle"
    @error="onError"
  />
  <div v-else class="block-image-placeholder">
    <i class="i-carbon-image" />
  </div>
</template>

<style scoped>
.block-image {
  display: block;
  width: 100%;
  height: 100%;
}

.block-image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 100px;
  background-color: #f5f5f5;
  color: #999;
  font-size: 32px;
}
</style>
