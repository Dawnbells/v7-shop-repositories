<script setup lang="ts">
/**
 * InlineOrderForm Block - 内嵌下单表单组件
 * 在商品详情页内直接完成下单（COD 落地页模式）：
 * 价格 + 数量 + 收货地址 + 支付方式 + 订单汇总 + 提交按钮
 *
 * 复用收银台体系（CheckoutAddressForm / CheckoutOrderSummary / CheckoutSubmitButton），
 * 结算商品来源为「当前商品 + 选中规格 + 购买数量」，与 SpecSelector / QuantitySelector 联动
 */

interface Props {
  showTitle?: boolean
  title?: string
  // 价格行
  showPrice?: boolean
  priceLabel?: string
  // 数量行
  showQuantity?: boolean
  quantityLabel?: string
  // 地址表单
  showEmail?: boolean
  showNote?: boolean
  addressLayout?: 'single' | 'double'
  // 支付方式
  showPaymentMethod?: boolean
  paymentLabel?: string
  codText?: string
  // 订单汇总
  showSummary?: boolean
  showShipping?: boolean
  showDiscount?: boolean
  // 提交按钮
  submitText?: string
  submitLoadingText?: string
  showSubmitNote?: boolean
  submitNoteText?: string
}

const props = withDefaults(defineProps<Props>(), {
  showTitle: true,
  title: '',
  showPrice: true,
  priceLabel: '',
  showQuantity: true,
  quantityLabel: '',
  showEmail: true,
  showNote: true,
  addressLayout: 'single',
  showPaymentMethod: true,
  paymentLabel: '',
  codText: '',
  showSummary: true,
  showShipping: true,
  showDiscount: true,
  submitText: '',
  submitLoadingText: '',
  showSubmitNote: true,
  submitNoteText: '',
})

const { t } = useI18n()
const { productInfo, selectedSpec, quantity, setQuantity, formatPrice: formatProductPrice } = useProductPage()
const { checkoutItems, calculatePrice } = useCheckoutPage()
const { buildImageUrl } = useImageUrl()

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>('isInEditor', ref(false))

// 文案（优先使用 props，否则使用 i18n）
const displayTitle = computed(() => props.title || t('checkout.shippingInfo'))
const displayQuantityLabel = computed(() => props.quantityLabel || t('product.quantity'))
const displayPaymentLabel = computed(() => props.paymentLabel || t('orderResult.paymentMethodLabel'))
const displayCodText = computed(() => props.codText || t('orderResult.paymentMethod.COD'))

// ============ 当前商品（与 ActionButtons 口径一致） ============

const currentItem = computed(() => {
  const product = productInfo.value
  if (!product) return null

  const spec = selectedSpec.value
  const price = spec?.sellPrice ?? product.sellPrice ?? 0
  const originPrice = spec?.originPrice ?? product.originPrice ?? null

  let image: string | undefined
  if (spec?.specImagePath) {
    image = buildImageUrl(spec.specImagePath)
  } else if (spec?.attributes?.length) {
    const attrWithImage = spec.attributes.find((a) => a.imagePath)
    if (attrWithImage?.imagePath) {
      image = buildImageUrl(attrWithImage.imagePath)
    }
  }
  const mainImagePath = (product as any).mainImagePath
  if (!image && mainImagePath) {
    image = buildImageUrl(mainImagePath)
  }

  return {
    productId: product.id,
    productName: product.title,
    specId: spec?.id ?? null,
    specAttributes: spec?.attributes ?? [],
    price,
    originPrice,
    image,
    stockQuantity: spec?.stockQuantity ?? -1,
  }
})

// 是否缺货（负数表示不跟踪库存）
const isOutOfStock = computed(() => currentItem.value?.stockQuantity === 0)

// 价格展示
const displayPrice = computed(() => {
  if (!currentItem.value) return isInEditor.value ? '$45.00' : ''
  return formatProductPrice(currentItem.value.price)
})

const displayOriginPrice = computed(() => {
  if (!currentItem.value) return isInEditor.value ? '$90.00' : ''
  const origin = currentItem.value.originPrice
  if (origin == null || origin <= currentItem.value.price) return ''
  return formatProductPrice(origin)
})

const discountBadge = computed(() => {
  if (!currentItem.value) return isInEditor.value ? '50% OFF' : ''
  const { price, originPrice } = currentItem.value
  if (originPrice == null || originPrice <= price) return ''
  const pct = Math.round(((originPrice - price) / originPrice) * 100)
  return pct > 0 ? `${pct}% OFF` : ''
})

// ============ 同步结算商品 + 计算价格（仅客户端、非编辑器） ============

let calcTimer: ReturnType<typeof setTimeout> | null = null

function syncCheckoutItems() {
  if (isInEditor.value) return

  const item = currentItem.value
  if (!item || isOutOfStock.value) {
    checkoutItems.value = []
    return
  }

  checkoutItems.value = [
    {
      id: `inline-${item.productId}-${item.specId ?? 'default'}`,
      productId: item.productId,
      productName: item.productName,
      specId: item.specId,
      specAttributes: item.specAttributes,
      price: item.price,
      originPrice: item.originPrice,
      quantity: Math.max(1, quantity.value),
      image: item.image,
    },
  ]

  // 防抖调用后端计价（运费/优惠以后端为准）
  if (calcTimer) clearTimeout(calcTimer)
  calcTimer = setTimeout(() => {
    calculatePrice()
  }, 300)
}

onMounted(() => {
  syncCheckoutItems()
})

onBeforeUnmount(() => {
  if (calcTimer) {
    clearTimeout(calcTimer)
    calcTimer = null
  }
})

watch([productInfo, selectedSpec, quantity], () => {
  if (import.meta.server) return
  syncCheckoutItems()
})

// ============ 数量操作 ============

function handleDecrease() {
  if (isInEditor.value) return
  setQuantity(quantity.value - 1)
}

function handleIncrease() {
  if (isInEditor.value) return
  setQuantity(quantity.value + 1)
}

function handleQuantityInput(event: Event) {
  if (isInEditor.value) return
  const value = parseInt((event.target as HTMLInputElement).value, 10)
  setQuantity(Number.isNaN(value) ? 1 : value)
}
</script>

<template>
  <div class="block-inline-order-form">
    <!-- 标题 -->
    <h2 v-if="showTitle" class="order-form-title">{{ displayTitle }}</h2>

    <!-- 价格行 -->
    <div v-if="showPrice" class="order-form-row price-row">
      <span v-if="priceLabel" class="row-label">{{ priceLabel }}</span>
      <div class="price-content">
        <span class="current-price">{{ displayPrice }}</span>
        <span v-if="displayOriginPrice" class="origin-price">{{ displayOriginPrice }}</span>
        <span v-if="discountBadge" class="discount-badge">{{ discountBadge }}</span>
      </div>
    </div>

    <!-- 数量行 -->
    <div v-if="showQuantity" class="order-form-row quantity-row">
      <span class="row-label">{{ displayQuantityLabel }}</span>
      <div class="quantity-stepper">
        <button
          type="button"
          class="stepper-btn"
          :disabled="quantity <= 1"
          @click="handleDecrease"
        >
          <i class="i-carbon-subtract" />
        </button>
        <input
          class="stepper-input"
          type="number"
          min="1"
          :value="quantity"
          @change="handleQuantityInput"
        />
        <button type="button" class="stepper-btn" @click="handleIncrease">
          <i class="i-carbon-add" />
        </button>
      </div>
    </div>

    <!-- 缺货提示 -->
    <div v-if="isOutOfStock" class="stock-warning">
      <i class="i-carbon-warning" />
      <span>{{ t('product.outOfStock') }}</span>
    </div>

    <!-- 收货地址表单（省/市/区/邮编按国家配置级联） -->
    <div class="order-form-section">
      <CommonCheckoutAddressForm
        :show-email="showEmail"
        :show-note="showNote"
        :layout="addressLayout"
      />
    </div>

    <!-- 支付方式（COD） -->
    <div v-if="showPaymentMethod" class="order-form-section payment-section">
      <span class="row-label">{{ displayPaymentLabel }}</span>
      <div class="payment-option">
        <span class="payment-radio">
          <span class="payment-radio-inner" />
        </span>
        <i class="i-carbon-delivery payment-icon" />
        <span class="payment-name">{{ displayCodText }}</span>
      </div>
    </div>

    <!-- 订单汇总（小计/运费/优惠/合计，后端计价） -->
    <div v-if="showSummary" class="order-form-section summary-section">
      <ClientOnly>
        <CommonCheckoutOrderSummary
          :show-item-count="false"
          :show-shipping="showShipping"
          :show-discount="showDiscount"
          layout="detailed"
        />
        <template #fallback>
          <div class="summary-skeleton">
            <div class="skeleton-row" />
            <div class="skeleton-row" />
          </div>
        </template>
      </ClientOnly>
    </div>

    <!-- 提交按钮（验证 + 下单 + 跳转结果页） -->
    <div class="order-form-submit">
      <CommonCheckoutSubmitButton
        :text="submitText"
        :loading-text="submitLoadingText"
        :show-note="showSubmitNote"
        :note-text="submitNoteText"
        size="large"
        :full-width="true"
      />
    </div>
  </div>
</template>

<style scoped>
.block-inline-order-form {
  display: flex;
  flex-direction: column;
  gap: var(--iof-gap, 16px);
  width: 100%;
  max-width: var(--iof-max-width, 720px);
  margin: 0 auto;
  padding: var(--iof-padding, 20px);
  box-sizing: border-box;
  background: var(--iof-bg, #ffffff);
  border: var(--iof-border, 1px solid #e5e7eb);
  border-radius: var(--iof-radius, 12px);
}

.order-form-title {
  margin: 0;
  font-size: var(--iof-title-size, 18px);
  font-weight: var(--iof-title-weight, 700);
  color: var(--iof-title-color, inherit);
}

.order-form-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.row-label {
  flex-shrink: 0;
  font-size: var(--iof-label-size, 14px);
  font-weight: 600;
  color: var(--iof-label-color, inherit);
}

/* 价格行 */
.price-content {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
}

.current-price {
  font-size: var(--iof-price-size, 24px);
  font-weight: 700;
  color: var(--iof-price-color, #ef4444);
}

.origin-price {
  font-size: var(--iof-origin-price-size, 14px);
  color: var(--iof-origin-price-color, #9ca3af);
  text-decoration: line-through;
}

.discount-badge {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--iof-badge-bg, #fee2e2);
  color: var(--iof-badge-color, #ef4444);
  font-size: 12px;
  font-weight: 700;
}

/* 数量行 */
.quantity-stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--iof-stepper-border, #d1d5db);
  border-radius: var(--iof-stepper-radius, 8px);
  overflow: hidden;
}

.stepper-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: var(--iof-stepper-btn-bg, #f9fafb);
  color: inherit;
  cursor: pointer;
}

.stepper-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.stepper-input {
  width: 48px;
  height: 32px;
  border: none;
  border-left: 1px solid var(--iof-stepper-border, #d1d5db);
  border-right: 1px solid var(--iof-stepper-border, #d1d5db);
  text-align: center;
  font-size: 14px;
  background: transparent;
  color: inherit;
  -moz-appearance: textfield;
  appearance: textfield;
}

.stepper-input::-webkit-outer-spin-button,
.stepper-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

/* 缺货提示 */
.stock-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  font-size: 14px;
}

/* 区块 */
.order-form-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 支付方式 */
.payment-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--iof-payment-padding, 12px 14px);
  border: 1px solid var(--iof-payment-border, var(--primary-color, #3b82f6));
  border-radius: var(--iof-payment-radius, 8px);
  background: var(--iof-payment-bg, rgba(59, 130, 246, 0.04));
}

.payment-radio {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 2px solid var(--primary-color, #3b82f6);
  border-radius: 50%;
  flex-shrink: 0;
}

.payment-radio-inner {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary-color, #3b82f6);
}

.payment-icon {
  font-size: 20px;
  color: var(--primary-color, #3b82f6);
}

.payment-name {
  font-size: 14px;
  font-weight: 600;
}

/* 汇总骨架 */
.summary-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-row {
  height: 16px;
  border-radius: 4px;
  background: #f3f4f6;
}

.order-form-submit {
  width: 100%;
}
</style>
