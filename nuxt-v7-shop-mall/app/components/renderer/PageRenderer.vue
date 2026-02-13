<script setup lang="ts">
/**
 * 页面渲染器
 * 递归渲染组件树
 */

import type {
  PageSchema,
  LayoutSchema,
  ComponentNode,
  DeviceType,
  GlobalStyle,
} from "~/types/builder";

const props = defineProps<{
  schema: PageSchema | LayoutSchema;
  globalStyle?: GlobalStyle;
  siteConfig?: Record<string, any>;
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
</script>

<template>
  <div
    class="page-renderer"
    :style="globalStyleVars"
    :data-page-id="schema.id"
    :data-device="previewDevice"
  >
    <ComponentRenderer
      v-for="component in schema.components"
      :key="component.id"
      :node="component"
      :global-style="globalStyle"
      :preview-device="previewDevice"
      :is-editor="isEditor"
      @component-click="handleComponentClick"
    />
  </div>
</template>

<style scoped>
.page-renderer {
  min-height: 100%;
  background-color: var(--background-color, #ffffff);
  color: var(--text-color, #1e293b);
  font-family: var(--font-family, sans-serif);
  font-size: var(--font-size-base, 14px);
  line-height: var(--line-height, 1.5);
}
</style>
