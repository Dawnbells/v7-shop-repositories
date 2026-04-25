/**
 * Bigo Ads Pixel Composable
 *
 * 支持 Bigo 后台生成的完整 resource/pixel URL，或直接填写像素 ID。
 */

declare global {
  interface Window {
    bgdataLayer: any[];
    bge: (...args: any[]) => void;
  }
}

export function useBigoPixel() {
  const { pixels } = usePageContext();

  const bigoPixels = computed(() => pixels.value?.bigo || []);
  const hasBigoPixel = computed(() => bigoPixels.value.length > 0);

  const getBigoScriptSrc = (pixelId: string, orgId?: string) => {
    if (/^https?:\/\//.test(pixelId)) return pixelId;
    const params = new URLSearchParams({ accountId: pixelId });
    if (orgId) params.set("orgId", orgId);
    return `https://ads.bigo.sg/resource/pixel?${params.toString()}`;
  };

  const getBigoInitId = (pixelId: string) => {
    if (!/^https?:\/\//.test(pixelId)) return pixelId;
    try {
      const url = new URL(pixelId);
      return url.searchParams.get("accountId") || pixelId;
    } catch {
      return pixelId;
    }
  };

  const getBigoScriptId = (pixelId: string) =>
    `bigo_pixel_${getBigoInitId(pixelId).replace(/[^a-zA-Z0-9_-]/g, "_")}`;

  useHead({
    script: computed(() => {
      if (!hasBigoPixel.value) return [];

      const initCalls = bigoPixels.value
        .map((p) => {
          const initId = JSON.stringify(getBigoInitId(p.pixelId));
          const scriptId = JSON.stringify(getBigoScriptId(p.pixelId));
          const scriptSrc = JSON.stringify(getBigoScriptSrc(p.pixelId, p.accessToken));
          return [
            `bge('init',${initId});`,
            `bge('event','page_view');`,
            `(function(d,s,id,u){if(d.getElementById(id))return;var js=d.createElement(s);js.id=id;js.async=1;js.src=u;var f=d.getElementsByTagName(s)[0];f.parentNode.insertBefore(js,f);})(document,'script',${scriptId},${scriptSrc});`,
          ].join("");
        })
        .join("");

      return [
        {
          key: "bigo-pixel",
          innerHTML: `window.bgdataLayer=window.bgdataLayer||[];function bge(){window.bgdataLayer.push(arguments);}${initCalls}`,
        },
      ];
    }),
  });

  function trackEvent(eventName: string, params?: Record<string, any>) {
    if (typeof window !== "undefined" && window.bge && hasBigoPixel.value) {
      window.bge("event", eventName, params);
    }
  }

  function trackPurchase(
    value: number,
    currency: string,
    contentIds?: string[],
    transactionId?: string,
  ) {
    trackEvent("ec_purchase", {
      value,
      currency,
      content_ids: contentIds,
      order_id: transactionId,
    });
  }

  function trackAddToCart(value: number, currency: string, contentId?: string) {
    trackEvent("ec_add_cart", {
      value,
      currency,
      content_id: contentId,
    });
  }

  function trackInitiateCheckout(value: number, currency: string) {
    trackEvent("ec_order", { value, currency });
  }

  return {
    hasBigoPixel,
    bigoPixels,
    trackEvent,
    trackPurchase,
    trackAddToCart,
    trackInitiateCheckout,
  };
}
