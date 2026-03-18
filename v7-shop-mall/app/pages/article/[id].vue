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
const { cssVariables, getPageSchema, getLayoutSchema } = usePageTheme();

// 获取文章数据
const { articleInfo, isLoading, error, useSiteTitle } = useArticlePage();

// 页面配置
const pageSchema = computed(() => getPageSchema("article"));
const layoutSchema = computed(() => {
  const layoutId = pageSchema.value?.layoutId;
  return layoutId ? getLayoutSchema(layoutId) : undefined;
});
const hasTheme = computed(() => !!pageSchema.value);

// 设置浏览器标签页标题
useSiteTitle(computed(() => articleInfo.value?.title || "文章详情"));

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
    <template v-else>
      <!-- 加载中 -->
      <div v-if="isLoading" class="article-loading">
        加载中...
      </div>

      <!-- 加载错误 -->
      <div v-else-if="error" class="article-error">
        <p>加载文章失败</p>
        <p class="error-detail">{{ error.message }}</p>
      </div>

      <!-- 文章内容 -->
      <div v-else-if="articleInfo" class="default-article-page">
        <h1 class="article-default-title">{{ articleInfo.title }}</h1>
        <p v-if="articleInfo.description" class="article-default-description">
          {{ articleInfo.description }}
        </p>
        <div class="article-default-content" v-html="articleInfo.content"></div>
      </div>

      <!-- 文章不存在 -->
      <div v-else class="article-not-found">
        <p>文章不存在或已被删除</p>
      </div>
    </template>
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

.default-article-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 40px 24px;
}

.article-default-title {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 16px 0;
  line-height: 1.4;
}

.article-default-description {
  font-size: 15px;
  color: #6b7280;
  line-height: 1.7;
  margin: 0 0 32px 0;
  padding-bottom: 24px;
  border-bottom: 1px solid #e5e7eb;
}

.article-default-content {
  font-size: 15px;
  color: #374151;
  line-height: 1.8;
}

.article-default-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 12px 0;
}

.article-default-content :deep(p) {
  margin: 12px 0;
}

.article-default-content :deep(h1),
.article-default-content :deep(h2),
.article-default-content :deep(h3),
.article-default-content :deep(h4) {
  margin: 20px 0 10px 0;
  color: #1f2937;
}

.article-default-content :deep(ul),
.article-default-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
}

.article-default-content :deep(li) {
  margin: 4px 0;
}

.article-default-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.article-default-content :deep(th),
.article-default-content :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 8px 12px;
  text-align: left;
}

.article-default-content :deep(th) {
  background: #f9fafb;
}

.article-loading,
.article-error,
.article-not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: #9ca3af;
  font-size: 16px;
  text-align: center;
  padding: 24px;
}

.article-error {
  color: #ef4444;
}

.error-detail {
  font-size: 14px;
  color: #9ca3af;
  margin-top: 8px;
}
</style>
