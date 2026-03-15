<script setup lang="ts">
/**
 * ProductInfo Block - 商品信息组件
 * 显示商品标题、主图轮播、商品简介、原价和真实价格
 * 数据从 pageContext 中获取（04-product.ts 中间件注入）
 */

interface Props {
  showSummary?: boolean
  showOriginPrice?: boolean
  indicatorStyle?: 'dots' | 'numbers' | 'thumbnails' | 'none'
  indicatorPosition?: 'bottom' | 'outside'
  autoplay?: boolean
  autoplayInterval?: number
}

const props = withDefaults(defineProps<Props>(), {
  showSummary: true,
  showOriginPrice: true,
  indicatorStyle: 'dots',
  indicatorPosition: 'bottom',
  autoplay: false,
  autoplayInterval: 3000,
})

const { productInfo } = usePageContext()

const currentIndex = ref(0)
const carouselRef = ref<HTMLElement | null>(null)
let autoplayTimer: ReturnType<typeof setInterval> | null = null

const images = computed(() => productInfo.value?.images || [])
const hasImages = computed(() => images.value.length > 0)
const imageCount = computed(() => images.value.length)

const title = computed(() => productInfo.value?.title || '')
const summary = computed(() => productInfo.value?.summary || '')
const sellPrice = computed(() => productInfo.value?.sellPrice || 0)
const originPrice = computed(() => productInfo.value?.originPrice)

const hasOriginPrice = computed(() => 
  props.showOriginPrice && originPrice.value !== null && originPrice.value !== undefined && originPrice.value > sellPrice.value
)

function formatPrice(price: number): string {
  return `$${price.toFixed(2)}`
}

function goToSlide(index: number) {
  if (index < 0) {
    currentIndex.value = imageCount.value - 1
  } else if (index >= imageCount.value) {
    currentIndex.value = 0
  } else {
    currentIndex.value = index
  }
  scrollToCurrentSlide()
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
  if (carouselRef.value) {
    const slideWidth = carouselRef.value.offsetWidth
    const scrollLeft = carouselRef.value.scrollLeft
    const newIndex = Math.round(scrollLeft / slideWidth)
    if (newIndex !== currentIndex.value && newIndex >= 0 && newIndex < imageCount.value) {
      currentIndex.value = newIndex
    }
  }
}

function startAutoplay() {
  if (props.autoplay && imageCount.value > 1) {
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

onMounted(() => {
  startAutoplay()
})

onUnmounted(() => {
  stopAutoplay()
})

watch(() => props.autoplay, (newVal) => {
  if (newVal) {
    startAutoplay()
  } else {
    stopAutoplay()
  }
})
</script>

<template>
  <div class="block-product-info">
    <!-- 图片轮播区域 -->
    <div class="product-carousel-wrapper">
      <div
        v-if="hasImages"
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
          <img
            :src="image.relativePath"
            :alt="image.name || `商品图片 ${index + 1}`"
            class="carousel-image"
          />
        </div>
      </div>
      <div v-else class="product-no-image">
        <i class="i-carbon-image" />
        <span>暂无图片</span>
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
      v-if="indicatorStyle === 'thumbnails' && indicatorPosition === 'outside' && imageCount > 1"
      class="carousel-thumbnails"
    >
      <button
        v-for="(image, index) in images"
        :key="image.id"
        class="thumbnail-item"
        :class="{ active: index === currentIndex }"
        @click="goToSlide(index)"
      >
        <img :src="image.relativePath" :alt="image.name" />
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

    <!-- 商品信息区域 -->
    <div class="product-details">
      <!-- 商品标题 -->
      <h1 class="product-title">{{ title }}</h1>

      <!-- 价格区域 -->
      <div class="product-price-wrapper">
        <span class="product-price">{{ formatPrice(sellPrice) }}</span>
        <span v-if="hasOriginPrice" class="product-origin-price">
          {{ formatPrice(originPrice!) }}
        </span>
      </div>

      <!-- 商品简介 -->
      <p v-if="showSummary && summary" class="product-summary">
        {{ summary }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.block-product-info {
  container-type: inline-size;
  width: 100%;
}

/* 轮播图容器 */
.product-carousel-wrapper {
  position: relative;
  width: 100%;
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

.carousel-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

.thumbnail-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 商品详情区域 */
.product-details {
  padding: var(--product-details-padding, 16px 0);
}

.product-title {
  margin: 0 0 12px 0;
  font-size: var(--product-title-size, 20px);
  font-weight: var(--product-title-weight, 600);
  color: var(--product-title-color, var(--text-color, #1f2937));
  line-height: 1.4;
}

.product-price-wrapper {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.product-price {
  font-size: var(--product-price-size, 24px);
  font-weight: var(--product-price-weight, 700);
  color: var(--product-price-color, var(--primary-color, #3b82f6));
}

.product-origin-price {
  font-size: var(--product-origin-price-size, 14px);
  color: var(--product-origin-price-color, #9ca3af);
  text-decoration: line-through;
}

.product-summary {
  margin: 0;
  font-size: var(--product-summary-size, 14px);
  color: var(--product-summary-color, #6b7280);
  line-height: 1.6;
}

/* 响应式 - 移动端 */
@container (max-width: 640px) {
  .product-title {
    font-size: var(--product-title-size-mobile, 18px);
  }

  .product-price {
    font-size: var(--product-price-size-mobile, 20px);
  }

  .thumbnail-item {
    width: 48px;
    height: 48px;
  }
}
</style>
