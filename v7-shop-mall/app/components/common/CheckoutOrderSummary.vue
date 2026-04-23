<script setup lang="ts">
/**
 * CheckoutOrderSummary Block - 订单汇总组件
 * 显示商品小计、运费、优惠、总计
 */

interface Props {
  showItemCount?: boolean;
  showShipping?: boolean;
  showDiscount?: boolean;
  layout?: "compact" | "detailed";
}

const props = withDefaults(defineProps<Props>(), {
  showItemCount: true,
  showShipping: true,
  showDiscount: true,
  layout: "detailed",
});

const {
  subtotal,
  shippingFee,
  discount,
  total,
  itemCount,
  isCalculating,
  formatPrice,
} = useCheckoutPage();

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));
const { t } = useI18n();

// 编辑器预览数据
const previewData = {
  subtotal: 398.00,
  shippingFee: 0,
  discount: 20.00,
  total: 378.00,
  itemCount: 3,
};

// 显示的数据
const displaySubtotal = computed(() =>
  isInEditor.value ? previewData.subtotal : subtotal.value
);
const displayShippingFee = computed(() =>
  isInEditor.value ? previewData.shippingFee : shippingFee.value
);
const displayDiscount = computed(() =>
  isInEditor.value ? previewData.discount : discount.value
);
const displayTotal = computed(() =>
  isInEditor.value ? previewData.total : total.value
);
const displayItemCount = computed(() =>
  isInEditor.value ? previewData.itemCount : itemCount.value
);

const showSkeleton = computed(() => !isInEditor.value && isCalculating.value);
</script>

<template>
  <div class="block-checkout-order-summary" :class="`layout-${layout}`">
    <!-- 明细行 -->
    <div class="summary-rows">
      <!-- 商品小计 -->
      <div class="summary-row">
        <span class="summary-label">
          {{ t('checkout.subtotal') }}
          <template v-if="showItemCount">
            ({{ displayItemCount }}{{ t('checkout.items') }})
          </template>
        </span>
        <span v-if="showSkeleton" class="price-skeleton" />
        <span v-else class="summary-value">{{ formatPrice(displaySubtotal) }}</span>
      </div>

      <!-- 运费 -->
      <div v-if="showShipping" class="summary-row">
        <span class="summary-label">{{ t('checkout.shipping') }}</span>
        <span v-if="showSkeleton" class="price-skeleton" />
        <span v-else class="summary-value">
          {{ displayShippingFee > 0 ? formatPrice(displayShippingFee) : t('checkout.freeShipping') }}
        </span>
      </div>

      <!-- 优惠 -->
      <div v-if="showDiscount && displayDiscount > 0" class="summary-row discount">
        <span class="summary-label">{{ t('checkout.discount') }}</span>
        <span v-if="showSkeleton" class="price-skeleton" />
        <span v-else class="summary-value">-{{ formatPrice(displayDiscount) }}</span>
      </div>
    </div>

    <!-- 总计 -->
    <div class="summary-total">
      <span class="total-label">{{ t('checkout.orderTotal') }}</span>
      <span v-if="showSkeleton" class="price-skeleton price-skeleton-lg" />
      <span v-else class="total-value">{{ formatPrice(displayTotal) }}</span>
    </div>
  </div>
</template>

<style scoped>
.block-checkout-order-summary {
  width: 100%;
}

.summary-rows {
  display: flex;
  flex-direction: column;
  gap: var(--order-summary-row-gap, 12px);
  margin-bottom: var(--order-summary-total-margin, 16px);
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary-label {
  font-size: var(--order-summary-label-size, 14px);
  color: var(--text-secondary-color, #6b7280);
}

.summary-value {
  font-size: var(--order-summary-value-size, 14px);
  color: var(--text-color, #1f2937);
}

.summary-row.discount .summary-value {
  color: var(--success-color, #22c55e);
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--order-summary-total-padding, 16px);
  border-top: 1px solid var(--border-color, #e5e7eb);
}

.total-label {
  font-size: var(--order-summary-total-label-size, 16px);
  font-weight: var(--order-summary-total-label-weight, 600);
  color: var(--text-color, #1f2937);
}

.total-value {
  font-size: var(--order-summary-total-value-size, 24px);
  font-weight: var(--order-summary-total-value-weight, 700);
  color: var(--primary-color, #3b82f6);
}

/* 价格骨架占位 */
@keyframes price-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.price-skeleton {
  display: inline-block;
  width: 60px;
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--border-color, #e5e7eb) 25%, var(--background-color, #f3f4f6) 50%, var(--border-color, #e5e7eb) 75%);
  background-size: 200% 100%;
  animation: price-shimmer 1.5s infinite;
}

.price-skeleton-lg {
  width: 80px;
  height: 24px;
}

/* 紧凑布局 */
.layout-compact .summary-rows {
  gap: 8px;
  margin-bottom: 12px;
}

.layout-compact .summary-label,
.layout-compact .summary-value {
  font-size: 13px;
}

.layout-compact .summary-total {
  padding-top: 12px;
}

.layout-compact .total-label {
  font-size: 14px;
}

.layout-compact .total-value {
  font-size: 20px;
}

</style>
