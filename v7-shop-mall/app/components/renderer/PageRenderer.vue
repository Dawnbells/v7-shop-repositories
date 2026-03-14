<script setup lang="ts">
/**
 * PageRenderer - 页面渲染器
 * 
 * 渲染完整页面，支持布局嵌套：
 * - 有布局时：渲染布局节点，页面内容通过 page-slot 插入
 * - 无布局时：直接渲染页面内容
 * 
 * SSR 时在服务端执行，生成完整 HTML
 */

import type { PageData, LayoutData, ComponentNode } from '~/types/component-meta'

interface Props {
  page: PageData
  layout?: LayoutData | null
  device?: 'desktop' | 'tablet' | 'mobile'
}

const props = withDefaults(defineProps<Props>(), {
  layout: null,
  device: 'desktop',
})

// 是否使用布局
const hasLayout = computed(() => !!props.layout)

// 页面内容节点
const pageNodes = computed<ComponentNode[]>(() => {
  return props.page.root?.children || []
})

// 布局节点
const layoutNodes = computed<ComponentNode[]>(() => {
  return props.layout?.root?.children || []
})

// 提供页面内容给 NodeRenderer（用于 page-slot 渲染）
provide('pageContent', pageNodes)
</script>

<template>
  <div class="page-renderer">
    <!-- 有布局时，渲染布局节点（NodeRenderer 会处理 page-slot 替换） -->
    <template v-if="hasLayout && layoutNodes.length">
      <RendererNodeRenderer
        v-for="node in layoutNodes"
        :key="node.id"
        :node="node"
        :device="device"
      />
    </template>

    <!-- 无布局或布局为空时，直接渲染页面内容 -->
    <template v-else>
      <RendererNodeRenderer
        v-for="node in pageNodes"
        :key="node.id"
        :node="node"
        :device="device"
      />
    </template>
  </div>
</template>

<style scoped>
.page-renderer {
  width: 100%;
  min-height: 100vh;
  container-type: inline-size;
  container-name: page;
}
</style>
