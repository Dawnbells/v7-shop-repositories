import type { ArticleInfo } from "~~/server/repositories/article.repository";

/**
 * 获取文章信息
 * 自动从路由获取 articleId，调用 /api/article/info 获取文章数据
 * 占位符替换在服务端 API 层完成（API 从 pageContext 获取上下文）
 *
 * @returns 文章信息、加载状态、错误信息、刷新函数
 *
 * @example
 * ```ts
 * const { data, pending, error, refresh } = useArticleInfo();
 * ```
 */
export function useArticleInfo() {
  // 从路由获取 articleId
  const route = useRoute();
  const articleId = computed(() => {
    const id = route.params.id;
    return id ? Number(id) : undefined;
  });

  // 构建唯一的请求 key
  const key = computed(() => {
    if (!articleId.value) return null;
    return `article-info-${articleId.value}`;
  });

  // 使用 useFetch 获取文章信息
  const { data, pending, error, refresh } = useFetch<ArticleInfo>(
    "/api/article/info",
    {
      key: key.value || "article-info-placeholder",
      query: computed(() => ({
        articleId: articleId.value,
      })),
      // 只有当 articleId 有效时才发起请求
      immediate: !!articleId.value,
      watch: [articleId],
    }
  );

  return {
    /** 文章信息 */
    data,
    /** 是否正在加载 */
    pending,
    /** 错误信息 */
    error,
    /** 刷新数据 */
    refresh,
  };
}
