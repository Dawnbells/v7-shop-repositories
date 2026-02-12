<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ArticleContent 组件元数据
 * 文章正文富文本展示组件
 */
export const meta: ComponentMeta = {
  type: "article-content",
  name: "文章正文",
  icon: "i-carbon-document",
  category: "business",
  description: "展示文章正文富文本内容",

  propsSchema: [
    {
      key: "content",
      label: "正文内容(静态)",
      type: "richtext",
      defaultValue: "",
      description: "留空则自动绑定文章正文",
    },
  ],

  styleSchema: [
    {
      key: "fontSize",
      label: "字号",
      type: "text",
      defaultValue: "15px",
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
      defaultValue: "#374151",
    },
    {
      key: "lineHeight",
      label: "行高",
      type: "text",
      defaultValue: "1.8",
    },
  ],

  supportEvents: ["click"],

  defaultProps: {
    content: "",
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
import { useDataContext } from "~/composables";

interface Props {
  content?: string;
  componentStyle?: Record<string, any>;
  previewDevice?: string;
}

const props = withDefaults(defineProps<Props>(), {
  content: "",
  componentStyle: () => ({}),
  previewDevice: "",
});

// 数据上下文
const dataContext = useDataContext();

// 获取正文内容 - 优先使用 props，否则从 dataContext 获取
const displayContent = computed(() => {
  return props.content || dataContext.value.article?.content || "";
});

// 合并样式
const mergedStyle = computed(() => {
  const base = props.componentStyle?.base || {};
  const device = props.previewDevice
    ? props.componentStyle?.[props.previewDevice] || {}
    : {};
  return { ...base, ...device };
});

const contentStyle = computed(() => ({
  fontSize: mergedStyle.value.fontSize || undefined,
  fontFamily: mergedStyle.value.fontFamily || undefined,
  color: mergedStyle.value.color || undefined,
  lineHeight: mergedStyle.value.lineHeight || undefined,
}));
</script>

<template>
  <div v-if="displayContent" class="article-content" :style="contentStyle" v-html="displayContent"></div>
  <div v-else class="article-content-empty">暂无文章内容</div>
</template>

<style scoped>
.article-content {
  width: 100%;
  word-break: break-word;
}

.article-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 12px 0;
}

.article-content :deep(p) {
  margin: 12px 0;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4) {
  margin: 20px 0 10px 0;
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
}

.article-content :deep(li) {
  margin: 4px 0;
}

.article-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.article-content :deep(th),
.article-content :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 8px 12px;
  text-align: left;
}

.article-content :deep(th) {
  background: #f9fafb;
}

.article-content :deep(blockquote) {
  margin: 12px 0;
  padding: 12px 16px;
  border-left: 4px solid #e5e7eb;
  background: #f9fafb;
  color: #6b7280;
}

.article-content :deep(a) {
  color: var(--primary-color, #3b82f6);
  text-decoration: underline;
}

.article-content :deep(code) {
  padding: 2px 6px;
  background: #f3f4f6;
  border-radius: 4px;
  font-size: 0.9em;
}

.article-content :deep(pre) {
  margin: 12px 0;
  padding: 16px;
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 8px;
  overflow-x: auto;
}

.article-content :deep(pre code) {
  padding: 0;
  background: transparent;
  border-radius: 0;
}

.article-content-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: #9ca3af;
  font-size: 14px;
}
</style>
