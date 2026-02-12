import type { ProductInfo } from "~/types/page-context";

/**
 * 产品页面专用 composable
 * 包含产品数据获取、主题渲染、页面配置等功能
 *
 * @example
 * ```ts
 * const {
 *   productInfo, productPending,
 *   themeConfig, globalStyleVars,
 *   pageSchema, useThemeRenderer,
 * } = useProductPage();
 * ```
 */
export function useProductPage() {
  // ==================== 产品数据获取 ====================

  // 从 pageContext 自动获取所需参数
  const pageContext = usePageContext([
    "landingProductId",
    "domain.id",
    "domain.languageId",
  ]);

  // 从路由获取原始 spuId（用于 LAND 类型回退查询）
  const route = useRoute();
  const routeSpuId = computed(() => {
    const id = route.params.id;
    return id ? Number(id) : undefined;
  });

  const productId = computed(() => pageContext.value.landingProductId);
  const subDomainId = computed(() => pageContext.value.domain?.id);
  const languageId = computed(() => pageContext.value.domain?.languageId);

  // 构建唯一的请求 key
  const productKey = computed(() => {
    if (productId.value) {
      // CLOAK 类型：使用 productId
      return `product-info-pid-${productId.value}`;
    }
    // LAND 类型：使用 spuId + languageId
    if (!routeSpuId.value || !languageId.value) return null;
    return `product-info-${subDomainId.value || 0}-${routeSpuId.value}-${languageId.value}`;
  });

  // 使用 useFetch 获取产品信息
  const { data: productInfo, pending: productPending, error: productError, refresh: productRefresh } = useFetch<ProductInfo>(
    "/api/product/info",
    {
      key: productKey.value || "product-info-placeholder",
      query: computed(() => {
        if (productId.value) {
          // CLOAK 类型：直接使用 productId
          return {
            productId: productId.value,
            subDomainId: subDomainId.value,
          };
        }
        // LAND 类型：使用 spuId + languageId
        return {
          spuId: routeSpuId.value,
          languageId: languageId.value,
          subDomainId: subDomainId.value,
        };
      }),
      // 只有当参数有效时才发起请求
      immediate: !!(productId.value || (routeSpuId.value && languageId.value)),
      watch: [productId, routeSpuId, languageId, subDomainId],
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

  provide('productInfo', productInfo);
  provide('productPending', productPending);

  // ==================== 返回值 ====================

  return {
    // 产品信息
    productInfo,
    productPending: readonly(productPending),
    productError: readonly(productError),
    productRefresh,

    // 主题配置
    themeConfig,
    globalStyle,
    globalStyleVars,
    hasTheme,
    siteConfig,
    variableValues,

    // 页面配置
    pageSchema: computed(() => getPageSchema("product")),
    layoutSchema: computed(() => getPageLayout("product")),
    useThemeRenderer: computed(() => hasTheme.value && !!getPageSchema("product")),

    // 工具
    device,
    useSiteTitle,
  };
}

/**
 * 获取产品信息（异步版本，用于服务端渲染或一次性获取）
 * 自动从 pageContext 获取参数
 *
 * @returns 产品信息
 *
 * @example
 * ```ts
 * const productInfo = await fetchProductInfo();
 * ```
 */
export async function fetchProductInfo(): Promise<ProductInfo | null> {
  const pageContext = usePageContext([
    "landingProductId",
    "domain.id",
    "domain.languageId",
  ]);

  const productId = pageContext.value.landingProductId;
  const languageId = pageContext.value.domain?.languageId;
  const subDomainId = pageContext.value.domain?.id;

  // 从路由获取原始 spuId
  const route = useRoute();
  const routeSpuId = route.params.id ? Number(route.params.id) : undefined;

  try {
    const query = new URLSearchParams();

    if (productId) {
      // CLOAK 类型：直接使用 productId
      query.set("productId", String(productId));
    } else {
      // LAND 类型：使用 spuId + languageId
      if (!routeSpuId || !languageId) {
        console.warn("[fetchProductInfo] Missing spuId or languageId from pageContext");
        return null;
      }
      query.set("spuId", String(routeSpuId));
      query.set("languageId", String(languageId));
    }

    if (subDomainId) {
      query.set("subDomainId", String(subDomainId));
    }

    const response = await $fetch<ProductInfo>(
      `/api/product/info?${query.toString()}`
    );
    return response;
  } catch (error) {
    console.error("[fetchProductInfo] Failed to fetch product:", error);
    return null;
  }
}
