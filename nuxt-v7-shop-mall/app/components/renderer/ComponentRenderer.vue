<script setup lang="ts">
/**
 * 单个组件渲染器
 * 根据组件类型动态渲染对应的组件
 */

import type { ComponentNode, DeviceType, GlobalStyle, ResponsiveStyle } from "~/types/builder";
import type { VariableValues } from "~/types/data-context";
import { useDataContext, resolvePropsBindings, hasBindingExpression } from "~/composables";

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
const { resolveStyle, resolveResponsiveStyleRefs } = useResponsive();

// 注入变量值（由页面或编辑器提供）
const variableValues = inject<Ref<VariableValues>>('variableValues', ref({}));

// 组件注册表
const { getComponentInstance } = useComponentRegistry();

// 数据上下文（用于解析绑定表达式）
const dataContext = useDataContext();

// 当前页面状态（仅编辑器模式使用）
// 使用条件调用避免在前端渲染时引入编辑器依赖
const selectedComponentId = props.isEditor
  ? useCurrentPage().selectedComponentId
  : ref<string | null>(null);

// 编辑器操作方法（从 BuilderCanvas 注入）
interface EditorActions {
  moveComponentUp: (id: string) => void;
  moveComponentDown: (id: string) => void;
  removeComponent: (id: string) => void;
  canMoveUp: (id: string) => boolean;
  canMoveDown: (id: string) => boolean;
  getComponentMeta: (type: string) => any;
}

const editorActions = props.isEditor
  ? inject<EditorActions>('editorActions', null)
  : null;

// 计算样式（用于 wrapper div）
const computedStyle = computed(() => {
  return resolveStyle(props.node.style, props.previewDevice, props.globalStyle, variableValues.value);
});

// 解析后的组件样式（传递给子组件，所有引用已解析为实际值）
const resolvedComponentStyle = computed<ResponsiveStyle | undefined>(() => {
  if (!props.node.style || !props.globalStyle) return props.node.style;
  return resolveResponsiveStyleRefs(props.node.style, props.globalStyle, variableValues.value);
});

// 是否选中
const isSelected = computed(() => {
  return props.isEditor && selectedComponentId.value === props.node.id;
});

// 是否处于 hover 状态（用于显示悬浮菜单）
const isHovered = ref(false);

// 鼠标进入
function handleMouseEnter() {
  if (props.isEditor) {
    isHovered.value = true;
  }
}

// 鼠标离开
function handleMouseLeave() {
  isHovered.value = false;
}

// 是否可以上移
const canMoveUp = computed(() => {
  if (!editorActions) return false;
  return editorActions.canMoveUp(props.node.id);
});

// 是否可以下移
const canMoveDown = computed(() => {
  if (!editorActions) return false;
  return editorActions.canMoveDown(props.node.id);
});

// 组件元数据
const componentMeta = computed(() => {
  if (!editorActions) return null;
  return editorActions.getComponentMeta(props.node.type);
});

// 处理点击
function handleClick(event: MouseEvent) {
  if (props.isEditor) {
    event.stopPropagation();
    emit("component-click", props.node);
  }
}

// 上移组件
function handleMoveUp(event: MouseEvent) {
  event.stopPropagation();
  if (editorActions) {
    editorActions.moveComponentUp(props.node.id);
  }
}

// 下移组件
function handleMoveDown(event: MouseEvent) {
  event.stopPropagation();
  if (editorActions) {
    editorActions.moveComponentDown(props.node.id);
  }
}

// 删除组件
function handleDelete(event: MouseEvent) {
  event.stopPropagation();
  if (editorActions && confirm("确定要删除这个组件吗？")) {
    editorActions.removeComponent(props.node.id);
  }
}

// 获取要渲染的组件（从注册表获取）
const renderComponent = computed(() => {
  return getComponentInstance(props.node.type) || null;
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
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <!-- 鼠标悬浮时显示的操作菜单 -->
    <div v-if="isHovered && editorActions" class="floating-toolbar">
      <span v-if="componentMeta" class="toolbar-label">
        <span :class="componentMeta.icon" class="toolbar-icon"></span>
        {{ componentMeta.name }}
      </span>
      <div class="toolbar-buttons">
        <button
          class="toolbar-btn"
          :disabled="!canMoveUp"
          title="上移"
          @click="handleMoveUp"
        >
          <span class="i-carbon-arrow-up"></span>
        </button>
        <button
          class="toolbar-btn"
          :disabled="!canMoveDown"
          title="下移"
          @click="handleMoveDown"
        >
          <span class="i-carbon-arrow-down"></span>
        </button>
        <button
          class="toolbar-btn toolbar-btn-danger"
          title="删除"
          @click="handleDelete"
        >
          <span class="i-carbon-trash-can"></span>
        </button>
      </div>
    </div>

    <!-- 动态组件渲染 -->
    <component
      :is="renderComponent"
      v-if="renderComponent"
      v-bind="resolvedProps"
      :global-style="globalStyle"
      :preview-device="previewDevice"
      :component-style="resolvedComponentStyle"
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

/* 悬浮操作菜单 */
.floating-toolbar {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  background-color: #1e293b;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  z-index: 100;
  white-space: nowrap;
}

.toolbar-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #94a3b8;
  padding-right: 8px;
  border-right: 1px solid #334155;
}

.toolbar-icon {
  font-size: 14px;
}

.toolbar-buttons {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 14px;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.toolbar-btn:hover:not(:disabled) {
  color: #e2e8f0;
  background-color: #334155;
}

.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.toolbar-btn-danger:hover:not(:disabled) {
  color: #ef4444;
  background-color: rgba(239, 68, 68, 0.15);
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
