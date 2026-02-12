import type { ProductInfo } from "~/types/page-context";

/**
 * 获取产品信息
 * 自动从 pageContext 获取 landingProductId、spuId、languageId、subDomainId
 * 
 * - 如果 landingProductId 存在（CLOAK 类型），直接使用 productId 查询
 * - 如果 landingProductId 为空（LAND 类型），使用 spuId + languageId 查询
 *
 * @returns 产品信息、加载状态、错误信息、刷新函数
 *
 * @example
 * ```ts
 * const { data, pending, error, refresh } = useProductInfo();
 * ```
 */
export function useProductInfo() {
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
  const key = computed(() => {
    if (productId.value) {
      // CLOAK 类型：使用 productId
      return `product-info-pid-${productId.value}`;
    }
    // LAND 类型：使用 spuId + languageId
    if (!routeSpuId.value || !languageId.value) return null;
    return `product-info-${subDomainId.value || 0}-${routeSpuId.value}-${languageId.value}`;
  });

  // 使用 useFetch 获取产品信息
  const { data, pending, error, refresh } = useFetch<ProductInfo>(
    "/api/product/info",
    {
      key: key.value || "product-info-placeholder",
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

  return {
    /** 产品信息 */
    data,
    /** 是否正在加载 */
    pending,
    /** 错误信息 */
    error,
    /** 刷新数据 */
    refresh,
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
        console.warn("[useProductInfo] Missing spuId or languageId from pageContext");
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
    console.error("[useProductInfo] Failed to fetch product:", error);
    return null;
  }
}
