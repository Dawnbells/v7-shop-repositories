<script setup lang="ts">
/**
 * 单个组件渲染器
 * 根据组件类型动态渲染对应的组件
 */

import type {
  ComponentNode,
  DeviceType,
  GlobalStyle,
  ResponsiveStyle,
} from "~/types/builder";
import type { VariableValues } from "~/types/data-context";
import type { ProductInfo } from "~/types/page-context";
import type { ArticleInfo } from "~~/server/repositories/article.repository";
import { resolvePropsBindings, hasBindingExpression } from "~/composables";

const props = defineProps<{
  node: ComponentNode;
  globalStyle?: GlobalStyle;
  previewDevice: DeviceType;
  editorActions?: EditorActions | null;
  // 父组件信息（可选，用于编辑父容器）
  parentInfo?: {
    id: string;
    type: string;
    name: string;
  } | null;
}>();

const emit = defineEmits<{
  "component-click": [component: ComponentNode];
}>();

// 响应式样式
const { resolveStyle, resolveResponsiveStyleRefs } = useResponsive();

// 注入变量值（由页面或编辑器提供）
const variableValues = inject<Ref<VariableValues>>("variableValues", ref({}));

// 注入产品数据
const productInfo = inject<Ref<ProductInfo | null>>("productInfo", ref(null));
// 注入文章数据
const articleInfo = inject<Ref<ArticleInfo | null>>("articleInfo", ref(null));

// 注入编辑器状态（来自 BuilderCanvas 或容器组件）
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 计算属性：是否在编辑器模式
const isEditor = computed(() => isInEditor.value);

// 提供给子组件的函数，用于隐藏当前组件的菜单
const hideParentToolbar = () => {
  isHovered.value = false;
  showToolbar.value = false;
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
  if (showTimer) {
    clearTimeout(showTimer);
    showTimer = null;
  }
};
// 注入父组件的 hideToolbar 函数
const parentHideToolbar = inject<() => void>("hideToolbar", () => {});
// 提供给子组件的 hideToolbar 函数
provide("hideToolbar", hideParentToolbar);

// 组件注册表
const { getComponentInstance } = useComponentRegistry();

// 构造页面数据上下文（用于解析绑定表达式）
const pageDataContext = computed(() => ({
  product: productInfo.value,
  article: articleInfo.value,
}));

// 当前页面状态（仅编辑器模式使用）
// 使用条件调用避免在前端渲染时引入编辑器依赖
const selectedComponentId = isInEditor.value
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
  selectComponent: (id: string | null) => void;
}

// 编辑器操作（优先使用 inject 获取完整方法）
// 注意：props.editorActions 可能来自容器组件，可能不完整
const injectedEditorActions = inject<any>("editorActions", null);
const editorActions = computed(() => {
  // 优先使用 inject 获得的完整 editorActions
  if (injectedEditorActions) {
    return injectedEditorActions;
  }
  return props.editorActions ?? null;
});

// 注入父组件信息（用于编辑父容器）
interface ParentComponentInfo {
  id: string;
  type: string;
  name: string;
}
// 注入父组件信息（用于编辑父容器）
const injectedParentComponent = inject<Ref<ParentComponentInfo | null>>(
  "parentComponent",
  ref(null)
);
// 优先使用 props.parentInfo，否则使用 inject
const parentComponent = computed(
  () => props.parentInfo ?? injectedParentComponent?.value ?? null
);

// 计算样式（用于 wrapper div）
const computedStyle = computed(() => {
  return resolveStyle(
    props.node.style,
    props.previewDevice,
    props.globalStyle,
    variableValues.value
  );
});

// 解析后的组件样式（传递给子组件，所有引用已解析为实际值）
const resolvedComponentStyle = computed<ResponsiveStyle | undefined>(() => {
  if (!props.node.style || !props.globalStyle) return props.node.style;
  return resolveResponsiveStyleRefs(
    props.node.style,
    props.globalStyle,
    variableValues.value
  );
});

// 是否选中
const isSelected = computed(() => {
  return isInEditor.value && selectedComponentId.value === props.node.id;
});

// 组件包装器引用
const wrapperRef = ref<HTMLElement | null>(null);
const toolbarRef = ref<HTMLElement | null>(null);

// 是否处于 hover 状态（用于显示悬浮菜单）
const isHovered = ref(false);
const showToolbar = ref(false);

// 延迟显示/隐藏定时器
let showTimer: ReturnType<typeof setTimeout> | null = null;
let hideTimer: ReturnType<typeof setTimeout> | null = null;

// 悬浮工具栏位置
const toolbarStyle = computed(() => {
  if (!wrapperRef.value) return {};
  const rect = wrapperRef.value.getBoundingClientRect();
  return {
    position: "fixed" as const,
    top: `${rect.top + window.scrollY - 36}px`,
    right: `${window.innerWidth - rect.right + window.scrollX}px`,
  };
});

// 鼠标进入
function handleMouseEnter(e: MouseEvent) {
  if (!isInEditor.value) return;

  // 检查鼠标是否从子组件移入（如果是，则不处理）
  const target = e.target as HTMLElement;
  if (
    wrapperRef.value &&
    wrapperRef.value.contains(target) &&
    target !== wrapperRef.value
  ) {
    // 鼠标是从子组件移入的，忽略此事件
    return;
  }

  // 通知父组件隐藏菜单
  if (parentHideToolbar) {
    parentHideToolbar();
  }

  // 清除隐藏定时器
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
  // 延迟显示工具栏
  showTimer = setTimeout(() => {
    isHovered.value = true;
    showToolbar.value = true;
  }, 100);
}

// 鼠标离开
function handleMouseLeave(e: MouseEvent) {
  // 检查鼠标是否移到了子组件上（如果是，则不隐藏当前组件的菜单）
  const relatedTarget = e.relatedTarget as HTMLElement;
  if (
    wrapperRef.value &&
    relatedTarget &&
    wrapperRef.value.contains(relatedTarget)
  ) {
    // 鼠标移到了子组件上，不隐藏当前组件的菜单
    return;
  }

  // 清除显示定时器
  if (showTimer) {
    clearTimeout(showTimer);
    showTimer = null;
  }
  // 延迟隐藏，给鼠标移动到工具栏的时间
  hideTimer = setTimeout(() => {
    isHovered.value = false;
    showToolbar.value = false;
  }, 100);
}

// 鼠标进入工具栏
function handleToolbarMouseEnter() {
  // 清除隐藏定时器
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
  isHovered.value = true;
  showToolbar.value = true;
}

// 鼠标离开工具栏
function handleToolbarMouseLeave() {
  isHovered.value = false;
  showToolbar.value = false;
}

// 是否可以上移
const canMoveUp = computed(() => {
  if (!editorActions.value) return false;
  return editorActions.value.canMoveUp(props.node.id);
});

// 是否可以下移
const canMoveDown = computed(() => {
  if (!editorActions.value) return false;
  return editorActions.value.canMoveDown(props.node.id);
});

// 组件元数据
const componentMeta = computed(() => {
  if (!editorActions.value) return null;
  return editorActions.value.getComponentMeta(props.node.type);
});

// 是否应该自动渲染子组件
// 如果组件是容器组件（isContainer: true），则不自动渲染，由容器组件自己控制渲染
const shouldRenderChildren = computed(() => {
  // 如果没有元数据或不是容器组件，则自动渲染 children
  if (!componentMeta.value) return true;
  // 容器组件由自己控制 children 的渲染
  return !componentMeta.value.isContainer;
});

// 处理点击
function handleClick(event: MouseEvent) {
  event.stopPropagation();
  if (isInEditor.value) {
    // 优先使用 selectComponent 方法
    if (editorActions.value?.selectComponent) {
      editorActions.value.selectComponent(props.node.id);
    }
    emit("component-click", props.node);
  }
}

// 上移组件
function handleMoveUp(event: MouseEvent) {
  event.stopPropagation();
  if (editorActions.value) {
    editorActions.value.moveComponentUp(props.node.id);
  }
}

// 下移组件
function handleMoveDown(event: MouseEvent) {
  event.stopPropagation();
  if (editorActions.value) {
    editorActions.value.moveComponentDown(props.node.id);
  }
}

// 删除组件
function handleDelete(event: MouseEvent) {
  event.stopPropagation();
  if (editorActions.value && confirm("确定要删除这个组件吗？")) {
    editorActions.value.removeComponent(props.node.id);
  }
}

// 编辑父容器
function handleEditParent(event: MouseEvent) {
  event.stopPropagation();
  if (parentComponent.value && editorActions.value) {
    editorActions.value.selectComponent(parentComponent.value.id);
    // 隐藏当前工具栏
    showToolbar.value = false;
    isHovered.value = false;
  }
}

// 获取要渲染的组件（从注册表获取）
const renderComponent = computed(() => {
  return getComponentInstance(props.node.type) || null;
});

// 解析后的 props（处理数据绑定表达式）
const resolvedProps = computed(() => {
  // 编辑器模式下不解析绑定，直接显示原始值
  if (isInEditor.value) {
    return props.node.props;
  }

  // 检查是否有绑定表达式
  if (!hasBindingExpression(props.node.props)) {
    return props.node.props;
  }

  // 解析绑定表达式
  return resolvePropsBindings(props.node.props, pageDataContext.value);
});
</script>

<template>
  <div
    ref="wrapperRef"
    class="component-wrapper"
    :class="{ selected: isSelected, 'is-editor': isEditor }"
    :style="computedStyle"
    :data-component-id="node.id"
    :data-component-type="node.type"
    @click="handleClick"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <!-- 鼠标悬浮时显示的操作菜单 (Teleport 到 body 避免被裁剪) -->
    <Teleport to="body">
      <div
        v-if="showToolbar && editorActions"
        ref="toolbarRef"
        class="floating-toolbar"
        :style="toolbarStyle"
        @mouseenter="handleToolbarMouseEnter"
        @mouseleave="handleToolbarMouseLeave"
      >
        <span
          v-if="componentMeta"
          class="toolbar-label"
          title="点击编辑组件属性"
          @click="handleClick"
        >
          <span :class="componentMeta.icon" class="toolbar-icon"></span>
          {{ componentMeta.name }}
        </span>
        <div class="toolbar-buttons">
          <button
            v-if="parentComponent"
            class="toolbar-btn"
            title="编辑父容器"
            @click="handleEditParent"
          >
            <span class="i-carbon-folder-parent"></span>
          </button>
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
    </Teleport>

    <!-- 动态组件渲染 -->
    <component
      :is="renderComponent"
      v-if="renderComponent"
      v-bind="resolvedProps"
      :global-style="globalStyle"
      :preview-device="previewDevice"
      :component-style="resolvedComponentStyle"
      :node="node"
      v-on="$attrs"
    />

    <!-- 未注册的组件显示占位 -->
    <div v-else class="placeholder-component">
      <span class="placeholder-icon">📦</span>
      <span class="placeholder-text">{{ node.type }}</span>
    </div>
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
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  background-color: #1e293b;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  z-index: 9999;
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
  cursor: pointer;
  transition: color 0.15s;
}

.toolbar-label:hover {
  color: #60a5fa;
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
