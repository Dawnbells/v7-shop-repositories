<script setup lang="ts">
/**
 * CartDrawer - 购物车抽屉组件
 * 从右侧滑入，显示购物车商品列表
 */

import type { CartItem } from "~/composables/useCart";

interface Props {
  visible: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "checkout"): void;
}>();

const router = useRouter();
const {
  cartItems,
  cartCount,
  cartTotal,
  isCartEmpty,
  removeFromCart,
  updateQuantity,
  loadFromStorage,
} = useCart();
const { formatPrice } = useCurrency();
const { globalConfig } = usePageTheme();
const { t } = useI18n();

// 是否启用数量选择器
const enableQuantitySelector = computed(
  () => globalConfig.value?.enableQuantitySelector ?? true
);

// 关闭抽屉
function handleClose() {
  emit("update:visible", false);
}

// 点击遮罩关闭
function handleOverlayClick() {
  handleClose();
}

// 阻止抽屉内容区域的点击冒泡
function handleDrawerClick(e: Event) {
  e.stopPropagation();
}

// 增加数量
function handleIncrease(item: CartItem) {
  updateQuantity(item.id, item.quantity + 1);
}

// 减少数量
function handleDecrease(item: CartItem) {
  if (item.quantity > 1) {
    updateQuantity(item.id, item.quantity - 1);
  }
}

// 删除商品
function handleRemove(item: CartItem) {
  removeFromCart(item.id);
}

// 去结算
function handleCheckout() {
  handleClose();
  emit("checkout");
  router.push("/checkout");
}

// 监听 visible 变化，打开时加载购物车数据
watch(
  () => props.visible,
  (val) => {
    if (val) {
      loadFromStorage();
    }
  }
);

// 格式化规格属性
function formatSpecAttributes(
  attrs: Array<{ name: string; value: string }>
): string {
  if (!attrs || attrs.length === 0) return "";
  return attrs.map((a) => `${a.name}: ${a.value}`).join(", ");
}
</script>

<template>
  <Teleport to="body">
    <Transition name="cart-drawer">
      <div v-if="visible" class="cart-drawer-overlay" @click="handleOverlayClick">
        <div class="cart-drawer" @click="handleDrawerClick">
          <!-- 头部 -->
          <div class="cart-drawer-header">
            <h3 class="cart-drawer-title">
              {{ t('cart.title') }}
              <span v-if="cartCount > 0" class="cart-count">({{ cartCount }})</span>
            </h3>
            <button type="button" class="cart-drawer-close" @click="handleClose">
              <i class="i-carbon-close" />
            </button>
          </div>

          <!-- 商品列表 -->
          <div class="cart-drawer-body">
            <template v-if="!isCartEmpty">
              <div
                v-for="item in cartItems"
                :key="item.id"
                class="cart-item"
              >
                <!-- 商品图片 -->
                <div class="cart-item-image">
                  <img
                    v-if="item.image"
                    :src="item.image"
                    :alt="item.productName"
                  />
                  <div v-else class="cart-item-no-image">
                    <i class="i-carbon-image" />
                  </div>
                </div>

                <!-- 商品信息 -->
                <div class="cart-item-info">
                  <div class="cart-item-name">{{ item.productName }}</div>
                  <div
                    v-if="item.specAttributes?.length"
                    class="cart-item-spec"
                  >
                    {{ formatSpecAttributes(item.specAttributes) }}
                  </div>
                  <div class="cart-item-price">
                    {{ formatPrice(item.price) }}
                  </div>
                </div>

                <!-- 数量控制 - 仅在启用数量选择器时显示 -->
                <div v-if="enableQuantitySelector" class="cart-item-quantity">
                  <button
                    type="button"
                    class="qty-btn"
                    :disabled="item.quantity <= 1"
                    @click="handleDecrease(item)"
                  >
                    −
                  </button>
                  <span class="qty-value">{{ item.quantity }}</span>
                  <button
                    type="button"
                    class="qty-btn"
                    @click="handleIncrease(item)"
                  >
                    +
                  </button>
                </div>
                <!-- 不启用数量选择器时只显示数量 -->
                <span v-else class="qty-value-only">x{{ item.quantity }}</span>

                <!-- 删除按钮 -->
                <button
                  type="button"
                  class="cart-item-remove"
                  @click="handleRemove(item)"
                >
                  <i class="i-carbon-trash-can" />
                </button>
              </div>
            </template>

            <!-- 空状态 -->
            <div v-else class="cart-empty">
              <i class="i-carbon-shopping-cart cart-empty-icon" />
              <p class="cart-empty-text">{{ t('cart.empty') }}</p>
            </div>
          </div>

          <!-- 底部 -->
          <div v-if="!isCartEmpty" class="cart-drawer-footer">
            <div class="cart-total">
              <span class="cart-total-label">{{ t('cart.total') }}</span>
              <span class="cart-total-price">{{ formatPrice(cartTotal) }}</span>
            </div>
            <button
              type="button"
              class="cart-checkout-btn"
              @click="handleCheckout"
            >
              {{ t('cart.checkout') }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.cart-drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.cart-drawer {
  width: 380px;
  max-width: 100%;
  height: 100%;
  background-color: var(--surface-color, #ffffff);
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.15);
}

.cart-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
  flex-shrink: 0;
}

.cart-drawer-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color, #1f2937);
  margin: 0;
}

.cart-count {
  font-weight: 400;
  color: var(--text-color-secondary, #6b7280);
}

.cart-drawer-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: none;
  color: var(--text-color-secondary, #6b7280);
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.cart-drawer-close:hover {
  background-color: var(--background-color, #f3f4f6);
}

.cart-drawer-close i {
  font-size: 20px;
}

.cart-drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.cart-item:last-child {
  border-bottom: none;
}

.cart-item-image {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background-color: var(--background-color, #f3f4f6);
}

.cart-item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cart-item-no-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-color-secondary, #9ca3af);
}

.cart-item-no-image i {
  font-size: 24px;
}

.cart-item-info {
  flex: 1;
  min-width: 0;
}

.cart-item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color, #1f2937);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cart-item-spec {
  font-size: 12px;
  color: var(--text-color-secondary, #6b7280);
  margin-top: 4px;
}

.cart-item-price {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color, #3b82f6);
  margin-top: 4px;
}

.cart-item-quantity {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.qty-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color, #e5e7eb);
  background-color: var(--surface-color, #ffffff);
  color: var(--text-color, #374151);
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
}

.qty-btn:hover:not(:disabled) {
  border-color: var(--primary-color, #3b82f6);
  color: var(--primary-color, #3b82f6);
}

.qty-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.qty-value {
  min-width: 28px;
  text-align: center;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color, #1f2937);
}

.qty-value-only {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color-secondary, #6b7280);
  flex-shrink: 0;
}

.cart-item-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  color: var(--text-color-secondary, #9ca3af);
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.cart-item-remove:hover {
  color: #ef4444;
  background-color: rgba(239, 68, 68, 0.1);
}

.cart-item-remove i {
  font-size: 16px;
}

.cart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-color-secondary, #9ca3af);
}

.cart-empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.cart-empty-text {
  font-size: 14px;
  margin: 0;
}

.cart-drawer-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border-color, #e5e7eb);
  background-color: var(--surface-color, #ffffff);
  flex-shrink: 0;
}

.cart-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.cart-total-label {
  font-size: 14px;
  color: var(--text-color-secondary, #6b7280);
}

.cart-total-price {
  font-size: 20px;
  font-weight: 700;
  color: var(--primary-color, #3b82f6);
}

.cart-checkout-btn {
  width: 100%;
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  background-color: var(--primary-color, #3b82f6);
  color: #ffffff;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s;
}

.cart-checkout-btn:hover {
  background-color: var(--primary-color-dark, #2563eb);
}

/* 动画 */
.cart-drawer-enter-active,
.cart-drawer-leave-active {
  transition: opacity 0.3s ease;
}

.cart-drawer-enter-active .cart-drawer,
.cart-drawer-leave-active .cart-drawer {
  transition: transform 0.3s ease;
}

.cart-drawer-enter-from,
.cart-drawer-leave-to {
  opacity: 0;
}

.cart-drawer-enter-from .cart-drawer,
.cart-drawer-leave-to .cart-drawer {
  transform: translateX(100%);
}

/* 移动端适配 */
@media (max-width: 480px) {
  .cart-drawer {
    width: 100%;
  }

  .cart-item-image {
    width: 56px;
    height: 56px;
  }

  .cart-item-name {
    font-size: 13px;
  }
}
</style>
