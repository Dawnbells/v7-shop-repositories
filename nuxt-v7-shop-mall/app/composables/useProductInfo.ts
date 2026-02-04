import type { ProductInfo } from "~/types/page-context";

/**
 * 获取产品信息
 * 自动从 pageContext 获取 landingSpuId、languageId、subDomainId
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
    "landingSpuId",
    "domain.id",
    "domain.languageId",
  ]);

  const spuId = computed(() => pageContext.value.landingSpuId);
  const subDomainId = computed(() => pageContext.value.domain?.id);
  const languageId = computed(() => pageContext.value.domain?.languageId);

  // 构建唯一的请求 key
  const key = computed(() => {
    if (!spuId.value || !languageId.value) return null;
    return `product-info-${subDomainId.value || 0}-${spuId.value}-${languageId.value}`;
  });

  // 使用 useFetch 获取产品信息
  const { data, pending, error, refresh } = useFetch<ProductInfo>(
    "/api/product/info",
    {
      key: key.value || "product-info-placeholder",
      query: computed(() => ({
        spuId: spuId.value,
        languageId: languageId.value,
        subDomainId: subDomainId.value,
      })),
      // 只有当参数有效时才发起请求
      immediate: !!(spuId.value && languageId.value),
      watch: [spuId, languageId, subDomainId],
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
    "landingSpuId",
    "domain.id",
    "domain.languageId",
  ]);

  const spuId = pageContext.value.landingSpuId;
  const languageId = pageContext.value.domain?.languageId;
  const subDomainId = pageContext.value.domain?.id;

  if (!spuId || !languageId) {
    console.warn("[useProductInfo] Missing spuId or languageId from pageContext");
    return null;
  }

  try {
    const query = new URLSearchParams({
      spuId: String(spuId),
      languageId: String(languageId),
    });

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
