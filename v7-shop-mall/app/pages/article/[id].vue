<script setup lang="ts">
/**
 * 文章详情页
 *
 * SSR 完整渲染：
 * - 主题数据由中间件加载，通过 usePageTheme 获取
 * - 文章数据通过 useArticlePage 获取
 * - 绑定解析和组件渲染在服务端完成
 * - 浏览器收到完整渲染的 HTML
 */

// 获取主题相关数据
const { cssVariables, getPageSchema, getLayoutSchema, siteConfig } = usePageTheme();

// 获取文章数据
const { articleInfo, useSiteTitle } = useArticlePage();

// 获取占位符替换方法
const { replacePlaceholders } = useProtocol();

// 页面配置
const pageSchema = computed(() => getPageSchema("article"));
const layoutSchema = computed(() => {
  const layoutId = pageSchema.value?.layoutId;
  return layoutId ? getLayoutSchema(layoutId) : undefined;
});
const hasTheme = computed(() => !!pageSchema.value);

// 设置浏览器标签页标题（支持占位符替换）
useSiteTitle(computed(() => {
  const title = replacePlaceholders(articleInfo.value?.title || "文章详情");
  const siteName = siteConfig.value?.globalConfig?.siteName;
  return siteName ? `${title} - ${siteName}` : title;
}));

// 提供编辑器状态（非编辑器模式）
provide('isInEditor', ref(false))

// 提供页面数据供 NodeRenderer 绑定解析使用
provide('pageData', computed(() => ({
  article: articleInfo.value,
})))
</script>

<template>
  <div class="article-page" :style="cssVariables">
    <!-- 有主题配置时使用 PageRenderer -->
    <RendererPageRenderer
      v-if="hasTheme && pageSchema"
      :page="pageSchema"
      :layout="layoutSchema"
    />

    <!-- 无主题配置时的 fallback -->
    <BlockBusinessArticleDetail v-else />
  </div>
</template>

<style scoped>
.article-page {
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
</style>
