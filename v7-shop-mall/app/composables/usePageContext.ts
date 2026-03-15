/**
 * 页面上下文 Composable
 * 
 * 在 SSR 时从 event.context.pageContext 读取中间件注入的数据
 * 初始化 usePageTheme 的 useState 状态
 * 客户端通过 hydration payload 自动恢复，无需操作
 */

import type { ThemeConfig } from '~/types/component-meta'
import type { SiteConfig, VariableValues } from '~/types/data-context'

interface PageThemeContext {
  themeConfig: ThemeConfig | null
  siteConfig: SiteConfig
  variableValues: VariableValues
}

interface LandingPageInfo {
  landingSpuId: number | null
  protocolId: number | null
  protocolPlaceholderValues: Record<string, any>
  variableSchema: any[]
}

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

interface PageContext {
  pageTheme: PageThemeContext | null
  landingPage: LandingPageInfo | null
  productInfo: ProductInfo | null
}

export function usePageContext() {
  const { themeConfig, siteConfig, variableValues } = usePageTheme()

  const productInfo = useState<ProductInfo | null>('productInfo', () => null)
  const landingPage = useState<LandingPageInfo | null>('landingPage', () => null)

  // SSR 时：从 event.context.pageContext 读取中间件注入的数据
  if (import.meta.server) {
    const event = useRequestEvent()
    const pageContext = event?.context?.pageContext as PageContext | null | undefined

    const pageTheme = pageContext?.pageTheme as PageThemeContext | null | undefined

    if (pageTheme) {
      if (themeConfig.value === null) {
        themeConfig.value = pageTheme.themeConfig
      }
      if (Object.keys(siteConfig.value).length === 0 && pageTheme.siteConfig) {
        siteConfig.value = pageTheme.siteConfig
      }
      if (Object.keys(variableValues.value).length === 0 && pageTheme.variableValues) {
        variableValues.value = pageTheme.variableValues
      }
    }

    if (pageContext?.landingPage && landingPage.value === null) {
      landingPage.value = pageContext.landingPage
    }

    if (pageContext?.productInfo && productInfo.value === null) {
      productInfo.value = pageContext.productInfo
    }
  }

  return {
    themeConfig,
    siteConfig,
    variableValues,
    landingPage,
    productInfo,
  }
}
