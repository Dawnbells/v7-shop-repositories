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

// 编辑器中禁用输入
function handleInput(field: keyof typeof shippingAddress.value, event: Event) {
  if (isInEditor.value) return;
  const target = event.target as HTMLInputElement;
  updateAddress(field, target.value);
}
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
      <div v-if="showEmail" class="form-group">
        <label class="form-label">邮箱</label>
        <input
          type="email"
          class="form-input"
          :class="{ 'has-error': formErrors.email }"
          :value="shippingAddress.email"
          placeholder="请输入邮箱地址"
          :disabled="isInEditor"
          @input="handleInput('email', $event)"
        />
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
