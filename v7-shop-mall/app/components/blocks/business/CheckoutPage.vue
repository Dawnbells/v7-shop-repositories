<script setup lang="ts">
/**
 * CheckoutPage Block - 收银台页面组件
 * 整合收货地址、商品列表、支付方式、订单汇总和提交按钮
 * 支持左右布局和单列布局
 * 移动端订单汇总可折叠
 */

interface Props {
  layout?: "two-column" | "single-column";
  // 地址表单配置
  showEmail?: boolean;
  showPostalCode?: boolean;
  showNote?: boolean;
  addressLayout?: "single" | "double";
  // 商品列表配置
  showImage?: boolean;
  imageSize?: "small" | "medium" | "large";
  showSpec?: boolean;
  showQuantity?: boolean;
  showPrice?: boolean;
  // 订单汇总配置
  showItemCount?: boolean;
  showShipping?: boolean;
  showDiscount?: boolean;
  summaryLayout?: "compact" | "detailed";
  // 提交按钮配置
  submitText?: string;
  submitLoadingText?: string;
  showSubmitNote?: boolean;
  submitNoteText?: string;
  submitSize?: "small" | "medium" | "large";
  submitFullWidth?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  layout: "two-column",
  // 地址表单
  showEmail: true,
  showPostalCode: true,
  showNote: true,
  addressLayout: "single",
  // 商品列表
  showImage: true,
  imageSize: "small",
  showSpec: true,
  showQuantity: true,
  showPrice: true,
  // 订单汇总
  showItemCount: false,
  showShipping: true,
  showDiscount: true,
  summaryLayout: "detailed",
  // 提交按钮
  submitText: "提交订单",
  submitLoadingText: "提交中...",
  showSubmitNote: true,
  submitNoteText: "点击\"提交订单\"即表示您同意我们的服务条款",
  submitSize: "large",
  submitFullWidth: true,
});

// 移动端订单汇总折叠状态
const isSummaryExpanded = ref(false);

// 获取订单总计用于折叠时显示
const { total, itemCount, formatPrice } = useCheckoutPage();

// 切换折叠状态
function toggleSummary() {
  isSummaryExpanded.value = !isSummaryExpanded.value;
}
</script>

<template>
  <div class="block-checkout-page" :class="`layout-${layout}`">
    <!-- 订单汇总（桌面端侧边栏，移动端可折叠） -->
    <aside class="checkout-sidebar" :class="{ 'is-expanded': isSummaryExpanded }">
      <div class="sidebar-sticky">
        <section class="checkout-section summary-section">
          <!-- 移动端折叠头部 -->
          <button class="summary-toggle" @click="toggleSummary">
            <div class="summary-toggle-info">
              <span class="summary-toggle-title">订单汇总</span>
              <span class="summary-toggle-total">{{ formatPrice(total) }}</span>
              <span class="summary-toggle-count">({{ itemCount }}件)</span>
            </div>
            <i class="summary-toggle-icon" :class="isSummaryExpanded ? 'i-carbon-chevron-up' : 'i-carbon-chevron-down'" />
          </button>
          
          <!-- 桌面端标题 -->
          <h2 class="section-title desktop-only">订单汇总</h2>
          
          <!-- 订单内容 -->
          <div class="summary-content">
            <CommonCheckoutOrderItems
              :show-image="showImage"
              :image-size="imageSize"
              :show-spec="showSpec"
              :show-quantity="showQuantity"
              :show-price="showPrice"
            />
            <div class="order-summary-divider"></div>
            <CommonCheckoutOrderSummary
              :show-item-count="showItemCount"
              :show-shipping="showShipping"
              :show-discount="showDiscount"
              :layout="summaryLayout"
            />
          </div>
        </section>
      </div>
    </aside>

    <!-- 收货信息 + 提交按钮 -->
    <div class="checkout-main">
      <!-- 收货地址区块 -->
      <section class="checkout-section">
        <h2 class="section-title">收货信息</h2>
        <p class="section-subtitle">请填写您的收货信息以完成订单</p>
        <CommonCheckoutAddressForm
          :show-email="showEmail"
          :show-postal-code="showPostalCode"
          :show-note="showNote"
          :layout="addressLayout"
        />
      </section>

      <!-- 提交按钮 -->
      <div class="checkout-submit-wrapper">
        <CommonCheckoutSubmitButton
          :text="submitText"
          :loading-text="submitLoadingText"
          :show-note="showSubmitNote"
          :note-text="submitNoteText"
          :size="submitSize"
          :full-width="submitFullWidth"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.block-checkout-page {
  width: 100%;
  max-width: var(--checkout-max-width, 1200px);
  margin: 0 auto;
  padding: var(--checkout-padding, 24px 16px);
  box-sizing: border-box;
}

/* 左右布局：左侧订单汇总，右侧表单 */
.layout-two-column {
  display: grid;
  grid-template-columns: minmax(280px, var(--checkout-sidebar-width, 380px)) 1fr;
  gap: var(--checkout-gap, 32px);
  align-items: start;
}

/* 单列布局 */
.layout-single-column {
  display: flex;
  flex-direction: column;
  gap: var(--checkout-section-gap, 24px);
}

.layout-single-column .checkout-sidebar {
  order: 1;
}

.layout-single-column .checkout-main {
  order: 2;
}

/* 主区域（右侧表单） */
.checkout-main {
  display: flex;
  flex-direction: column;
  gap: var(--checkout-section-gap, 24px);
  min-width: 0;
  width: 100%;
}

/* 侧边栏（左侧订单汇总） */
.checkout-sidebar {
  position: relative;
  width: 100%;
}

.sidebar-sticky {
  position: sticky;
  top: var(--checkout-sidebar-top, 24px);
  display: flex;
  flex-direction: column;
  gap: var(--checkout-section-gap, 24px);
}

/* 区块样式 */
.checkout-section {
  width: 100%;
  background-color: var(--checkout-section-bg, var(--surface-color, #ffffff));
  border-radius: var(--checkout-section-radius, 12px);
  padding: var(--checkout-section-padding, 24px);
  border: 1px solid var(--checkout-section-border, var(--border-color, #e5e7eb));
  box-sizing: border-box;
}

.section-title {
  margin: 0 0 var(--checkout-title-margin, 16px) 0;
  font-size: var(--checkout-title-size, 18px);
  font-weight: var(--checkout-title-weight, 700);
  color: var(--checkout-title-color, var(--text-color, #1f2937));
}

.section-subtitle {
  margin: 0 0 var(--checkout-subtitle-margin, 20px) 0;
  font-size: var(--checkout-subtitle-size, 14px);
  color: var(--checkout-subtitle-color, var(--text-secondary-color, #6b7280));
}

/* 订单汇总分隔线 */
.order-summary-divider {
  height: 1px;
  background-color: var(--border-color, #e5e7eb);
  margin: var(--checkout-divider-margin, 20px 0);
}

/* 提交按钮区域 */
.checkout-submit-wrapper {
  width: 100%;
  margin-top: var(--checkout-submit-margin, 8px);
}

/* 桌面端：折叠按钮隐藏，内容显示 */
.summary-toggle {
  display: none;
}

.summary-content {
  display: block;
}

/* 响应式：平板及以下降级为单列 + 订单汇总可折叠 */
@media (max-width: 860px) {
  .layout-two-column {
    display: flex;
    flex-direction: column;
    gap: var(--checkout-section-gap, 24px);
  }

  .layout-two-column .checkout-sidebar {
    order: 1;
    width: 100%;
  }

  .layout-two-column .checkout-main {
    order: 2;
    width: 100%;
  }

  .layout-two-column .sidebar-sticky {
    position: static;
  }

  /* 隐藏桌面端标题 */
  .desktop-only {
    display: none;
  }

  /* 显示折叠按钮 */
  .summary-toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding: 0;
    margin: 0;
    background: none;
    border: none;
    cursor: pointer;
    text-align: left;
  }

  .summary-toggle-info {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .summary-toggle-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--text-color, #1f2937);
  }

  .summary-toggle-total {
    font-size: 16px;
    font-weight: 700;
    color: var(--primary-color, #3b82f6);
  }

  .summary-toggle-count {
    font-size: 14px;
    color: var(--text-secondary-color, #6b7280);
  }

  .summary-toggle-icon {
    font-size: 20px;
    color: var(--text-secondary-color, #6b7280);
    flex-shrink: 0;
  }

  /* 折叠内容 - 默认隐藏 */
  .summary-content {
    display: none;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid var(--border-color, #e5e7eb);
  }

  /* 展开状态 */
  .checkout-sidebar.is-expanded .summary-content {
    display: block;
  }
}

/* 响应式：移动端进一步优化 */
@media (max-width: 480px) {
  .checkout-section {
    padding: var(--checkout-section-padding-mobile, 16px);
  }

  .section-title {
    font-size: var(--checkout-title-size-mobile, 16px);
  }

  .checkout-submit-wrapper {
    margin-top: var(--checkout-submit-margin-mobile, 4px);
  }
}
</style>
