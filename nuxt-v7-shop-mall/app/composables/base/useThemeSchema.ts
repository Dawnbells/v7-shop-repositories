/**
 * 主题 Schema 状态管理
 * 
 * 数据分离设计：
 * - themeState: 页面布局、组件、样式（theme_config）
 * - variableSchemaState: 变量定义结构（variable_schema）
 * - siteConfigState: 站点配置值（site_config）
 * - variableValuesState: 变量实际值（variable_values）
 */

import { nanoid } from "nanoid";
import type {
  ThemeSchema,
  PageSchema,
  CustomPageSchema,
  LayoutSchema,
  GlobalStyle,
  CustomVariable,
  I18nValues,
  SiteConfig,
  SiteConfigI18n,
  VariableValues,
  VariableValuesI18n,
} from "~/types/builder";
import { createEmptyTheme, createDefaultGlobalStyle } from "~/types/theme";
import { createDefaultSiteConfig } from "~/constants/site-config.schema";

// 主题状态（页面布局、组件、样式）
const themeState = ref<ThemeSchema | null>(null);

// 变量定义 Schema（独立存储，仅编辑器需要）
const variableSchemaState = ref<CustomVariable[]>([]);

// 站点配置值
const siteConfigState = ref<SiteConfig>({});
const siteConfigI18nState = ref<SiteConfigI18n>({});

// 变量实际值
const variableValuesState = ref<VariableValues>({});
const variableValuesI18nState = ref<VariableValuesI18n>({});

// 是否有未保存的更改
const hasUnsavedChanges = ref(false);

// 加载状态
const isLoading = ref(false);

export function useThemeSchema() {
  // 获取当前主题
  const theme = computed(() => themeState.value);

  // 获取变量定义
  const variableSchema = computed(() => variableSchemaState.value);

  // 获取站点配置
  const siteConfig = computed(() => siteConfigState.value);
  const siteConfigI18n = computed(() => siteConfigI18nState.value);

  // 获取变量值
  const variableValues = computed(() => variableValuesState.value);
  const variableValuesI18n = computed(() => variableValuesI18nState.value);

  // 初始化新主题
  function initTheme(name: string = "新主题"): ThemeSchema {
    const id = nanoid();
    const newTheme = createEmptyTheme(id, name);
    themeState.value = newTheme;
    // 初始化独立状态
    variableSchemaState.value = [];
    siteConfigState.value = createDefaultSiteConfig();
    siteConfigI18nState.value = {};
    variableValuesState.value = {};
    variableValuesI18nState.value = {};
    hasUnsavedChanges.value = true;
    return newTheme;
  }

  // 加载主题
  function loadTheme(schema: ThemeSchema) {
    // 确保 layouts 字段存在（兼容旧数据）
    if (!Array.isArray(schema.layouts)) {
      schema.layouts = [];
    }
    themeState.value = schema;
    variableSchemaState.value = [];
    hasUnsavedChanges.value = false;
  }

  // 加载完整数据（包含独立字段）
  interface LoadFullDataParams {
    themeConfig: ThemeSchema;
    variableSchema?: CustomVariable[];
    siteConfig?: SiteConfig;
    siteConfigI18n?: SiteConfigI18n;
    variableValues?: VariableValues;
    variableValuesI18n?: VariableValuesI18n;
  }

  function loadFullData(data: LoadFullDataParams) {
    // 确保 layouts 字段存在（兼容旧数据）
    if (!Array.isArray(data.themeConfig.layouts)) {
      data.themeConfig.layouts = [];
    }
    themeState.value = data.themeConfig;
    variableSchemaState.value = data.variableSchema || [];
    siteConfigState.value = data.siteConfig || createDefaultSiteConfig();
    siteConfigI18nState.value = data.siteConfigI18n || {};
    variableValuesState.value = data.variableValues || {};
    variableValuesI18nState.value = data.variableValuesI18n || {};
    hasUnsavedChanges.value = false;
  }

  // 导出完整数据（用于保存）
  interface ExportFullDataResult {
    themeConfig: ThemeSchema | null;
    variableSchema: CustomVariable[];
    siteConfig: SiteConfig;
    siteConfigI18n: SiteConfigI18n;
    variableValues: VariableValues;
    variableValuesI18n: VariableValuesI18n;
  }

  function exportFullData(): ExportFullDataResult {
    return {
      themeConfig: themeState.value,
      variableSchema: variableSchemaState.value,
      siteConfig: siteConfigState.value,
      siteConfigI18n: siteConfigI18nState.value,
      variableValues: variableValuesState.value,
      variableValuesI18n: variableValuesI18nState.value,
    };
  }

  // 更新主题基本信息
  function updateThemeInfo(
    updates: Partial<Pick<ThemeSchema, "name" | "description" | "version">>
  ) {
    if (!themeState.value) return;

    Object.assign(themeState.value, updates);
    themeState.value.updatedAt = new Date().toISOString();
    hasUnsavedChanges.value = true;
  }

  // 更新全局样式（存储在 siteConfig.globalStyle 中）
  function updateGlobalStyle(updates: Partial<GlobalStyle>) {
    if (!siteConfigState.value.globalStyle) {
      siteConfigState.value.globalStyle = {};
    }
    Object.assign(siteConfigState.value.globalStyle, updates);
    hasUnsavedChanges.value = true;
  }

  // 重置全局样式为默认值
  function resetGlobalStyle() {
    siteConfigState.value.globalStyle = createDefaultGlobalStyle();
    hasUnsavedChanges.value = true;
  }

  // ============ 全局变量 Schema 操作 ============

  // 添加全局变量定义
  function addGlobalVariable(variable: CustomVariable): boolean {
    // 检查 key 是否已存在
    if (variableSchemaState.value.some((v) => v.key === variable.key)) {
      console.warn(`Global variable with key "${variable.key}" already exists`);
      return false;
    }

    variableSchemaState.value.push(variable);
    hasUnsavedChanges.value = true;
    return true;
  }

  // 更新全局变量定义
  function updateGlobalVariable(key: string, updates: Partial<CustomVariable>): boolean {
    const index = variableSchemaState.value.findIndex((v) => v.key === key);
    if (index === -1) return false;

    const existingVar = variableSchemaState.value[index];
    if (existingVar) {
      Object.assign(existingVar, updates);
    }
    hasUnsavedChanges.value = true;
    return true;
  }

  // 删除全局变量定义
  function removeGlobalVariable(key: string) {
    const index = variableSchemaState.value.findIndex((v) => v.key === key);
    if (index !== -1) {
      variableSchemaState.value.splice(index, 1);
      // 同时删除对应的变量值
      delete variableValuesState.value[key];
      // 删除多语言值
      for (const langId in variableValuesI18nState.value) {
        const langValues = variableValuesI18nState.value[langId];
        if (langValues) {
          delete langValues[key];
        }
      }
      hasUnsavedChanges.value = true;
    }
  }

  // ============ 站点配置操作 ============

  // 更新站点配置值
  function updateSiteConfig(key: string, value: any) {
    siteConfigState.value[key] = value;
    hasUnsavedChanges.value = true;
  }

  // 批量更新站点配置
  function updateSiteConfigBatch(updates: SiteConfig) {
    Object.assign(siteConfigState.value, updates);
    hasUnsavedChanges.value = true;
  }

  // 更新站点配置多语言值
  function updateSiteConfigI18n(languageId: number, key: string, value: any) {
    if (!siteConfigI18nState.value[languageId]) {
      siteConfigI18nState.value[languageId] = {};
    }
    siteConfigI18nState.value[languageId][key] = value;
    hasUnsavedChanges.value = true;
  }

  // ============ 变量值操作 ============

  // 更新变量值
  function updateVariableValue(key: string, value: any) {
    variableValuesState.value[key] = value;
    hasUnsavedChanges.value = true;
  }

  // 批量更新变量值
  function updateVariableValuesBatch(updates: VariableValues) {
    Object.assign(variableValuesState.value, updates);
    hasUnsavedChanges.value = true;
  }

  // 更新变量多语言值
  function updateVariableValueI18n(languageId: number, key: string, value: any) {
    if (!variableValuesI18nState.value[languageId]) {
      variableValuesI18nState.value[languageId] = {};
    }
    variableValuesI18nState.value[languageId][key] = value;
    hasUnsavedChanges.value = true;
  }

  // 获取变量的实际值（考虑多语言）
  function getVariableValue(key: string, languageId?: number): any {
    // 如果指定了语言且有多语言值，返回多语言值
    if (languageId && variableValuesI18nState.value[languageId]?.[key] !== undefined) {
      return variableValuesI18nState.value[languageId][key];
    }
    // 否则返回默认值
    return variableValuesState.value[key];
  }

  // 获取站点配置的实际值（考虑多语言）
  function getSiteConfigValue(key: string, languageId?: number): any {
    // 如果指定了语言且有多语言值，返回多语言值
    if (languageId && siteConfigI18nState.value[languageId]?.[key] !== undefined) {
      return siteConfigI18nState.value[languageId][key];
    }
    // 否则返回默认值
    return siteConfigState.value[key];
  }

  // 获取页面 Schema
  function getPageSchema(
    pageKey: keyof ThemeSchema["pages"]
  ): PageSchema | CustomPageSchema[] | LayoutSchema[] | undefined {
    if (!themeState.value) return undefined;
    return themeState.value.pages[pageKey];
  }

  // 更新页面 Schema
  function updatePageSchema(
    pageKey: "home" | "product" | "orderResult" | "article" | "checkout",
    updates: Partial<PageSchema>
  ) {
    if (!themeState.value) return;

    const page = themeState.value.pages[pageKey];
    if (page) {
      Object.assign(page, updates);
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;
    }
  }

  // 添加自定义页面
  function addCustomPage(slug: string, name: string): CustomPageSchema {
    if (!themeState.value) {
      throw new Error("Theme not initialized");
    }

    const newPage: CustomPageSchema = {
      id: nanoid(),
      name,
      slug,
      pageType: "custom",
      components: [],
      meta: { title: name },
    };

    themeState.value.pages.custom.push(newPage);
    themeState.value.updatedAt = new Date().toISOString();
    hasUnsavedChanges.value = true;

    return newPage;
  }

  // 更新自定义页面
  function updateCustomPage(
    pageId: string,
    updates: Partial<CustomPageSchema>
  ) {
    if (!themeState.value) return;

    const page = themeState.value.pages.custom.find((p) => p.id === pageId);
    if (page) {
      Object.assign(page, updates);
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;
    }
  }

  // 删除自定义页面
  function removeCustomPage(pageId: string) {
    if (!themeState.value) return;

    const index = themeState.value.pages.custom.findIndex(
      (p) => p.id === pageId
    );
    if (index !== -1) {
      themeState.value.pages.custom.splice(index, 1);
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;
    }
  }

  // 启用收银台页面
  function enableCheckoutPage() {
    if (!themeState.value) return;

    if (!themeState.value.pages.checkout) {
      themeState.value.pages.checkout = {
        id: `${themeState.value.id}-checkout`,
        name: "收银台",
        pageType: "checkout",
        components: [],
        meta: { title: "收银台" },
      };
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;
    }
  }

  // 禁用收银台页面
  function disableCheckoutPage() {
    if (!themeState.value) return;

    themeState.value.pages.checkout = undefined;
    themeState.value.updatedAt = new Date().toISOString();
    hasUnsavedChanges.value = true;
  }

  // ============ 布局管理 ============

  // 获取所有布局
  const layouts = computed<LayoutSchema[]>(() => {
    return themeState.value?.layouts || [];
  });

  // 获取布局
  function getLayout(layoutId: string): LayoutSchema | undefined {
    return themeState.value?.layouts.find((l) => l.id === layoutId);
  }

  // 添加布局（空布局，需要手动拖拽添加 Page Slot）
  function addLayout(name: string): LayoutSchema {
    if (!themeState.value) {
      throw new Error("Theme not initialized");
    }

    const layoutId = nanoid();
    const newLayout: LayoutSchema = {
      id: layoutId,
      name,
      components: [],
    };

    themeState.value.layouts.push(newLayout);
    themeState.value.updatedAt = new Date().toISOString();
    hasUnsavedChanges.value = true;

    return newLayout;
  }

  // 更新布局
  function updateLayout(layoutId: string, updates: Partial<LayoutSchema>) {
    if (!themeState.value) return;

    const layout = themeState.value.layouts.find((l) => l.id === layoutId);
    if (layout) {
      Object.assign(layout, updates);
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;
    }
  }

  // 删除布局
  function removeLayout(layoutId: string) {
    if (!themeState.value) return;

    const index = themeState.value.layouts.findIndex((l) => l.id === layoutId);
    if (index !== -1) {
      themeState.value.layouts.splice(index, 1);
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;

      // 清除所有使用该布局的页面的 layoutId
      clearLayoutFromPages(layoutId);
    }
  }

  // 清除页面中对某布局的引用
  function clearLayoutFromPages(layoutId: string) {
    if (!themeState.value) return;

    // 清除必选页面的布局引用
    const pageKeys = ["home", "product", "orderResult", "article", "checkout"] as const;
    for (const key of pageKeys) {
      const page = themeState.value.pages[key];
      if (page && page.layoutId === layoutId) {
        page.layoutId = undefined;
      }
    }

    // 清除自定义页面的布局引用
    for (const customPage of themeState.value.pages.custom) {
      if (customPage.layoutId === layoutId) {
        customPage.layoutId = undefined;
      }
    }
  }

  // 设置页面使用的布局
  function setPageLayout(
    pageKey: "home" | "product" | "orderResult" | "article" | "checkout",
    layoutId: string | undefined
  ) {
    if (!themeState.value) return;

    const page = themeState.value.pages[pageKey];
    if (page) {
      page.layoutId = layoutId;
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;
    }
  }

  // 设置自定义页面使用的布局
  function setCustomPageLayout(pageId: string, layoutId: string | undefined) {
    if (!themeState.value) return;

    const page = themeState.value.pages.custom.find((p) => p.id === pageId);
    if (page) {
      page.layoutId = layoutId;
      themeState.value.updatedAt = new Date().toISOString();
      hasUnsavedChanges.value = true;
    }
  }

  // 导出主题 JSON
  function exportTheme(): string {
    if (!themeState.value) return "{}";
    return JSON.stringify(themeState.value, null, 2);
  }

  // 导入主题 JSON
  function importTheme(json: string): boolean {
    try {
      const schema = JSON.parse(json) as ThemeSchema;
      loadTheme(schema);
      return true;
    } catch {
      return false;
    }
  }

  // 标记为已保存
  function markAsSaved() {
    hasUnsavedChanges.value = false;
  }

  // 清除主题（清除所有状态）
  function clearTheme() {
    themeState.value = null;
    variableSchemaState.value = [];
    siteConfigState.value = {};
    siteConfigI18nState.value = {};
    variableValuesState.value = {};
    variableValuesI18nState.value = {};
    hasUnsavedChanges.value = false;
  }

  // ============ i18n 值管理 ============

  // 更新 i18n 值
  function updateI18nValues(i18nValues: I18nValues) {
    if (!themeState.value) return;

    themeState.value.i18nValues = i18nValues;
    themeState.value.updatedAt = new Date().toISOString();
    hasUnsavedChanges.value = true;
  }

  return {
    // 状态
    theme,
    hasUnsavedChanges: readonly(hasUnsavedChanges),
    isLoading: readonly(isLoading),

    // 独立状态
    variableSchema,
    siteConfig,
    siteConfigI18n,
    variableValues,
    variableValuesI18n,

    // 主题操作
    initTheme,
    loadTheme,
    loadFullData,
    exportFullData,
    updateThemeInfo,
    clearTheme,

    // 全局样式（存储在 siteConfig.globalStyle）
    updateGlobalStyle,
    resetGlobalStyle,

    // 变量 Schema 操作
    addGlobalVariable,
    updateGlobalVariable,
    removeGlobalVariable,

    // 站点配置操作
    updateSiteConfig,
    updateSiteConfigBatch,
    updateSiteConfigI18n,
    getSiteConfigValue,

    // 变量值操作
    updateVariableValue,
    updateVariableValuesBatch,
    updateVariableValueI18n,
    getVariableValue,

    // 页面操作
    getPageSchema,
    updatePageSchema,

    // 自定义页面
    addCustomPage,
    updateCustomPage,
    removeCustomPage,

    // 收银台
    enableCheckoutPage,
    disableCheckoutPage,

    // 布局管理
    layouts,
    getLayout,
    addLayout,
    updateLayout,
    removeLayout,
    setPageLayout,
    setCustomPageLayout,

    // 导入导出
    exportTheme,
    importTheme,
    markAsSaved,

    // i18n 值管理
    updateI18nValues,
  };
}
