<script setup lang="ts">
/**
 * ProductGallery - 商品图片轮播组件
 * 支持多种指示器样式和自动播放
 */

interface ProductImage {
  id: number
  relativePath: string
  name?: string
}

interface Props {
  images?: ProductImage[]
  previewImage?: string | null
  indicatorStyle?: 'dots' | 'numbers' | 'thumbnails' | 'none'
  indicatorPosition?: 'bottom' | 'outside'
  autoplay?: boolean
  autoplayInterval?: number
  showThumbnails?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  images: () => [],
  previewImage: null,
  indicatorStyle: 'dots',
  indicatorPosition: 'bottom',
  autoplay: false,
  autoplayInterval: 3000,
  showThumbnails: true,
})

const currentIndex = ref(0)
const carouselRef = ref<HTMLElement | null>(null)
const thumbnailsRef = ref<HTMLElement | null>(null)
const galleryRef = ref<HTMLElement | null>(null)
let autoplayTimer: ReturnType<typeof setInterval> | null = null
let observer: IntersectionObserver | null = null
const isInViewport = ref(true)
const { t } = useI18n()

// 是否正在显示预览图片
const isShowingPreview = ref(false)

const hasImages = computed(() => props.images.length > 0)
const imageCount = computed(() => props.images.length)

// 当预览图片变化时，临时显示预览图片
watch(() => props.previewImage, (newPreviewImage) => {
  if (newPreviewImage) {
    isShowingPreview.value = true
  }
})

function goToSlide(index: number) {
  // 用户手动切换时，退出预览模式
  isShowingPreview.value = false
  
  if (index < 0) {
    currentIndex.value = imageCount.value - 1
  } else if (index >= imageCount.value) {
    currentIndex.value = 0
  } else {
    currentIndex.value = index
  }
  scrollToCurrentSlide()
  scrollThumbnailIntoView(currentIndex.value)
}

function scrollThumbnailIntoView(index: number) {
  if (!thumbnailsRef.value) return
  const thumbnails = thumbnailsRef.value.querySelectorAll('.thumbnail-item')
  const target = thumbnails[index] as HTMLElement
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
  }
}

function scrollToCurrentSlide() {
  if (carouselRef.value) {
    const slideWidth = carouselRef.value.offsetWidth
    carouselRef.value.scrollTo({
      left: currentIndex.value * slideWidth,
      behavior: 'smooth',
    })
  }
}

function handleScroll() {
  // 用户滑动时，退出预览模式
  isShowingPreview.value = false
  
  if (carouselRef.value) {
    const slideWidth = carouselRef.value.offsetWidth
    const scrollLeft = carouselRef.value.scrollLeft
    const newIndex = Math.round(scrollLeft / slideWidth)
    if (newIndex !== currentIndex.value && newIndex >= 0 && newIndex < imageCount.value) {
      currentIndex.value = newIndex
      scrollThumbnailIntoView(newIndex)
    }
  }
}

function startAutoplay() {
  if (props.autoplay && imageCount.value > 1 && isInViewport.value) {
    stopAutoplay()
    autoplayTimer = setInterval(() => {
      goToSlide(currentIndex.value + 1)
    }, props.autoplayInterval)
  }
}

function stopAutoplay() {
  if (autoplayTimer) {
    clearInterval(autoplayTimer)
    autoplayTimer = null
  }
}

function setupIntersectionObserver() {
  if (!galleryRef.value) return

  observer = new IntersectionObserver(
    (entries) => {
      const entry = entries[0]
      isInViewport.value = entry.isIntersecting

      if (entry.isIntersecting) {
        startAutoplay()
      } else {
        stopAutoplay()
      }
    },
    { threshold: 0.1 },
  )

  observer.observe(galleryRef.value)
}

onMounted(() => {
  setupIntersectionObserver()
  if (isInViewport.value) {
    startAutoplay()
  }
})

onUnmounted(() => {
  stopAutoplay()
  if (observer) {
    observer.disconnect()
    observer = null
  }
})

watch(() => props.autoplay, (newVal) => {
  if (newVal && isInViewport.value) {
    startAutoplay()
  } else {
    stopAutoplay()
  }
})

watch(() => props.images, () => {
  currentIndex.value = 0
})
</script>

<template>
  <div class="product-gallery">
    <div class="product-carousel-wrapper">
      <!-- 预览图片（规格选中时临时显示） -->
      <div
        v-show="isShowingPreview && previewImage"
        class="preview-slide"
      >
        <BlockBasicImage
          :src="previewImage"
          :alt="t('product.specPreview')"
          object-fit="cover"
          class="carousel-image"
        />
      </div>
      
      <!-- 原始轮播图 -->
      <div
        v-show="hasImages && !(isShowingPreview && previewImage)"
        ref="carouselRef"
        class="product-carousel"
        @scroll="handleScroll"
        @mouseenter="stopAutoplay"
        @mouseleave="startAutoplay"
      >
        <div
          v-for="(image, index) in images"
          :key="image.id"
          class="carousel-slide"
        >
          <BlockBasicImage
            :src="image.relativePath"
            :alt="image.name || `${t('product.productImage')} ${index + 1}`"
            object-fit="cover"
            class="carousel-image"
          />
        </div>
      </div>
      
      <!-- 无图片提示 -->
      <div v-show="!hasImages && !(isShowingPreview && previewImage)" class="product-no-image">
        <i class="i-carbon-image" />
        <span>{{ t('product.noImage') }}</span>
      </div>

      <!-- 指示器 - 底部位置 -->
      <div
        v-if="indicatorStyle !== 'none' && indicatorPosition === 'bottom' && imageCount > 1"
        class="carousel-indicators indicators-bottom"
      >
        <!-- 圆点指示器 -->
        <template v-if="indicatorStyle === 'dots'">
          <button
            v-for="(_, index) in images"
            :key="index"
            class="indicator-dot"
            :class="{ active: index === currentIndex }"
            @click="goToSlide(index)"
          />
        </template>

        <!-- 数字指示器 -->
        <template v-else-if="indicatorStyle === 'numbers'">
          <span class="indicator-numbers">
            {{ currentIndex + 1 }} / {{ imageCount }}
          </span>
        </template>
      </div>
    </div>

    <!-- 缩略图指示器 - 外部位置 -->
    <div
      v-if="showThumbnails && imageCount > 1"
      ref="thumbnailsRef"
      class="carousel-thumbnails"
    >
      <button
        v-for="(image, index) in images"
        :key="image.id"
        class="thumbnail-item"
        :class="{ active: index === currentIndex }"
        @click="goToSlide(index)"
      >
        <BlockBasicImage
          :src="image.relativePath"
          :alt="image.name"
          object-fit="cover"
        />
      </button>
    </div>

    <!-- 外部指示器（非缩略图） -->
    <div
      v-if="indicatorStyle !== 'none' && indicatorStyle !== 'thumbnails' && indicatorPosition === 'outside' && imageCount > 1"
      class="carousel-indicators indicators-outside"
    >
      <template v-if="indicatorStyle === 'dots'">
        <button
          v-for="(_, index) in images"
          :key="index"
          class="indicator-dot"
          :class="{ active: index === currentIndex }"
          @click="goToSlide(index)"
        />
      </template>
      <template v-else-if="indicatorStyle === 'numbers'">
        <span class="indicator-numbers">
          {{ currentIndex + 1 }} / {{ imageCount }}
        </span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.product-gallery {
  width: 100%;
}

.product-carousel-wrapper {
  position: relative;
  width: 100%;
  max-width: var(--product-image-max-width, 500px);
  overflow: hidden;
  border-radius: var(--product-image-radius, 8px);
  background-color: var(--product-image-bg, #f5f5f5);
}

.product-carousel {
  display: flex;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.product-carousel::-webkit-scrollbar {
  display: none;
}

.carousel-slide {
  flex: 0 0 100%;
  scroll-snap-align: start;
  aspect-ratio: 1;
}

.preview-slide {
  aspect-ratio: 1;
}

.carousel-image {
  width: 100%;
  height: 100%;
}

.product-no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  color: #999;
  font-size: 14px;
  gap: 8px;
}

.product-no-image i {
  font-size: 48px;
}

/* 指示器通用样式 */
.carousel-indicators {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.indicators-bottom {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  padding: 6px 12px;
  background-color: rgba(0, 0, 0, 0.3);
  border-radius: 16px;
}

.indicators-outside {
  margin-top: 12px;
}

.indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: none;
  padding: 0;
  cursor: pointer;
  background-color: var(--product-indicator-color, rgba(255, 255, 255, 0.5));
  transition: all 0.2s;
}

.indicator-dot.active {
  width: 24px;
  border-radius: 4px;
  background-color: var(--product-indicator-active-color, #fff);
}

.indicators-outside .indicator-dot {
  background-color: var(--product-indicator-color, #ddd);
}

.indicators-outside .indicator-dot.active {
  background-color: var(--product-indicator-active-color, var(--primary-color, #3b82f6));
}

.indicator-numbers {
  font-size: 12px;
  color: #fff;
  font-weight: 500;
}

.indicators-outside .indicator-numbers {
  color: var(--text-color, #333);
}

/* 缩略图指示器 */
.carousel-thumbnails {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  overflow-x: auto;
  padding: 4px 0;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.carousel-thumbnails::-webkit-scrollbar {
  display: none;
}

.thumbnail-item {
  flex-shrink: 0;
  width: 60px;
  height: 60px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  background: none;
  transition: border-color 0.2s;
}

.thumbnail-item.active {
  border-color: var(--product-indicator-active-color, var(--primary-color, #3b82f6));
}

.thumbnail-item :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
