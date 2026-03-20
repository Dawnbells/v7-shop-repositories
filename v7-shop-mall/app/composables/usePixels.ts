/**
 * 统一像素 Composable
 *
 * 同时初始化 Meta、Google、TikTok 三个平台的像素
 * 提供统一的事件触发方法，自动向所有平台发送事件
 */

export function usePixels() {
  const meta = useMetaPixel();
  const google = useGooglePixel();
  const tiktok = useTiktokPixel();

  /**
   * 触发 Purchase 转化事件（向所有平台发送）
   * @param value 订单金额
   * @param currency 货币代码
   * @param options 可选参数
   */
  function trackPurchase(
    value: number,
    currency: string,
    options?: {
      contentIds?: string[];
      transactionId?: string;
    }
  ) {
    meta.trackPurchase(value, currency, options?.contentIds);
    google.trackConversion(value, currency, options?.transactionId);
    tiktok.trackPurchase(value, currency, options?.contentIds);
  }

  /**
   * 触发 AddToCart 事件（向所有平台发送）
   * @param value 商品价格
   * @param currency 货币代码
   * @param contentId 商品ID（可选）
   */
  function trackAddToCart(value: number, currency: string, contentId?: string) {
    meta.trackAddToCart(value, currency, contentId);
    google.trackAddToCart(value, currency, contentId);
    tiktok.trackAddToCart(value, currency, contentId);
  }

  /**
   * 触发 InitiateCheckout/BeginCheckout 事件（向所有平台发送）
   * @param value 订单金额
   * @param currency 货币代码
   */
  function trackInitiateCheckout(value: number, currency: string) {
    meta.trackInitiateCheckout(value, currency);
    google.trackBeginCheckout(value, currency);
    tiktok.trackInitiateCheckout(value, currency);
  }

  return {
    meta,
    google,
    tiktok,
    trackPurchase,
    trackAddToCart,
    trackInitiateCheckout,
  };
}
