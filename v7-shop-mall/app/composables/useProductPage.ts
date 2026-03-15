/**
 * 产品页 Composable
 * 
 * 整合产品页所需的所有数据：
 * - 主题数据（从 usePageContext 获取）
 * - 产品数据（优先从中间件获取，fallback 到 useAsyncData）
 * - 页面和布局 schema
 * 
 * SSR 时所有数据在服务端获取，客户端仅做 hydration
 */

interface ProductImage {
  id: number
  relativePath: string
  name: string
  width: number
  height: number
  suffix: string
  fileSize: number
  mediaType: string
  mediaState: string
}

interface ProductSpecificationAttribute {
  name: string
  value: string
}

interface ProductSpecification {
  id: number
  sid: number | null
  skuId: number
  sellPrice: number
  originPrice: number | null
  costPrice: number | null
  barcode: string | null
  stockQuantity: number
  linkStock: boolean
  specificationImageId: number | null
  attributes: ProductSpecificationAttribute[]
}

interface ProductInfo {
  id: number
  spuId: number
  skuId: number | null
  countryId: number
  languageId: number | null
  title: string
  summary: string | null
  introduction: string | null
  merchandise: string | null
  waybillProductName: string | null
  sellPrice: number
  originPrice: number | null
  costPrice: number | null
  isTaxable: boolean
  taxationMethod: string | null
  fixedTaxAmount: number | null
  taxAmountThreshold: number | null
  taxQuantityThreshold: number
  taxPerBase: number | null
  barcode: string | null
  stockQuantity: number
  linkStock: boolean
  isMultiSpecs: boolean
  videoFileId: number | null
  botShowSpuId: number | null
  riskUserShowSpuId: number | null
  blacklistedUserShowSpuId: number | null
  images: ProductImage[]
  specifications: ProductSpecification[]
}

export function useProductPage() {
  const route = useRoute()
  const productId = computed(() => route.params.id as string)

  // 初始化主题数据和产品数据（SSR 时从 event.context 读取）
  const { themeConfig, siteConfig, variableValues, productInfo: middlewareProductInfo } = usePageContext()

  // 获取主题相关的计算属性和方法
  const { getPageSchema, getLayoutSchema, globalStyle, globalConfig, cssVariables } = usePageTheme()

  // 获取产品数据（仅当中间件未提供时才通过 API 获取）
  const { data: productData, status, error } = useAsyncData(
    `product-${productId.value}`,
    async () => {
      // 如果中间件已提供产品数据，直接返回
      if (middlewareProductInfo.value) {
        return middlewareProductInfo.value
      }
      // fallback: 通过 API 获取
      const response = await $fetch<{ success: boolean; data: ProductInfo }>('/api/product/info', {
        query: { id: productId.value },
      })
      return response.data
    },
    {
      watch: [productId],
    }
  )

  // 产品信息：优先使用中间件数据，fallback 到 API 数据
  const productInfo = computed(() => middlewareProductInfo.value || productData.value)

  // 是否正在加载
  const isLoading = computed(() => status.value === 'pending')

  // 从 themeConfig 提取产品详情页的 schema
  const pageSchema = computed(() => getPageSchema('product-detail'))

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

  // 格式化价格
  function formatPrice(price: number | null | undefined): string {
    if (price == null) return ''
    return `$${price.toFixed(2)}`
  }

  return {
    // 路由参数
    productId,

    // 产品数据
    productInfo,
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
    formatPrice,
    getPageSchema,
    getLayoutSchema,
  }
}
