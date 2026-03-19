<script setup lang="ts">
/**
 * SearchableSelect - 可搜索下拉选择组件
 * 支持输入搜索、键盘导航、点击选中
 */

interface Props {
  modelValue: string;
  options: string[];
  placeholder?: string;
  disabled?: boolean;
  loading?: boolean;
  hasError?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择',
  disabled: false,
  loading: false,
  hasError: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const searchQuery = ref('');
const isOpen = ref(false);
const highlightedIndex = ref(-1);
const inputRef = ref<HTMLInputElement | null>(null);
const listRef = ref<HTMLElement | null>(null);

const filteredOptions = computed(() => {
  if (!searchQuery.value) return props.options;
  const q = searchQuery.value.toLowerCase();
  return props.options.filter(opt => opt.toLowerCase().includes(q));
});

const displayValue = computed(() => {
  if (isOpen.value) return searchQuery.value;
  return props.modelValue || '';
});

function openDropdown() {
  if (props.disabled || props.loading) return;
  isOpen.value = true;
  searchQuery.value = '';
  highlightedIndex.value = -1;
  nextTick(() => inputRef.value?.focus());
}

function closeDropdown() {
  isOpen.value = false;
  searchQuery.value = '';
  highlightedIndex.value = -1;
}

function selectOption(value: string) {
  emit('update:modelValue', value);
  closeDropdown();
}

function handleInputFocus() {
  if (!isOpen.value) {
    openDropdown();
  }
}

function handleInputBlur(event: FocusEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null;
  if (listRef.value?.contains(relatedTarget)) return;
  closeDropdown();
}

function handleInput(event: Event) {
  const target = event.target as HTMLInputElement;
  searchQuery.value = target.value;
  highlightedIndex.value = -1;
  if (!isOpen.value) isOpen.value = true;
}

function handleKeydown(event: KeyboardEvent) {
  if (!isOpen.value) {
    if (event.key === 'ArrowDown' || event.key === 'Enter') {
      openDropdown();
      event.preventDefault();
    }
    return;
  }

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault();
      highlightedIndex.value = Math.min(
        highlightedIndex.value + 1,
        filteredOptions.value.length - 1
      );
      scrollToHighlighted();
      break;
    case 'ArrowUp':
      event.preventDefault();
      highlightedIndex.value = Math.max(highlightedIndex.value - 1, 0);
      scrollToHighlighted();
      break;
    case 'Enter':
      event.preventDefault();
      if (highlightedIndex.value >= 0 && filteredOptions.value[highlightedIndex.value]) {
        selectOption(filteredOptions.value[highlightedIndex.value]);
      }
      break;
    case 'Escape':
      event.preventDefault();
      closeDropdown();
      break;
  }
}

function scrollToHighlighted() {
  nextTick(() => {
    const list = listRef.value;
    if (!list) return;
    const item = list.children[highlightedIndex.value] as HTMLElement;
    if (item) {
      item.scrollIntoView({ block: 'nearest' });
    }
  });
}
</script>

<template>
  <div class="searchable-select" :class="{ 'is-disabled': disabled, 'is-open': isOpen, 'has-error': hasError }">
    <input
      ref="inputRef"
      type="text"
      class="searchable-select-input"
      :value="displayValue"
      :placeholder="loading ? '加载中...' : placeholder"
      :disabled="disabled || loading"
      autocomplete="off"
      @focus="handleInputFocus"
      @blur="handleInputBlur"
      @input="handleInput"
      @keydown="handleKeydown"
    />
    <span class="searchable-select-arrow" :class="{ 'is-open': isOpen }"></span>
    <div
      v-if="isOpen && filteredOptions.length > 0"
      ref="listRef"
      class="searchable-select-dropdown"
    >
      <button
        v-for="(option, index) in filteredOptions"
        :key="option"
        type="button"
        class="searchable-select-option"
        :class="{ 
          'is-highlighted': index === highlightedIndex,
          'is-selected': option === modelValue 
        }"
        @mousedown.prevent="selectOption(option)"
        @mouseenter="highlightedIndex = index"
      >
        {{ option }}
      </button>
    </div>
    <div
      v-if="isOpen && filteredOptions.length === 0"
      class="searchable-select-dropdown searchable-select-empty"
    >
      无匹配结果
    </div>
  </div>
</template>

<style scoped>
.searchable-select {
  position: relative;
  width: 100%;
}

.searchable-select-input {
  width: 100%;
  padding: var(--address-form-input-padding, 10px 14px);
  padding-right: 36px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: var(--address-form-input-radius, 8px);
  font-size: var(--address-form-input-size, 14px);
  color: var(--text-color, #1f2937);
  background-color: var(--surface-color, #ffffff);
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  cursor: pointer;
}

.searchable-select-input:focus {
  outline: none;
  border-color: var(--primary-color, #3b82f6);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  cursor: text;
}

.searchable-select-input:disabled {
  background-color: var(--background-color, #f9fafb);
  cursor: not-allowed;
  opacity: 0.6;
}

.searchable-select-input::placeholder {
  color: var(--text-secondary-color, #9ca3af);
}

.searchable-select.has-error .searchable-select-input {
  border-color: var(--error-color, #ef4444);
}

.searchable-select-arrow {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 12px;
  height: 12px;
  pointer-events: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%236b7280' d='M2.22 4.47a.75.75 0 0 1 1.06 0L6 7.19l2.72-2.72a.75.75 0 1 1 1.06 1.06L6.53 8.78a.75.75 0 0 1-1.06 0L2.22 5.53a.75.75 0 0 1 0-1.06z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: center;
  transition: transform 0.2s;
}

.searchable-select-arrow.is-open {
  transform: translateY(-50%) rotate(180deg);
}

.searchable-select-dropdown {
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

.searchable-select-option {
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

.searchable-select-option:hover,
.searchable-select-option.is-highlighted {
  background-color: var(--primary-color-light, #eff6ff);
}

.searchable-select-option.is-selected {
  color: var(--primary-color, #3b82f6);
  font-weight: 500;
}

.searchable-select-option:not(:last-child) {
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.searchable-select-empty {
  padding: 12px 14px;
  text-align: center;
  color: var(--text-secondary-color, #9ca3af);
  font-size: var(--address-form-input-size, 14px);
}
</style>
