/**
 * Google Pixel Composable
 *
 * 在 SSR 阶段通过 useHead() 将 Google Ads (gtag.js) 代码注入到 HTML <head> 中
 * 支持多个像素同时初始化，自动触发 PageView 事件
 */

declare global {
  interface Window {
    dataLayer: any[];
    gtag: (...args: any[]) => void;
  }
}

export function useGooglePixel() {
  const { pixels } = usePageContext();

  const googlePixels = computed(() => pixels.value?.google || []);
  const hasGooglePixel = computed(() => googlePixels.value.length > 0);

  // SSR 时通过 useHead 注入脚本到 <head>
  useHead({
    script: computed(() => {
      if (!hasGooglePixel.value) return [];

      const firstPixelId = googlePixels.value[0]!.pixelId;
      const configCalls = googlePixels.value
        .map((p) => `gtag('config','${p.pixelId}');`)
        .join("");

      return [
        {
          key: "gtag-js",
          src: `https://www.googletagmanager.com/gtag/js?id=${firstPixelId}`,
          async: true,
        },
        {
          key: "gtag-config",
          innerHTML: `window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments);}gtag('js',new Date());${configCalls}`,
        },
      ];
    }),
  });

  /**
   * 触发 Conversion 转化事件
   * @param value 订单金额
   * @param currency 货币代码（如 USD, CNY）
   * @param transactionId 交易ID（可选）
   */
  function trackConversion(
    value: number,
    currency: string,
    transactionId?: string
  ) {
    if (typeof window !== "undefined" && window.gtag) {
      for (const pixel of googlePixels.value) {
        const params: Record<string, any> = {
          send_to: `${pixel.pixelId}/${pixel.conversionEvent}`,
          value,
          currency,
        };
        if (transactionId) {
          params.transaction_id = transactionId;
        }
        window.gtag("event", "conversion", params);
      }
    }
  }

  /**
   * 触发 add_to_cart 事件
   * @param value 商品价格
   * @param currency 货币代码
   * @param itemId 商品ID（可选）
   */
  function trackAddToCart(value: number, currency: string, itemId?: string) {
    if (typeof window !== "undefined" && window.gtag) {
      const params: Record<string, any> = { value, currency };
      if (itemId) {
        params.items = [{ id: itemId }];
      }
      window.gtag("event", "add_to_cart", params);
    }
  }

  /**
   * 触发 begin_checkout 事件
   * @param value 订单金额
   * @param currency 货币代码
   */
  function trackBeginCheckout(value: number, currency: string) {
    if (typeof window !== "undefined" && window.gtag) {
      window.gtag("event", "begin_checkout", { value, currency });
    }
  }

  return {
    hasGooglePixel,
    googlePixels,
    trackConversion,
    trackAddToCart,
    trackBeginCheckout,
  };
}
