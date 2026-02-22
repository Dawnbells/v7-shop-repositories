/**
 * 主题 Schema 状态管理
 * 
 * 数据分离设计：
 * - variableSchemaState: 变量定义结构（variable_schema）
 * - siteConfigState: 站点配置值（site_config）
 * - variableValuesState: 变量实际值（variable_values）
 */

import type {
  GlobalStyle,
} from "~/types/builder";
import type {
  CustomVariable,
  SiteConfig,
  SiteConfigI18n,
  VariableValues,
  VariableValuesI18n,
} from "~/types/data-context";
import { createDefaultGlobalStyle } from "~/types/theme";
import { createDefaultSiteConfig } from "~/constants/site-config.schema";

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
  // 获取变量定义
  const variableSchema = computed(() => variableSchemaState.value);

  // 获取站点配置
  const siteConfig = computed(() => siteConfigState.value);
  const siteConfigI18n = computed(() => siteConfigI18nState.value);

  // 获取变量值
  const variableValues = computed(() => variableValuesState.value);
  const variableValuesI18n = computed(() => variableValuesI18nState.value);

  // 加载完整数据（包含独立字段）
  interface LoadFullDataParams {
    variableSchema?: CustomVariable[];
    siteConfig?: SiteConfig;
    siteConfigI18n?: SiteConfigI18n;
    variableValues?: VariableValues;
    variableValuesI18n?: VariableValuesI18n;
  }

  function loadFullData(data: LoadFullDataParams) {
    variableSchemaState.value = data.variableSchema || [];
    // 如果 siteConfig 为空或空对象，使用默认值
    const defaultSiteConfig = createDefaultSiteConfig();
    const loadedSiteConfig = data.siteConfig || {};
    siteConfigState.value = Object.keys(loadedSiteConfig).length > 0 
      ? loadedSiteConfig 
      : defaultSiteConfig;
    siteConfigI18nState.value = data.siteConfigI18n || {};
    variableValuesState.value = data.variableValues || {};
    variableValuesI18nState.value = data.variableValuesI18n || {};
    hasUnsavedChanges.value = false;
  }

  // 导出完整数据（用于保存）
  interface ExportFullDataResult {
    variableSchema: CustomVariable[];
    siteConfig: SiteConfig;
    siteConfigI18n: SiteConfigI18n;
    variableValues: VariableValues;
    variableValuesI18n: VariableValuesI18n;
  }

  function exportFullData(): ExportFullDataResult {
    return {
      variableSchema: variableSchemaState.value,
      siteConfig: siteConfigState.value,
      siteConfigI18n: siteConfigI18nState.value,
      variableValues: variableValuesState.value,
      variableValuesI18n: variableValuesI18nState.value,
    };
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

  // 标记为已保存
  function markAsSaved() {
    hasUnsavedChanges.value = false;
  }

  // 清除所有状态
  function clearAll() {
    variableSchemaState.value = [];
    siteConfigState.value = {};
    siteConfigI18nState.value = {};
    variableValuesState.value = {};
    variableValuesI18nState.value = {};
    hasUnsavedChanges.value = false;
  }

  return {
    // 状态
    hasUnsavedChanges: readonly(hasUnsavedChanges),
    isLoading: readonly(isLoading),

    // 独立状态
    variableSchema,
    siteConfig,
    siteConfigI18n,
    variableValues,
    variableValuesI18n,

    // 数据操作
    loadFullData,
    exportFullData,

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

    // 其他
    markAsSaved,
    clearAll,
  };
}
