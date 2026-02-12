<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductQuantity 组件元数据
 * 商品数量选择器，支持加减输入
 */
export const meta: ComponentMeta = {
  type: "product-quantity",
  name: "数量选择器",
  icon: "i-carbon-add-alt",
  category: "business",
  description: "商品数量选择器，支持数量加减",

  propsSchema: [
    {
      key: "value",
      label: "默认数量",
      type: "number",
      defaultValue: 1,
    },
    {
      key: "min",
      label: "最小数量",
      type: "number",
      defaultValue: 1,
    },
    {
      key: "max",
      label: "最大数量",
      type: "number",
      defaultValue: 99,
    },
    {
      key: "label",
      label: "标签文字",
      type: "text",
      defaultValue: "数量",
    },
  ],

  styleSchema: [],

  supportEvents: ["change"],

  defaultProps: {
    value: 1,
    min: 1,
    max: 99,
    label: "数量",
  },

  defaultStyle: {
    base: {
      width: "auto",
    },
  },

  isContainer: false,
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
import { useThemeSchema } from "~/composables";

interface Props {
  value?: number;
  min?: number;
  max?: number;
  label?: string;
}

const props = withDefaults(defineProps<Props>(), {
  value: 1,
  min: 1,
  max: 99,
  label: "数量",
});

const emit = defineEmits<{
  (e: "change", quantity: number): void;
}>();

// 全局配置
const { siteConfig } = useThemeSchema();

// 是否启用数量选择器
const isEnabled = computed(() => {
  return siteConfig.value?.enableQuantitySelector !== false;
});

// 内部数量状态
const quantity = ref(props.value);

// 监听 props.value 变化
watch(() => props.value, (newVal) => {
  quantity.value = newVal;
});

// 减少数量
function decrease() {
  if (quantity.value > props.min) {
    quantity.value--;
    emit("change", quantity.value);
  }
}

// 增加数量
function increase() {
  if (quantity.value < props.max) {
    quantity.value++;
    emit("change", quantity.value);
  }
}

// 输入处理
function handleInput(event: Event) {
  const input = event.target as HTMLInputElement;
  let value = parseInt(input.value) || props.min;
  value = Math.max(props.min, Math.min(props.max, value));
  quantity.value = value;
  emit("change", quantity.value);
}
</script>

<template>
  <div v-if="isEnabled" class="product-quantity">
    <span v-if="label" class="quantity-label">{{ label }}</span>
    <div class="quantity-control">
      <button
        class="quantity-btn"
        :disabled="quantity <= min"
        @click="decrease"
      >
        <span class="i-carbon-subtract"></span>
      </button>
      <input
        type="number"
        class="quantity-input"
        :value="quantity"
        :min="min"
        :max="max"
        @input="handleInput"
      />
      <button
        class="quantity-btn"
        :disabled="quantity >= max"
        @click="increase"
      >
        <span class="i-carbon-add"></span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.product-quantity {
  display: flex;
  align-items: center;
  gap: 16px;
}

.quantity-label {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
}

.quantity-control {
  display: flex;
  align-items: center;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.quantity-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  border: none;
  cursor: pointer;
  color: #374151;
  transition: all 0.2s;
}

.quantity-btn:hover:not(:disabled) {
  background: #f3f4f6;
}

.quantity-btn:disabled {
  color: #d1d5db;
  cursor: not-allowed;
}

.quantity-input {
  width: 56px;
  height: 36px;
  border: none;
  border-left: 1px solid #e5e7eb;
  border-right: 1px solid #e5e7eb;
  text-align: center;
  font-size: 14px;
  color: #374151;
  -moz-appearance: textfield;
}

.quantity-input::-webkit-outer-spin-button,
.quantity-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

/* 响应式 */
@media (max-width: 480px) {
  .quantity-btn {
    width: 32px;
    height: 32px;
  }

  .quantity-input {
    width: 48px;
    height: 32px;
  }
}
</style>
