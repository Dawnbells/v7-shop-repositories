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
  const taboola = useTaboolaPixel();
  const bigo = useBigoPixel();
  const embed = useEmbedPixel();

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
      eventId?: string;
    },
  ) {
    meta.trackPurchase(value, currency, options?.contentIds, options?.eventId);
    google.trackConversion(value, currency, options?.transactionId);
    tiktok.trackPurchase(value, currency, options?.contentIds, options?.eventId);
    taboola.trackPurchase(value, currency, options?.contentIds, options?.transactionId);
    bigo.trackPurchase(value, currency, options?.contentIds, options?.transactionId);
  }

  function trackAddToCart(
    value: number,
    currency: string,
    contentId?: string,
    eventId?: string,
  ) {
    meta.trackAddToCart(value, currency, contentId, eventId);
    google.trackAddToCart(value, currency, contentId);
    tiktok.trackAddToCart(value, currency, contentId, eventId);
    taboola.trackAddToCart(value, currency, contentId);
    bigo.trackAddToCart(value, currency, contentId);
  }

  function trackInitiateCheckout(value: number, currency: string) {
    meta.trackInitiateCheckout(value, currency);
    google.trackBeginCheckout(value, currency);
    tiktok.trackInitiateCheckout(value, currency);
    taboola.trackInitiateCheckout(value, currency);
    bigo.trackInitiateCheckout(value, currency);
  }

  /**
   * 根据各像素的 conversionEvent 配置决定触发 Purchase 或 AddToCart
   */
  function trackConversionByConfig(
    value: number,
    currency: string,
    options?: {
      contentIds?: string[];
      transactionId?: string;
      eventId?: string;
    },
  ) {
    const allPixels = [
      ...(meta.metaPixels.value || []),
      ...(google.googlePixels.value || []),
      ...(tiktok.tiktokPixels.value || []),
      ...(taboola.taboolaPixels.value || []),
      ...(bigo.bigoPixels.value || []),
    ];

    const needPurchase = allPixels.some((p) => p.conversionEvent === "PURCHASE");
    const needAddToCart = allPixels.some((p) => p.conversionEvent === "ADD_TO_CART");

    if (needPurchase) {
      trackPurchase(value, currency, options);
    }
    if (needAddToCart) {
      trackAddToCart(value, currency, options?.contentIds?.[0], options?.eventId);
    }
  }

  return {
    meta,
    google,
    tiktok,
    taboola,
    bigo,
    embed,
    trackPurchase,
    trackAddToCart,
    trackInitiateCheckout,
    trackConversionByConfig,
  };
}
