<script setup lang="ts">
/**
 * QuantitySelector Block - 购买数量选择组件
 * 支持增减按钮和直接输入，由全局配置控制是否显示
 */

interface Props {
  min?: number;
  max?: number;
  buttonSize?: "small" | "medium" | "large";
  showLabel?: boolean;
  label?: string;
}

const props = withDefaults(defineProps<Props>(), {
  min: 1,
  max: 999,
  buttonSize: "medium",
  showLabel: true,
  label: "",
});

const { globalConfig } = usePageTheme();
const { t } = useI18n();

// 计算实际显示的标签（优先使用 props，否则使用 i18n）
const displayLabel = computed(() => props.label || t("product.quantity"));
const {
  quantity,
  setQuantity,
  increaseQuantity,
  decreaseQuantity,
  selectedSpec,
} = useProductPage();

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 是否显示组件（由全局配置控制）
const shouldShow = computed(() => {
  return globalConfig.value?.enableQuantitySelector ?? true;
});

// 当前库存限制
const stockLimit = computed(() => {
  if (selectedSpec.value && selectedSpec.value.stockQuantity >= 0) {
    return selectedSpec.value.stockQuantity;
  }
  return props.max;
});

// 实际最大值（取配置和库存的较小值）
const actualMax = computed(() => {
  return Math.min(props.max, stockLimit.value);
});

// 是否可以减少
const canDecrease = computed(() => quantity.value > props.min);

// 是否可以增加
const canIncrease = computed(() => quantity.value < actualMax.value);

// 处理输入变化
function handleInput(event: Event) {
  if (isInEditor.value) return; // 编辑模式下不执行

  const target = event.target as HTMLInputElement;
  let val = parseInt(target.value, 10);

  if (isNaN(val) || val < props.min) {
    val = props.min;
  } else if (val > actualMax.value) {
    val = actualMax.value;
  }

  setQuantity(val);
}

// 处理增加
function handleIncrease() {
  if (isInEditor.value) return; // 编辑模式下不执行

  if (canIncrease.value) {
    if (quantity.value < actualMax.value) {
      increaseQuantity();
    }
  }
}

// 处理减少
function handleDecrease() {
  if (isInEditor.value) return; // 编辑模式下不执行

  if (canDecrease.value) {
    decreaseQuantity();
  }
}

// 按钮尺寸类
const sizeClass = computed(() => `size-${props.buttonSize}`);
</script>

<template>
  <div v-if="shouldShow" class="block-quantity-selector" :class="sizeClass">
    <span v-if="showLabel" class="quantity-label">{{ displayLabel }}</span>
    <div class="quantity-controls">
      <button
        type="button"
        class="quantity-btn decrease"
        :disabled="!canDecrease"
        @click="handleDecrease"
      >
        <span class="btn-icon">−</span>
      </button>
      <input
        type="number"
        class="quantity-input"
        :value="quantity"
        :min="min"
        :max="actualMax"
        @change="handleInput"
      />
      <button
        type="button"
        class="quantity-btn increase"
        :disabled="!canIncrease"
        @click="handleIncrease"
      >
        <span class="btn-icon">+</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.block-quantity-selector {
  container-type: inline-size;
  display: flex;
  align-items: center;
  gap: var(--quantity-gap, 12px);
  padding: var(--quantity-padding, 12px 0);
}

.quantity-label {
  font-size: var(--quantity-label-size, 14px);
  font-weight: var(--quantity-label-weight, 500);
  color: var(--quantity-label-color, #374151);
  flex-shrink: 0;
}

.quantity-controls {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--quantity-border-color, #e5e7eb);
  border-radius: var(--quantity-radius, 6px);
  overflow: hidden;
  background-color: var(--quantity-bg, #fff);
}

.quantity-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--quantity-btn-size, 36px);
  height: var(--quantity-btn-size, 36px);
  border: none;
  background-color: var(--quantity-btn-bg, #f9fafb);
  color: var(--quantity-btn-color, #374151);
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.quantity-btn:hover:not(:disabled) {
  background-color: var(--quantity-btn-hover-bg, #f3f4f6);
}

.quantity-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  background-color: var(--quantity-btn-disabled-bg, #f9fafb);
}

.btn-icon {
  font-size: var(--quantity-btn-icon-size, 18px);
  font-weight: 500;
  line-height: 1;
}

.quantity-input {
  width: var(--quantity-input-width, 60px);
  height: var(--quantity-btn-size, 36px);
  border: none;
  border-left: 1px solid var(--quantity-border-color, #e5e7eb);
  border-right: 1px solid var(--quantity-border-color, #e5e7eb);
  text-align: center;
  font-size: var(--quantity-input-size, 14px);
  font-weight: var(--quantity-input-weight, 500);
  color: var(--quantity-input-color, #1f2937);
  background-color: var(--quantity-input-bg, #fff);
  -moz-appearance: textfield;
}

.quantity-input::-webkit-outer-spin-button,
.quantity-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.quantity-input:focus {
  outline: none;
  background-color: var(--quantity-input-focus-bg, #f9fafb);
}

/* 尺寸变体 */
.size-small .quantity-btn {
  width: 28px;
  height: 28px;
}

.size-small .btn-icon {
  font-size: 14px;
}

.size-small .quantity-input {
  width: 48px;
  height: 28px;
  font-size: 12px;
}

.size-small .quantity-label {
  font-size: 12px;
}

.size-medium .quantity-btn {
  width: 36px;
  height: 36px;
}

.size-medium .btn-icon {
  font-size: 18px;
}

.size-medium .quantity-input {
  width: 60px;
  height: 36px;
  font-size: 14px;
}

.size-large .quantity-btn {
  width: 44px;
  height: 44px;
}

.size-large .btn-icon {
  font-size: 22px;
}

.size-large .quantity-input {
  width: 72px;
  height: 44px;
  font-size: 16px;
}

.size-large .quantity-label {
  font-size: 16px;
}

/* 响应式：移动端 */
@container (max-width: 480px) {
  .block-quantity-selector {
    gap: 8px;
  }

  .quantity-btn {
    width: 32px;
    height: 32px;
  }

  .btn-icon {
    font-size: 16px;
  }

  .quantity-input {
    width: 52px;
    height: 32px;
    font-size: 13px;
  }
}
</style>
