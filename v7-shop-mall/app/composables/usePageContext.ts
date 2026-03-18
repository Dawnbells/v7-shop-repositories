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

interface PageThemeContext {
  themeConfig: ThemeConfig | null;
  siteConfig: SiteConfig;
  variableValues: VariableValues;
}

interface PageContext {
  pageTheme: PageThemeContext | null;
  landingPage: LandingPageInfo | null;
  productInfo: ProductInfo | null;
  currency: Currency | null;
  protocolGroups: ProtocolGroup[] | null;
  articleInfo: ArticleInfo | null;
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
  const articleInfo = useState<ArticleInfo | null>("articleInfo", () => null);

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

    if (pageContext?.articleInfo) {
      articleInfo.value = pageContext.articleInfo;
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
    articleInfo,
    setMockData,
  };
}
