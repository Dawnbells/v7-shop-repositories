<script setup lang="ts">
/**
 * 模板渲染器
 * 统一处理页面/文章/产品等模板的渲染逻辑
 * 根据是否有布局自动选择 LayoutRenderer 或 PageRenderer
 * 将 fallback 内容通过 slot 外部传入
 */

import type { PageSchema, LayoutSchema, GlobalStyle, DeviceType, ComponentNode } from "~/types/builder";

const props = defineProps<{
  page: PageSchema | null | undefined;
  layout?: LayoutSchema | null;
  globalStyle?: GlobalStyle;
  siteConfig?: Record<string, any>;
  previewDevice?: DeviceType;
  isEditor?: boolean;
}>();

const emit = defineEmits<{
  "component-click": [component: ComponentNode];
}>();

// 生成全局样式 CSS 变量
const globalStyleVars = computed(() => {
  const style = props.globalStyle;
  if (!style) return {};

  return {
    "--primary-color": style.primaryColor,
    "--secondary-color": style.secondaryColor,
    "--success-color": style.successColor,
    "--warning-color": style.warningColor,
    "--error-color": style.errorColor,
    "--background-color": style.backgroundColor,
    "--surface-color": style.surfaceColor,
    "--text-color": style.textColor,
    "--text-secondary-color": style.textSecondaryColor,
    "--border-color": style.borderColor,
    "--font-family": style.fontFamily,
    "--font-size-base": style.fontSizeBase,
    "--line-height": style.lineHeight,
    "--border-radius-small": style.borderRadiusSmall,
    "--border-radius-medium": style.borderRadiusMedium,
    "--border-radius-large": style.borderRadiusLarge,
    "--spacing-unit": style.spacingUnit,
  };
});

// 是否有有效的页面配置
const hasValidPage = computed(() => {
  return !!(props.page && props.page.components && props.page.components.length > 0);
});

// 是否有布局
const hasLayout = computed(() => {
  return !!(props.layout && props.layout.components && props.layout.components.length > 0);
});

// 处理组件点击
function handleComponentClick(component: ComponentNode) {
  emit("component-click", component);
}
</script>

<template>
  <div class="template-renderer" :style="globalStyleVars">
    <!-- 有布局时使用 LayoutRenderer -->
    <LayoutRenderer
      v-if="hasLayout && hasValidPage"
      :layout="layout!"
      :page="page!"
      :global-style="globalStyle"
      :site-config="siteConfig"
      :preview-device="previewDevice || 'pc'"
      :is-editor="isEditor"
      @component-click="handleComponentClick"
    />

    <!-- 无布局时直接使用 PageRenderer -->
    <PageRenderer
      v-else-if="hasValidPage"
      :schema="page!"
      :global-style="globalStyle"
      :site-config="siteConfig"
      :preview-device="previewDevice || 'pc'"
      :is-editor="isEditor"
      @component-click="handleComponentClick"
    />

    <!-- 无有效页面配置时显示 fallback -->
    <template v-else>
      <slot name="fallback">
        <div class="template-fallback">
          <slot name="loading">加载中...</slot>
        </div>
      </slot>
    </template>
  </div>
</template>

<style scoped>
.template-renderer {
  min-height: 100vh;
  background-color: var(--background-color, #f8fafc);
  color: var(--text-color, #1e293b);
  font-family: var(
    --font-family,
    "Inter",
    -apple-system,
    BlinkMacSystemFont,
    sans-serif
  );
}

.template-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: #9ca3af;
  font-size: 14px;
}
</style>
