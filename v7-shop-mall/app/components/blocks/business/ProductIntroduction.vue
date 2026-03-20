<script setup lang="ts">
/**
 * ProductIntroduction Block - 商品详情组件
 * 解析并渲染商品 introduction 字段中的图片和 HTML 内容
 * 图片根据数据库中的宽高比自适应显示，确保视窗内完整展示
 */

import type { IntroductionItem } from "~/types/product";

const { productInfo } = useProductPage();
const { t } = useI18n();

const introductionData = computed<IntroductionItem[]>(
  () => productInfo.value?.introductionData || [],
);

const hasContent = computed(() => introductionData.value.length > 0);

function getImageStyle(item: IntroductionItem): Record<string, string> {
  const style: Record<string, string> = {};
  if (item.aspectRatio && item.aspectRatio > 0) {
    style["--img-aspect-ratio"] = String(item.aspectRatio);
  }
  return style;
}
</script>

<template>
  <div class="block-product-introduction">
    <template v-if="hasContent">
      <template v-for="(item, index) in introductionData" :key="index">
        <div
          v-if="item.type === 'image'"
          class="intro-image-wrapper"
          :style="getImageStyle(item)"
        >
          <BlockBasicImage
            :src="item.src"
            :alt="`Product detail image ${index + 1}`"
            object-fit="contain"
            class="intro-image"
          />
        </div>

        <div
          v-else-if="item.type === 'html' && item.content"
          class="intro-html-content"
          v-html="item.content"
        />
      </template>
    </template>

    <div v-else class="intro-empty">
      <span class="empty-text">{{ t('product.noDetail') }}</span>
    </div>
  </div>
</template>

<style scoped>
.block-product-introduction {
  width: 100%;
  max-width: var(--intro-max-width, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--intro-image-gap, 1px);
}

.intro-image-wrapper {
  width: 100%;
  aspect-ratio: var(--img-aspect-ratio, auto);
  overflow: hidden;
  background-color: var(--intro-placeholder-bg, #f5f5f5);
}

.intro-image {
  width: 100%;
  height: 100%;
  display: block;
}

.intro-image :deep(.block-image) {
  object-fit: contain;
}

.intro-image :deep(.block-image-placeholder) {
  min-height: 200px;
  background-color: var(--intro-placeholder-bg, #f5f5f5);
}

.intro-html-content {
  width: 100%;
  padding: var(--intro-html-padding, 0 0);
  font-size: var(--intro-html-font-size, 14px);
  line-height: var(--intro-html-line-height, 1.6);
  color: var(--intro-html-color, var(--text-color, #333));
}

.intro-html-content :deep(p) {
  margin: 0;
  /* padding: 4px 0; */
}

.intro-html-content :deep(ul),
.intro-html-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.intro-html-content :deep(li) {
  margin: 4px 0;
}

.intro-html-content :deep(strong) {
  font-weight: 600;
}

.intro-empty {
  width: 100%;
  padding: 40px 16px;
  text-align: center;
}

.empty-text {
  color: var(--text-color-secondary, #999);
  font-size: 14px;
}
</style>
