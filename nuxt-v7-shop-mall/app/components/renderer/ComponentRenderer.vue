<script setup lang="ts">
/**
 * 单个组件渲染器
 * 根据组件类型动态渲染对应的组件
 */

import type { ComponentNode, DeviceType, GlobalStyle } from "~/types/builder";
import { useDataContext, resolvePropsBindings, hasBindingExpression } from "~/composables/useDataContext";

const props = defineProps<{
  node: ComponentNode;
  globalStyle?: GlobalStyle;
  previewDevice: DeviceType;
  isEditor?: boolean;
}>();

const emit = defineEmits<{
  "component-click": [component: ComponentNode];
}>();

// 响应式样式
const { resolveStyle } = useResponsive();

// 数据上下文（用于解析绑定表达式）
const dataContext = useDataContext();

/**
 * kebab-case 转 PascalCase
 * 例如: "header-bar" -> "HeaderBar"
 */
function kebabToPascal(str: string): string {
  return str
    .split("-")
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join("");
}

// 当前页面状态（仅编辑器模式使用）
// 使用条件调用避免在前端渲染时引入编辑器依赖
const selectedComponentId = props.isEditor
  ? useCurrentPage().selectedComponentId
  : ref<string | null>(null);

// 计算样式
const computedStyle = computed(() => {
  return resolveStyle(props.node.style, props.previewDevice, props.globalStyle);
});

// 是否选中
const isSelected = computed(() => {
  return props.isEditor && selectedComponentId.value === props.node.id;
});

// 处理点击
function handleClick(event: MouseEvent) {
  if (props.isEditor) {
    event.stopPropagation();
    emit("component-click", props.node);
  }
}

// 获取要渲染的组件（使用 Vue 的 resolveComponent 从全局组件解析）
const renderComponent = computed(() => {
  // kebab-case 转 PascalCase（Nuxt 全局组件使用 PascalCase）
  const pascalName = kebabToPascal(props.node.type);
  
  try {
    const component = resolveComponent(pascalName);
    // resolveComponent 找不到时返回字符串，需要检查
    return typeof component === "string" ? null : component;
  } catch {
    return null;
  }
});

// 解析后的 props（处理数据绑定表达式）
const resolvedProps = computed(() => {
  // 编辑器模式下不解析绑定，直接显示原始值
  if (props.isEditor) {
    return props.node.props;
  }

  // 检查是否有绑定表达式
  if (!hasBindingExpression(props.node.props)) {
    return props.node.props;
  }

  // 解析绑定表达式
  return resolvePropsBindings(props.node.props, dataContext.value);
});
</script>

<template>
  <div
    class="component-wrapper"
    :class="{ selected: isSelected, 'is-editor': isEditor }"
    :style="computedStyle"
    :data-component-id="node.id"
    :data-component-type="node.type"
    @click="handleClick"
  >
    <!-- 动态组件渲染 -->
    <component
      :is="renderComponent"
      v-if="renderComponent"
      v-bind="resolvedProps"
      :global-style="globalStyle"
      :preview-device="previewDevice"
    />

    <!-- 未注册的组件显示占位 -->
    <div v-else class="placeholder-component">
      <span class="placeholder-icon">📦</span>
      <span class="placeholder-text">{{ node.type }}</span>
    </div>

    <!-- 子组件渲染 -->
    <template v-if="node.children && node.children.length > 0">
      <ComponentRenderer
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :global-style="globalStyle"
        :preview-device="previewDevice"
        :is-editor="isEditor"
        @component-click="emit('component-click', $event)"
      />
    </template>
  </div>
</template>

<style scoped>
.component-wrapper {
  position: relative;
}

.component-wrapper.is-editor {
  cursor: pointer;
  transition: outline 0.2s;
}

.component-wrapper.is-editor:hover {
  outline: 1px dashed #3b82f6;
  outline-offset: 2px;
}

.component-wrapper.selected {
  outline: 2px solid #3b82f6 !important;
  outline-offset: 2px;
}

.placeholder-component {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  padding: 16px;
  background-color: #f1f5f9;
  border: 2px dashed #cbd5e1;
  border-radius: 8px;
  color: #64748b;
}

.placeholder-icon {
  font-size: 24px;
  margin-bottom: 4px;
}

.placeholder-text {
  font-size: 12px;
  font-family: monospace;
}
</style>
