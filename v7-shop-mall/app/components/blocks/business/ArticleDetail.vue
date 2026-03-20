<script setup lang="ts">
/**
 * ArticleDetail Block - 文章详情组件
 * 展示文章的标题、更新时间、描述和正文内容
 * 数据通过 useArticlePage composable 从 PageContext 获取
 * 支持 {{key}} 格式的占位符替换
 */

const { articleInfo } = useArticlePage();
const { replacePlaceholders } = useProtocol();

const hasContent = computed(() => !!articleInfo.value);

const title = computed(() =>
  replacePlaceholders(articleInfo.value?.title || "")
);

const description = computed(() =>
  replacePlaceholders(articleInfo.value?.description || "")
);

const content = computed(() =>
  replacePlaceholders(articleInfo.value?.content || "")
);

const formattedDate = computed(() => {
  if (!articleInfo.value?.updateTime) return null;
  try {
    const date = new Date(articleInfo.value.updateTime);
    return date.toLocaleDateString("zh-CN", {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  } catch {
    return articleInfo.value.updateTime;
  }
});
</script>

<template>
  <article v-if="hasContent" class="block-article-detail">
    <!-- 标题 -->
    <h1 class="article-title">{{ title }}</h1>

    <!-- 更新时间 -->
    <div v-if="formattedDate" class="article-meta">
      <time class="article-date">{{ formattedDate }}</time>
    </div>

    <!-- 描述 -->
    <p v-if="description" class="article-description">
      {{ description }}
    </p>

    <!-- 正文 -->
    <div v-if="content" class="article-content" v-html="content" />
  </article>

  <!-- 空状态 -->
  <div v-else class="article-empty">
    <span class="empty-text">文章不存在或已被删除</span>
  </div>
</template>

<style scoped>
.block-article-detail {
  width: 100%;
  max-width: var(--article-max-width, 800px);
  margin: 0 auto;
  padding: var(--article-padding, 40px 24px);
}

.article-title {
  font-size: var(--article-title-size, 28px);
  font-weight: var(--article-title-weight, 700);
  color: var(--article-title-color, #1f2937);
  margin: 0 0 var(--article-title-margin, 16px) 0;
  line-height: 1.4;
}

.article-meta {
  font-size: var(--article-meta-size, 14px);
  color: var(--article-meta-color, #6b7280);
  margin-bottom: var(--article-meta-margin, 20px);
}

.article-date {
  color: var(--article-meta-color, #6b7280);
}

.article-description {
  font-size: var(--article-desc-size, 16px);
  color: var(--article-desc-color, #4b5563);
  line-height: 1.7;
  margin: 0 0 var(--article-desc-margin, 24px) 0;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--article-border-color, #e5e7eb);
}

.article-content {
  font-size: var(--article-content-size, 16px);
  color: var(--article-content-color, #374151);
  line-height: 1.8;
}

.article-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 16px 0;
}

.article-content :deep(p) {
  margin: 16px 0;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4),
.article-content :deep(h5),
.article-content :deep(h6) {
  margin: 24px 0 12px 0;
  color: var(--article-heading-color, #1f2937);
  font-weight: 600;
  line-height: 1.4;
}

.article-content :deep(h2) {
  font-size: 1.5em;
}

.article-content :deep(h3) {
  font-size: 1.25em;
}

.article-content :deep(h4) {
  font-size: 1.1em;
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin: 16px 0;
  padding-left: 24px;
}

.article-content :deep(li) {
  margin: 8px 0;
}

.article-content :deep(blockquote) {
  margin: 16px 0;
  padding: 12px 20px;
  border-left: 4px solid var(--article-quote-border, #e5e7eb);
  background-color: var(--article-quote-bg, #f9fafb);
  color: var(--article-quote-color, #4b5563);
  font-style: italic;
}

.article-content :deep(pre) {
  margin: 16px 0;
  padding: 16px;
  background-color: var(--article-code-bg, #1f2937);
  color: var(--article-code-color, #e5e7eb);
  border-radius: 8px;
  overflow-x: auto;
  font-family: "Fira Code", "Monaco", "Consolas", monospace;
  font-size: 14px;
  line-height: 1.6;
}

.article-content :deep(code) {
  font-family: "Fira Code", "Monaco", "Consolas", monospace;
  font-size: 0.9em;
}

.article-content :deep(:not(pre) > code) {
  padding: 2px 6px;
  background-color: var(--article-inline-code-bg, #f3f4f6);
  color: var(--article-inline-code-color, #e11d48);
  border-radius: 4px;
}

.article-content :deep(a) {
  color: var(--article-link-color, #2563eb);
  text-decoration: none;
}

.article-content :deep(a:hover) {
  text-decoration: underline;
}

.article-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}

.article-content :deep(th),
.article-content :deep(td) {
  border: 1px solid var(--article-table-border, #e5e7eb);
  padding: 10px 14px;
  text-align: left;
}

.article-content :deep(th) {
  background-color: var(--article-table-header-bg, #f9fafb);
  font-weight: 600;
}

.article-content :deep(hr) {
  margin: 32px 0;
  border: none;
  border-top: 1px solid var(--article-border-color, #e5e7eb);
}

.article-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  padding: 40px 24px;
  text-align: center;
}

.empty-text {
  color: var(--text-color-secondary, #9ca3af);
  font-size: 16px;
}
</style>
