<script setup lang="ts">
/**
 * OrderResultPage Block - 订单结果页组件
 * 显示订单提交成功后的结果信息
 * 支持后台编辑器配置
 */

import type { OrderResultInfo } from "~/composables/usePageContext";

interface Props {
  layout?: "centered" | "left-aligned";
  showIcon?: boolean;
  iconType?: "success" | "pending" | "auto";
  showOrderId?: boolean;
  showAmount?: boolean;
  showRecipient?: boolean;
  showPhone?: boolean;
  showEmail?: boolean;
  showAddress?: boolean;
  showPaymentMethod?: boolean;
  showPaymentStatus?: boolean;
  showOrderTime?: boolean;
  showBackButton?: boolean;
  successTitle?: string;
  pendingTitle?: string;
  successDesc?: string;
  pendingDesc?: string;
  backButtonText?: string;
  backButtonLink?: string;
}

const props = withDefaults(defineProps<Props>(), {
  layout: "centered",
  showIcon: true,
  iconType: "auto",
  showOrderId: true,
  showAmount: true,
  showRecipient: true,
  showPhone: true,
  showEmail: true,
  showAddress: true,
  showPaymentMethod: true,
  showPaymentStatus: true,
  showOrderTime: true,
  showBackButton: true,
  successTitle: "",
  pendingTitle: "",
  successDesc: "",
  pendingDesc: "",
  backButtonText: "",
  backButtonLink: "/",
});

const { t } = useI18n();

const pageData = inject<ComputedRef<{ orderResult?: OrderResultInfo | null }>>(
  "pageData",
  computed(() => ({}))
);

const orderResult = computed(() => pageData.value?.orderResult);

const isSuccess = computed(() => {
  const status = orderResult.value?.paymentStatus;
  return status === "PAID" || status === "SUCCESS";
});

const displayIconType = computed(() => {
  if (props.iconType === "auto") {
    return isSuccess.value ? "success" : "pending";
  }
  return props.iconType;
});

const displayTitle = computed(() => {
  if (isSuccess.value) {
    return props.successTitle || t("orderResult.successTitle");
  }
  return props.pendingTitle || t("orderResult.pendingTitle");
});

const displayDesc = computed(() => {
  if (isSuccess.value) {
    return props.successDesc || t("orderResult.successDesc");
  }
  return props.pendingDesc || t("orderResult.pendingDesc");
});

const displayBackText = computed(() => {
  return props.backButtonText || t("orderResult.backHome");
});

const formatOrderTime = computed(() => {
  if (!orderResult.value?.orderTime) return "";
  const date = new Date(orderResult.value.orderTime);
  return date.toLocaleString();
});

const formatAmount = computed(() => {
  if (!orderResult.value) return "";
  const { currencySymbol, totalAmount } = orderResult.value;
  return `${currencySymbol || ""}${totalAmount}`;
});

const paymentMethodText = computed(() => {
  const method = orderResult.value?.paymentMethod;
  if (!method) return "";
  return t(`orderResult.paymentMethod.${method}`, method);
});

const paymentStatusText = computed(() => {
  const status = orderResult.value?.paymentStatus;
  if (!status) return "";
  return t(`orderResult.paymentStatus.${status}`, status);
});
</script>

<template>
  <div class="block-order-result" :class="`layout-${layout}`">
    <div class="result-container">
      <!-- 图标 -->
      <div v-if="showIcon" class="result-icon" :class="`icon-${displayIconType}`">
        <i v-if="displayIconType === 'success'" class="i-carbon-checkmark-filled" />
        <i v-else class="i-carbon-time" />
      </div>

      <!-- 标题和描述 -->
      <h1 class="result-title">{{ displayTitle }}</h1>
      <p class="result-desc">{{ displayDesc }}</p>

      <!-- 订单信息卡片 -->
      <div v-if="orderResult" class="order-info-card">
        <!-- 订单号 -->
        <div v-if="showOrderId" class="info-row">
          <span class="info-label">{{ t("orderResult.orderId") }}</span>
          <span class="info-value order-id">{{ orderResult.id }}</span>
        </div>

        <!-- 订单金额 -->
        <div v-if="showAmount" class="info-row highlight">
          <span class="info-label">{{ t("orderResult.amount") }}</span>
          <span class="info-value amount">{{ formatAmount }}</span>
        </div>

        <!-- 收件人 -->
        <div v-if="showRecipient && orderResult.firstName" class="info-row">
          <span class="info-label">{{ t("orderResult.recipient") }}</span>
          <span class="info-value">{{ orderResult.firstName }}</span>
        </div>

        <!-- 联系电话 -->
        <div v-if="showPhone && orderResult.phone" class="info-row">
          <span class="info-label">{{ t("orderResult.phone") }}</span>
          <span class="info-value">{{ orderResult.phone }}</span>
        </div>

        <!-- 邮箱 -->
        <div v-if="showEmail && orderResult.email" class="info-row">
          <span class="info-label">{{ t("orderResult.email") }}</span>
          <span class="info-value">{{ orderResult.email }}</span>
        </div>

        <!-- 收货地址 -->
        <div v-if="showAddress && orderResult.address" class="info-row">
          <span class="info-label">{{ t("orderResult.address") }}</span>
          <span class="info-value">{{ orderResult.address }}</span>
        </div>

        <!-- 支付方式 -->
        <div v-if="showPaymentMethod && orderResult.paymentMethod" class="info-row">
          <span class="info-label">{{ t("orderResult.paymentMethodLabel") }}</span>
          <span class="info-value">{{ paymentMethodText }}</span>
        </div>

        <!-- 支付状态 -->
        <div v-if="showPaymentStatus && orderResult.paymentStatus" class="info-row">
          <span class="info-label">{{ t("orderResult.paymentStatusLabel") }}</span>
          <span class="info-value status" :class="`status-${orderResult.paymentStatus.toLowerCase()}`">
            {{ paymentStatusText }}
          </span>
        </div>

        <!-- 下单时间 -->
        <div v-if="showOrderTime && orderResult.orderTime" class="info-row">
          <span class="info-label">{{ t("orderResult.orderTime") }}</span>
          <span class="info-value">{{ formatOrderTime }}</span>
        </div>
      </div>

      <!-- 无订单数据提示 -->
      <div v-else class="no-order-info">
        <p>{{ t("orderResult.noOrderInfo") }}</p>
      </div>

      <!-- 返回按钮 -->
      <NuxtLink v-if="showBackButton" :to="backButtonLink" class="back-btn">
        {{ displayBackText }}
      </NuxtLink>
    </div>
  </div>
</template>

<style scoped>
.block-order-result {
  width: 100%;
  padding: var(--order-result-padding, 40px 24px);
  box-sizing: border-box;
  background-color: var(--order-result-bg, var(--background-color, #f8fafc));
}

.layout-centered {
  display: flex;
  justify-content: center;
}

.layout-left-aligned .result-container {
  text-align: left;
  margin: 0;
}

.result-container {
  text-align: center;
  max-width: var(--order-result-max-width, 520px);
  width: 100%;
}

.result-icon {
  font-size: var(--order-result-icon-size, 72px);
  margin-bottom: var(--order-result-icon-margin, 24px);
}

.icon-success {
  color: var(--order-result-success-color, #22c55e);
}

.icon-pending {
  color: var(--order-result-pending-color, #f59e0b);
}

.result-title {
  font-size: var(--order-result-title-size, 28px);
  font-weight: var(--order-result-title-weight, 700);
  color: var(--order-result-title-color, var(--text-color, #1f2937));
  margin: 0 0 var(--order-result-title-margin, 12px) 0;
}

.result-desc {
  font-size: var(--order-result-desc-size, 16px);
  color: var(--order-result-desc-color, var(--text-secondary-color, #6b7280));
  margin: 0 0 var(--order-result-desc-margin, 32px) 0;
  line-height: 1.6;
}

.order-info-card {
  background-color: var(--order-result-card-bg, var(--surface-color, #ffffff));
  border-radius: var(--order-result-card-radius, 12px);
  border: 1px solid var(--order-result-card-border, var(--border-color, #e5e7eb));
  padding: var(--order-result-card-padding, 24px);
  margin-bottom: var(--order-result-card-margin, 32px);
  text-align: left;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: var(--order-result-row-padding, 12px 0);
  border-bottom: 1px solid var(--order-result-row-border, var(--border-color, #e5e7eb));
}

.info-row:last-child {
  border-bottom: none;
}

.info-row.highlight {
  background-color: var(--order-result-highlight-bg, #f0fdf4);
  margin: 0 calc(var(--order-result-card-padding, 24px) * -1);
  padding-left: var(--order-result-card-padding, 24px);
  padding-right: var(--order-result-card-padding, 24px);
  border-bottom: none;
}

.info-label {
  font-size: var(--order-result-label-size, 14px);
  color: var(--order-result-label-color, var(--text-secondary-color, #6b7280));
  flex-shrink: 0;
}

.info-value {
  font-size: var(--order-result-value-size, 14px);
  color: var(--order-result-value-color, var(--text-color, #1f2937));
  font-weight: 500;
  text-align: right;
  word-break: break-all;
  margin-left: 16px;
}

.info-value.order-id {
  font-family: var(--font-mono, monospace);
  font-size: 13px;
}

.info-value.amount {
  font-size: var(--order-result-amount-size, 20px);
  font-weight: 700;
  color: var(--order-result-amount-color, var(--primary-color, #3b82f6));
}

.info-value.status {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
}

.info-value.status-paid,
.info-value.status-success {
  background-color: #dcfce7;
  color: #166534;
}

.info-value.status-pending,
.info-value.status-unpaid {
  background-color: #fef3c7;
  color: #92400e;
}

.info-value.status-failed {
  background-color: #fee2e2;
  color: #991b1b;
}

.no-order-info {
  background-color: var(--order-result-card-bg, var(--surface-color, #ffffff));
  border-radius: var(--order-result-card-radius, 12px);
  border: 1px solid var(--order-result-card-border, var(--border-color, #e5e7eb));
  padding: var(--order-result-card-padding, 24px);
  margin-bottom: var(--order-result-card-margin, 32px);
  color: var(--text-secondary-color, #6b7280);
}

.back-btn {
  display: inline-block;
  padding: var(--order-result-btn-padding, 12px 32px);
  background-color: var(--order-result-btn-bg, var(--primary-color, #3b82f6));
  color: var(--order-result-btn-color, #ffffff);
  text-decoration: none;
  border-radius: var(--order-result-btn-radius, 8px);
  font-weight: 500;
  font-size: var(--order-result-btn-size, 15px);
  transition: background-color 0.2s, transform 0.1s;
}

.back-btn:hover {
  background-color: var(--order-result-btn-hover-bg, var(--primary-color-dark, #2563eb));
}

.back-btn:active {
  transform: scale(0.98);
}

@container (max-width: 480px) {
  .block-order-result {
    padding: var(--order-result-padding-mobile, 24px 16px);
  }

  .result-icon {
    font-size: var(--order-result-icon-size-mobile, 56px);
  }

  .result-title {
    font-size: var(--order-result-title-size-mobile, 22px);
  }

  .order-info-card {
    padding: var(--order-result-card-padding-mobile, 16px);
  }

  .info-row {
    flex-direction: column;
    gap: 4px;
  }

  .info-value {
    text-align: left;
    margin-left: 0;
  }

  .info-row.highlight {
    padding-left: var(--order-result-card-padding-mobile, 16px);
    padding-right: var(--order-result-card-padding-mobile, 16px);
    margin: 0 calc(var(--order-result-card-padding-mobile, 16px) * -1);
  }
}
</style>
