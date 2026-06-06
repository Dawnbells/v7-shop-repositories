/**
 * Google Tag Manager (GTM) Composable
 *
 * 在 SSR 阶段通过 useHead() 将 GTM 容器代码注入到 HTML <head> 与 <body>(noscript) 中。
 * 转化事件统一以标准事件名推送到 window.dataLayer，由 GTM 后台自行配置标签消费。
 * 支持多个容器同时注入。
 *
 * pixelId 即 GTM 容器 ID（GTM-XXXXXX）。
 */

declare global {
  interface Window {
    dataLayer: any[];
  }
}

export function useGtmPixel() {
  const { pixels } = usePageContext();

  const formatContainerId = (pixelId: string) =>
    pixelId.startsWith("GTM-") ? pixelId : `GTM-${pixelId}`;

  const gtmPixels = computed(() => pixels.value?.gtm || []);
  const hasGtmPixel = computed(() => gtmPixels.value.length > 0);

  const containerIds = computed(() =>
    gtmPixels.value
      .map((p) => formatContainerId(p.pixelId))
      .filter((id, index, arr) => id && arr.indexOf(id) === index),
  );

  // SSR 时通过 useHead 注入容器脚本到 <head>，noscript iframe 注入到 <body>
  useHead({
    script: computed(() => {
      if (!hasGtmPixel.value) return [];
      const loadCalls = containerIds.value
        .map(
          // 容器 ID 以 JSON.stringify 注入为合法 JS 字符串字面量，避免脚本被截断/注入
          (id) =>
            `(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src='https://www.googletagmanager.com/gtm.js?id='+encodeURIComponent(i)+dl;f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer',${JSON.stringify(id)});`,
        )
        .join("");
      return [
        {
          key: "gtm-container",
          innerHTML: `window.dataLayer=window.dataLayer||[];${loadCalls}`,
        },
      ];
    }),
    noscript: computed(() => {
      if (!hasGtmPixel.value) return [];
      return containerIds.value.map((id) => ({
        key: `gtm-noscript-${id}`,
        // iframe URL 参数使用 encodeURIComponent，避免属性/URL 注入
        innerHTML: `<iframe src="https://www.googletagmanager.com/ns.html?id=${encodeURIComponent(id)}" height="0" width="0" style="display:none;visibility:hidden"></iframe>`,
        tagPosition: "bodyOpen" as const,
      }));
    }),
  });

  function pushEvent(event: string, params: Record<string, any>) {
    if (typeof window === "undefined" || !hasGtmPixel.value) return;
    window.dataLayer = window.dataLayer || [];
    window.dataLayer.push({ event, ...params });
  }

  /**
   * 触发 purchase 转化事件（推送至 dataLayer）
   */
  function trackPurchase(
    value: number,
    currency: string,
    contentIds?: string[],
    transactionId?: string,
  ) {
    const params: Record<string, any> = { value, currency };
    if (contentIds?.length) {
      params.items = contentIds.map((id) => ({ item_id: id }));
    }
    if (transactionId) params.transaction_id = transactionId;
    pushEvent("purchase", params);
  }

  /**
   * 触发 add_to_cart 事件
   */
  function trackAddToCart(value: number, currency: string, contentId?: string) {
    const params: Record<string, any> = { value, currency };
    if (contentId) params.items = [{ item_id: contentId }];
    pushEvent("add_to_cart", params);
  }

  /**
   * 触发 begin_checkout 事件
   */
  function trackInitiateCheckout(value: number, currency: string) {
    pushEvent("begin_checkout", { value, currency });
  }

  return {
    hasGtmPixel,
    gtmPixels,
    trackPurchase,
    trackAddToCart,
    trackInitiateCheckout,
  };
}
