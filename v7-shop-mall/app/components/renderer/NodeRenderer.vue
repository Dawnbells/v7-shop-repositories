<script setup lang="ts">
/**
 * NodeRenderer - 前台节点渲染器
 *
 * 轻量级递归组件，用于渲染组件节点树
 * - 无编辑/拖拽逻辑
 * - 解析数据绑定
 * - 处理响应式样式
 * - 递归渲染子节点
 *
 * SSR 时在服务端执行，生成完整 HTML
 */

import type { ComponentNode } from "~/types/component-meta";
import type { BindingContext } from "~/composables/useBindingResolver";
import { normalizeStyle } from "~/utils/style-normalizer";

interface Props {
  node: ComponentNode;
  device?: "desktop" | "tablet" | "mobile";
}

const props = withDefaults(defineProps<Props>(), {
  device: "desktop",
});

// 获取 Block 组件注册表
const { getBlock, getBlockMeta } = useBlockRegistry();

// 获取绑定解析器
const { resolveNodeBindings, resolveNodeStyleBindings } = useBindingResolver();

// 获取主题数据
const { siteConfig, variableValues, globalStyle } = usePageTheme();

// 注入页面数据（由页面组件 provide）
const pageData = inject<Ref<{ product?: any; article?: any }>>(
  "pageData",
  ref({}),
);

// 注入页面内容（用于 page-slot 渲染）
const pageContent = inject<Ref<ComponentNode[]>>("pageContent", ref([]));

// 获取组件
const blockComponent = computed(() => {
  return getBlock(props.node.type);
});

// 获取组件元数据
const blockMeta = computed(() => {
  return getBlockMeta(props.node.type);
});

// 是否为容器组件
const isContainer = computed(() => {
  return blockMeta.value?.isContainer ?? false;
});

// 是否为页面插槽组件
const isPageSlot = computed(() => {
  return props.node.type === "page-slot" || props.node.type === "pageslot";
});

// 是否隐藏
const isHidden = computed(() => {
  return props.node.hidden ?? false;
});

// 构建绑定解析上下文
const bindingContext = computed<BindingContext>(() => ({
  custom: variableValues.value || {},
  siteConfig: siteConfig.value?.globalConfig || siteConfig.value || {},
  globalStyle: globalStyle.value || {},
  product: pageData.value?.product,
  article: pageData.value?.article,
}));

// 解析后的属性（静态值 + 绑定值）
const resolvedProps = computed(() => {
  const baseProps = { ...props.node.props };
  const boundProps = resolveNodeBindings(props.node, bindingContext.value);
  return { ...baseProps, ...boundProps };
});

// 解析后的样式（base + 设备特定 + 绑定值）
const resolvedStyle = computed(() => {
  const style = props.node.style || {};

  // 获取组件默认样式作为回退
  const defaultBaseStyle = blockMeta.value?.defaultStyle?.base || {};

  // 合并响应式样式：默认样式 + base + device specific（后者优先）
  const baseStyle = { ...defaultBaseStyle, ...(style.base || {}) };
  const deviceStyle = style[props.device] || {};
  const mergedStyle = { ...baseStyle, ...deviceStyle };

  // 应用样式绑定
  const boundStyle = resolveNodeStyleBindings(props.node, bindingContext.value);
  const finalStyle = { ...mergedStyle, ...boundStyle };

  // 规范化样式（为纯数字值添加 px 单位，处理 CSS 变量等）
  return normalizeStyle(finalStyle);
});
</script>

<template>
  <!-- 隐藏节点不渲染 -->
  <template v-if="!isHidden">
    <!-- 页面插槽：渲染注入的页面内容 -->
    <template v-if="isPageSlot">
      <div class="page-slot-content" :style="resolvedStyle">
        <RendererNodeRenderer
          v-for="contentNode in pageContent"
          :key="contentNode.id"
          :node="contentNode"
          :device="device"
        />
      </div>
    </template>

    <!-- 普通组件渲染 -->
    <component
      v-else-if="blockComponent"
      :is="blockComponent"
      v-bind="resolvedProps"
      :style="resolvedStyle"
    >
      <!-- 容器组件递归渲染子节点 -->
      <template v-if="isContainer && node.children?.length">
        <RendererNodeRenderer
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :device="device"
        />
      </template>
    </component>

    <!-- 组件未找到的回退显示 -->
    <div v-else class="node-fallback">
      <span>组件未找到: {{ node.type }}</span>
    </div>
  </template>
</template>

<style scoped>
.page-slot-content {
  width: 100%;
}

.node-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  font-size: 14px;
  color: #94a3b8;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 4px;
}
</style>
