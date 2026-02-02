<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductDetails 组件元数据
 * 商品详情富文本展示组件
 */
export const meta: ComponentMeta = {
  type: "product-details",
  name: "商品详情",
  icon: "i-carbon-document",
  category: "business",
  description: "展示商品详情富文本内容",

  propsSchema: [
    {
      key: "content",
      label: "详情内容(静态)",
      type: "richtext",
      defaultValue: "",
      description: "留空则自动绑定产品详情",
    },
    {
      key: "title",
      label: "标题",
      type: "text",
      defaultValue: "商品详情",
    },
    {
      key: "showTitle",
      label: "显示标题",
      type: "switch",
      defaultValue: true,
    },
  ],

  styleSchema: [],

  supportEvents: ["click"],

  defaultProps: {
    content: "",
    title: "商品详情",
    showTitle: true,
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
  content?: string;
  title?: string;
  showTitle?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  content: "",
  title: "商品详情",
  showTitle: true,
});

const emit = defineEmits<{
  (e: "click", event: MouseEvent): void;
}>();

// 数据上下文
const dataContext = useDataContext();

// 获取详情内容 - 优先使用 props，否则从 dataContext 获取 merchandise
const detailContent = computed(() => {
  return props.content || dataContext.value.product?.merchandise || "";
});
</script>

<template>
  <div class="product-details">
    <h3 v-if="showTitle && title" class="details-title">{{ title }}</h3>
    <div v-if="detailContent" class="details-content" v-html="detailContent"></div>
    <div v-else class="details-empty">暂无商品详情</div>
  </div>
</template>

<style scoped>
.product-details {
  width: 100%;
}

.details-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 16px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.details-content {
  line-height: 1.8;
  color: #374151;
  font-size: 14px;
}

.details-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 12px 0;
}

.details-content :deep(p) {
  margin: 12px 0;
}

.details-content :deep(h1),
.details-content :deep(h2),
.details-content :deep(h3),
.details-content :deep(h4) {
  margin: 16px 0 8px 0;
  color: #1f2937;
}

.details-content :deep(ul),
.details-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
}

.details-content :deep(li) {
  margin: 4px 0;
}

.details-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.details-content :deep(th),
.details-content :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 8px 12px;
  text-align: left;
}

.details-content :deep(th) {
  background: #f9fafb;
}

.details-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: #9ca3af;
  font-size: 14px;
}
</style>
