<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductRecommend 组件元数据
 * 相关推荐商品组件
 */
export const meta: ComponentMeta = {
  type: "product-recommend",
  name: "相关推荐",
  icon: "i-carbon-recommend",
  category: "business",
  description: "展示相关推荐商品列表",

  propsSchema: [
    {
      key: "products",
      label: "推荐商品",
      type: "json",
      defaultValue: [],
      description: "推荐商品列表数据",
    },
    {
      key: "title",
      label: "标题",
      type: "text",
      defaultValue: "相关推荐",
    },
    {
      key: "showTitle",
      label: "显示标题",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "columns",
      label: "每行数量",
      type: "number",
      defaultValue: 4,
    },
  ],

  styleSchema: [
    {
      key: "gap",
      label: "商品间距",
      type: "size",
      defaultValue: "16px",
      unit: "px",
    },
  ],

  supportEvents: ["click"],

  defaultProps: {
    products: [],
    title: "相关推荐",
    showTitle: true,
    columns: 4,
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
import { useIframeAuth } from "~/composables";

// 推荐商品类型
interface RecommendProduct {
  id: string;
  name: string;
  price: number;
  image: string;
  url?: string;
}

interface Props {
  products?: RecommendProduct[];
  title?: string;
  showTitle?: boolean;
  columns?: number;
}

const props = withDefaults(defineProps<Props>(), {
  products: () => [],
  title: "相关推荐",
  showTitle: true,
  columns: 4,
});

const emit = defineEmits<{
  (e: "click", product: RecommendProduct): void;
}>();

const { buildImageUrl } = useIframeAuth();

// 获取商品图片 URL
function getProductImage(image: string): string {
  if (!image) return "";
  // 如果是完整 URL 直接返回，否则使用 buildImageUrl
  if (image.startsWith("http://") || image.startsWith("https://")) {
    return image;
  }
  return buildImageUrl(image);
}

// 格式化价格
function formatPrice(value: number): string {
  return value.toFixed(2);
}

// 点击商品
function handleProductClick(product: RecommendProduct) {
  emit("click", product);
}
</script>

<template>
  <div class="product-recommend">
    <h3 v-if="showTitle && title" class="recommend-title">{{ title }}</h3>

    <div
      v-if="products.length"
      class="recommend-list"
      :style="{ '--columns': columns }"
    >
      <div
        v-for="product in products"
        :key="product.id"
        class="recommend-item"
        @click="handleProductClick(product)"
      >
        <div class="recommend-image">
          <img
            v-if="product.image"
            :src="getProductImage(product.image)"
            :alt="product.name"
          />
          <div v-else class="recommend-image-placeholder">
            <span class="i-carbon-image text-2xl text-gray-300"></span>
          </div>
        </div>
        <div class="recommend-info">
          <div class="recommend-name">{{ product.name }}</div>
          <div class="recommend-price">¥{{ formatPrice(product.price) }}</div>
        </div>
      </div>
    </div>

    <div v-else class="recommend-empty">暂无推荐商品</div>
  </div>
</template>

<style scoped>
.product-recommend {
  width: 100%;
}

.recommend-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 16px 0;
}

.recommend-list {
  display: grid;
  grid-template-columns: repeat(var(--columns, 4), 1fr);
  gap: 16px;
}

.recommend-item {
  cursor: pointer;
  transition: transform 0.2s;
}

.recommend-item:hover {
  transform: translateY(-4px);
}

.recommend-image {
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  background: #f8f9fa;
  margin-bottom: 8px;
}

.recommend-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recommend-image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recommend-info {
  padding: 0 4px;
}

.recommend-name {
  font-size: 13px;
  color: #374151;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 4px;
}

.recommend-price {
  font-size: 14px;
  color: #ef4444;
  font-weight: 500;
}

.recommend-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: #9ca3af;
  font-size: 14px;
}

/* 响应式 */
@media (max-width: 768px) {
  .recommend-list {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 480px) {
  .recommend-list {
    grid-template-columns: repeat(2, 1fr);
  }

  .recommend-name {
    font-size: 12px;
  }

  .recommend-price {
    font-size: 13px;
  }
}
</style>
