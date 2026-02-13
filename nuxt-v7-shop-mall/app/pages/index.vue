<script setup lang="ts">
import type { DeviceType } from "~/types/schema";

/**
 * 首页 - SSR 渲染
 * 根据主题配置渲染页面
 */

definePageMeta({
  layout: "default",
});

// 主题渲染
const {
  globalStyle,
  globalStyleVars,
  hasTheme,
  siteConfig,
  variableValues,
  getPageSchema,
  getPageLayout,
  useSiteTitle,
} = useThemeRender();

// 设置浏览器标签页标题
useSiteTitle('首页');

// 首页配置
const homePageSchema = computed(() => getPageSchema("home"));

// 首页使用的布局（如果页面没有指定布局，不使用布局）
const layout = computed(() => getPageLayout("home"));

// 预览设备（前端访问时使用桌面端）
const previewDevice = ref<DeviceType>("pc");
</script>

<template>
  <div class="home-page" :style="globalStyleVars">
    <TemplateRenderer
      :page="homePageSchema"
      :layout="layout"
      :global-style="globalStyle"
      :site-config="siteConfig"
      :preview-device="previewDevice"
      :is-editor="false"
    >
      <!-- 无主题配置时的 fallback -->
      <template #fallback>
        <div class="default-content">
          <div class="hero-section">
            <h1 class="hero-title">欢迎访问商城</h1>
            <p class="hero-subtitle">发现优质好物，享受购物乐趣</p>
          </div>
        </div>
      </template>
    </TemplateRenderer>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background-color: var(--background-color, #f8fafc);
  color: var(--text-color, #1e293b);
  font-family: var(--font-family, "Inter", -apple-system, BlinkMacSystemFont, sans-serif);
}

/* 默认内容样式 */
.default-content {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.hero-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.hero-title {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 16px;
}

.hero-subtitle {
  font-size: 20px;
  opacity: 0.9;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-subtitle {
    font-size: 16px;
  }
}
</style>
