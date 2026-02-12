import type { ArticleInfo } from "~~/server/repositories/article.repository";

/**
 * 文章页面专用 composable
 * 包含文章数据获取、主题渲染、页面配置等功能
 *
 * @example
 * ```ts
 * const {
 *   articleInfo, articlePending,
 *   themeConfig, globalStyleVars,
 *   pageSchema, useThemeRenderer,
 * } = useArticlePage();
 * ```
 */
export function useArticlePage() {
  // ==================== 文章数据获取 ====================

  // 从路由获取 articleId
  const route = useRoute();
  const articleId = computed(() => {
    const id = route.params.id;
    return id ? Number(id) : undefined;
  });

  // 构建唯一的请求 key
  const articleKey = computed(() => {
    if (!articleId.value) return null;
    return `article-info-${articleId.value}`;
  });

  // 使用 useFetch 获取文章信息
  const { data: articleInfo, pending: articlePending, error: articleError, refresh: articleRefresh } = useFetch<ArticleInfo>(
    "/api/article/info",
    {
      key: articleKey.value || "article-info-placeholder",
      query: computed(() => ({
        articleId: articleId.value,
      })),
      // 只有当 articleId 有效时才发起请求
      immediate: !!articleId.value,
      watch: [articleId],
    }
  );

  // ==================== 主题渲染 ====================

  const {
    themeConfig,
    globalStyle,
    globalStyleVars,
    hasTheme,
    getPageSchema,
    getPageLayout,
    siteConfig,
    variableValues,
    useSiteTitle,
  } = useThemeRender();

  // ==================== 设备检测 ====================

  const { device } = useDeviceDetect();

  // ==================== 提供数据给子组件 ====================

  provide('articleInfo', articleInfo);
  provide('articlePending', articlePending);

  // ==================== 返回值 ====================

  return {
    // 文章信息
    articleInfo,
    articlePending: readonly(articlePending),
    articleError: readonly(articleError),
    articleRefresh,

    // 主题配置
    themeConfig,
    globalStyle,
    globalStyleVars,
    hasTheme,
    siteConfig,
    variableValues,

    // 页面配置
    pageSchema: computed(() => getPageSchema("article")),
    layoutSchema: computed(() => getPageLayout("article")),
    useThemeRenderer: computed(() => hasTheme.value && !!getPageSchema("article")),

    // 工具
    device,
    useSiteTitle,
  };
}
