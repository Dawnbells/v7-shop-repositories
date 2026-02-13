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
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
interface Props {
  leftWidth?: string;
  gap?: string;
  verticalAlign?: "top" | "center" | "bottom";
  wrap?: boolean;
  leftMinHeight?: string;
  rightMinHeight?: string;
  componentStyle?: Record<string, any>;
  previewDevice?: string;
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
  alignItems: props.wrap ? "stretch" : props.verticalAlign === "center"
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

// 响应式 - 在小屏幕上自动换行
const isSmallScreen = computed(() => {
  const deviceWidths: Record<string, number> = {
    desktop: 1920,
    laptop: 1440,
    tablet: 1024,
    mobile: 375,
  };
  return false; // 默认不自动换行，由 wrap 属性控制
});
</script>

<template>
  <div class="two-column-container" :style="containerStyle">
    <div class="column-left" :style="leftStyle">
      <slot name="left">
        <div class="column-placeholder column-left-placeholder">
          <span class="i-carbon-text-align-left text-2xl text-gray-400 mb-2"></span>
          <span class="text-gray-500">左侧内容区域</span>
        </div>
      </slot>
    </div>
    <div class="column-right" :style="rightStyle">
      <slot name="right">
        <div class="column-placeholder column-right-placeholder">
          <span class="i-carbon-text-align-right text-2xl text-gray-400 mb-2"></span>
          <span class="text-gray-500">右侧内容区域</span>
        </div>
      </slot>
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
}

.column-left-placeholder {
  border-style: dashed;
}

.column-right-placeholder {
  border-style: dashed;
}
</style>
