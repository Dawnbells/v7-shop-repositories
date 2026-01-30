<script setup lang="ts">
/**
 * 布局渲染器
 * 渲染布局，并在 page-slot 位置插入页面内容
 */

import type {
  LayoutSchema,
  PageSchema,
  ComponentNode,
  DeviceType,
  GlobalStyle,
} from "~/types/builder";

const props = defineProps<{
  layout: LayoutSchema;
  page: PageSchema;
  globalStyle?: GlobalStyle;
  previewDevice: DeviceType;
  isEditor?: boolean;
}>();

const emit = defineEmits<{
  "component-click": [component: ComponentNode];
}>();

// 生成全局样式 CSS 变量
const globalStyleVars = computed(() => {
  if (!props.globalStyle) return {};

  return {
    "--primary-color": props.globalStyle.primaryColor,
    "--secondary-color": props.globalStyle.secondaryColor,
    "--success-color": props.globalStyle.successColor,
    "--warning-color": props.globalStyle.warningColor,
    "--error-color": props.globalStyle.errorColor,
    "--background-color": props.globalStyle.backgroundColor,
    "--surface-color": props.globalStyle.surfaceColor,
    "--text-color": props.globalStyle.textColor,
    "--text-secondary-color": props.globalStyle.textSecondaryColor,
    "--border-color": props.globalStyle.borderColor,
    "--font-family": props.globalStyle.fontFamily,
    "--font-size-base": props.globalStyle.fontSizeBase,
    "--line-height": props.globalStyle.lineHeight,
    "--border-radius-small": props.globalStyle.borderRadiusSmall,
    "--border-radius-medium": props.globalStyle.borderRadiusMedium,
    "--border-radius-large": props.globalStyle.borderRadiusLarge,
    "--spacing-unit": props.globalStyle.spacingUnit,
  };
});

// 处理组件点击
function handleComponentClick(component: ComponentNode) {
  emit("component-click", component);
}

// 检查组件是否是 page-slot
function isPageSlot(node: ComponentNode): boolean {
  return node.type === "page-slot";
}
</script>

<template>
  <div
    class="layout-renderer"
    :style="globalStyleVars"
    :data-layout-id="layout.id"
    :data-device="previewDevice"
  >
    <!-- 遍历布局组件 -->
    <template v-for="component in layout.components" :key="component.id">
      <!-- page-slot 组件：渲染页面内容 -->
      <div v-if="isPageSlot(component)" class="page-slot-wrapper">
        <ComponentRenderer
          v-for="pageComponent in page.components"
          :key="pageComponent.id"
          :node="pageComponent"
          :global-style="globalStyle"
          :preview-device="previewDevice"
          :is-editor="isEditor"
          @component-click="handleComponentClick"
        />
      </div>

      <!-- 普通布局组件 -->
      <ComponentRenderer
        v-else
        :node="component"
        :global-style="globalStyle"
        :preview-device="previewDevice"
        :is-editor="isEditor"
        @component-click="handleComponentClick"
      />
    </template>

    <!-- 如果布局没有 page-slot，在末尾渲染页面内容 -->
    <template v-if="!layout.components.some(isPageSlot)">
      <div class="page-slot-wrapper page-slot-fallback">
        <ComponentRenderer
          v-for="pageComponent in page.components"
          :key="pageComponent.id"
          :node="pageComponent"
          :global-style="globalStyle"
          :preview-device="previewDevice"
          :is-editor="isEditor"
          @component-click="handleComponentClick"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.layout-renderer {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--background-color, #ffffff);
  color: var(--text-color, #1e293b);
  font-family: var(--font-family, sans-serif);
  font-size: var(--font-size-base, 14px);
  line-height: var(--line-height, 1.5);
}

.page-slot-wrapper {
  flex: 1 0 auto;
  min-height: 100px;
}

/* 编辑器模式下显示 page-slot 边界 */
.layout-renderer[data-is-editor="true"] .page-slot-wrapper {
  outline: 2px dashed var(--primary-color, #3b82f6);
  outline-offset: -2px;
}

/* 确保 footer 组件不会被压缩，保持在底部 */
.layout-renderer > .component-wrapper[data-component-type="footer-bar"] {
  flex-shrink: 0;
  margin-top: auto;
}

/* header 组件也不应该被压缩 */
.layout-renderer > .component-wrapper[data-component-type="header-bar"] {
  flex-shrink: 0;
}
</style>
