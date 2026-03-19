<script setup lang="ts">
/**
 * CheckoutAddressForm Block - 收货地址表单组件
 * 收货人姓名、电话、地址等字段，带表单验证
 */

interface Props {
  showEmail?: boolean;
  showPostalCode?: boolean;
  showNote?: boolean;
  layout?: "single" | "double";
}

const props = withDefaults(defineProps<Props>(), {
  showEmail: true,
  showPostalCode: true,
  showNote: true,
  layout: "double",
});

const { shippingAddress, formErrors, updateAddress } = useCheckoutPage();

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 邮箱联想状态
const emailSuggestions = ref<string[]>([]);
const showEmailSuggestions = ref(false);
const selectedSuggestionIndex = ref(-1);
const emailInputRef = ref<HTMLInputElement | null>(null);
const suggestionsRef = ref<HTMLElement | null>(null);

// 防抖定时器
let debounceTimer: ReturnType<typeof setTimeout> | null = null;

// 获取邮箱建议
async function fetchEmailSuggestions(prefix: string) {
  if (!prefix.includes('@') || isInEditor.value) {
    emailSuggestions.value = [];
    showEmailSuggestions.value = false;
    return;
  }
  
  try {
    const country = shippingAddress.value.country || 'default';
    const suggestions = await $fetch<string[]>('/api/email/suggestions', {
      query: { prefix, country, limit: 8 }
    });
    emailSuggestions.value = suggestions;
    showEmailSuggestions.value = suggestions.length > 0;
    selectedSuggestionIndex.value = -1;
  } catch {
    emailSuggestions.value = [];
    showEmailSuggestions.value = false;
  }
}

// 处理邮箱输入
function handleEmailInput(event: Event) {
  if (isInEditor.value) return;
  const target = event.target as HTMLInputElement;
  updateAddress('email', target.value);
  
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    fetchEmailSuggestions(target.value);
  }, 150);
}

// 选择邮箱建议
function selectSuggestion(email: string) {
  updateAddress('email', email);
  showEmailSuggestions.value = false;
  emailSuggestions.value = [];
  selectedSuggestionIndex.value = -1;
}

// 处理邮箱输入框键盘事件
function handleEmailKeydown(event: KeyboardEvent) {
  if (!showEmailSuggestions.value || emailSuggestions.value.length === 0) return;
  
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault();
      selectedSuggestionIndex.value = Math.min(
        selectedSuggestionIndex.value + 1,
        emailSuggestions.value.length - 1
      );
      break;
    case 'ArrowUp':
      event.preventDefault();
      selectedSuggestionIndex.value = Math.max(selectedSuggestionIndex.value - 1, -1);
      break;
    case 'Enter':
      if (selectedSuggestionIndex.value >= 0) {
        event.preventDefault();
        selectSuggestion(emailSuggestions.value[selectedSuggestionIndex.value]);
      }
      break;
    case 'Escape':
      showEmailSuggestions.value = false;
      selectedSuggestionIndex.value = -1;
      break;
  }
}

// 处理邮箱输入框失焦
function handleEmailBlur(event: FocusEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null;
  if (suggestionsRef.value?.contains(relatedTarget)) return;
  
  setTimeout(() => {
    showEmailSuggestions.value = false;
    selectedSuggestionIndex.value = -1;
  }, 150);
}

// 编辑器中禁用输入
function handleInput(field: keyof typeof shippingAddress.value, event: Event) {
  if (isInEditor.value) return;
  const target = event.target as HTMLInputElement;
  updateAddress(field, target.value);
}

// 清理定时器
onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
});
</script>

<template>
  <div class="block-checkout-address-form" :class="`layout-${layout}`">
    <!-- 第一行：姓名和电话 -->
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
          :disabled="isInEditor"
          @input="handleInput('fullName', $event)"
        />
        <span v-if="formErrors.fullName" class="form-error">
          {{ formErrors.fullName }}
        </span>
      </div>
      <div class="form-group">
        <label class="form-label">
          联系电话 <span class="required">*</span>
        </label>
        <input
          type="tel"
          class="form-input"
          :class="{ 'has-error': formErrors.phone }"
          :value="shippingAddress.phone"
          placeholder="请输入联系电话"
          :disabled="isInEditor"
          @input="handleInput('phone', $event)"
        />
        <span v-if="formErrors.phone" class="form-error">
          {{ formErrors.phone }}
        </span>
      </div>
    </div>

    <!-- 第二行：邮箱和国家 -->
    <div class="form-row">
      <div v-if="showEmail" class="form-group email-group">
        <label class="form-label">邮箱</label>
        <div class="email-input-wrapper">
          <input
            ref="emailInputRef"
            type="email"
            class="form-input"
            :class="{ 'has-error': formErrors.email }"
            :value="shippingAddress.email"
            placeholder="请输入邮箱地址"
            autocomplete="off"
            :disabled="isInEditor"
            @input="handleEmailInput"
            @keydown="handleEmailKeydown"
            @blur="handleEmailBlur"
            @focus="shippingAddress.email?.includes('@') && fetchEmailSuggestions(shippingAddress.email)"
          />
          <div
            v-if="showEmailSuggestions && emailSuggestions.length > 0"
            ref="suggestionsRef"
            class="email-suggestions"
          >
            <button
              v-for="(suggestion, index) in emailSuggestions"
              :key="suggestion"
              type="button"
              class="email-suggestion-item"
              :class="{ 'is-selected': index === selectedSuggestionIndex }"
              @mousedown.prevent="selectSuggestion(suggestion)"
            >
              {{ suggestion }}
            </button>
          </div>
        </div>
        <span v-if="formErrors.email" class="form-error">
          {{ formErrors.email }}
        </span>
      </div>
      <div class="form-group">
        <label class="form-label">
          国家/地区 <span class="required">*</span>
        </label>
        <input
          type="text"
          class="form-input"
          :class="{ 'has-error': formErrors.country }"
          :value="shippingAddress.country"
          placeholder="请输入国家/地区"
          :disabled="isInEditor"
          @input="handleInput('country', $event)"
        />
        <span v-if="formErrors.country" class="form-error">
          {{ formErrors.country }}
        </span>
      </div>
    </div>

    <!-- 第三行：省/州和城市 -->
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
          :disabled="isInEditor"
          @input="handleInput('province', $event)"
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
          :disabled="isInEditor"
          @input="handleInput('city', $event)"
        />
        <span v-if="formErrors.city" class="form-error">
          {{ formErrors.city }}
        </span>
      </div>
    </div>

    <!-- 详细地址 -->
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
        :disabled="isInEditor"
        @input="handleInput('address', $event)"
      />
      <span v-if="formErrors.address" class="form-error">
        {{ formErrors.address }}
      </span>
    </div>

    <!-- 最后一行：邮编和备注 -->
    <div class="form-row">
      <div v-if="showPostalCode" class="form-group">
        <label class="form-label">邮政编码</label>
        <input
          type="text"
          class="form-input"
          :value="shippingAddress.postalCode"
          placeholder="请输入邮政编码"
          :disabled="isInEditor"
          @input="handleInput('postalCode', $event)"
        />
      </div>
      <div v-if="showNote" class="form-group">
        <label class="form-label">订单备注</label>
        <input
          type="text"
          class="form-input"
          :value="shippingAddress.note"
          placeholder="如有特殊要求请备注"
          :disabled="isInEditor"
          @input="handleInput('note', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.block-checkout-address-form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--address-form-gap, 16px);
}

.form-row {
  display: grid;
  gap: var(--address-form-gap, 16px);
}

.layout-double .form-row {
  grid-template-columns: 1fr 1fr;
}

.layout-single .form-row {
  grid-template-columns: 1fr;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--address-form-label-gap, 6px);
}

.form-group.full-width {
  grid-column: 1 / -1;
}

.form-label {
  font-size: var(--address-form-label-size, 14px);
  font-weight: var(--address-form-label-weight, 500);
  color: var(--text-color, #374151);
}

.required {
  color: var(--error-color, #ef4444);
}

.form-input {
  padding: var(--address-form-input-padding, 10px 14px);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: var(--address-form-input-radius, 8px);
  font-size: var(--address-form-input-size, 14px);
  color: var(--text-color, #1f2937);
  background-color: var(--surface-color, #ffffff);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: var(--primary-color, #3b82f6);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-input:disabled {
  background-color: var(--background-color, #f9fafb);
  cursor: not-allowed;
}

.form-input.has-error {
  border-color: var(--error-color, #ef4444);
}

.form-input::placeholder {
  color: var(--text-secondary-color, #9ca3af);
}

.form-error {
  font-size: var(--address-form-error-size, 12px);
  color: var(--error-color, #ef4444);
}

/* 邮箱联想样式 */
.email-group {
  position: relative;
}

.email-input-wrapper {
  position: relative;
  width: 100%;
}

.email-suggestions {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 100;
  margin-top: 4px;
  background-color: var(--surface-color, #ffffff);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: var(--address-form-input-radius, 8px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  max-height: 240px;
  overflow-y: auto;
}

.email-suggestion-item {
  display: block;
  width: 100%;
  padding: 10px 14px;
  text-align: left;
  font-size: var(--address-form-input-size, 14px);
  color: var(--text-color, #1f2937);
  background: none;
  border: none;
  cursor: pointer;
  transition: background-color 0.15s;
}

.email-suggestion-item:hover,
.email-suggestion-item.is-selected {
  background-color: var(--primary-color-light, #eff6ff);
}

.email-suggestion-item:not(:last-child) {
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

/* 响应式 - 使用容器查询以支持主题编辑器预览 */
@container (max-width: 640px) {
  .layout-double .form-row {
    grid-template-columns: 1fr;
  }

  .form-group.full-width {
    grid-column: auto;
  }
}
</style>
