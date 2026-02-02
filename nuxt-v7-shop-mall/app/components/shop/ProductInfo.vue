<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductInfo 组件元数据
 * 商品基本信息展示：名称、价格、描述
 */
export const meta: ComponentMeta = {
  type: "product-info",
  name: "商品信息",
  icon: "i-carbon-information",
  category: "business",
  description: "展示商品名称、价格、描述等基本信息",

  propsSchema: [
    {
      key: "title",
      label: "商品名称(静态)",
      type: "text",
      defaultValue: "",
      description: "留空则自动绑定产品标题",
    },
    {
      key: "price",
      label: "价格(静态)",
      type: "number",
      defaultValue: null,
      description: "留空则自动绑定产品价格",
    },
    {
      key: "originalPrice",
      label: "原价(静态)",
      type: "number",
      defaultValue: null,
    },
    {
      key: "description",
      label: "描述(静态)",
      type: "textarea",
      defaultValue: "",
      description: "留空则自动绑定产品介绍",
    },
    {
      key: "showDiscount",
      label: "显示折扣",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "currencySymbol",
      label: "货币符号",
      type: "text",
      defaultValue: "¥",
    },
  ],

  styleSchema: [
    {
      key: "titleSize",
      label: "标题字号",
      type: "size",
      defaultValue: "24px",
      unit: "px",
    },
    {
      key: "priceSize",
      label: "价格字号",
      type: "size",
      defaultValue: "28px",
      unit: "px",
    },
  ],

  supportEvents: ["click"],

  defaultProps: {
    title: "",
    price: null,
    originalPrice: null,
    description: "",
    showDiscount: true,
    currencySymbol: "¥",
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

interface Props {
  title?: string;
  price?: number | null;
  originalPrice?: number | null;
  description?: string;
  showDiscount?: boolean;
  currencySymbol?: string;
}

const props = withDefaults(defineProps<Props>(), {
  title: "",
  price: null,
  originalPrice: null,
  description: "",
  showDiscount: true,
  currencySymbol: "¥",
});

const emit = defineEmits<{
  (e: "click", event: MouseEvent): void;
}>();

// 数据上下文
const dataContext = useDataContext();

// 计算属性 - 优先使用 props，否则从 dataContext 获取
const productTitle = computed(() => {
  return props.title || dataContext.value.product?.title || "";
});

const productPrice = computed(() => {
  return props.price ?? dataContext.value.product?.sellPrice ?? 0;
});

const productOriginalPrice = computed(() => {
  return props.originalPrice ?? dataContext.value.product?.originPrice ?? null;
});

const productDescription = computed(() => {
  return props.description || dataContext.value.product?.introduction || "";
});

// 计算折扣比例
const discountPercent = computed(() => {
  if (!productOriginalPrice.value || productOriginalPrice.value <= productPrice.value) {
    return null;
  }
  return Math.round((1 - productPrice.value / productOriginalPrice.value) * 100);
});

// 格式化价格
function formatPrice(value: number): string {
  return value.toFixed(2);
}
</script>

<template>
  <div class="product-info">
    <!-- 商品名称 -->
    <h1 v-if="productTitle" class="product-name">{{ productTitle }}</h1>

    <!-- 商品描述 -->
    <p v-if="productDescription" class="product-description">
      {{ productDescription }}
    </p>

    <!-- 价格区域 -->
    <div class="price-section">
      <span class="current-price">
        <span class="currency">{{ currencySymbol }}</span>
        <span class="price-value">{{ formatPrice(productPrice) }}</span>
      </span>
      <span v-if="productOriginalPrice" class="original-price">
        {{ currencySymbol }}{{ formatPrice(productOriginalPrice) }}
      </span>
      <span v-if="showDiscount && discountPercent" class="discount-tag">
        {{ discountPercent }}% OFF
      </span>
    </div>
  </div>
</template>

<style scoped>
.product-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-name {
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
  margin: 0;
}

.product-description {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.6;
  margin: 0;
}

.price-section {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.current-price {
  color: #ef4444;
  font-weight: 600;
}

.currency {
  font-size: 16px;
}

.price-value {
  font-size: 28px;
}

.original-price {
  font-size: 14px;
  color: #9ca3af;
  text-decoration: line-through;
}

.discount-tag {
  font-size: 12px;
  color: #ef4444;
  background: #fef2f2;
  padding: 2px 8px;
  border-radius: 4px;
}

/* 响应式 */
@media (max-width: 768px) {
  .product-name {
    font-size: 20px;
  }

  .price-value {
    font-size: 24px;
  }
}

@media (max-width: 480px) {
  .product-name {
    font-size: 18px;
  }

  .price-value {
    font-size: 22px;
  }
}
</style>
