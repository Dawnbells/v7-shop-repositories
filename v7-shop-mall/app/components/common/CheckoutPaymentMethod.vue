<script setup lang="ts">
/**
 * CheckoutPaymentMethod Block - 支付方式选择组件
 * 目前只支持 COD（货到付款），可扩展其他支付方式
 */

interface Props {
  showDescription?: boolean;
  showIcon?: boolean;
  layout?: "vertical" | "horizontal";
}

const props = withDefaults(defineProps<Props>(), {
  showDescription: true,
  showIcon: true,
  layout: "vertical",
});

const { paymentMethod, paymentMethods, setPaymentMethod } = useCheckoutPage();
const { t } = useI18n();

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 处理选择
function handleSelect(methodId: string) {
  if (isInEditor.value) return;
  const method = paymentMethods.value.find((m) => m.id === methodId);
  if (method?.enabled) {
    setPaymentMethod(methodId as any);
  }
}
</script>

<template>
  <div class="block-checkout-payment-method" :class="`layout-${layout}`">
    <div
      v-for="method in paymentMethods"
      :key="method.id"
      class="payment-option"
      :class="{
        active: paymentMethod === method.id,
        disabled: !method.enabled,
      }"
      @click="handleSelect(method.id)"
    >
      <!-- 单选按钮 -->
      <div class="payment-radio">
        <div
          v-if="paymentMethod === method.id"
          class="payment-radio-inner"
        />
      </div>

      <!-- 图标 -->
      <i v-if="showIcon" :class="method.icon" class="payment-icon" />

      <!-- 信息 -->
      <div class="payment-info">
        <div class="payment-name">{{ method.name }}</div>
        <div v-if="showDescription" class="payment-desc">
          {{ method.description }}
        </div>
      </div>

      <!-- 不可用标签 -->
      <span v-if="!method.enabled" class="payment-badge">
        {{ t('common.notSupported') }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.block-checkout-payment-method {
  container-type: inline-size;
  display: flex;
  gap: var(--payment-method-gap, 12px);
}

.layout-vertical {
  flex-direction: column;
}

.layout-horizontal {
  flex-direction: row;
  flex-wrap: wrap;
}

.layout-horizontal .payment-option {
  flex: 1;
  min-width: 200px;
}

.payment-option {
  display: flex;
  align-items: center;
  gap: var(--payment-method-item-gap, 12px);
  padding: var(--payment-method-padding, 16px);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: var(--payment-method-radius, 10px);
  cursor: pointer;
  transition: all 0.2s;
  background-color: var(--surface-color, #ffffff);
}

.payment-option:hover:not(.disabled) {
  border-color: var(--primary-color, #3b82f6);
}

.payment-option.active {
  border-color: var(--primary-color, #3b82f6);
  background-color: var(--payment-method-active-bg, rgba(59, 130, 246, 0.05));
}

.payment-option.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.payment-radio {
  width: var(--payment-method-radio-size, 20px);
  height: var(--payment-method-radio-size, 20px);
  border: 2px solid var(--border-color, #d1d5db);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: border-color 0.2s;
}

.payment-option.active .payment-radio {
  border-color: var(--primary-color, #3b82f6);
}

.payment-radio-inner {
  width: calc(var(--payment-method-radio-size, 20px) / 2);
  height: calc(var(--payment-method-radio-size, 20px) / 2);
  background-color: var(--primary-color, #3b82f6);
  border-radius: 50%;
}

.payment-icon {
  font-size: var(--payment-method-icon-size, 24px);
  color: var(--text-secondary-color, #6b7280);
  flex-shrink: 0;
}

.payment-option.active .payment-icon {
  color: var(--primary-color, #3b82f6);
}

.payment-info {
  flex: 1;
  min-width: 0;
}

.payment-name {
  font-size: var(--payment-method-name-size, 14px);
  font-weight: var(--payment-method-name-weight, 500);
  color: var(--text-color, #1f2937);
}

.payment-desc {
  font-size: var(--payment-method-desc-size, 12px);
  color: var(--text-secondary-color, #6b7280);
  margin-top: 2px;
}

.payment-badge {
  font-size: var(--payment-method-badge-size, 11px);
  padding: 2px 8px;
  background-color: var(--background-color, #f3f4f6);
  color: var(--text-secondary-color, #6b7280);
  border-radius: 4px;
  flex-shrink: 0;
}

/* 响应式 */
@container (max-width: 480px) {
  .layout-horizontal {
    flex-direction: column;
  }

  .layout-horizontal .payment-option {
    min-width: auto;
  }
}
</style>
