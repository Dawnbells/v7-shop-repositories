<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * TwoColumnContainer 组件元数据
 * 左右两栏布局容器组件
 */
export const meta: ComponentMeta = {
  type: "two-column-container",
  name: "左右容器",
  icon: "i-carbon-columns",
  category: "layout",
  description: "左右两栏布局容器，支持设置左右比例和间距",

  propsSchema: [
    {
      key: "leftWidth",
      label: "左侧宽度",
      type: "select",
      defaultValue: "50%",
      options: [
        { label: "25%", value: "25%" },
        { label: "33%", value: "33%" },
        { label: "40%", value: "40%" },
        { label: "50%", value: "50%" },
        { label: "60%", value: "60%" },
        { label: "67%", value: "67%" },
        { label: "75%", value: "75%" },
      ],
      description: "左侧区域宽度占比",
    },
    {
      key: "gap",
      label: "间距",
      type: "size",
      defaultValue: "16px",
      unit: "px",
      description: "左右两栏之间的间距",
    },
    {
      key: "verticalAlign",
      label: "垂直对齐",
      type: "select",
      defaultValue: "top",
      options: [
        { label: "顶部对齐", value: "top" },
        { label: "居中对齐", value: "center" },
        { label: "底部对齐", value: "bottom" },
      ],
      description: "左右两栏的垂直对齐方式",
    },
    {
      key: "wrap",
      label: "允许换行",
      type: "switch",
      defaultValue: false,
      description: "在小屏幕下是否允许左右换行显示",
    },
    {
      key: "leftMinHeight",
      label: "左侧最小高度",
      type: "size",
      defaultValue: "",
      unit: "px",
      placeholder: "不设置则自适应",
      description: "左侧区域的最小高度",
    },
    {
      key: "rightMinHeight",
      label: "右侧最小高度",
      type: "size",
      defaultValue: "",
      unit: "px",
      placeholder: "不设置则自适应",
      description: "右侧区域的最小高度",
    },
  ],

  styleSchema: [
    {
      key: "backgroundColor",
      label: "背景色",
      type: "color",
      defaultValue: "",
    },
    {
      key: "padding",
      label: "内边距",
      type: "size",
      defaultValue: "0px",
      unit: "px",
    },
    {
      key: "borderRadius",
      label: "圆角",
      type: "size",
      defaultValue: "0px",
      unit: "px",
    },
  ],

  supportEvents: [],

  defaultProps: {
    leftWidth: "50%",
    gap: "16px",
    verticalAlign: "top",
    wrap: false,
    leftMinHeight: "",
    rightMinHeight: "",
  },

  defaultStyle: {
    base: {
      width: "100%",
    },
  },

  // 这是一个容器组件
  isContainer: true,

  // 支持子组件
  supportChildren: true,

  // 定义插槽配置
  slots: [
    { key: "left", name: "左侧", description: "左侧区域内容" },
    { key: "right", name: "右侧", description: "右侧区域内容" },
  ],
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
import type { ComponentNode, EditorActions } from "~/types/builder";

interface Props {
  leftWidth?: string;
  gap?: string;
  verticalAlign?: "top" | "center" | "bottom";
  wrap?: boolean;
  leftMinHeight?: string;
  rightMinHeight?: string;
  componentStyle?: Record<string, any>;
  previewDevice?: string;
  // 注入的子组件节点（编辑器模式）
  node?: ComponentNode;
}

const props = withDefaults(defineProps<Props>(), {
  leftWidth: "50%",
  gap: "16px",
  verticalAlign: "top",
  wrap: false,
  leftMinHeight: "",
  rightMinHeight: "",
  componentStyle: () => ({}),
  previewDevice: "",
});

// 事件定义
const emit = defineEmits<{
  "component-click": [component: ComponentNode];
}>();

// 编辑器状态
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 编辑器操作（用于悬浮菜单）
const editorActions = inject<EditorActions | null>("editorActions", null);

// 拖拽状态
const { isDragging, dragState, updateDropTarget } = useDragDrop();

// 合并样式
const mergedStyle = computed(() => {
  const base = props.componentStyle?.base || {};
  const device = props.previewDevice
    ? props.componentStyle?.[props.previewDevice] || {}
    : {};
  return { ...base, ...device };
});

// 容器样式
const containerStyle = computed(() => ({
  display: "flex",
  flexDirection: props.wrap ? "column" : "row",
  gap: props.gap,
  alignItems: props.wrap
    ? "stretch"
    : props.verticalAlign === "center"
    ? "center"
    : props.verticalAlign === "bottom"
    ? "flex-end"
    : "flex-start",
  padding: mergedStyle.value.padding || "0px",
  backgroundColor: mergedStyle.value.backgroundColor || "transparent",
  borderRadius: mergedStyle.value.borderRadius || "0px",
  width: "100%",
}));

// 左侧样式
const leftStyle = computed(() => ({
  width: props.wrap ? "100%" : props.leftWidth,
  minHeight: props.leftMinHeight ? `${props.leftMinHeight}px` : "auto",
}));

// 右侧样式
const rightStyle = computed(() => ({
  width: props.wrap ? "100%" : `calc(100% - ${props.leftWidth} - ${props.gap})`,
  minHeight: props.rightMinHeight ? `${props.rightMinHeight}px` : "auto",
}));

// 根据 slot 属性过滤子组件
const leftChildren = computed(() => {
  if (!props.node?.children) return [];
  return props.node.children.filter(
    (child: ComponentNode) => child.props?.slot === "left"
  );
});

const rightChildren = computed(() => {
  if (!props.node?.children) return [];
  return props.node.children.filter(
    (child: ComponentNode) => child.props?.slot === "right"
  );
});

// 没有 slot 属性的子组件默认放在左边
const defaultChildren = computed(() => {
  if (!props.node?.children) return [];
  return props.node.children.filter(
    (child: ComponentNode) => !child.props?.slot
  );
});

// 拖拽进入左侧区域
function handleLeftDragEnter(event: DragEvent) {
  if (isInEditor && props.node) {
    event.preventDefault();
    updateDropTarget(props.node.id, "inside-left");
  }
}

// 拖拽进入右侧区域
function handleRightDragEnter(event: DragEvent) {
  if (isInEditor && props.node) {
    event.preventDefault();
    updateDropTarget(props.node.id, "inside-right");
  }
}

// 拖拽离开
function handleDragLeave(event: DragEvent) {
  if (isInEditor) {
    // 只有真正离开容器才清除目标
    const relatedTarget = event.relatedTarget as HTMLElement;
    if (!relatedTarget?.closest(".two-column-container")) {
      updateDropTarget(null, null);
    }
  }
}

// 放置处理
function handleDrop(event: DragEvent, slot: "left" | "right") {
  event.preventDefault();
  event.stopPropagation();

  if (isInEditor && props.node && dragState.value.dragData) {
    const { dragType, dragData } = dragState.value;

    if (dragType === "new" && "type" in dragData) {
      // 新增组件，添加 slot 属性
      const meta = dragData as any;
      const { addComponent } = useCurrentPage();
      addComponent(
        meta.type,
        { ...meta.defaultProps, slot },
        { ...meta.defaultStyle },
        props.node.id
      );
    } else if (dragType === "move" && "id" in dragData) {
      // 移动现有组件，更新 slot 属性
      const component = dragData as ComponentNode;
      const { updateComponentProps } = useCurrentPage();
      updateComponentProps(component.id, { ...component.props, slot });
    }

    // 清除拖拽状态
    updateDropTarget(null, null);
  }
}

// 允许拖拽
function handleDragOver(event: DragEvent) {
  if (isInEditor) {
    event.preventDefault();
  }
}

// 检查当前拖拽目标是否是左侧
const isLeftDropTarget = computed(() => {
  return (
    dragState.value.dropTargetId === props.node?.id &&
    dragState.value.dropPosition === "inside-left"
  );
});

// 检查当前拖拽目标是否是右侧
const isRightDropTarget = computed(() => {
  return (
    dragState.value.dropTargetId === props.node?.id &&
    dragState.value.dropPosition === "inside-right"
  );
});
</script>

<template>
  <div
    class="two-column-container"
    :style="containerStyle"
    @dragleave="handleDragLeave"
    @dragover="handleDragOver"
  >
    <!-- 左侧区域 -->
    <div
      class="column-left"
      :class="{
        'drop-target': isInEditor && isDragging && isLeftDropTarget,
        'drag-over': isInEditor && isDragging && isLeftDropTarget,
      }"
      :style="leftStyle"
      @dragenter="handleLeftDragEnter"
      @drop="handleDrop($event, 'left')"
    >
      <!-- 渲染左侧子组件（支持编辑器和前端渲染模式） -->
      <template v-if="props.node">
        <ComponentRenderer
          v-for="child in leftChildren"
          :key="child.id"
          :node="child"
          :global-style="{}"
          :preview-device="previewDevice"
          :editor-actions="editorActions"
          @component-click="$emit('component-click', $event)"
        />
        <!-- 默认子组件也放在左边 -->
        <ComponentRenderer
          v-for="child in defaultChildren"
          :key="child.id"
          :node="child"
          :global-style="{}"
          :preview-device="previewDevice"
          :editor-actions="editorActions"
          @component-click="$emit('component-click', $event)"
        />
      </template>
      <!-- 空状态占位符 -->
      <div
        v-if="
          !props.node ||
          (leftChildren.length === 0 && defaultChildren.length === 0)
        "
        class="column-placeholder column-left-placeholder"
      >
        <span
          class="i-carbon-text-align-left text-2xl text-gray-400 mb-2"
        ></span>
        <span class="text-gray-500">{{
          isInEditor?.value ? "拖拽组件到此处" : "左侧内容区域"
        }}</span>
      </div>
    </div>

    <!-- 右侧区域 -->
    <div
      class="column-right"
      :class="{
        'drop-target': isInEditor && isDragging && isRightDropTarget,
        'drag-over': isInEditor && isDragging && isRightDropTarget,
      }"
      :style="rightStyle"
      @dragenter="handleRightDragEnter"
      @drop="handleDrop($event, 'right')"
    >
      <!-- 渲染右侧子组件（支持编辑器和前端渲染模式） -->
      <template v-if="props.node">
        <ComponentRenderer
          v-for="child in rightChildren"
          :key="child.id"
          :node="child"
          :global-style="{}"
          :preview-device="previewDevice"
          :editor-actions="editorActions"
          @component-click="$emit('component-click', $event)"
        />
      </template>
      <!-- 空状态占位符 -->
      <div
        v-if="!props.node || rightChildren.length === 0"
        class="column-placeholder column-right-placeholder"
      >
        <span
          class="i-carbon-text-align-right text-2xl text-gray-400 mb-2"
        ></span>
        <span class="text-gray-500">{{
          isInEditor?.value ? "拖拽组件到此处" : "右侧内容区域"
        }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.two-column-container {
  box-sizing: border-box;
}

.column-left,
.column-right {
  box-sizing: border-box;
  position: relative;
  min-height: 100px;
  transition: all 0.2s ease;
}

/* 拖拽目标高亮 */
.column-left.drop-target,
.column-right.drop-target {
  outline: 2px dashed #3b82f6;
  outline-offset: -2px;
  background-color: rgba(59, 130, 246, 0.05);
}

.column-left.drag-over,
.column-right.drag-over {
  background-color: rgba(59, 130, 246, 0.1);
}

.column-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 24px;
  border: 2px dashed #e2e8f0;
  border-radius: 8px;
  background-color: #f8fafc;
  width: 100%;
  box-sizing: border-box;
}

.column-left-placeholder {
  border-style: dashed;
}

.column-right-placeholder {
  border-style: dashed;
}
</style>
