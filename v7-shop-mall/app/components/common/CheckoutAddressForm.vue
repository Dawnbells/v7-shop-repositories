<script setup lang="ts">
/**
 * CheckoutAddressForm Block - 收货地址表单组件
 * 收货人姓名、电话、地址等字段，带表单验证
 * 支持根据国家 addressFields 动态显示省/市/区/邮编级联选择
 */

import type { CountryInfo } from '~/composables/usePageContext';

interface DistrictItem {
  district: string;
  postalCode: string;
}

interface Props {
  showEmail?: boolean;
  showNote?: boolean;
  layout?: "single" | "double";
}

const props = withDefaults(defineProps<Props>(), {
  showEmail: true,
  showNote: true,
  layout: "double",
});

const { shippingAddress, formErrors, updateAddress } = useCheckoutPage();
const { countryInfo } = usePageContext();
const { globalConfig } = usePageTheme();
const { t } = useI18n();

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 是否允许手动输入地址
const allowCustomAddress = computed(() => globalConfig.value?.allowCustomAddress ?? false);

// ============ addressFields 解析 ============

type AddressFieldKey = 'province' | 'city' | 'district' | 'postal_code';

const addressFields = computed<AddressFieldKey[]>(() => {
  const raw = countryInfo.value?.addressFields;
  if (!raw) return [];
  return raw.split(',').map(s => s.trim()).filter(Boolean) as AddressFieldKey[];
});

const hasField = (field: AddressFieldKey) => addressFields.value.includes(field);

// ============ 级联选择状态 ============

const provinceList = ref<string[]>([]);
const cityList = ref<string[]>([]);
const districtList = ref<DistrictItem[]>([]);
const postalCodeList = ref<string[]>([]);

const loadingProvinces = ref(false);
const loadingCities = ref(false);
const loadingDistricts = ref(false);
const loadingPostalCodes = ref(false);

const districtNames = computed(() => districtList.value.map(d => d.district));

// 加载省份列表
async function loadProvinces() {
  if (isInEditor.value || !hasField('province') || !countryInfo.value?.code) return;
  loadingProvinces.value = true;
  try {
    provinceList.value = await $fetch<string[]>('/api/address/provinces', {
      query: { country: countryInfo.value.code }
    });
  } catch {
    provinceList.value = [];
  } finally {
    loadingProvinces.value = false;
  }
}

// 加载城市列表
async function loadCities(province: string) {
  if (isInEditor.value || !hasField('city') || !province || !countryInfo.value?.code) {
    cityList.value = [];
    return;
  }
  loadingCities.value = true;
  try {
    cityList.value = await $fetch<string[]>('/api/address/cities', {
      query: { country: countryInfo.value.code, province }
    });
  } catch {
    cityList.value = [];
  } finally {
    loadingCities.value = false;
  }
}

// 加载区县列表（含邮编）
async function loadDistricts(province: string, city: string) {
  if (isInEditor.value || !hasField('district') || !province || !city || !countryInfo.value?.code) {
    districtList.value = [];
    return;
  }
  loadingDistricts.value = true;
  try {
    districtList.value = await $fetch<DistrictItem[]>('/api/address/districts', {
      query: { country: countryInfo.value.code, province, city }
    });
  } catch {
    districtList.value = [];
  } finally {
    loadingDistricts.value = false;
  }
}

// 加载邮编列表（无区县但有邮编的场景）
async function loadPostalCodes(province: string, city: string) {
  if (isInEditor.value || !hasField('postal_code') || !province || !city || !countryInfo.value?.code) {
    postalCodeList.value = [];
    return;
  }
  loadingPostalCodes.value = true;
  try {
    postalCodeList.value = await $fetch<string[]>('/api/address/postal-codes', {
      query: { country: countryInfo.value.code, province, city }
    });
  } catch {
    postalCodeList.value = [];
  } finally {
    loadingPostalCodes.value = false;
  }
}

// 级联选择处理
function onProvinceSelect(value: string) {
  if (isInEditor.value) return;
  updateAddress('province', value);
  updateAddress('city', '');
  updateAddress('district', '');
  updateAddress('postalCode', '');
  cityList.value = [];
  districtList.value = [];
  postalCodeList.value = [];
  if (value) loadCities(value);
}

function onCitySelect(value: string) {
  if (isInEditor.value) return;
  updateAddress('city', value);
  updateAddress('district', '');
  updateAddress('postalCode', '');
  districtList.value = [];
  postalCodeList.value = [];
  
  if (value && shippingAddress.value.province) {
    if (hasField('district')) {
      loadDistricts(shippingAddress.value.province, value);
    } else if (hasField('postal_code')) {
      loadPostalCodes(shippingAddress.value.province, value);
    }
  }
}

function onDistrictSelect(value: string) {
  if (isInEditor.value) return;
  updateAddress('district', value);
  const matched = districtList.value.find(d => d.district === value);
  updateAddress('postalCode', matched?.postalCode || '');
}

function onPostalCodeSelect(value: string) {
  if (isInEditor.value) return;
  updateAddress('postalCode', value);
}

// 页面加载时获取省份
onMounted(() => {
  if (hasField('province')) {
    loadProvinces();
  }
});

// ============ 邮箱联想 ============

const emailSuggestions = ref<string[]>([]);
const showEmailSuggestions = ref(false);
const selectedSuggestionIndex = ref(-1);
const emailInputRef = ref<HTMLInputElement | null>(null);
const suggestionsRef = ref<HTMLElement | null>(null);

let debounceTimer: ReturnType<typeof setTimeout> | null = null;

async function fetchEmailSuggestions(prefix: string) {
  if (!prefix.includes('@') || isInEditor.value) {
    emailSuggestions.value = [];
    showEmailSuggestions.value = false;
    return;
  }
  
  try {
    const country = countryInfo.value?.code || 'default';
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

function handleEmailInput(event: Event) {
  if (isInEditor.value) return;
  const target = event.target as HTMLInputElement;
  updateAddress('email', target.value);
  
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    fetchEmailSuggestions(target.value);
  }, 150);
}

function selectEmailSuggestion(email: string) {
  updateAddress('email', email);
  showEmailSuggestions.value = false;
  emailSuggestions.value = [];
  selectedSuggestionIndex.value = -1;
}

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
        selectEmailSuggestion(emailSuggestions.value[selectedSuggestionIndex.value]);
      }
      break;
    case 'Escape':
      showEmailSuggestions.value = false;
      selectedSuggestionIndex.value = -1;
      break;
  }
}

function handleEmailBlur(event: FocusEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null;
  if (suggestionsRef.value?.contains(relatedTarget)) return;
  
  setTimeout(() => {
    showEmailSuggestions.value = false;
    selectedSuggestionIndex.value = -1;
  }, 150);
}

// ============ 通用输入 ============

function handleInput(field: keyof typeof shippingAddress.value, event: Event) {
  if (isInEditor.value) return;
  const target = event.target as HTMLInputElement;
  updateAddress(field, target.value);
}

// 电话输入处理：只允许输入数字
function handlePhoneInput(event: Event) {
  if (isInEditor.value) return;
  const input = event.target as HTMLInputElement;
  const filtered = input.value.replace(/\D/g, '');
  input.value = filtered;
  updateAddress('phone', filtered);
}

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
          {{ t('address.fullNameLabel') }} <span class="required">*</span>
        </label>
        <input
          type="text"
          class="form-input"
          :class="{ 'has-error': formErrors.fullName }"
          :value="shippingAddress.fullName"
          :placeholder="t('address.fullName')"
          :disabled="isInEditor"
          @input="handleInput('fullName', $event)"
        />
        <span v-if="formErrors.fullName" class="form-error">
          {{ formErrors.fullName }}
        </span>
      </div>
      <div class="form-group">
        <label class="form-label">
          {{ t('address.phoneLabel') }} <span class="required">*</span>
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
            :placeholder="t('address.phone')"
            :disabled="isInEditor"
            @input="handlePhoneInput($event)"
          />
        </div>
        <span v-if="formErrors.phone" class="form-error">
          {{ formErrors.phone }}
        </span>
      </div>
    </div>

    <!-- 第二行：邮箱 -->
    <div v-if="showEmail" class="form-row">
      <div class="form-group email-group">
        <label class="form-label">{{ t('address.email') }}</label>
        <div class="email-input-wrapper">
          <input
            ref="emailInputRef"
            type="email"
            class="form-input"
            :class="{ 'has-error': formErrors.email }"
            :value="shippingAddress.email"
            :placeholder="t('address.emailPlaceholder')"
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
              @mousedown.prevent="selectEmailSuggestion(suggestion)"
            >
              {{ suggestion }}
            </button>
          </div>
        </div>
        <span v-if="formErrors.email" class="form-error">
          {{ formErrors.email }}
        </span>
        <!-- 订阅复选框 -->
        <label class="subscribe-checkbox">
          <input
            type="checkbox"
            :checked="shippingAddress.subscribeToUpdates"
            :disabled="isInEditor"
            @change="updateAddress('subscribeToUpdates', ($event.target as HTMLInputElement).checked)"
          />
          <span class="checkbox-label">{{ t('address.subscribeToUpdates') }}</span>
        </label>
      </div>
    </div>

    <!-- 省/市/区/邮编级联选择 - 客户端渲染避免水合不匹配 -->
    <ClientOnly>
      <div v-if="hasField('province') || hasField('city')" class="form-row">
        <div v-if="hasField('province')" class="form-group">
          <label class="form-label">
            {{ t('address.provinceLabel') }} <span class="required">*</span>
          </label>
          <CommonSearchableSelect
            :model-value="shippingAddress.province"
            :options="provinceList"
            :placeholder="t('address.province')"
            :disabled="isInEditor || loadingProvinces"
            :loading="loadingProvinces"
            :has-error="!!formErrors.province"
            :allow-custom="allowCustomAddress"
            @update:model-value="onProvinceSelect"
          />
          <span v-if="formErrors.province" class="form-error">
            {{ formErrors.province }}
          </span>
        </div>
        <div v-if="hasField('city')" class="form-group">
          <label class="form-label">
            {{ t('address.cityLabel') }} <span class="required">*</span>
          </label>
          <CommonSearchableSelect
            :model-value="shippingAddress.city"
            :options="cityList"
            :placeholder="t('address.city')"
            :disabled="isInEditor || loadingCities || !shippingAddress.province"
            :loading="loadingCities"
            :has-error="!!formErrors.city"
            :allow-custom="allowCustomAddress"
            @update:model-value="onCitySelect"
          />
          <span v-if="formErrors.city" class="form-error">
            {{ formErrors.city }}
          </span>
        </div>
      </div>

      <div v-if="hasField('district') || hasField('postal_code')" class="form-row">
        <div v-if="hasField('district')" class="form-group">
          <label class="form-label">
            {{ t('address.districtLabel') }} <span class="required">*</span>
          </label>
          <CommonSearchableSelect
            :model-value="shippingAddress.district"
            :options="districtNames"
            :placeholder="t('address.district')"
            :disabled="isInEditor || loadingDistricts || !shippingAddress.city"
            :loading="loadingDistricts"
            :has-error="!!formErrors.district"
            :allow-custom="allowCustomAddress"
            @update:model-value="onDistrictSelect"
          />
          <span v-if="formErrors.district" class="form-error">
            {{ formErrors.district }}
          </span>
        </div>
        <div v-if="hasField('postal_code') && hasField('district')" class="form-group">
          <label class="form-label">{{ t('address.postalCode') }}</label>
          <input
            type="text"
            class="form-input"
            :value="shippingAddress.postalCode"
            :placeholder="t('address.postalCodeAuto')"
            disabled
          />
        </div>
        <div v-if="hasField('postal_code') && !hasField('district')" class="form-group">
          <label class="form-label">
            {{ t('address.postalCode') }} <span class="required">*</span>
          </label>
          <CommonSearchableSelect
            :model-value="shippingAddress.postalCode"
            :options="postalCodeList"
            :placeholder="t('address.postalCodePlaceholder')"
            :disabled="isInEditor || loadingPostalCodes || !shippingAddress.city"
            :loading="loadingPostalCodes"
            :has-error="!!formErrors.postalCode"
            :allow-custom="allowCustomAddress"
            @update:model-value="onPostalCodeSelect"
          />
          <span v-if="formErrors.postalCode" class="form-error">
            {{ formErrors.postalCode }}
          </span>
        </div>
      </div>

      <template #fallback>
        <div class="form-row address-skeleton-row">
          <div class="form-group">
            <div class="skeleton-label"></div>
            <div class="skeleton-input"></div>
          </div>
          <div class="form-group">
            <div class="skeleton-label"></div>
            <div class="skeleton-input"></div>
          </div>
        </div>
      </template>
    </ClientOnly>

    <!-- 详细地址 -->
    <div class="form-group full-width">
      <label class="form-label">
        {{ t('address.addressLabel') }} <span class="required">*</span>
      </label>
      <input
        type="text"
        class="form-input"
        :class="{ 'has-error': formErrors.address }"
        :value="shippingAddress.address"
        :placeholder="t('address.address')"
        :disabled="isInEditor"
        @input="handleInput('address', $event)"
      />
      <span v-if="formErrors.address" class="form-error">
        {{ formErrors.address }}
      </span>
    </div>

    <!-- 备注 -->
    <div v-if="showNote" class="form-row">
      <div class="form-group">
        <label class="form-label">{{ t('address.note') }}</label>
        <input
          type="text"
          class="form-input"
          :value="shippingAddress.note"
          :placeholder="t('address.notePlaceholder')"
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
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
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

/* 下拉选择框样式 */
.form-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%236b7280' d='M2.22 4.47a.75.75 0 0 1 1.06 0L6 7.19l2.72-2.72a.75.75 0 1 1 1.06 1.06L6.53 8.78a.75.75 0 0 1-1.06 0L2.22 5.53a.75.75 0 0 1 0-1.06z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 12px;
  padding-inline-end: 36px;
  cursor: pointer;
}

/* background-position 无逻辑属性变体，RTL 下箭头移到左侧 */
[dir='rtl'] .form-select {
  background-position: left 12px center;
}

.form-select:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.form-select.is-placeholder {
  color: var(--text-secondary-color, #9ca3af);
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
  border-inline-end: none;
  border-start-start-radius: var(--address-form-input-radius, 8px);
  border-end-start-radius: var(--address-form-input-radius, 8px);
  border-start-end-radius: 0;
  border-end-end-radius: 0;
  color: var(--text-secondary-color, #6b7280);
  font-size: var(--address-form-input-size, 14px);
  white-space: nowrap;
}

.phone-input-wrapper .form-input {
  flex: 1;
  min-width: 0;
}

.phone-input-wrapper .form-input.has-prefix {
  border-start-start-radius: 0;
  border-end-start-radius: 0;
  border-start-end-radius: var(--address-form-input-radius, 8px);
  border-end-end-radius: var(--address-form-input-radius, 8px);
}

/* 邮箱联想样式 */
.email-group {
  position: relative;
}

.email-input-wrapper {
  position: relative;
  width: 100%;
}

.email-input-wrapper .form-input {
  min-width: 0;
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
  text-align: start;
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

/* 骨架屏样式 */
.address-skeleton-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--address-form-gap, 16px);
}

.skeleton-label {
  height: 14px;
  width: 60px;
  background: linear-gradient(90deg, var(--border-color, #e5e7eb) 25%, var(--background-color, #f3f4f6) 50%, var(--border-color, #e5e7eb) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  border-radius: 4px;
}

.skeleton-input {
  height: 42px;
  background: linear-gradient(90deg, var(--border-color, #e5e7eb) 25%, var(--background-color, #f3f4f6) 50%, var(--border-color, #e5e7eb) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  border-radius: var(--address-form-input-radius, 8px);
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* 响应式 - 使用容器查询以支持主题编辑器预览 */
@container (max-width: 640px) {
  .layout-double .form-row {
    grid-template-columns: 1fr;
  }

  .form-group.full-width {
    grid-column: auto;
  }

  .address-skeleton-row {
    grid-template-columns: 1fr;
  }
}
</style>
