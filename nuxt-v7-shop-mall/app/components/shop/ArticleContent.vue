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
import type { ArticleInfo } from "~~/server/repositories/article.repository";

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

// 从父组件注入文章数据
const articleInfo = inject<Ref<ArticleInfo | null>>("articleInfo", ref(null));

// 获取正文内容 - 优先使用 props，否则从 inject 获取
const displayContent = computed(() => {
  return props.content || articleInfo.value?.content || "";
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
  <div
    v-if="displayContent"
    class="article-content"
    :style="contentStyle"
    v-html="displayContent"
  ></div>
  <div v-else class="article-content-empty">暂无文章内容</div>
</template>

<style scoped>
.article-content {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  word-break: break-word;
  font-size: 1rem;
  color: #333;
  line-height: 1.75;
}

.article-content :deep(img) {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 1.5em 0;
}

.article-content :deep(p) {
  margin: 1em 0;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4) {
  margin: 1.5em 0 0.75em 0;
  color: #000;
  font-weight: 600;
  line-height: 1.3;
}

.article-content :deep(h2) {
  font-size: 1.375rem;
}

.article-content :deep(h3) {
  font-size: 1.125rem;
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin: 1em 0;
  padding-left: 1.25em;
}

.article-content :deep(li) {
  margin: 0.375em 0;
}

.article-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1.5em 0;
}

.article-content :deep(th),
.article-content :deep(td) {
  border: 1px solid #eee;
  padding: 10px 12px;
  text-align: left;
}

.article-content :deep(th) {
  background: #fafafa;
  font-weight: 500;
}

.article-content :deep(blockquote) {
  margin: 1.5em 0;
  padding: 1em 1.25em;
  border-left: 3px solid #ddd;
  color: #666;
  font-size: 0.9375rem;
}

.article-content :deep(a) {
  color: #000;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.article-content :deep(code) {
  font-family: monospace;
  font-size: 0.875em;
  background: #f5f5f5;
  padding: 0.15em 0.4em;
  border-radius: 3px;
}

.article-content :deep(pre) {
  margin: 1.5em 0;
  padding: 1em;
  background: #f5f5f5;
  border-radius: 6px;
  overflow-x: auto;
}

.article-content :deep(pre code) {
  padding: 0;
  background: transparent;
  color: #333;
}

.article-content-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 150px;
  color: #ccc;
  font-size: 0.875rem;
  border: 1px dashed #e5e5e5;
  border-radius: 4px;
}
</style>
