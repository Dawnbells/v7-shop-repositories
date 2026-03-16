/**
 * 产品页 Composable
 *
 * 提供产品相关数据和工具方法
 * 产品数据从 pageContext 获取（由中间件注入）
 */

import Decimal from "decimal.js";

export function useProductPage() {
  const { productInfo, currency } = usePageContext();

  function formatPrice(price: number | string | null | undefined): string {
    if (price == null) return "";
    const num = typeof price === "string" ? parseFloat(price) : price;
    if (isNaN(num)) return "";

    const curr = currency.value;
    if (curr?.code) {
      // 使用 decimal.js 进行高精度汇率转换
      let converted = new Decimal(num);
      if (curr.exchangeRate && curr.exchangeRate !== 1) {
        converted = converted.times(curr.exchangeRate);
      }

      return new Intl.NumberFormat(undefined, {
        style: "currency",
        currency: curr.code,
        minimumFractionDigits: curr.fractionDigits ?? 2,
        maximumFractionDigits: curr.fractionDigits ?? 2,
      }).format(converted.toNumber());
    }

    // 降级：使用默认格式（USD）
    return `$${num.toFixed(2)}`;
  }

  console.log(productInfo.value);

  return {
    productInfo,
    currency,
    formatPrice,
  };
}
