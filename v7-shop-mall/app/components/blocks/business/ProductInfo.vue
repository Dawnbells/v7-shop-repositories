<script setup lang="ts">
/**
 * ProductInfo Block - 商品信息组件
 * 显示商品标题、主图轮播、商品简介、原价和真实价格
 * 数据从 useProductPage 中获取
 */

import Decimal from "decimal.js";

interface Props {
  showSummary?: boolean;
  showOriginPrice?: boolean;
  indicatorStyle?: "dots" | "numbers" | "thumbnails" | "none";
  indicatorPosition?: "bottom" | "outside";
  autoplay?: boolean;
  autoplayInterval?: number;
  layout?: "horizontal" | "vertical";
  showThumbnails?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showSummary: true,
  showOriginPrice: true,
  indicatorStyle: "dots",
  indicatorPosition: "bottom",
  autoplay: false,
  autoplayInterval: 3000,
  layout: "horizontal",
  showThumbnails: true,
});

const { productInfo, selectedSpec, formatPrice } = useProductPage();

const images = computed(() => productInfo.value?.images || []);
const title = computed(() => productInfo.value?.title || "");
const summary = computed(() => productInfo.value?.summary || "");

// 价格逻辑：优先使用选中规格的价格，其次使用第一个规格，最后使用商品默认价格
const sellPrice = computed(() => {
  // 优先使用选中规格的价格
  if (selectedSpec.value) {
    return selectedSpec.value.sellPrice;
  }
  const info = productInfo.value;
  if (!info) return 0;
  // 多规格商品使用第一个规格的价格
  const firstSpec = info.isMultiSpecs ? info.specifications?.[0] : null;
  if (firstSpec) {
    return firstSpec.sellPrice;
  }
  return info.sellPrice;
});

const originPrice = computed(() => {
  // 优先使用选中规格的原价
  if (selectedSpec.value) {
    return selectedSpec.value.originPrice;
  }
  const info = productInfo.value;
  if (!info) return null;
  // 多规格商品使用第一个规格的原价
  const firstSpec = info.isMultiSpecs ? info.specifications?.[0] : null;
  if (firstSpec) {
    return firstSpec.originPrice;
  }
  return info.originPrice;
});

const hasOriginPrice = computed(() => {
  if (!props.showOriginPrice) return false;
  if (originPrice.value === null || originPrice.value === undefined) return false;
  const origin = new Decimal(originPrice.value);
  const sell = new Decimal(sellPrice.value);
  return origin.greaterThan(sell);
});
</script>

<template>
  <div class="block-product-info" :class="[`layout-${props.layout}`]">
    <!-- 图片区域 -->
    <CommonProductGallery
      :images="images"
      :indicator-style="indicatorStyle"
      :indicator-position="indicatorPosition"
      :autoplay="autoplay"
      :autoplay-interval="autoplayInterval"
      :show-thumbnails="showThumbnails"
      class="product-gallery"
    />

    <!-- 商品信息区域（右侧垂直容器） -->
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

      <!-- 子组件插槽（可拖入规格选择等组件） -->
      <div class="product-slot">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.block-product-info {
  container-type: inline-size;
  width: 100%;
  max-width: var(--product-max-width, 1200px);
  margin: 0 auto;
  padding: var(--product-padding, 0 16px);
  box-sizing: border-box;
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

.product-slot {
  display: flex;
  flex-direction: column;
  gap: var(--product-slot-gap, 16px);
}

.product-slot:empty {
  display: none;
}

/* 左右布局 - 默认 PC 端样式 */
.layout-horizontal {
  display: flex;
  gap: var(--product-gap, 40px);
  align-items: flex-start;
}

.layout-horizontal .product-gallery {
  flex: 0 0 var(--product-image-width, 45%);
  max-width: var(--product-image-width, 45%);
}

.layout-horizontal :deep(.product-carousel-wrapper) {
  max-width: 100%;
}

.layout-horizontal .product-details {
  flex: 1;
  padding: var(--product-details-padding-desktop, 0);
}

.layout-horizontal :deep(.carousel-thumbnails) {
  flex-wrap: wrap;
}

.layout-horizontal :deep(.thumbnail-item) {
  width: 72px;
  height: 72px;
}

/* 左右布局 - 移动端自动降级为上下布局 */
@container (max-width: 767px) {
  .layout-horizontal {
    display: block;
  }

  .layout-horizontal .product-gallery {
    flex: none;
    max-width: 100%;
  }

  .layout-horizontal :deep(.product-carousel-wrapper) {
    max-width: 100%;
  }

  .layout-horizontal .product-details {
    padding: var(--product-details-padding, 16px 0);
  }

  .layout-horizontal .product-title {
    font-size: var(--product-title-size-mobile, 18px);
  }

  .layout-horizontal .product-price {
    font-size: var(--product-price-size-mobile, 20px);
  }

  .layout-horizontal :deep(.thumbnail-item) {
    width: 48px;
    height: 48px;
  }
}

/* 上下布局 - 始终垂直排列 */
.layout-vertical {
  display: block;
}

.layout-vertical :deep(.product-carousel-wrapper) {
  max-width: 100%;
}

.layout-vertical .product-title {
  font-size: var(--product-title-size-mobile, 18px);
}

.layout-vertical .product-price {
  font-size: var(--product-price-size-mobile, 20px);
}

.layout-vertical :deep(.thumbnail-item) {
  width: 48px;
  height: 48px;
}
</style>
