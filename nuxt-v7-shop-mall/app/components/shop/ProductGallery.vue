<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductGallery 组件元数据
 * 商品图片画廊，支持主图+缩略图切换、放大预览
 */
export const meta: ComponentMeta = {
  type: "product-gallery",
  name: "商品图片画廊",
  icon: "i-carbon-image",
  category: "business",
  description: "商品图片画廊，支持主图切换和放大预览",

  propsSchema: [
    {
      key: "images",
      label: "图片列表(静态)",
      type: "json",
      defaultValue: [],
      description: "留空则自动绑定产品图片",
    },
    {
      key: "showThumbnails",
      label: "显示缩略图",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "enableZoom",
      label: "启用放大",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "aspectRatio",
      label: "图片比例",
      type: "select",
      options: [
        { label: "1:1", value: "1:1" },
        { label: "4:3", value: "4:3" },
        { label: "16:9", value: "16:9" },
      ],
      defaultValue: "1:1",
    },
  ],

  styleSchema: [
    {
      key: "maxWidth",
      label: "最大宽度",
      type: "size",
      defaultValue: "100%",
      unit: "px",
    },
  ],

  supportEvents: ["click"],

  defaultProps: {
    images: [],
    showThumbnails: true,
    enableZoom: true,
    aspectRatio: "1:1",
  },

  defaultStyle: {
    base: {
      width: "100%",
    },
  },

  isContainer: false,
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
import { useDataContext } from "~/composables/useDataContext";
import { useIframeAuth } from "~/composables/useIframeAuth";

interface Props {
  images?: string[];
  showThumbnails?: boolean;
  enableZoom?: boolean;
  aspectRatio?: "1:1" | "4:3" | "16:9";
}

const props = withDefaults(defineProps<Props>(), {
  images: () => [],
  showThumbnails: true,
  enableZoom: true,
  aspectRatio: "1:1",
});

const emit = defineEmits<{
  (e: "click", event: MouseEvent): void;
}>();

// 数据上下文
const dataContext = useDataContext();
const { buildImageUrl } = useIframeAuth();

// 获取图片列表 - 优先使用 props，否则从 dataContext 获取
const imageList = computed(() => {
  if (props.images && props.images.length > 0) {
    return props.images;
  }
  // 从 dataContext.product.images 获取
  const productImages = dataContext.value.product?.images;
  if (productImages && productImages.length > 0) {
    return productImages.map((img) => buildImageUrl(img.relativePath));
  }
  return [];
});

// 当前选中的图片索引
const currentIndex = ref(0);
const showPreview = ref(false);

// 当前显示的图片
const currentImage = computed(() => {
  return imageList.value[currentIndex.value] || "";
});

// 计算宽高比样式
const aspectRatioStyle = computed(() => {
  const ratioMap: Record<string, string> = {
    "1:1": "1",
    "4:3": "4/3",
    "16:9": "16/9",
  };
  return { aspectRatio: ratioMap[props.aspectRatio] || "1" };
});

// 选择图片
function selectImage(index: number) {
  currentIndex.value = index;
}

// 打开预览
function openPreview() {
  if (props.enableZoom) {
    showPreview.value = true;
  }
}

// 关闭预览
function closePreview() {
  showPreview.value = false;
}

// 上一张
function prevImage() {
  if (imageList.value.length === 0) return;
  currentIndex.value = (currentIndex.value - 1 + imageList.value.length) % imageList.value.length;
}

// 下一张
function nextImage() {
  if (imageList.value.length === 0) return;
  currentIndex.value = (currentIndex.value + 1) % imageList.value.length;
}

// 键盘控制
onMounted(() => {
  const handleKeydown = (e: KeyboardEvent) => {
    if (!showPreview.value) return;
    if (e.key === "Escape") closePreview();
    if (e.key === "ArrowLeft") prevImage();
    if (e.key === "ArrowRight") nextImage();
  };
  window.addEventListener("keydown", handleKeydown);
  onUnmounted(() => {
    window.removeEventListener("keydown", handleKeydown);
  });
});
</script>

<template>
  <div class="product-gallery">
    <!-- 主图 -->
    <div
      class="main-image"
      :style="aspectRatioStyle"
      @click="openPreview"
    >
      <img
        v-if="currentImage"
        :src="currentImage"
        alt="产品图片"
        class="main-image-img"
      />
      <div v-else class="main-image-placeholder">
        <span class="i-carbon-image text-4xl text-gray-300"></span>
      </div>
      <div v-if="enableZoom && currentImage" class="image-zoom-hint">
        <span class="i-carbon-zoom-in"></span>
      </div>
    </div>

    <!-- 缩略图列表 -->
    <div v-if="showThumbnails && imageList.length > 1" class="thumbnail-list">
      <button
        v-for="(image, index) in imageList"
        :key="index"
        class="thumbnail-item"
        :class="{ active: currentIndex === index }"
        @click="selectImage(index)"
      >
        <img :src="image" :alt="`图片 ${index + 1}`" />
      </button>
    </div>

    <!-- 图片预览弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showPreview" class="image-preview-overlay" @click="closePreview">
          <div class="image-preview-content" @click.stop>
            <button class="preview-close" @click="closePreview">
              <span class="i-carbon-close"></span>
            </button>
            <button v-if="imageList.length > 1" class="preview-nav prev" @click="prevImage">
              <span class="i-carbon-chevron-left"></span>
            </button>
            <img :src="currentImage" alt="产品图片" class="preview-image" />
            <button v-if="imageList.length > 1" class="preview-nav next" @click="nextImage">
              <span class="i-carbon-chevron-right"></span>
            </button>
            <div v-if="imageList.length > 1" class="preview-counter">
              {{ currentIndex + 1 }} / {{ imageList.length }}
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.product-gallery {
  width: 100%;
}

.main-image {
  position: relative;
  background: #f8f9fa;
  border-radius: 8px;
  overflow: hidden;
  cursor: zoom-in;
}

.main-image-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.main-image-placeholder {
  width: 100%;
  height: 100%;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-zoom-hint {
  position: absolute;
  bottom: 12px;
  right: 12px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  color: white;
  font-size: 18px;
  opacity: 0;
  transition: opacity 0.2s;
}

.main-image:hover .image-zoom-hint {
  opacity: 1;
}

.thumbnail-list {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.thumbnail-item {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  border: 2px solid transparent;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s;
  background: #f8f9fa;
  padding: 0;
}

.thumbnail-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumbnail-item.active {
  border-color: var(--primary-color, #3b82f6);
}

.thumbnail-item:hover:not(.active) {
  border-color: #d1d5db;
}

/* 预览弹窗 */
.image-preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.9);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-preview-content {
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
}

.preview-image {
  max-width: 100%;
  max-height: 85vh;
  object-fit: contain;
}

.preview-close {
  position: absolute;
  top: -48px;
  right: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 50%;
  color: white;
  font-size: 20px;
  cursor: pointer;
  transition: background 0.2s;
}

.preview-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.preview-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 50%;
  color: white;
  font-size: 24px;
  cursor: pointer;
  transition: background 0.2s;
}

.preview-nav:hover {
  background: rgba(255, 255, 255, 0.2);
}

.preview-nav.prev {
  left: -64px;
}

.preview-nav.next {
  right: -64px;
}

.preview-counter {
  position: absolute;
  bottom: -40px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .preview-nav.prev {
    left: 8px;
  }

  .preview-nav.next {
    right: 8px;
  }
}

@media (max-width: 480px) {
  .thumbnail-item {
    width: 56px;
    height: 56px;
  }
}
</style>
