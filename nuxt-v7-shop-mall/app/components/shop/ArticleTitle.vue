<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ArticleTitle 组件元数据
 * 文章标题展示组件
 */
export const meta: ComponentMeta = {
  type: "article-title",
  name: "文章标题",
  icon: "i-carbon-text-font",
  category: "business",
  description: "展示文章标题，支持标签级别和对齐方式",

  propsSchema: [
    {
      key: "title",
      label: "标题(静态)",
      type: "text",
      defaultValue: "",
      description: "留空则自动绑定文章标题",
    },
    {
      key: "tag",
      label: "标签级别",
      type: "select",
      defaultValue: "h1",
      options: [
        { label: "H1", value: "h1" },
        { label: "H2", value: "h2" },
        { label: "H3", value: "h3" },
      ],
    },
    {
      key: "align",
      label: "对齐方式",
      type: "select",
      defaultValue: "left",
      options: [
        { label: "左对齐", value: "left" },
        { label: "居中", value: "center" },
        { label: "右对齐", value: "right" },
      ],
    },
  ],

  styleSchema: [
    {
      key: "fontSize",
      label: "字号",
      type: "text",
      defaultValue: "28px",
    },
    {
      key: "fontWeight",
      label: "字重",
      type: "select",
      defaultValue: "700",
      options: [
        { label: "正常", value: "400" },
        { label: "中等", value: "500" },
        { label: "半粗", value: "600" },
        { label: "粗体", value: "700" },
        { label: "特粗", value: "800" },
      ],
    },
    {
      key: "fontFamily",
      label: "字体",
      type: "text",
      defaultValue: "",
      placeholder: "如: Arial, sans-serif",
    },
    {
      key: "color",
      label: "颜色",
      type: "color",
      defaultValue: "#1f2937",
    },
  ],

  supportEvents: ["click"],

  defaultProps: {
    title: "",
    tag: "h1",
    align: "left",
  },

  defaultStyle: {
    base: {
      width: "100%",
    },
  },

  isContainer: false,
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
import { useDataContext } from "~/composables/useDataContext";

interface Props {
  title?: string;
  tag?: "h1" | "h2" | "h3";
  align?: "left" | "center" | "right";
  componentStyle?: Record<string, any>;
  previewDevice?: string;
}

const props = withDefaults(defineProps<Props>(), {
  title: "",
  tag: "h1",
  align: "left",
  componentStyle: () => ({}),
  previewDevice: "",
});

// 数据上下文
const dataContext = useDataContext();

// 获取标题 - 优先使用 props，否则从 dataContext 获取
const displayTitle = computed(() => {
  return props.title || dataContext.value.article?.title || "";
});

// 合并样式
const mergedStyle = computed(() => {
  const base = props.componentStyle?.base || {};
  const device = props.previewDevice
    ? props.componentStyle?.[props.previewDevice] || {}
    : {};
  return { ...base, ...device };
});

const titleStyle = computed(() => ({
  fontSize: mergedStyle.value.fontSize || undefined,
  fontWeight: mergedStyle.value.fontWeight || undefined,
  fontFamily: mergedStyle.value.fontFamily || undefined,
  color: mergedStyle.value.color || undefined,
  textAlign: props.align,
}));
</script>

<template>
  <component
    :is="tag"
    v-if="displayTitle"
    class="article-title"
    :style="titleStyle"
  >
    {{ displayTitle }}
  </component>
  <div v-else class="article-title-empty">暂无文章标题</div>
</template>

<style scoped>
.article-title {
  margin: 0;
  line-height: 1.4;
  word-break: break-word;
}

.article-title-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  color: #9ca3af;
  font-size: 14px;
}
</style>
