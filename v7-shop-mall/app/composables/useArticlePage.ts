/**
 * 文章页 Composable
 * 
 * 整合文章页所需的所有数据：
 * - 主题数据（从 usePageContext 获取）
 * - 文章数据（通过 useAsyncData 获取）
 * - 页面和布局 schema
 * 
 * SSR 时所有数据在服务端获取，客户端仅做 hydration
 */

interface ArticleInfo {
  id: number
  title: string
  name: string
  description: string | null
  content: string | null
  author: string | null
  publishedAt: string | null
  coverImage: string | null
}

export function useArticlePage() {
  const route = useRoute()
  const articleId = computed(() => route.params.id as string)

  // 初始化主题数据（SSR 时从 event.context 读取）
  const { themeConfig, siteConfig, variableValues } = usePageContext()

  // 获取主题相关的计算属性和方法
  const { getPageSchema, getLayoutSchema, globalStyle, globalConfig, cssVariables } = usePageTheme()

  // 获取文章数据（SSR 时在服务端执行，直接调用 handler，无 HTTP）
  const { data: articleData, status, error } = useAsyncData(
    `article-${articleId.value}`,
    async () => {
      const response = await $fetch<{ success: boolean; data: ArticleInfo }>('/api/article/info', {
        query: { id: articleId.value },
      })
      return response.data
    },
    {
      watch: [articleId],
    }
  )

  // 文章信息
  const articleInfo = computed(() => articleData.value)

  // 是否正在加载
  const isLoading = computed(() => status.value === 'pending')

  // 从 themeConfig 提取文章页的 schema
  const pageSchema = computed(() => getPageSchema('article'))

  // 从 themeConfig 提取布局 schema
  const layoutSchema = computed(() => {
    const layoutId = pageSchema.value?.layoutId
    return layoutId ? getLayoutSchema(layoutId) : undefined
  })

  // 是否有主题配置
  const hasTheme = computed(() => !!pageSchema.value)

  // 设置页面标题
  function useSiteTitle(title: Ref<string> | ComputedRef<string>) {
    useHead({
      title,
    })
  }

  return {
    // 路由参数
    articleId,

    // 文章数据
    articleInfo,
    isLoading,
    error,

    // 主题数据
    themeConfig,
    siteConfig,
    variableValues,
    globalStyle,
    globalConfig,
    cssVariables,

    // 页面配置
    pageSchema,
    layoutSchema,
    hasTheme,

    // 工具方法
    useSiteTitle,
    getPageSchema,
    getLayoutSchema,
  }
}
