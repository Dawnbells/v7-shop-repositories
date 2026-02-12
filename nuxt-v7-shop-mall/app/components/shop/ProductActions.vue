<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductActions 组件元数据
 * 购买按钮组件，包含加入购物车和立即购买
 */
export const meta: ComponentMeta = {
  type: "product-actions",
  name: "购买按钮",
  icon: "i-carbon-shopping-cart",
  category: "business",
  description: "购买操作按钮，支持加入购物车和立即购买",

  propsSchema: [
    {
      key: "cartText",
      label: "加购按钮文字",
      type: "text",
      defaultValue: "加入购物车",
    },
    {
      key: "buyText",
      label: "购买按钮文字",
      type: "text",
      defaultValue: "立即购买",
    },
    {
      key: "showCart",
      label: "显示加购按钮",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "showBuy",
      label: "显示购买按钮",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "layout",
      label: "布局",
      type: "select",
      options: [
        { label: "横向", value: "horizontal" },
        { label: "纵向", value: "vertical" },
      ],
      defaultValue: "horizontal",
    },
  ],

  styleSchema: [
    {
      key: "gap",
      label: "按钮间距",
      type: "size",
      defaultValue: "12px",
      unit: "px",
    },
  ],

  supportEvents: ["addToCart", "buy"],

  defaultProps: {
    cartText: "加入购物车",
    buyText: "立即购买",
    showCart: true,
    showBuy: true,
    layout: "horizontal",
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
import { useThemeSchema } from "~/composables";

interface Props {
  cartText?: string;
  buyText?: string;
  showCart?: boolean;
  showBuy?: boolean;
  layout?: "horizontal" | "vertical";
}

const props = withDefaults(defineProps<Props>(), {
  cartText: "加入购物车",
  buyText: "立即购买",
  showCart: true,
  showBuy: true,
  layout: "horizontal",
});

const emit = defineEmits<{
  (e: "addToCart"): void;
  (e: "buy"): void;
}>();

// 全局配置
const { siteConfig } = useThemeSchema();

// 是否显示加购按钮 - 同时满足 props.showCart 和全局配置 enableCart
const showCartButton = computed(() => {
  return props.showCart && siteConfig.value?.enableCart !== false;
});

// 加入购物车
function handleAddToCart() {
  emit("addToCart");
}

// 立即购买
function handleBuy() {
  emit("buy");
}
</script>

<template>
  <div
    class="product-actions"
    :class="[`layout-${layout}`]"
  >
    <button
      v-if="showCartButton"
      class="btn-cart"
      @click="handleAddToCart"
    >
      <span class="i-carbon-shopping-cart"></span>
      {{ cartText }}
    </button>
    <button
      v-if="showBuy"
      class="btn-buy"
      @click="handleBuy"
    >
      {{ buyText }}
    </button>
  </div>
</template>

<style scoped>
.product-actions {
  display: flex;
  gap: 12px;
}

.product-actions.layout-vertical {
  flex-direction: column;
}

.btn-cart,
.btn-buy {
  flex: 1;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cart {
  background: white;
  border: 1px solid var(--primary-color, #3b82f6);
  color: var(--primary-color, #3b82f6);
}

.btn-cart:hover {
  background: #eff6ff;
}

.btn-buy {
  background: var(--primary-color, #3b82f6);
  border: none;
  color: white;
}

.btn-buy:hover {
  background: #2563eb;
}

/* 纵向布局时按钮全宽 */
.product-actions.layout-vertical .btn-cart,
.product-actions.layout-vertical .btn-buy {
  width: 100%;
}

/* 响应式 */
@media (max-width: 768px) {
  .product-actions {
    flex-direction: column;
  }

  .btn-cart,
  .btn-buy {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .btn-cart,
  .btn-buy {
    height: 44px;
    font-size: 15px;
  }
}
</style>
