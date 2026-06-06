<script setup lang="ts">
/**
 * ActionButtons Block - 购买操作按钮组件
 * 包含"加入购物车"和"立即购买"按钮
 * 根据全局配置控制显示和行为
 * 支持滚动不可见时底部悬浮
 */

interface Props {
  layout?: "horizontal" | "vertical";
  buttonSize?: "small" | "medium" | "large";
  addToCartText?: string;
  buyNowText?: string;
  showAddToCart?: boolean;
  fullWidth?: boolean;
  stickyEnabled?: boolean;
  stickyFrame?: "bar" | "pill" | "card" | "minimal";
  stickyLayout?: "row" | "stack" | "condensed";
  stickyShowPrice?: boolean;
  stickyShowAddToCart?: boolean;
  stickyShowImage?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  layout: "horizontal",
  buttonSize: "medium",
  addToCartText: "",
  buyNowText: "",
  showAddToCart: true,
  fullWidth: true,
  stickyEnabled: true,
  stickyFrame: "bar",
  stickyLayout: "row",
  stickyShowPrice: true,
  stickyShowAddToCart: true,
  stickyShowImage: false,
});

// 本组件有两个根节点（按钮容器 + Teleport 悬浮栏），fallthrough 属性无法自动继承，
// 关闭自动继承并把渲染器传入的节点 style 显式绑定到主容器，消除 Vue 警告。
defineOptions({ inheritAttrs: false });

const router = useRouter();
const { t } = useI18n();

// 计算实际显示的文本（优先使用 props，否则使用 i18n）
const displayAddToCartText = computed(() => props.addToCartText || t("product.addToCart"));
const displayBuyNowText = computed(() => props.buyNowText || t("product.buyNow"));
const { globalConfig } = usePageTheme();
const { productInfo, selectedSpec, quantity, formatPrice } = useProductPage();
const { addToCart, clearCart, openCartDrawer } = useCart();
const { buildImageUrl } = useImageUrl();

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 全局配置：是否启用购物车
const enableCart = computed(() => globalConfig.value?.enableCart ?? true);

// 全局配置：是否启用数量选择器
const enableQuantitySelector = computed(
  () => globalConfig.value?.enableQuantitySelector ?? true
);

// 是否显示加入购物车按钮（需要全局启用购物车 + 组件配置显示）
const shouldShowAddToCart = computed(() => {
  return enableCart.value && props.showAddToCart;
});

// 获取当前商品信息
const currentProduct = computed(() => {
  if (!productInfo.value) return null;

  const product = productInfo.value;
  const spec = selectedSpec.value;

  // 获取价格：优先使用规格价格，否则使用商品价格
  const price = spec?.sellPrice ?? product.sellPrice ?? 0;
  const originPrice = spec?.originPrice ?? product.originPrice ?? null;

  // 获取图片
  let image: string | undefined;
  if (spec?.specImagePath) {
    image = buildImageUrl(spec.specImagePath);
  } else if (spec?.attributes?.length) {
    const attrWithImage = spec.attributes.find((a) => a.imagePath);
    if (attrWithImage?.imagePath) {
      image = buildImageUrl(attrWithImage.imagePath);
    }
  }
  if (!image && product.mainImagePath) {
    image = buildImageUrl(product.mainImagePath);
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
  };
});

// 获取当前购买数量
const currentQuantity = computed(() => {
  // 如果启用数量选择器，使用选择的数量；否则固定为 1
  return enableQuantitySelector.value ? quantity.value : 1;
});

// 是否可以购买（检查库存）
const canPurchase = computed(() => {
  if (!currentProduct.value) return false;
  const stock = currentProduct.value.stockQuantity;
  // 负数表示不跟踪库存，可以购买
  if (stock < 0) return true;
  // 库存为 0 不可购买
  return stock > 0;
});

// 库存不足提示
const stockMessage = computed(() => {
  if (!currentProduct.value) return "";
  const stock = currentProduct.value.stockQuantity;
  if (stock === 0) return t("product.outOfStock");
  return "";
});

// 处理加入购物车
function handleAddToCart() {
  if (isInEditor.value) return; // 编辑模式下不执行
  if (!currentProduct.value || !canPurchase.value) return;

  addToCart({
    productId: currentProduct.value.productId,
    productName: currentProduct.value.productName,
    specId: currentProduct.value.specId,
    specAttributes: currentProduct.value.specAttributes,
    price: currentProduct.value.price,
    originPrice: currentProduct.value.originPrice,
    quantity: currentQuantity.value,
    image: currentProduct.value.image,
    stockQuantity: currentProduct.value.stockQuantity,
  }, { accumulate: enableQuantitySelector.value });

  openCartDrawer();
}

// 处理立即购买
function handleBuyNow() {
  if (isInEditor.value) return; // 编辑模式下不执行
  if (!currentProduct.value || !canPurchase.value) return;

  if (!enableCart.value) {
    clearCart();
  }

  addToCart({
    productId: currentProduct.value.productId,
    productName: currentProduct.value.productName,
    specId: currentProduct.value.specId,
    specAttributes: currentProduct.value.specAttributes,
    price: currentProduct.value.price,
    originPrice: currentProduct.value.originPrice,
    quantity: currentQuantity.value,
    image: currentProduct.value.image,
    stockQuantity: currentProduct.value.stockQuantity,
  }, { accumulate: enableQuantitySelector.value });

  router.push("/checkout");
}

// 按钮尺寸类
const sizeClass = computed(() => `size-${props.buttonSize}`);

// --- 底部悬浮逻辑 ---
const buttonsRef = ref<HTMLElement | null>(null);
const isOutOfView = ref(false);

const shouldShowSticky = computed(() => {
  return props.stickyEnabled && !isInEditor.value && isOutOfView.value;
});

const stickyShowAddToCartFinal = computed(() => {
  return enableCart.value && props.stickyShowAddToCart;
});

const stickyPrice = computed(() => {
  if (!props.stickyShowPrice || !currentProduct.value) return null;
  return {
    sell: formatPrice(currentProduct.value.price),
    origin: currentProduct.value.originPrice
      ? formatPrice(currentProduct.value.originPrice)
      : null,
  };
});

let observer: IntersectionObserver | null = null;
const stickyBarRef = ref<HTMLElement | null>(null);
let stickyResizeObserver: ResizeObserver | null = null;

function applyStickyPadding() {
  const el = stickyBarRef.value;
  if (el) {
    document.body.style.paddingBottom = `${el.offsetHeight}px`;
  }
}

function resetStickyPadding() {
  document.body.style.paddingBottom = "";
}

watch(shouldShowSticky, (visible) => {
  if (visible) {
    nextTick(() => {
      applyStickyPadding();
      const el = stickyBarRef.value;
      if (el && !stickyResizeObserver) {
        stickyResizeObserver = new ResizeObserver(() => applyStickyPadding());
        stickyResizeObserver.observe(el);
      }
    });
  } else {
    stickyResizeObserver?.disconnect();
    stickyResizeObserver = null;
    resetStickyPadding();
  }
});

onMounted(() => {
  if (!props.stickyEnabled || isInEditor.value) return;
  const el = buttonsRef.value;
  if (!el) return;
  observer = new IntersectionObserver(
    ([entry]) => {
      isOutOfView.value = !entry.isIntersecting;
    },
    { threshold: 0 },
  );
  observer.observe(el);
});

onBeforeUnmount(() => {
  observer?.disconnect();
  stickyResizeObserver?.disconnect();
  resetStickyPadding();
});
</script>

<template>
  <div
    ref="buttonsRef"
    class="block-action-buttons"
    :class="[sizeClass, `layout-${layout}`, { 'full-width': fullWidth }]"
    :style="$attrs.style as any"
  >
    <!-- 加入购物车按钮 -->
    <button
      v-if="shouldShowAddToCart"
      type="button"
      class="action-btn add-to-cart-btn"
      :disabled="!canPurchase"
      @click="handleAddToCart"
    >
      <span class="btn-icon">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <circle cx="9" cy="21" r="1" />
          <circle cx="20" cy="21" r="1" />
          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
        </svg>
      </span>
      <span class="btn-text">{{ displayAddToCartText }}</span>
    </button>

    <!-- 立即购买按钮 -->
    <button
      type="button"
      class="action-btn buy-now-btn"
      :disabled="!canPurchase"
      @click="handleBuyNow"
    >
      <span v-if="stockMessage" class="btn-text">{{ stockMessage }}</span>
      <span v-else class="btn-text">{{ displayBuyNowText }}</span>
    </button>
  </div>

  <!-- 底部悬浮栏 -->
  <Teleport to="body">
    <Transition name="sticky-slide">
      <div v-if="shouldShowSticky" ref="stickyBarRef" class="sticky-action-bar" :class="[`sticky-frame-${stickyFrame}`, `sticky-layout-${stickyLayout}`]">
        <div class="sticky-inner">
          <!-- 商品缩图 -->
          <img
            v-if="stickyShowImage && currentProduct?.image"
            :src="currentProduct.image"
            alt=""
            class="sticky-thumb"
          >

          <!-- 价格区域 -->
          <div v-if="stickyPrice" class="sticky-price">
            <span class="sticky-sell-price">{{ stickyPrice.sell }}</span>
            <span v-if="stickyPrice.origin" class="sticky-origin-price">{{ stickyPrice.origin }}</span>
          </div>

          <!-- 按钮区域 -->
          <div class="sticky-buttons">
            <button
              v-if="stickyShowAddToCartFinal"
              type="button"
              class="action-btn add-to-cart-btn sticky-btn"
              :disabled="!canPurchase"
              @click="handleAddToCart"
            >
              <span class="btn-text">{{ displayAddToCartText }}</span>
            </button>
            <button
              type="button"
              class="action-btn buy-now-btn sticky-btn"
              :disabled="!canPurchase"
              @click="handleBuyNow"
            >
              <span v-if="stockMessage" class="btn-text">{{ stockMessage }}</span>
              <span v-else class="btn-text">{{ displayBuyNowText }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.block-action-buttons {
  container-type: inline-size;
  display: flex;
  gap: var(--action-btn-gap, 12px);
  padding: var(--action-btn-padding, 16px 0);
}

.block-action-buttons.layout-horizontal {
  flex-direction: row;
}

.block-action-buttons.layout-vertical {
  flex-direction: column;
}

.block-action-buttons.full-width .action-btn {
  flex: 1;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: var(--action-btn-inner-padding, 12px 24px);
  border: none;
  border-radius: var(--action-btn-radius, 8px);
  font-size: var(--action-btn-font-size, 16px);
  font-weight: var(--action-btn-font-weight, 500);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.add-to-cart-btn {
  background-color: var(--action-btn-secondary-bg, #f3f4f6);
  color: var(--action-btn-secondary-color, #374151);
  border: 1px solid var(--action-btn-secondary-border, #e5e7eb);
}

.add-to-cart-btn:hover:not(:disabled) {
  background-color: var(--action-btn-secondary-hover-bg, #e5e7eb);
}

.buy-now-btn {
  background-color: var(--action-btn-primary-bg, var(--primary-color, #3b82f6));
  color: var(--action-btn-primary-color, #ffffff);
}

.buy-now-btn:hover:not(:disabled) {
  background-color: var(
    --action-btn-primary-hover-bg,
    var(--primary-color-dark, #2563eb)
  );
}

.btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon svg {
  width: var(--action-btn-icon-size, 20px);
  height: var(--action-btn-icon-size, 20px);
}

/* 尺寸变体 */
.size-small .action-btn {
  padding: 8px 16px;
  font-size: 14px;
}

.size-small .btn-icon svg {
  width: 16px;
  height: 16px;
}

.size-medium .action-btn {
  padding: 12px 24px;
  font-size: 16px;
}

.size-medium .btn-icon svg {
  width: 20px;
  height: 20px;
}

.size-large .action-btn {
  padding: 16px 32px;
  font-size: 18px;
}

.size-large .btn-icon svg {
  width: 24px;
  height: 24px;
}

/* 响应式：移动端 */
@container (max-width: 480px) {
  .block-action-buttons.layout-horizontal {
    flex-direction: column;
  }

  .action-btn {
    width: 100%;
    padding: 14px 20px;
  }
}
</style>

<!-- 悬浮栏样式不使用 scoped，因为 Teleport 到 body -->
<style>
/* ====== 基础 ====== */
.sticky-action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: var(--sticky-bar-z-index, 1000);
  background: var(--sticky-bar-bg, #ffffff);
  box-shadow: var(--sticky-bar-shadow, 0 -2px 12px rgba(0, 0, 0, 0.1));
  padding: var(--sticky-bar-padding, 12px 16px);
}

.sticky-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.sticky-thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.sticky-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-shrink: 0;
}

.sticky-sell-price {
  font-size: var(--sticky-price-size, 18px);
  font-weight: 700;
  color: var(--sticky-price-color, var(--primary-color, #3b82f6));
}

.sticky-origin-price {
  font-size: var(--sticky-origin-price-size, 13px);
  color: #9ca3af;
  text-decoration: line-through;
}

.sticky-buttons {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.sticky-btn {
  padding: 10px 20px !important;
  font-size: 14px !important;
  border-radius: var(--action-btn-radius, 8px);
}

.sticky-slide-enter-active,
.sticky-slide-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.sticky-slide-enter-from,
.sticky-slide-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

/* ====== 外框：pill ====== */
.sticky-frame-pill {
  left: 16px;
  right: 16px;
  bottom: 12px;
  border-radius: 24px;
  box-shadow: var(--sticky-bar-shadow, 0 4px 24px rgba(0, 0, 0, 0.15));
  padding: var(--sticky-bar-padding, 10px 20px);
}

/* ====== 外框：card ====== */
.sticky-frame-card {
  left: 12px;
  right: 12px;
  bottom: 12px;
  border-radius: 12px;
  box-shadow: var(--sticky-bar-shadow, 0 4px 20px rgba(0, 0, 0, 0.12));
  padding: var(--sticky-bar-padding, 10px 14px);
}

/* ====== 外框：minimal ====== */
.sticky-frame-minimal {
  box-shadow: none;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  background: var(--sticky-bar-bg, rgba(255, 255, 255, 0.85));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: var(--sticky-bar-padding, 8px 16px);
}

/* ====== 排版：row（默认，基础样式已覆盖） ====== */

/* ====== 排版：stack ====== */
.sticky-layout-stack .sticky-inner {
  flex-direction: column;
  align-items: stretch;
  text-align: center;
  gap: 8px;
}

.sticky-layout-stack .sticky-price {
  justify-content: center;
}

.sticky-layout-stack .sticky-buttons {
  width: 100%;
}

.sticky-layout-stack .sticky-btn {
  flex: 1;
}

/* ====== 排版：condensed ====== */
.sticky-layout-condensed .sticky-inner {
  gap: 10px;
}

.sticky-layout-condensed .sticky-sell-price {
  font-size: var(--sticky-price-size, 15px);
}

.sticky-layout-condensed .sticky-origin-price {
  font-size: var(--sticky-origin-price-size, 11px);
}

.sticky-layout-condensed .sticky-btn {
  padding: 8px 14px !important;
  font-size: 13px !important;
}

.sticky-layout-condensed .sticky-thumb {
  width: 36px;
  height: 36px;
  border-radius: 6px;
}

/* ====== 自适应：按钮自动撑满（无价格、无图片时） ====== */
.sticky-inner:not(:has(.sticky-price)):not(:has(.sticky-thumb)) .sticky-buttons {
  flex: 1;
}

.sticky-inner:not(:has(.sticky-price)):not(:has(.sticky-thumb)) .sticky-btn {
  flex: 1;
}

/* ====== 移动端响应 ====== */
@media (max-width: 480px) {
  .sticky-layout-row .sticky-inner {
    flex-direction: column;
    gap: 8px;
  }

  .sticky-layout-row .sticky-buttons {
    width: 100%;
  }

  .sticky-layout-row .sticky-btn {
    flex: 1;
  }

  .sticky-frame-pill {
    left: 8px;
    right: 8px;
    bottom: 8px;
  }

  .sticky-frame-card {
    left: 8px;
    right: 8px;
    bottom: 8px;
  }
}
</style>
