/**
 * TikTok Pixel Composable
 *
 * 在 SSR 阶段通过 useHead() 将 TikTok Pixel 代码注入到 HTML <head> 中
 * 支持多个像素同时初始化，自动触发 PageView 事件
 */

declare global {
  interface Window {
    ttq: {
      track: (event: string, params?: Record<string, any>) => void;
      page: () => void;
      load: (pixelId: string) => void;
      identify: (params: Record<string, any>) => void;
    };
    TiktokAnalyticsObject: string;
  }
}

export function useTiktokPixel() {
  const { pixels } = usePageContext();

  const tiktokPixels = computed(() => pixels.value?.tiktok || []);
  const hasTiktokPixel = computed(() => tiktokPixels.value.length > 0);

  // SSR 时通过 useHead 注入脚本到 <head>
  useHead({
    script: computed(() => {
      if (!hasTiktokPixel.value) return [];

      const loadCalls = tiktokPixels.value
        .map((p) => `ttq.load('${p.pixelId}');`)
        .join("");

      return [
        {
          key: "tiktok-pixel",
          innerHTML: `!function(w,d,t){w.TiktokAnalyticsObject=t;var ttq=w[t]=w[t]||[];ttq.methods=["page","track","identify","instances","debug","on","off","once","ready","alias","group","enableCookie","disableCookie"];ttq.setAndDefer=function(t,e){t[e]=function(){t.push([e].concat(Array.prototype.slice.call(arguments,0)))}};for(var i=0;i<ttq.methods.length;i++)ttq.setAndDefer(ttq,ttq.methods[i]);ttq.instance=function(t){for(var e=ttq._i[t]||[],n=0;n<ttq.methods.length;n++)ttq.setAndDefer(e,ttq.methods[n]);return e};ttq.load=function(e,n){var i="https://analytics.tiktok.com/i18n/pixel/events.js";ttq._i=ttq._i||{};ttq._i[e]=[];ttq._i[e]._u=i;ttq._t=ttq._t||{};ttq._t[e]=+new Date;ttq._o=ttq._o||{};ttq._o[e]=n||{};var o=document.createElement("script");o.type="text/javascript";o.async=!0;o.src=i+"?sdkid="+e+"&lib="+t;var a=document.getElementsByTagName("script")[0];a.parentNode.insertBefore(o,a)};${loadCalls}ttq.page()}(window,document,'ttq');`,
        },
      ];
    }),
  });

  /**
   * 触发 CompletePayment 转化事件
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
    if (typeof window !== "undefined" && window.ttq && hasTiktokPixel.value) {
      const params: Record<string, any> = { value, currency };
      if (contentIds?.length) {
        params.contents = contentIds.map((id) => ({
          content_id: id,
          content_type: "product",
        }));
      }
      if (eventId) params.event_id = eventId;
      window.ttq.track("CompletePayment", params);
    }
  }

  /**
   * 触发 AddToCart 事件
   * @param value 商品价格
   * @param currency 货币代码
   * @param contentId 商品ID（可选）
   */
  function trackAddToCart(value: number, currency: string, contentId?: string, eventId?: string) {
    if (typeof window !== "undefined" && window.ttq && hasTiktokPixel.value) {
      const params: Record<string, any> = { value, currency };
      if (contentId) {
        params.contents = [{ content_id: contentId, content_type: "product" }];
      }
      if (eventId) params.event_id = eventId;
      window.ttq.track("AddToCart", params);
    }
  }

  /**
   * 触发 InitiateCheckout 事件
   * @param value 订单金额
   * @param currency 货币代码
   */
  function trackInitiateCheckout(value: number, currency: string) {
    if (typeof window !== "undefined" && window.ttq && hasTiktokPixel.value) {
      window.ttq.track("InitiateCheckout", { value, currency });
    }
  }

  return {
    hasTiktokPixel,
    tiktokPixels,
    trackPurchase,
    trackAddToCart,
    trackInitiateCheckout,
  };
}
