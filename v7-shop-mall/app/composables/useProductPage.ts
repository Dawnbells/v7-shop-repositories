/**
 * 产品页 Composable
 *
 * 提供产品相关数据和工具方法
 * 产品数据从 pageContext 获取（由中间件注入）
 */

export function useProductPage() {
  const { productInfo } = usePageContext();

  function formatPrice(price: number | string | null | undefined): string {
    if (price == null) return "";
    const num = typeof price === "string" ? parseFloat(price) : price;
    if (isNaN(num)) return "";
    return `$${num.toFixed(2)}`;
  }

  return {
    productInfo,
    formatPrice,
  };
}
