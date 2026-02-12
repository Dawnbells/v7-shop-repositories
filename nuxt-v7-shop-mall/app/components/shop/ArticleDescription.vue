<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ArticleDescription 组件元数据
 * 文章描述展示组件
 */
export const meta: ComponentMeta = {
  type: "article-description",
  name: "文章描述",
  icon: "i-carbon-text-align-left",
  category: "business",
  description: "展示文章描述/摘要信息",

  propsSchema: [
    {
      key: "description",
      label: "描述(静态)",
      type: "textarea",
      defaultValue: "",
      description: "留空则自动绑定文章描述",
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
      defaultValue: "#6b7280",
    },
    {
      key: "lineHeight",
      label: "行高",
      type: "text",
      defaultValue: "1.7",
    },
  ],

  supportEvents: ["click"],

  defaultProps: {
    description: "",
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
  description?: string;
  componentStyle?: Record<string, any>;
  previewDevice?: string;
}

const props = withDefaults(defineProps<Props>(), {
  description: "",
  componentStyle: () => ({}),
  previewDevice: "",
});

// 数据上下文
const dataContext = useDataContext();

// 获取描述 - 优先使用 props，否则从 dataContext 获取
const displayDescription = computed(() => {
  return props.description || dataContext.value.article?.description || "";
});

// 合并样式
const mergedStyle = computed(() => {
  const base = props.componentStyle?.base || {};
  const device = props.previewDevice
    ? props.componentStyle?.[props.previewDevice] || {}
    : {};
  return { ...base, ...device };
});

const descStyle = computed(() => ({
  fontSize: mergedStyle.value.fontSize || undefined,
  fontFamily: mergedStyle.value.fontFamily || undefined,
  color: mergedStyle.value.color || undefined,
  lineHeight: mergedStyle.value.lineHeight || undefined,
}));
</script>

<template>
  <p v-if="displayDescription" class="article-description" :style="descStyle">
    {{ displayDescription }}
  </p>
  <div v-else class="article-description-empty">暂无文章描述</div>
</template>

<style scoped>
.article-description {
  margin: 0;
  word-break: break-word;
}

.article-description-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  color: #9ca3af;
  font-size: 14px;
}
</style>
