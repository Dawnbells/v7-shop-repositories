/**
 * Taboola Pixel Composable
 *
 * 在 SSR 阶段通过 useHead() 注入 Taboola Pixel 基础代码，
 * 并提供转化事件触发方法。
 */

declare global {
  interface Window {
    _tfa: any[];
  }
}

export function useTaboolaPixel() {
  const { pixels } = usePageContext();

  const taboolaPixels = computed(() => pixels.value?.taboola || []);
  const hasTaboolaPixel = computed(() => taboolaPixels.value.length > 0);

  useHead({
    script: computed(() => {
      if (!hasTaboolaPixel.value) return [];

      const initCalls = taboolaPixels.value
        .map((p) => {
          const accountId = JSON.stringify(p.pixelId);
          const scriptId = JSON.stringify(`tb_tfa_script_${p.pixelId}`);
          const scriptSrc = JSON.stringify(`//cdn.taboola.com/libtrc/unip/${p.pixelId}/tfa.js`);
          return [
            `window._tfa.push({notify:'event',name:'page_view',id:${accountId}});`,
            `(function(d,s,id,u){if(d.getElementById(id))return;var js=d.createElement(s);js.id=id;js.async=1;js.src=u;var f=d.getElementsByTagName(s)[0];f.parentNode.insertBefore(js,f);})(document,'script',${scriptId},${scriptSrc});`,
          ].join("");
        })
        .join("");

      return [
        {
          key: "taboola-pixel",
          innerHTML: `window._tfa=window._tfa||[];${initCalls}`,
        },
      ];
    }),
  });

  function trackEvent(
    eventName: string,
    params?: {
      value?: number;
      currency?: string;
      transactionId?: string;
      contentIds?: string[];
    },
  ) {
    if (typeof window !== "undefined" && window._tfa && hasTaboolaPixel.value) {
      for (const pixel of taboolaPixels.value) {
        const payload: Record<string, any> = {
          notify: "event",
          name: eventName,
          id: pixel.pixelId,
        };
        if (params?.value !== undefined) payload.revenue = params.value;
        if (params?.currency) payload.currency = params.currency;
        if (params?.transactionId) payload.orderid = params.transactionId;
        if (params?.contentIds?.length) payload.itemids = params.contentIds;
        window._tfa.push(payload);
      }
    }
  }

  function trackPurchase(
    value: number,
    currency: string,
    contentIds?: string[],
    transactionId?: string,
  ) {
    trackEvent("PURCHASE", { value, currency, contentIds, transactionId });
  }

  function trackAddToCart(
    value: number,
    currency: string,
    contentId?: string,
  ) {
    trackEvent("ADD_TO_CART", {
      value,
      currency,
      contentIds: contentId ? [contentId] : undefined,
    });
  }

  function trackInitiateCheckout(value: number, currency: string) {
    trackEvent("INITIATE_CHECKOUT", { value, currency });
  }

  return {
    hasTaboolaPixel,
    taboolaPixels,
    trackEvent,
    trackPurchase,
    trackAddToCart,
    trackInitiateCheckout,
  };
}
