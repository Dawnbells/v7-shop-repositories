<script setup lang="ts">
/**
 * 首页
 *
 * SSR 完整渲染：
 * - 主题数据由中间件加载，通过 usePageTheme 获取
 * - 绑定解析和组件渲染在服务端完成
 * - 浏览器收到完整渲染的 HTML
 */

// 获取主题相关数据和方法
const {
  siteConfig,
  getPageSchema,
  getLayoutSchema,
  cssVariables,
  hasThemeConfig,
} = usePageTheme();

// 获取首页 schema
const pageSchema = computed(() => getPageSchema('home'))
const layoutSchema = computed(() => {
  const layoutId = pageSchema.value?.layoutId
  return layoutId ? getLayoutSchema(layoutId) : undefined
})

// 是否有主题配置
const hasTheme = computed(() => hasThemeConfig.value && !!pageSchema.value)

// 设置页面标题
useHead({
  title: computed(() => siteConfig.value?.globalConfig?.siteName || '首页'),
})

// 提供编辑器状态（非编辑器模式）
provide('isInEditor', ref(false))

// 首页无特定业务数据，提供空的 pageData
provide('pageData', ref({}))
</script>

<template>
  <div class="home-page" :style="cssVariables">
    <!-- 有主题配置时使用 PageRenderer -->
    <RendererPageRenderer
      v-if="hasTheme && pageSchema"
      :page="pageSchema"
      :layout="layoutSchema"
    />

    <!-- 无主题配置时的 fallback -->
    <div v-else class="default-home-page">
      <div class="hero-section">
        <h1 class="hero-title">欢迎访问</h1>
        <p class="hero-description">
          请在后台配置首页主题
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background-color: var(--background-color, #f8fafc);
  color: var(--text-color, #1e293b);
  font-family: var(
    --font-family,
    'Inter',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif
  );
}

.default-home-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-section {
  text-align: center;
  padding: 40px 24px;
}

.hero-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--primary-color, #3b82f6);
  margin: 0 0 16px 0;
}

.hero-description {
  font-size: 18px;
  color: #6b7280;
  margin: 0;
}
</style>
