<script setup lang="ts">
import { useArticlePage } from "~/composables/useArticlePage";

const route = useRoute();
const articleId = computed(() => route.params.id as string);

// 只传递需要的字段到客户端
const pageContext = usePageContext([
  "cloak.page",
  "cloak.isAdmin",
  "themeConfig",
  "siteConfig",
  "variableValues",
  "languages",
]);

// 文章页面专用 composable
const {
  articleInfo,
  articlePending,
  themeConfig,
  globalStyle,
  globalStyleVars,
  hasTheme,
  siteConfig,
  variableValues,
  pageSchema,
  layoutSchema,
  useThemeRenderer,
  device,
  useSiteTitle,
} = useArticlePage();

// 设置浏览器标签页标题
useSiteTitle(computed(() => articleInfo.value?.title || "文章详情"));

// 预览设备
const previewDevice = ref(device);

// 提供站点配置给子组件
provide('siteConfig', siteConfig);
provide('variableValues', variableValues);
</script>

<template>
  <div class="article-page" :style="globalStyleVars">
    <!-- 使用 TemplateRenderer 统一渲染 -->
    <TemplateRenderer
      :page="pageSchema"
      :layout="layoutSchema"
      :global-style="globalStyle"
      :site-config="siteConfig"
      :preview-device="previewDevice"
      :is-editor="false"
    >
      <!-- 无主题配置时的 fallback -->
      <template #fallback>
        <template v-if="articlePending">
          <div class="article-loading">加载中...</div>
        </template>
        <template v-else-if="articleInfo">
          <div class="default-article-page">
            <h1 class="article-default-title">{{ articleInfo.title }}</h1>
            <p
              v-if="articleInfo.description"
              class="article-default-description"
            >
              {{ articleInfo.description }}
            </p>
            <div
              class="article-default-content"
              v-html="articleInfo.content"
            ></div>
          </div>
        </template>
        <template v-else>
          <div class="article-not-found">
            <p>文章不存在或已被删除</p>
          </div>
        </template>
      </template>
    </TemplateRenderer>
  </div>
</template>

<style scoped>
.article-page {
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

.article-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: #9ca3af;
  font-size: 14px;
}

.article-not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: #9ca3af;
  font-size: 16px;
}
</style>
