<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * HomePage 组件元数据
 * 首页组件
 */
export const meta: ComponentMeta = {
  type: "home-page",
  name: "首页",
  icon: "i-carbon-home",
  category: "layout",
  description: "首页内容区域",

  propsSchema: [],

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
      key: "minHeight",
      label: "最小高度",
      type: "size",
      defaultValue: "100vh",
      unit: "px",
    },
  ],

  supportEvents: [],

  defaultProps: {},

  defaultStyle: {
    base: {
      width: "100%",
      minHeight: "100vh",
    },
  },
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
interface Props {
  componentStyle?: Record<string, any>;
  previewDevice?: string;
}

const props = withDefaults(defineProps<Props>(), {
  componentStyle: () => ({}),
  previewDevice: "",
});

// 编辑器状态
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

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
  padding: mergedStyle.value.padding || "0px",
  backgroundColor: mergedStyle.value.backgroundColor || "transparent",
  width: mergedStyle.value.width || "100%",
  minHeight: mergedStyle.value.minHeight || "100vh",
}));
</script>

<template>
  <div class="home-page" :style="containerStyle">
    <slot></slot>
  </div>
</template>

<style scoped>
.home-page {
  width: 100%;
  min-height: 100vh;
}
</style>
