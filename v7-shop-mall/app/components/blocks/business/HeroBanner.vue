<script setup lang="ts">
/**
 * HeroBanner Block - 首页顶部海报组件
 * 支持单张图片和轮播图两种模式，包含点击跳转、轮播指示器和响应式高度功能
 */

interface BannerItem {
  src: string
  alt?: string
  link?: string
}

interface Props {
  items?: BannerItem[]
  autoplay?: boolean
  interval?: number
  showIndicators?: boolean
  objectFit?: 'cover' | 'contain' | 'fill'
}

const props = withDefaults(defineProps<Props>(), {
  items: () => [],
  autoplay: true,
  interval: 4000,
  showIndicators: true,
  objectFit: 'cover',
})

const currentIndex = ref(0)
const touchStartX = ref(0)
const touchEndX = ref(0)
const isTransitioning = ref(false)
const { t } = useI18n()

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false))

let autoplayTimer: ReturnType<typeof setInterval> | null = null

const hasMultipleItems = computed(() => props.items.length > 1)
const showDots = computed(() => props.showIndicators && hasMultipleItems.value)

const currentItem = computed(() => props.items[currentIndex.value])

function goTo(index: number) {
  if (isTransitioning.value) return
  if (index < 0) {
    currentIndex.value = props.items.length - 1
  } else if (index >= props.items.length) {
    currentIndex.value = 0
  } else {
    currentIndex.value = index
  }
}

function next() {
  goTo(currentIndex.value + 1)
}

function prev() {
  goTo(currentIndex.value - 1)
}

function startAutoplay() {
  if (!props.autoplay || !hasMultipleItems.value) return
  stopAutoplay()
  autoplayTimer = setInterval(next, props.interval)
}

function stopAutoplay() {
  if (autoplayTimer) {
    clearInterval(autoplayTimer)
    autoplayTimer = null
  }
}

function handleTouchStart(e: TouchEvent) {
  touchStartX.value = e.touches[0].clientX
  stopAutoplay()
}

function handleTouchEnd(e: TouchEvent) {
  touchEndX.value = e.changedTouches[0].clientX
  const diff = touchStartX.value - touchEndX.value
  const threshold = 50

  if (Math.abs(diff) > threshold) {
    if (diff > 0) {
      next()
    } else {
      prev()
    }
  }
  startAutoplay()
}

function handleItemClick(item: BannerItem) {
  if (isInEditor.value) return // 编辑模式下不执行
  if (!item.link) return
  
  if (item.link.startsWith('http://') || item.link.startsWith('https://')) {
    window.open(item.link, '_blank')
  } else {
    navigateTo(item.link)
  }
}

function getImageUrl(src: string): string {
  return buildImageUrl(src)
}

function handleImageError(e: Event, item: BannerItem) {
  const img = e.target as HTMLImageElement
  const fallbackUrl = buildFallbackImageUrl(item.src)
  if (fallbackUrl && img.src !== fallbackUrl) {
    img.src = fallbackUrl
  }
}

onMounted(() => {
  startAutoplay()
})

onUnmounted(() => {
  stopAutoplay()
})

watch(() => props.autoplay, (val) => {
  if (val) {
    startAutoplay()
  } else {
    stopAutoplay()
  }
})

watch(() => props.items, () => {
  currentIndex.value = 0
  startAutoplay()
}, { deep: true })
</script>

<template>
  <div
    class="block-hero-banner"
    @touchstart="handleTouchStart"
    @touchend="handleTouchEnd"
    @mouseenter="stopAutoplay"
    @mouseleave="startAutoplay"
  >
    <template v-if="items.length > 0">
      <div class="banner-track">
        <div
          v-for="(item, index) in items"
          :key="index"
          class="banner-slide"
          :class="{ 'is-active': index === currentIndex }"
          :style="{ cursor: item.link ? 'pointer' : 'default' }"
          @click="handleItemClick(item)"
        >
          <img
            :src="getImageUrl(item.src)"
            :alt="item.alt || ''"
            class="banner-image"
            :style="{ objectFit }"
            @error="(e) => handleImageError(e, item)"
          />
        </div>
      </div>

      <div v-if="showDots" class="banner-indicators">
        <button
          v-for="(_, index) in items"
          :key="index"
          type="button"
          class="indicator-dot"
          :class="{ 'is-active': index === currentIndex }"
          @click="goTo(index)"
        />
      </div>
    </template>

    <div v-else class="banner-placeholder">
      <i class="i-carbon-image" />
      <span>{{ t('product.addBanner') }}</span>
    </div>
  </div>
</template>

<style scoped>
.block-hero-banner {
  position: relative;
  width: 100%;
  overflow: hidden;
  background-color: var(--surface-color, #f5f5f5);
}

.banner-track {
  position: relative;
  width: 100%;
  height: 100%;
}

.banner-slide {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  transition: opacity 0.5s ease-in-out;
  pointer-events: none;
}

.banner-slide.is-active {
  opacity: 1;
  pointer-events: auto;
}

.banner-image {
  display: block;
  width: 100%;
  height: 100%;
}

.banner-indicators {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 10;
}

.indicator-dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: all 0.3s ease;
}

.indicator-dot:hover {
  background-color: rgba(255, 255, 255, 0.8);
}

.indicator-dot.is-active {
  width: 24px;
  border-radius: 4px;
  background-color: #ffffff;
}

.banner-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 200px;
  color: #999;
  font-size: 14px;
  gap: 8px;
}

.banner-placeholder i {
  font-size: 48px;
}

@container (max-width: 640px) {
  .banner-indicators {
    bottom: 12px;
    gap: 6px;
  }

  .indicator-dot {
    width: 6px;
    height: 6px;
  }

  .indicator-dot.is-active {
    width: 18px;
  }
}
</style>
