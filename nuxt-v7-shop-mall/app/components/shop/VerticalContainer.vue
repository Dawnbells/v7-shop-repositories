<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * VerticalContainer 组件元数据
 * 纵向布局容器组件
 */
export const meta: ComponentMeta = {
  type: "vertical-container",
  name: "纵向容器",
  icon: "i-carbon-rows",
  category: "layout",
  description: "纵向布局容器，支持无限添加子组件纵向排列",

  propsSchema: [
    {
      key: "gap",
      label: "间距",
      type: "size",
      defaultValue: "16px",
      unit: "px",
      description: "子组件之间的间距",
    },
    {
      key: "align",
      label: "水平对齐",
      type: "select",
      defaultValue: "left",
      options: [
        { label: "左对齐", value: "left" },
        { label: "居中对齐", value: "center" },
        { label: "右对齐", value: "right" },
      ],
      description: "子组件的水平对齐方式",
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
    gap: "16px",
    align: "left",
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

  // 不需要预定义插槽，子组件可以自由添加
  slots: [],
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
import type { ComponentNode } from "~/types/builder";

interface Props {
  gap?: string;
  align?: "left" | "center" | "right";
  componentStyle?: Record<string, any>;
  previewDevice?: string;
  // 注入的子组件节点（编辑器模式）
  node?: ComponentNode;
}

const props = withDefaults(defineProps<Props>(), {
  gap: "16px",
  align: "left",
  componentStyle: () => ({}),
  previewDevice: "",
});

// 事件定义
const emit = defineEmits<{
  "component-click": [component: ComponentNode];
}>();

// 编辑器状态
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 提供给子组件的编辑器状态
provide("isInEditor", isInEditor);

// 提供父组件信息给子组件
interface ParentComponentInfo {
  id: string;
  type: string;
  name: string;
}
const parentComponentInfo = computed<ParentComponentInfo | null>(() => {
  if (props.node) {
    return {
      id: props.node.id,
      type: props.node.type,
      name: "纵向布局",
    };
  }
  return null;
});
provide("parentComponent", parentComponentInfo);

// 编辑器操作（用于悬浮菜单）
const editorActions = inject<any>("editorActions", null);

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
  flexDirection: "column",
  gap: props.gap,
  alignItems:
    props.align === "center"
      ? "center"
      : props.align === "right"
      ? "flex-end"
      : "flex-start",
  padding: mergedStyle.value.padding || "0px",
  backgroundColor: mergedStyle.value.backgroundColor || "transparent",
  borderRadius: mergedStyle.value.borderRadius || "0px",
  width: "100%",
}));

// 获取所有子组件（不需要按 slot 过滤）
const children = computed(() => {
  if (!props.node?.children) return [];
  return props.node.children;
});

// 拖拽进入容器
function handleDragEnter(event: DragEvent) {
  if (isInEditor && props.node) {
    event.preventDefault();
    updateDropTarget(props.node.id, "inside");
  }
}

// 拖拽离开
function handleDragLeave(event: DragEvent) {
  if (isInEditor) {
    const relatedTarget = event.relatedTarget as HTMLElement;
    if (!relatedTarget?.closest(".vertical-container")) {
      updateDropTarget(null, null);
    }
  }
}

// 放置处理
function handleDrop(event: DragEvent) {
  event.preventDefault();
  event.stopPropagation();

  if (isInEditor && props.node && dragState.value.dragData) {
    const { dragType, dragData } = dragState.value;

    if (dragType === "new" && "type" in dragData) {
      // 新增组件
      const meta = dragData as any;
      const { addComponent } = useCurrentPage();
      if (addComponent) {
        addComponent(
          meta.type,
          meta.defaultProps || {},
          meta.defaultStyle || {},
          props.node.id
        );
      }
    } else if (dragType === "move" && "id" in dragData) {
      // 移动现有组件
      const { moveComponent } = useCurrentPage();
      if (moveComponent) {
        moveComponent(dragData.id, props.node.id);
      }
    }

    // 清除拖拽状态
    updateDropTarget(null, null);
  }
}

// 是否是拖拽目标
const isDropTarget = computed(() => {
  return (
    isDragging.value &&
    dragState.value.targetId === props.node?.id
  );
});

// 拖拽在哪个位置
const dropPosition = computed(() => {
  if (isDropTarget.value) {
    return dragState.value.position;
  }
  return null;
});
</script>

<template>
  <div
    class="vertical-container"
    :class="{
      'drop-target': isDropTarget,
      'drag-over': dropPosition === 'inside',
    }"
    :style="containerStyle"
    @dragenter="handleDragEnter"
    @dragleave="handleDragLeave"
    @dragover.prevent
    @drop="handleDrop"
  >
    <!-- 子组件渲染区域（编辑器模式） -->
    <template v-if="props.node && children.length > 0">
      <ComponentRenderer
        v-for="child in children"
        :key="child.id"
        :node="child"
        :global-style="{}"
        :preview-device="previewDevice"
        :editor-actions="editorActions"
        @component-click="$emit('component-click', $event)"
      />
    </template>

    <!-- 空状态提示 -->
    <div
      v-if="!props.node || children.length === 0"
      class="empty-placeholder"
      :style="{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px 20px',
        width: '100%',
      }"
    >
      <span
        class="i-carbon-rows text-2xl text-gray-400 mb-2"
      ></span>
      <span class="text-gray-500">{{
        isInEditor ? "拖拽组件到此处" : "内容区域"
      }}</span>
    </div>
  </div>

  <!-- 前端渲染模式（非编辑器）使用 slot -->
  <slot v-else>
    <div :style="containerStyle">
      <slot name="default"></slot>
    </div>
  </slot>
</template>

<style scoped>
.vertical-container {
  position: relative;
  min-height: 50px;
}

.vertical-container.drop-target {
  outline: 2px dashed #3b82f6;
  outline-offset: 2px;
}

.vertical-container.drag-over {
  background-color: rgba(59, 130, 246, 0.1);
}

.empty-placeholder {
  border: 2px dashed #e5e7eb;
  border-radius: 8px;
}
</style>
