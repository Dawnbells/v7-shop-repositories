<script setup lang="ts">
/**
 * 颜色选择器组件
 * 支持颜色选择器 + 文本输入
 */

interface Props {
  modelValue?: string;
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: "",
  placeholder: "#000000",
});

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

// 当前显示的颜色值
const displayColor = computed(() => {
  return props.modelValue || props.placeholder;
});

// 文本输入值
const textInput = ref(props.modelValue || "");

// 同步外部值到内部
watch(
  () => props.modelValue,
  (value) => {
    textInput.value = value || "";
  }
);

// 处理颜色选择器变化
function handleColorPickerChange(event: Event) {
  const input = event.target as HTMLInputElement;
  textInput.value = input.value;
  emit("update:modelValue", input.value);
}

// 处理文本输入变化
function handleTextInputChange(event: Event) {
  const input = event.target as HTMLInputElement;
  textInput.value = input.value;
  emit("update:modelValue", input.value);
}
</script>

<template>
  <div class="color-picker">
    <!-- 颜色预览 -->
    <div class="color-preview" :style="{ backgroundColor: displayColor }"></div>

    <!-- 颜色选择器 -->
    <input
      type="color"
      class="color-input"
      :value="displayColor"
      @input="handleColorPickerChange"
    />

    <!-- 文本输入 -->
    <input
      type="text"
      class="text-input"
      :value="textInput"
      :placeholder="placeholder"
      @input="handleTextInputChange"
    />
  </div>
</template>

<style scoped>
.color-picker {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-preview {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: 1px solid #334155;
  flex-shrink: 0;
}

.color-input {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
}

.text-input {
  flex: 1;
  min-width: 80px;
  padding: 6px 8px;
  font-size: 13px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #e2e8f0;
  outline: none;
  transition: border-color 0.2s;
}

.text-input:focus {
  border-color: #3b82f6;
}

.text-input::placeholder {
  color: #64748b;
}
</style>
