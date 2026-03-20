/**
 * 页面上下文 Composable
 *
 * 在 SSR 时从 event.context.pageContext 读取中间件注入的数据
 * 作为所有数据的唯一来源，其他职责 composable 从此处获取数据
 * 客户端通过 hydration payload 自动恢复，无需操作
 */

import type { ThemeConfig } from "~/types/component-meta";
import type { SiteConfig, VariableValues } from "~/types/data-context";
import type { ProductInfo } from "~/types/product";
import type { Currency } from "~/types/currency";
import type { LandingPageInfo } from "~/types/landing";
import type { ProtocolGroup } from "~/types/protocol";
import type { ArticleInfo } from "~/types/article";
import type { PixelsByPlatform } from "~/types/pixel";

interface PageThemeContext {
  themeConfig: ThemeConfig | null;
  siteConfig: SiteConfig;
  variableValues: VariableValues;
}

export interface CountryInfo {
  code: string;
  name: string;
  addressFields: string | null;
}

export interface LanguageInfo {
  id: number;
  code: string;
  name: string;
  cname: string;
}

interface PageContext {
  pageTheme: PageThemeContext | null;
  landingPage: LandingPageInfo | null;
  productInfo: ProductInfo | null;
  currency: Currency | null;
  protocolGroups: ProtocolGroup[] | null;
  pixels: PixelsByPlatform | null;
  articleInfo: ArticleInfo | null;
  country: CountryInfo | null;
  currentLanguage: LanguageInfo | null;
}

export function usePageContext() {
  // 标记是否已经注入过 SSR 数据（useState 相同 key 返回同一 ref，所以放在函数内安全）
  const ssrDataInjected = useState<boolean>("pageContextSSRInjected", () => false);

  const themeConfig = useState<ThemeConfig | null>("pageThemeConfig", () => null);
  const siteConfig = useState<SiteConfig>("pageSiteConfig", () => ({}));
  const variableValues = useState<VariableValues>("pageVariableValues", () => ({}));
  const productInfo = useState<ProductInfo | null>("productInfo", () => null);
  const landingPage = useState<LandingPageInfo | null>("landingPage", () => null);
  const currency = useState<Currency | null>("currency", () => null);
  const protocolGroups = useState<ProtocolGroup[] | null>("protocolGroups", () => null);
  const pixels = useState<PixelsByPlatform | null>("pixels", () => null);
  const articleInfo = useState<ArticleInfo | null>("articleInfo", () => null);
  const countryInfo = useState<CountryInfo | null>("countryInfo", () => null);
  const currentLanguage = useState<LanguageInfo | null>("currentLanguage", () => null);

  // SSR 时：从 event.context.pageContext 读取中间件注入的数据（只执行一次）
  if (import.meta.server && !ssrDataInjected.value) {
    ssrDataInjected.value = true;

    const event = useRequestEvent();
    const pageContext = event?.context?.pageContext as
      | PageContext
      | null
      | undefined;

    const pageTheme = pageContext?.pageTheme as
      | PageThemeContext
      | null
      | undefined;

    if (pageTheme) {
      themeConfig.value = pageTheme.themeConfig;
      siteConfig.value = pageTheme.siteConfig || {};
      variableValues.value = pageTheme.variableValues || {};
    }

    if (pageContext?.landingPage) {
      landingPage.value = pageContext.landingPage;
    }

    if (pageContext?.productInfo) {
      productInfo.value = pageContext.productInfo;
    }

    if (pageContext?.currency) {
      currency.value = pageContext.currency;
    }

    if (pageContext?.protocolGroups) {
      protocolGroups.value = pageContext.protocolGroups;
    }

    if (pageContext?.pixels) {
      pixels.value = pageContext.pixels;
    }

    if (pageContext?.articleInfo) {
      articleInfo.value = pageContext.articleInfo;
    }

    if (pageContext?.country) {
      countryInfo.value = {
        code: pageContext.country.code,
        name: pageContext.country.name,
        addressFields: pageContext.country.addressFields ?? null,
      };
    }

    if (pageContext?.currentLanguage) {
      currentLanguage.value = pageContext.currentLanguage;
    }
  }

  /**
   * 设置 Mock 数据（用于编辑器预览）
   * 仅供 builder composable 内部使用
   */
  function setMockData(mockData: {
    productInfo?: ProductInfo | null;
    currency?: Currency | null;
  }) {
    if (mockData.productInfo !== undefined) {
      productInfo.value = mockData.productInfo;
    }
    if (mockData.currency !== undefined) {
      currency.value = mockData.currency;
    }
  }

  return {
    themeConfig,
    siteConfig,
    variableValues,
    landingPage,
    productInfo,
    currency,
    protocolGroups,
    pixels,
    articleInfo,
    countryInfo,
    currentLanguage,
    setMockData,
  };
}
