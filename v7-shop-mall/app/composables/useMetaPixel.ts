/**
 * Meta Pixel Composable
 *
 * 在 SSR 阶段通过 useHead() 将 Meta Pixel 代码注入到 HTML <head> 中
 * 支持多个像素同时初始化，自动触发 PageView 事件
 */

declare global {
  interface Window {
    fbq: (...args: any[]) => void;
  }
}

export function useMetaPixel() {
  const { pixels } = usePageContext();

  const metaPixels = computed(() => pixels.value?.meta || []);
  const hasMetaPixel = computed(() => metaPixels.value.length > 0);

  // SSR 时通过 useHead 注入脚本到 <head>
  useHead({
    script: computed(() => {
      if (!hasMetaPixel.value) return [];

      const pixelIds = metaPixels.value.map((p) => p.pixelId);
      const initCalls = pixelIds.map((id) => `fbq('init','${id}');`).join("");

      return [
        {
          key: "meta-pixel",
          innerHTML: `!function(f,b,e,v,n,t,s){if(f.fbq)return;n=f.fbq=function(){n.callMethod?n.callMethod.apply(n,arguments):n.queue.push(arguments)};if(!f._fbq)f._fbq=n;n.push=n;n.loaded=!0;n.version='2.0';n.queue=[];t=b.createElement(e);t.async=!0;t.src=v;s=b.getElementsByTagName(e)[0];s.parentNode.insertBefore(t,s)}(window,document,'script','https://connect.facebook.net/en_US/fbevents.js');${initCalls}fbq('track','PageView');`,
        },
      ];
    }),
  });

  /**
   * 触发 Purchase 转化事件
   * @param value 订单金额
   * @param currency 货币代码（如 USD, CNY）
   * @param contentIds 商品ID列表（可选）
   */
  function trackPurchase(
    value: number,
    currency: string,
    contentIds?: string[],
    eventId?: string,
  ) {
    if (typeof window !== "undefined" && window.fbq && hasMetaPixel.value) {
      const params: Record<string, any> = { value, currency };
      if (contentIds?.length) {
        params.content_ids = contentIds;
        params.content_type = "product";
      }
      const options = eventId ? { eventID: eventId } : undefined;
      window.fbq("track", "Purchase", params, options);
    }
  }

  /**
   * 触发 AddToCart 事件
   * @param value 商品价格
   * @param currency 货币代码
   * @param contentId 商品ID（可选）
   */
  function trackAddToCart(value: number, currency: string, contentId?: string, eventId?: string) {
    if (typeof window !== "undefined" && window.fbq && hasMetaPixel.value) {
      const params: Record<string, any> = { value, currency };
      if (contentId) {
        params.content_ids = [contentId];
        params.content_type = "product";
      }
      const options = eventId ? { eventID: eventId } : undefined;
      window.fbq("track", "AddToCart", params, options);
    }
  }

  /**
   * 触发 InitiateCheckout 事件
   * @param value 订单金额
   * @param currency 货币代码
   */
  function trackInitiateCheckout(value: number, currency: string) {
    if (typeof window !== "undefined" && window.fbq && hasMetaPixel.value) {
      window.fbq("track", "InitiateCheckout", { value, currency });
    }
  }

  return {
    hasMetaPixel,
    metaPixels,
    trackPurchase,
    trackAddToCart,
    trackInitiateCheckout,
  };
}
