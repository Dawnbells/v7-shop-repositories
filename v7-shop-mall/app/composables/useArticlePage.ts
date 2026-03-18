/**
 * 文章页 Composable
 *
 * 提供文章相关的数据和方法：
 * - 文章数据（通过 useAsyncData 从 API 获取）
 * - 页面标题设置
 *
 * SSR 时所有数据在服务端获取，客户端仅做 hydration
 */

export interface ArticleInfo {
  id: number;
  title: string;
  name: string;
  description: string | null;
  content: string | null;
  author: string | null;
  publishedAt: string | null;
  coverImage: string | null;
}

export function useArticlePage() {
  const route = useRoute();
  const articleId = computed(() => route.params.id as string);

  // 获取文章数据（SSR 时在服务端执行，直接调用 handler，无 HTTP）
  const {
    data: articleData,
    status,
    error,
  } = useAsyncData(
    `article-${articleId.value}`,
    async () => {
      const response = await $fetch<{ success: boolean; data: ArticleInfo }>(
        "/api/article/info",
        {
          query: { id: articleId.value },
        }
      );
      return response.data;
    },
    {
      watch: [articleId],
    }
  );

  // 文章信息
  const articleInfo = computed(() => articleData.value);

  // 是否正在加载
  const isLoading = computed(() => status.value === "pending");

  // 设置页面标题
  function useSiteTitle(title: Ref<string> | ComputedRef<string>) {
    useHead({
      title,
    });
  }

  return {
    // 路由参数
    articleId,

    // 文章数据
    articleInfo,
    isLoading,
    error,

    // 工具方法
    useSiteTitle,
  };
}
