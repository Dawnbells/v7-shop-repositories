<script setup lang="ts">
/**
 * CheckoutOrderItems Block - 结算商品列表组件
 * 显示待结算的商品信息（图片、名称、规格、价格、数量）
 */

interface Props {
  showImage?: boolean;
  showSpec?: boolean;
  showQuantity?: boolean;
  showPrice?: boolean;
  imageSize?: "small" | "medium" | "large";
}

const props = withDefaults(defineProps<Props>(), {
  showImage: true,
  showSpec: true,
  showQuantity: true,
  showPrice: true,
  imageSize: "medium",
});

const { checkoutItems, formatSpecAttributes, formatPrice } = useCheckoutPage();

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));
const { t } = useI18n();

// 编辑器预览数据
const previewItems = [
  {
    id: "preview-1",
    productId: 1,
    productName: "示例商品名称",
    specId: 1,
    specAttributes: [
      { name: "颜色", value: "黑色" },
      { name: "尺寸", value: "M" },
    ],
    price: 199.0,
    quantity: 2,
    image: "",
  },
  {
    id: "preview-2",
    productId: 2,
    productName: "另一个商品",
    specId: null,
    specAttributes: [],
    price: 99.0,
    quantity: 1,
    image: "",
  },
];

// 显示的商品列表
const displayItems = computed(() => {
  if (isInEditor.value) {
    return previewItems;
  }
  return checkoutItems.value;
});

// 图片尺寸类
const imageSizeClass = computed(() => `image-${props.imageSize}`);
</script>

<template>
  <div class="block-checkout-order-items">
    <div v-if="displayItems.length === 0" class="empty-items">
      <i class="i-carbon-shopping-cart empty-icon" />
      <span class="empty-text">{{ t('checkout.noItems') }}</span>
    </div>

    <div v-else class="order-items-list">
      <div v-for="item in displayItems" :key="item.id" class="order-item">
        <!-- 商品图片 -->
        <div v-if="showImage" class="item-image" :class="imageSizeClass">
          <BlockBasicImage
            :src="item.image"
            :alt="item.productName"
            object-fit="cover"
          />
        </div>

        <!-- 商品信息 -->
        <div class="item-info">
          <div class="item-name" :title="item.productName">{{ item.productName }}</div>
          <template v-if="showSpec && item.specAttributes?.length">
            <div
              v-for="(attr, idx) in item.specAttributes"
              :key="idx"
              class="item-spec-line"
              :title="`${attr.name}: ${attr.value}`"
            >
              {{ attr.name }}: {{ attr.value }}
            </div>
          </template>
          <div class="item-unit-price">
            {{ formatPrice(item.price) }}
          </div>
        </div>

        <!-- 数量 -->
        <div v-if="showQuantity" class="item-quantity">
          x{{ item.quantity }}
        </div>

        <!-- 价格 -->
        <div v-if="showPrice" class="item-price">
          {{ formatPrice(item.price * item.quantity) }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.block-checkout-order-items {
  width: 100%;
  container-type: inline-size;
}

.empty-items {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--checkout-items-empty-padding, 40px 20px);
  color: var(--text-secondary-color, #9ca3af);
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.empty-text {
  font-size: 14px;
}

.order-items-list {
  display: flex;
  flex-direction: column;
  gap: var(--checkout-items-gap, 16px);
}

.order-item {
  display: flex;
  align-items: center;
  gap: var(--checkout-items-item-gap, 16px);
  padding: var(--checkout-items-item-padding, 12px 0);
  border-bottom: 1px solid var(--border-color, #f3f4f6);
}

.order-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.item-image {
  flex-shrink: 0;
  border-radius: var(--checkout-items-image-radius, 8px);
  overflow: hidden;
  background-color: var(--background-color, #f3f4f6);
}

.item-image.image-small {
  width: 48px;
  height: 48px;
}

.item-image.image-medium {
  width: 64px;
  height: 64px;
}

.item-image.image-large {
  width: 80px;
  height: 80px;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: var(--checkout-items-name-size, 14px);
  font-weight: var(--checkout-items-name-weight, 500);
  color: var(--text-color, #1f2937);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-spec-line {
  font-size: var(--checkout-items-spec-size, 12px);
  color: var(--text-secondary-color, #6b7280);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-spec-line:first-of-type {
  margin-top: 4px;
}

.item-unit-price {
  font-size: var(--checkout-items-unit-price-size, 12px);
  color: var(--text-secondary-color, #6b7280);
  margin-top: 2px;
}

.item-quantity {
  font-size: var(--checkout-items-quantity-size, 14px);
  color: var(--text-secondary-color, #6b7280);
  flex-shrink: 0;
}

.item-price {
  font-size: var(--checkout-items-price-size, 14px);
  font-weight: var(--checkout-items-price-weight, 600);
  color: var(--text-color, #1f2937);
  flex-shrink: 0;
  min-width: 80px;
  text-align: right;
}

</style>
