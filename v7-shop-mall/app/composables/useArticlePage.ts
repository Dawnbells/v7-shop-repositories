/**
 * 文章页 Composable
 *
 * 提供文章相关的数据和方法：
 * - 文章数据（从 usePageContext 获取，由中间件注入）
 * - 页面标题设置
 *
 * SSR 时所有数据在服务端由中间件获取，客户端通过 hydration 恢复
 */

export function useArticlePage() {
  const { articleInfo } = usePageContext();

  // 设置页面标题
  function useSiteTitle(title: Ref<string> | ComputedRef<string>) {
    useHead({
      title,
    });
  }

  return {
    articleInfo,
    useSiteTitle,
  };
}
