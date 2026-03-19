/**
 * 货币 Composable
 *
 * 提供货币数据和价格格式化
 * 数据从 usePageContext 获取（由中间件注入）
 */

import Decimal from "decimal.js";

export function useCurrency() {
  const { currency } = usePageContext();

  /**
   * 格式化价格显示
   * @param price 价格数值
   * @param skipConversion 是否跳过汇率转换（当价格已经是目标货币时设为 true）
   */
  function formatPrice(price: number | string | null | undefined, skipConversion = false): string {
    if (price == null) return "";
    const num = typeof price === "string" ? parseFloat(price) : price;
    if (isNaN(num)) return "";

    const curr = currency.value;
    if (curr?.code) {
      let converted = new Decimal(num);
      if (!skipConversion && curr.exchangeRate && curr.exchangeRate !== 1) {
        converted = converted.times(curr.exchangeRate);
      }

      return new Intl.NumberFormat(undefined, {
        style: "currency",
        currency: curr.code,
        minimumFractionDigits: curr.fractionDigits ?? 2,
        maximumFractionDigits: curr.fractionDigits ?? 2,
      }).format(converted.toNumber());
    }

    return `$${num.toFixed(2)}`;
  }

  return {
    currency,
    formatPrice,
  };
}
