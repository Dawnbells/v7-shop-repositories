<script setup lang="ts">
/**
 * CheckoutSubmitButton Block - 提交订单按钮组件
 * 提交订单功能，验证表单完整性
 */

interface Props {
  text?: string;
  loadingText?: string;
  showNote?: boolean;
  noteText?: string;
  size?: "small" | "medium" | "large";
  fullWidth?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  text: "",
  loadingText: "",
  showNote: true,
  noteText: "",
  size: "large",
  fullWidth: true,
});

const { isSubmitting, submitError, hasItems, submitOrder } = useCheckoutPage();
const { t } = useI18n();

// 计算实际显示的文本（优先使用 props，否则使用 i18n）
const displayText = computed(() => props.text || t("checkout.submitOrder"));
const displayLoadingText = computed(() => props.loadingText || t("checkout.submitting"));
const displayNoteText = computed(() => props.noteText || t("checkout.termsNote"));

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 处理提交
async function handleSubmit() {
  if (isInEditor.value) return;
  await submitOrder();
}

// 按钮尺寸类
const sizeClass = computed(() => `size-${props.size}`);
</script>

<template>
  <div class="block-checkout-submit-button">
    <!-- 错误提示 -->
    <div v-if="submitError" class="submit-error">
      <i class="i-carbon-warning error-icon" />
      <span>{{ submitError }}</span>
    </div>

    <!-- 提交按钮 -->
    <button
      type="button"
      class="submit-btn"
      :class="[sizeClass, { 'full-width': fullWidth }]"
      :disabled="isSubmitting || !hasItems"
      @click="handleSubmit"
    >
      <span v-if="isSubmitting" class="submit-loading">
        <i class="i-carbon-circle-dash loading-icon" />
        {{ displayLoadingText }}
      </span>
      <span v-else>{{ displayText }}</span>
    </button>

    <!-- 提示文字 -->
    <p v-if="showNote" class="submit-note">
      {{ displayNoteText }}
    </p>
  </div>
</template>

<style scoped>
.block-checkout-submit-button {
  width: 100%;
}

.submit-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: var(--submit-error-padding, 12px);
  background-color: var(--submit-error-bg, #fef2f2);
  border: 1px solid var(--submit-error-border, #fecaca);
  border-radius: var(--submit-error-radius, 8px);
  color: var(--submit-error-color, #dc2626);
  font-size: var(--submit-error-size, 14px);
  margin-bottom: var(--submit-error-margin, 16px);
}

.error-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  border: none;
  border-radius: var(--submit-btn-radius, 10px);
  background-color: var(--submit-btn-bg, var(--primary-color, #3b82f6));
  color: var(--submit-btn-color, #ffffff);
  font-weight: var(--submit-btn-weight, 600);
  cursor: pointer;
  transition: background-color 0.2s;
}

.submit-btn.size-small {
  padding: var(--submit-btn-padding-small, 10px 20px);
  font-size: var(--submit-btn-size-small, 14px);
}

.submit-btn.size-medium {
  padding: var(--submit-btn-padding-medium, 12px 24px);
  font-size: var(--submit-btn-size-medium, 15px);
}

.submit-btn.size-large {
  padding: var(--submit-btn-padding-large, 14px 28px);
  font-size: var(--submit-btn-size-large, 16px);
}

.submit-btn:hover:not(:disabled) {
  background-color: var(--submit-btn-hover-bg, var(--primary-color-dark, #2563eb));
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

.loading-icon {
  font-size: 18px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.submit-note {
  font-size: var(--submit-note-size, 12px);
  color: var(--submit-note-color, var(--text-secondary-color, #9ca3af));
  text-align: center;
  margin: var(--submit-note-margin, 12px 0 0 0);
}
</style>
