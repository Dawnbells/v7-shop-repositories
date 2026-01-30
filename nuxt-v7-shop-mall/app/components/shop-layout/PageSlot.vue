<!--
  PageSlot 组件
  布局中的页面内容占位符

  功能说明：
  - 在布局编辑器中作为占位符显示，标记页面内容的插入位置
  - 实际渲染时，LayoutRenderer 会识别此组件并在该位置渲染页面内容
  - 每个布局中只能有一个 PageSlot

  使用场景：
  - 在布局编辑器中拖拽到布局中，定义页面内容的位置
  - 通常放在 Header 和 Footer 之间
-->

<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * PageSlot 组件元数据
 * 用于编辑器组件面板展示和属性编辑
 */
export const meta: ComponentMeta = {
  type: "page-slot",
  name: "页面插槽",
  icon: "i-carbon-document",
  category: "layout",
  description: "布局中的页面内容占位符，每个布局只能有一个",

  // 属性定义
  propsSchema: [
    {
      key: "placeholder",
      label: "占位提示",
      type: "text",
      defaultValue: "页面内容区域",
      description: "编辑器中显示的占位提示文本",
    },
  ],

  // 样式定义
  styleSchema: [
    {
      key: "minHeight",
      label: "最小高度",
      type: "size",
      defaultValue: "200px",
      unit: "px",
      group: "size",
    },
  ],

  // 支持的事件
  supportEvents: [],

  // 默认属性
  defaultProps: {
    placeholder: "页面内容区域",
  },

  // 默认样式
  defaultStyle: {
    base: {
      minHeight: "200px",
    },
  },

  // 布局专用组件，只在编辑布局时显示
  layoutOnly: true,

  // 不是容器组件
  isContainer: false,
};

// 将元数据附加到默认导出，便于插件读取
export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
/**
 * PageSlot 组件
 * 布局中的页面内容占位符
 * 实际渲染由 LayoutRenderer 处理，此组件仅作为占位显示
 */

defineProps<{
  // 占位符提示文本
  placeholder?: string;
}>();
</script>

<template>
  <div class="page-slot">
    <div class="page-slot-placeholder"> 
      <span class="i-carbon-document text-3xl text-gray-400 mb-2"></span>
      <span class="text-gray-500">{{ placeholder || '页面内容区域' }}</span>
    </div>
  </div>
</template>

<style scoped>
.page-slot {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-slot-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
  border: 2px dashed #e2e8f0;
  border-radius: 8px;
  background-color: #f8fafc;
}
</style>
