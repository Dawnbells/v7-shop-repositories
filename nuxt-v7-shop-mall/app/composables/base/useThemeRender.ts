/**
 * 主题渲染 composable
 * 用于前端页面渲染主题配置
 * 
 * 数据来源：
 * - themeConfig: 页面布局、组件、样式（来自 pageContext）
 * - siteConfig: 站点配置值（全局固定配置，来自 pageContext）
 * - variableValues: 变量实际值（用户自定义变量，来自 pageContext）
 * - productInfo: 产品详情（来自 API）
 */

import type { ThemeSchema, GlobalStyle, PageSchema, LayoutSchema } from "~/types/builder";
import type { SiteConfig, VariableValues } from "~/types/data-context";

/**
 * 获取主题渲染所需的数据和方法
 */
export function useThemeRender() {
  // 从页面上下文获取渲染配置
  const pageContext = usePageContext(["themeConfig", "siteConfig", "variableValues", "landingProductId"]);

  // 调试日志：打印 pageContext 信息
  if (import.meta.dev) {
    console.log("[useThemeRender] pageContext:", {
      hasThemeConfig: !!pageContext.value.themeConfig,
      hasSiteConfig: !!pageContext.value.siteConfig,
      hasVariableValues: !!pageContext.value.variableValues,
      landingProductId: pageContext.value.landingProductId,
      themeConfigKeys: pageContext.value.themeConfig
        ? Object.keys(pageContext.value.themeConfig)
        : [],
    });
  }

  // 获取产品信息（自动从 pageContext 获取 landingProductId、languageId、subDomainId）
  const { data: productInfo, pending: productPending } = useProductInfo();

  // 主题配置（直接从 pageContext 获取）
  const themeConfig = computed<ThemeSchema | null>(() => {
    const config = pageContext.value.themeConfig || null;
    // 调试日志：打印主题配置信息
    if (import.meta.dev && config) {
      console.log("[useThemeRender] themeConfig computed:", {
        pagesKeys: config.pages ? Object.keys(config.pages) : [],
        layoutsCount: config.pages?.layouts?.length || 0,
      });
    }
    return config;
  });

  // 站点配置值（直接从 pageContext 获取）
  const siteConfig = computed<SiteConfig>(() => {
    return pageContext.value.siteConfig || {};
  });

  // 变量实际值（直接从 pageContext 获取）
  const variableValues = computed<VariableValues>(() => {
    return pageContext.value.variableValues || {};
  });

  // 合并的数据上下文（供组件绑定使用）
  const dataContext = computed(() => {
    return {
      // 站点配置（以 site. 前缀访问）
      site: siteConfig.value,
      // 变量值（以 var. 前缀访问）
      var: variableValues.value,
      // 产品数据（以 product. 前缀访问，来自 API）
      product: productInfo.value
        ? {
            id: productInfo.value.id,
            spuId: productInfo.value.spuId,
            title: productInfo.value.title,
            merchandise: productInfo.value.merchandise,
            introduction: productInfo.value.introduction,
            summary: productInfo.value.summary,
            sellPrice: productInfo.value.sellPrice,
            originPrice: productInfo.value.originPrice,
            isMultiSpecs: productInfo.value.isMultiSpecs,
            images: productInfo.value.images,
            specifications: productInfo.value.specifications,
          }
        : null,
    };
  });

  // 全局样式（从 siteConfig.globalStyle 获取）
  const globalStyle = computed<GlobalStyle | undefined>(() => {
    const gs = siteConfig.value?.globalStyle;
    return gs ? (gs as GlobalStyle) : undefined;
  });

  // 生成全局样式 CSS 变量（变量命名与 LayoutRenderer/PageRenderer 保持一致）
  const globalStyleVars = computed(() => {
    const style = globalStyle.value;
    if (!style) return {};

    return {
      "--primary-color": style.primaryColor,
      "--secondary-color": style.secondaryColor,
      "--success-color": style.successColor,
      "--warning-color": style.warningColor,
      "--error-color": style.errorColor,
      "--background-color": style.backgroundColor,
      "--surface-color": style.surfaceColor,
      "--text-color": style.textColor,
      "--text-secondary-color": style.textSecondaryColor,
      "--border-color": style.borderColor,
      "--font-family": style.fontFamily,
      "--font-size-base": style.fontSizeBase,
      "--line-height": style.lineHeight,
      "--border-radius-small": style.borderRadiusSmall,
      "--border-radius-medium": style.borderRadiusMedium,
      "--border-radius-large": style.borderRadiusLarge,
      "--spacing-unit": style.spacingUnit,
    };
  });

  // 获取指定页面类型的页面配置
  function getPageSchema(pageType: "home" | "product" | "orderResult" | "article" | "checkout"): PageSchema | null {
    if (!themeConfig.value) return null;
    return themeConfig.value.pages[pageType] || null;
  }

  // 获取自定义页面配置
  function getCustomPageSchema(slug: string): PageSchema | null {
    if (!themeConfig.value) return null;
    return themeConfig.value.pages.custom.find((p) => p.slug === slug) || null;
  }

  // 获取布局配置
  function getLayout(layoutId: string | undefined): LayoutSchema | null {
    if (!themeConfig.value || !layoutId) return null;
    return themeConfig.value.layouts.find((l) => l.id === layoutId) || null;
  }

  // 获取页面使用的布局
  function getPageLayout(pageType: "home" | "product" | "orderResult" | "article" | "checkout"): LayoutSchema | null {
    const page = getPageSchema(pageType);
    if (!page) return null;
    return getLayout(page.layoutId);
  }

  // 获取默认布局
  const defaultLayout = computed<LayoutSchema | null>(() => {
    if (!themeConfig.value) return null;
    return themeConfig.value.layouts.find((l) => l.name === "default") || themeConfig.value.layouts[0] || null;
  });

  // 是否有主题配置
  const hasTheme = computed(() => !!themeConfig.value);

  // 设置浏览器标签页标题（格式：页面标题 - browserTabTitle）
  function useSiteTitle(pageTitle: MaybeRefOrGetter<string>) {
    const title = computed(() => {
      const suffix = siteConfig.value?.browserTabTitle;
      const t = toValue(pageTitle);
      return suffix ? `${t} - ${suffix}` : t;
    });
    useHead({ title });
  }

  return {
    // 数据
    themeConfig,
    globalStyle,
    globalStyleVars,
    defaultLayout,
    hasTheme,
    
    // 分离的配置数据
    siteConfig,
    variableValues,
    dataContext,
    
    // 产品信息
    productInfo,
    productPending,

    // 方法
    getPageSchema,
    getCustomPageSchema,
    getLayout,
    getPageLayout,
    useSiteTitle,
  };
}
