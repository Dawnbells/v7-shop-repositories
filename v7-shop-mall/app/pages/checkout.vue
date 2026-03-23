<script setup lang="ts">
/**
 * 收银台页面
 *
 * SSR 完整渲染：
 * - 主题数据由中间件加载，通过 usePageTheme 获取
 * - 结算数据通过 useCheckoutPage 获取
 * - 绑定解析和组件渲染在服务端完成
 * - 浏览器收到完整渲染的 HTML
 */

// 获取主题相关数据
const { cssVariables, getPageSchema, getLayoutSchema, siteConfig } = usePageTheme();

// 获取国家信息（用于电话区号前缀）
const { countryInfo } = usePageContext();

// 获取结算数据
const {
  checkoutItems,
  shippingAddress,
  paymentMethod,
  paymentMethods,
  formErrors,
  isSubmitting,
  submitError,
  subtotal,
  total,
  itemCount,
  hasItems,
  shippingFee,
  discount,
  updateAddress,
  setPaymentMethod,
  submitOrder,
  formatSpecAttributes,
  formatPrice,
  initCheckoutItems,
} = useCheckoutPage();

// 页面级别初始化结算数据（只在页面组件中调用一次）
onMounted(() => {
  initCheckoutItems();
});

// 页面配置
const pageSchema = computed(() => getPageSchema("checkout"));
const layoutSchema = computed(() => {
  const layoutId = pageSchema.value?.layoutId;
  return layoutId ? getLayoutSchema(layoutId) : undefined;
});
const hasTheme = computed(() => !!pageSchema.value);

// 设置浏览器标签页标题
useHead({
  title: computed(() => siteConfig.value?.globalConfig?.siteName ? `收银台 - ${siteConfig.value.globalConfig.siteName}` : "收银台"),
});

// 提供编辑器状态（非编辑器模式）
provide("isInEditor", ref(false));

// 电话输入处理：只允许输入数字
function handlePhoneInput(event: Event) {
  const input = event.target as HTMLInputElement;
  const filtered = input.value.replace(/\D/g, '');
  input.value = filtered;
  updateAddress('phone', filtered);
}

// 提供页面数据供 NodeRenderer 绑定解析使用
provide(
  "pageData",
  computed(() => ({
    checkout: {
      items: checkoutItems.value,
      subtotal: subtotal.value,
      shippingFee: shippingFee.value,
      discount: discount.value,
      total: total.value,
      itemCount: itemCount.value,
    },
  }))
);
</script>

<template>
  <div class="checkout-page" :style="cssVariables">
    <!-- 有主题配置时使用 PageRenderer -->
    <RendererPageRenderer
      v-if="hasTheme && pageSchema"
      :page="pageSchema"
      :layout="layoutSchema"
    />

    <!-- 无主题配置时的 fallback -->
    <template v-else>
      <div class="default-checkout-page">
        <div class="checkout-container">
          <h1 class="checkout-title">收银台</h1>

          <!-- 空购物车提示 -->
          <div v-if="!hasItems" class="checkout-empty">
            <i class="i-carbon-shopping-cart checkout-empty-icon" />
            <p class="checkout-empty-text">购物车是空的</p>
            <NuxtLink to="/" class="checkout-empty-link">去购物</NuxtLink>
          </div>

          <template v-else>
            <div class="checkout-content">
              <!-- 左侧：表单区域 -->
              <div class="checkout-form-section">
                <!-- 商品列表 -->
                <div class="checkout-section">
                  <h2 class="section-title">商品信息</h2>
                  <div class="order-items">
                    <div
                      v-for="item in checkoutItems"
                      :key="item.id"
                      class="order-item"
                    >
                      <div class="item-image">
                        <img
                          v-if="item.image"
                          :src="item.image"
                          :alt="item.productName"
                        />
                        <div v-else class="item-no-image">
                          <i class="i-carbon-image" />
                        </div>
                      </div>
                      <div class="item-info">
                        <div class="item-name">{{ item.productName }}</div>
                        <div
                          v-if="item.specAttributes?.length"
                          class="item-spec"
                        >
                          {{ formatSpecAttributes(item.specAttributes) }}
                        </div>
                      </div>
                      <div class="item-quantity">x{{ item.quantity }}</div>
                      <div class="item-price">{{ formatPrice(item.price * item.quantity) }}</div>
                    </div>
                  </div>
                </div>

                <!-- 收货地址 -->
                <div class="checkout-section">
                  <h2 class="section-title">收货地址</h2>
                  <div class="address-form">
                    <div class="form-row">
                      <div class="form-group">
                        <label class="form-label">
                          收货人姓名 <span class="required">*</span>
                        </label>
                        <input
                          type="text"
                          class="form-input"
                          :class="{ 'has-error': formErrors.fullName }"
                          :value="shippingAddress.fullName"
                          placeholder="请输入收货人姓名"
                          @input="updateAddress('fullName', ($event.target as HTMLInputElement).value)"
                        />
                        <span v-if="formErrors.fullName" class="form-error">
                          {{ formErrors.fullName }}
                        </span>
                      </div>
                      <div class="form-group">
                        <label class="form-label">
                          联系电话 <span class="required">*</span>
                        </label>
                        <div class="phone-input-wrapper">
                          <span v-if="countryInfo?.phonePrefix" class="phone-prefix">
                            {{ countryInfo.phonePrefix }}
                          </span>
                          <input
                            type="tel"
                            class="form-input"
                            :class="{ 'has-error': formErrors.phone, 'has-prefix': countryInfo?.phonePrefix }"
                            :value="shippingAddress.phone"
                            placeholder="请输入联系电话"
                            @input="handlePhoneInput($event)"
                          />
                        </div>
                        <span v-if="formErrors.phone" class="form-error">
                          {{ formErrors.phone }}
                        </span>
                      </div>
                    </div>

                    <div class="form-row">
                      <div class="form-group">
                        <label class="form-label">邮箱</label>
                        <input
                          type="email"
                          class="form-input"
                          :class="{ 'has-error': formErrors.email }"
                          :value="shippingAddress.email"
                          placeholder="请输入邮箱地址"
                          @input="updateAddress('email', ($event.target as HTMLInputElement).value)"
                        />
                        <span v-if="formErrors.email" class="form-error">
                          {{ formErrors.email }}
                        </span>
                        <!-- 订阅复选框 -->
                        <label class="subscribe-checkbox">
                          <input
                            type="checkbox"
                            :checked="shippingAddress.subscribeToUpdates"
                            @change="updateAddress('subscribeToUpdates', ($event.target as HTMLInputElement).checked)"
                          />
                          <span class="checkbox-label">订阅订单状态更新</span>
                        </label>
                      </div>
                    </div>

                    <div class="form-row">
                      <div class="form-group">
                        <label class="form-label">
                          省/州 <span class="required">*</span>
                        </label>
                        <input
                          type="text"
                          class="form-input"
                          :class="{ 'has-error': formErrors.province }"
                          :value="shippingAddress.province"
                          placeholder="请输入省/州"
                          @input="updateAddress('province', ($event.target as HTMLInputElement).value)"
                        />
                        <span v-if="formErrors.province" class="form-error">
                          {{ formErrors.province }}
                        </span>
                      </div>
                      <div class="form-group">
                        <label class="form-label">
                          城市 <span class="required">*</span>
                        </label>
                        <input
                          type="text"
                          class="form-input"
                          :class="{ 'has-error': formErrors.city }"
                          :value="shippingAddress.city"
                          placeholder="请输入城市"
                          @input="updateAddress('city', ($event.target as HTMLInputElement).value)"
                        />
                        <span v-if="formErrors.city" class="form-error">
                          {{ formErrors.city }}
                        </span>
                      </div>
                    </div>

                    <div class="form-group full-width">
                      <label class="form-label">
                        详细地址 <span class="required">*</span>
                      </label>
                      <input
                        type="text"
                        class="form-input"
                        :class="{ 'has-error': formErrors.address }"
                        :value="shippingAddress.address"
                        placeholder="请输入详细地址（街道、门牌号等）"
                        @input="updateAddress('address', ($event.target as HTMLInputElement).value)"
                      />
                      <span v-if="formErrors.address" class="form-error">
                        {{ formErrors.address }}
                      </span>
                    </div>

                    <div class="form-row">
                      <div class="form-group">
                        <label class="form-label">邮政编码</label>
                        <input
                          type="text"
                          class="form-input"
                          :value="shippingAddress.postalCode"
                          placeholder="请输入邮政编码"
                          @input="updateAddress('postalCode', ($event.target as HTMLInputElement).value)"
                        />
                      </div>
                      <div class="form-group">
                        <label class="form-label">订单备注</label>
                        <input
                          type="text"
                          class="form-input"
                          :value="shippingAddress.note"
                          placeholder="如有特殊要求请备注"
                          @input="updateAddress('note', ($event.target as HTMLInputElement).value)"
                        />
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 支付方式 -->
                <div class="checkout-section">
                  <h2 class="section-title">支付方式</h2>
                  <div class="payment-methods">
                    <div
                      v-for="method in paymentMethods"
                      :key="method.id"
                      class="payment-method"
                      :class="{
                        active: paymentMethod === method.id,
                        disabled: !method.enabled,
                      }"
                      @click="method.enabled && setPaymentMethod(method.id)"
                    >
                      <div class="payment-radio">
                        <div
                          v-if="paymentMethod === method.id"
                          class="payment-radio-inner"
                        />
                      </div>
                      <i :class="method.icon" class="payment-icon" />
                      <div class="payment-info">
                        <div class="payment-name">{{ method.name }}</div>
                        <div class="payment-desc">{{ method.description }}</div>
                      </div>
                      <span v-if="!method.enabled" class="payment-badge">
                        暂不支持
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 右侧：订单汇总 -->
              <div class="checkout-summary-section">
                <div class="order-summary">
                  <h2 class="summary-title">订单汇总</h2>

                  <div class="summary-rows">
                    <div class="summary-row">
                      <span class="summary-label">商品小计 ({{ itemCount }}件)</span>
                      <span class="summary-value">{{ formatPrice(subtotal) }}</span>
                    </div>
                    <div class="summary-row">
                      <span class="summary-label">运费</span>
                      <span class="summary-value">
                        {{ shippingFee > 0 ? formatPrice(shippingFee) : '免运费' }}
                      </span>
                    </div>
                    <div v-if="discount > 0" class="summary-row discount">
                      <span class="summary-label">优惠</span>
                      <span class="summary-value">-{{ formatPrice(discount) }}</span>
                    </div>
                  </div>

                  <div class="summary-total">
                    <span class="total-label">订单总计</span>
                    <span class="total-value">{{ formatPrice(total) }}</span>
                  </div>

                  <div v-if="submitError" class="submit-error">
                    {{ submitError }}
                  </div>

                  <button
                    type="button"
                    class="submit-btn"
                    :disabled="isSubmitting || !hasItems"
                    @click="submitOrder"
                  >
                    <span v-if="isSubmitting" class="submit-loading">
                      <i class="i-carbon-circle-dash animate-spin" />
                      提交中...
                    </span>
                    <span v-else>提交订单</span>
                  </button>

                  <p class="submit-note">
                    点击"提交订单"即表示您同意我们的服务条款
                  </p>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.checkout-page {
  min-height: 100vh;
  background-color: var(--background-color, #f8fafc);
  color: var(--text-color, #1e293b);
  font-family: var(
    --font-family,
    "Inter",
    -apple-system,
    BlinkMacSystemFont,
    sans-serif
  );
}

.default-checkout-page {
  padding: 40px 24px;
}

.checkout-container {
  max-width: 1200px;
  margin: 0 auto;
}

.checkout-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-color, #1f2937);
  margin: 0 0 32px 0;
}

/* 空状态 */
.checkout-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background-color: var(--surface-color, #ffffff);
  border-radius: 12px;
}

.checkout-empty-icon {
  font-size: 64px;
  color: var(--text-secondary-color, #9ca3af);
  margin-bottom: 16px;
}

.checkout-empty-text {
  font-size: 16px;
  color: var(--text-secondary-color, #6b7280);
  margin: 0 0 24px 0;
}

.checkout-empty-link {
  padding: 12px 32px;
  background-color: var(--primary-color, #3b82f6);
  color: #ffffff;
  text-decoration: none;
  border-radius: 8px;
  font-weight: 500;
  transition: background-color 0.2s;
}

.checkout-empty-link:hover {
  background-color: var(--primary-color-dark, #2563eb);
}

/* 内容布局 */
.checkout-content {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 32px;
  align-items: start;
}

@media (max-width: 968px) {
  .checkout-content {
    grid-template-columns: 1fr;
  }
}

/* 区块样式 */
.checkout-section {
  background-color: var(--surface-color, #ffffff);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color, #1f2937);
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

/* 商品列表 */
.order-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color, #f3f4f6);
}

.order-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.item-image {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  background-color: var(--background-color, #f3f4f6);
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-no-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary-color, #9ca3af);
  font-size: 24px;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color, #1f2937);
  line-height: 1.4;
}

.item-spec {
  font-size: 12px;
  color: var(--text-secondary-color, #6b7280);
  margin-top: 4px;
}

.item-quantity {
  font-size: 14px;
  color: var(--text-secondary-color, #6b7280);
  flex-shrink: 0;
}

.item-price {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color, #1f2937);
  flex-shrink: 0;
  min-width: 80px;
  text-align: right;
}

/* 表单样式 */
.address-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 640px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group.full-width {
  grid-column: 1 / -1;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color, #374151);
}

.required {
  color: #ef4444;
}

.form-input {
  padding: 10px 14px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-color, #1f2937);
  background-color: var(--surface-color, #ffffff);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: var(--primary-color, #3b82f6);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-input.has-error {
  border-color: #ef4444;
}

.form-input::placeholder {
  color: var(--text-secondary-color, #9ca3af);
}

.form-error {
  font-size: 12px;
  color: #ef4444;
}

/* 电话输入框样式 */
.phone-input-wrapper {
  display: flex;
  align-items: stretch;
}

.phone-prefix {
  padding: 0 12px;
  display: flex;
  align-items: center;
  background: var(--background-color, #f9fafb);
  border: 1px solid var(--border-color, #e5e7eb);
  border-right: none;
  border-radius: 8px 0 0 8px;
  color: var(--text-secondary-color, #6b7280);
  font-size: 14px;
  white-space: nowrap;
}

.phone-input-wrapper .form-input {
  flex: 1;
  min-width: 0;
}

.phone-input-wrapper .form-input.has-prefix {
  border-radius: 0 8px 8px 0;
}

/* 订阅复选框样式 */
.subscribe-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  cursor: pointer;
}

.subscribe-checkbox input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--primary-color, #3b82f6);
}

.subscribe-checkbox .checkbox-label {
  font-size: 13px;
  color: var(--text-secondary-color, #6b7280);
}

/* 支付方式 */
.payment-methods {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.payment-method {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.payment-method:hover:not(.disabled) {
  border-color: var(--primary-color, #3b82f6);
}

.payment-method.active {
  border-color: var(--primary-color, #3b82f6);
  background-color: rgba(59, 130, 246, 0.05);
}

.payment-method.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.payment-radio {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border-color, #d1d5db);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: border-color 0.2s;
}

.payment-method.active .payment-radio {
  border-color: var(--primary-color, #3b82f6);
}

.payment-radio-inner {
  width: 10px;
  height: 10px;
  background-color: var(--primary-color, #3b82f6);
  border-radius: 50%;
}

.payment-icon {
  font-size: 24px;
  color: var(--text-secondary-color, #6b7280);
  flex-shrink: 0;
}

.payment-method.active .payment-icon {
  color: var(--primary-color, #3b82f6);
}

.payment-info {
  flex: 1;
}

.payment-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color, #1f2937);
}

.payment-desc {
  font-size: 12px;
  color: var(--text-secondary-color, #6b7280);
  margin-top: 2px;
}

.payment-badge {
  font-size: 11px;
  padding: 2px 8px;
  background-color: var(--background-color, #f3f4f6);
  color: var(--text-secondary-color, #6b7280);
  border-radius: 4px;
}

/* 订单汇总 */
.order-summary {
  background-color: var(--surface-color, #ffffff);
  border-radius: 12px;
  padding: 24px;
  position: sticky;
  top: 24px;
}

.summary-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color, #1f2937);
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.summary-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary-label {
  font-size: 14px;
  color: var(--text-secondary-color, #6b7280);
}

.summary-value {
  font-size: 14px;
  color: var(--text-color, #1f2937);
}

.summary-row.discount .summary-value {
  color: #22c55e;
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--border-color, #e5e7eb);
  margin-bottom: 24px;
}

.total-label {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color, #1f2937);
}

.total-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color, #3b82f6);
}

.submit-error {
  padding: 12px;
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #dc2626;
  font-size: 14px;
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
  padding: 14px 24px;
  border: none;
  border-radius: 10px;
  background-color: var(--primary-color, #3b82f6);
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background-color: var(--primary-color-dark, #2563eb);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.submit-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.submit-loading i {
  font-size: 18px;
}

.submit-note {
  font-size: 12px;
  color: var(--text-secondary-color, #9ca3af);
  text-align: center;
  margin: 12px 0 0 0;
}

/* 动画 */
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-spin {
  animation: spin 1s linear infinite;
}
</style>
