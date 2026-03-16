/**
 * 产品页 Composable
 *
 * 提供产品相关数据和工具方法
 * 产品数据从 pageContext 获取（由中间件注入）
 */

export function useProductPage() {
  const { productInfo } = usePageContext();

  console.log("productInfo", productInfo.value);

  function formatPrice(price: number | null | undefined): string {
    if (price == null) return "";
    return `$${price.toFixed(2)}`;
  }

  return {
    productInfo,
    formatPrice,
  };
}
