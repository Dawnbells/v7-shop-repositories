/**
 * Embed Pixel Composable
 *
 * 将后台粘贴的 HTML 像素代码解析为 head 中的 script / noscript 标签。
 */

type HeadScript = {
  key: string;
  src?: string;
  async?: boolean;
  defer?: boolean;
  innerHTML?: string;
};

type HeadNoScript = {
  key: string;
  innerHTML: string;
};

function getAttribute(tag: string, name: string) {
  const match = tag.match(new RegExp(`${name}\\s*=\\s*["']([^"']+)["']`, "i"));
  return match?.[1];
}

function hasAttribute(tag: string, name: string) {
  return new RegExp(`\\s${name}(\\s|>|=)`, "i").test(tag);
}

function parseEmbedCode(code: string, keyPrefix: string) {
  const scripts: HeadScript[] = [];
  const noscripts: HeadNoScript[] = [];
  let index = 0;

  for (const match of code.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    const attrs = match[1] || "";
    const content = match[2]?.trim();
    const src = getAttribute(attrs, "src");
    const script: HeadScript = {
      key: `${keyPrefix}-script-${index++}`,
    };
    if (src) {
      script.src = src;
      script.async = hasAttribute(attrs, "async") || true;
      if (hasAttribute(attrs, "defer")) script.defer = true;
    } else if (content) {
      script.innerHTML = content;
    }
    if (script.src || script.innerHTML) {
      scripts.push(script);
    }
  }

  index = 0;
  for (const match of code.matchAll(/<noscript\b[^>]*>([\s\S]*?)<\/noscript>/gi)) {
    const content = match[1]?.trim();
    if (content) {
      noscripts.push({
        key: `${keyPrefix}-noscript-${index++}`,
        innerHTML: content,
      });
    }
  }

  return { scripts, noscripts };
}

export function useEmbedPixel() {
  const { pixels } = usePageContext();

  const embedPixels = computed(() => pixels.value?.embed || []);
  const hasEmbedPixel = computed(() => embedPixels.value.length > 0);

  useHead({
    script: computed(() => {
      if (!hasEmbedPixel.value) return [];
      return embedPixels.value.flatMap((pixel) =>
        parseEmbedCode(pixel.embedCode || "", `embed-pixel-${pixel.id}`).scripts,
      );
    }),
    noscript: computed(() => {
      if (!hasEmbedPixel.value) return [];
      return embedPixels.value.flatMap((pixel) =>
        parseEmbedCode(pixel.embedCode || "", `embed-pixel-${pixel.id}`).noscripts,
      );
    }),
  });

  return {
    hasEmbedPixel,
    embedPixels,
  };
}
