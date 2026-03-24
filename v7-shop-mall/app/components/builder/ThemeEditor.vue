<script setup lang="ts">
/**
 * ThemeEditor - 主题编辑器主容器
 * 组合所有子组件，实现可拖拽面板布局
 */

import type { TabItem } from "./EditorTabs.vue";
import type { CustomVariable } from "~/types/data-context";
import { useIframeAuth } from "~/composables/useIframeAuth";
import { useThemeSchema } from "~/composables/useThemeSchema";
import { useCanvasState } from "~/composables/useCanvasState";
import { usePageTheme } from "~/composables/usePageTheme";
import { useBuilderPreview } from "~/composables/useBuilderPreview";
import {
  PRODUCT_INFO_MOCK,
  CURRENCY_MOCK,
  ARTICLE_INFO_MOCK,
  ORDER_RESULT_INFO_MOCK,
} from "~/constants/preset-datasets";

// 获取 iframe 认证信息
const {
  contextName,
  query,
  templateId,
  apiBaseUrl,
  isTemplateMode,
  isLandingMode,
  isReady,
  authFetch,
} = useIframeAuth();

// 主题状态管理
const {
  variableSchema,
  siteConfig: editorSiteConfig,
  loadFullData,
  exportFullData,
  addGlobalVariable,
  updateGlobalVariable,
  removeGlobalVariable,
  hasUnsavedChanges: themeHasUnsavedChanges,
  markAsSaved,
} = useThemeSchema();

// 页面主题状态（用于同步数据到组件）
const { siteConfig: pageSiteConfig } = usePageTheme();

// Builder 预览（注入 mock 数据）
const { setPreviewData } = useBuilderPreview();

// 在组件挂载时注入 mock 数据，确保在客户端正确执行
onMounted(() => {
  setPreviewData({
    productInfo: PRODUCT_INFO_MOCK,
    currency: CURRENCY_MOCK,
    articleInfo: ARTICLE_INFO_MOCK,
    orderResult: ORDER_RESULT_INFO_MOCK,
  });
});

// 同步编辑器数据到页面主题状态（使组件能读取全局配置）
watch(
  editorSiteConfig,
  (newConfig) => {
    pageSiteConfig.value = newConfig;
  },
  { deep: true, immediate: true },
);

// 画布状态管理
const {
  exportCanvasData,
  importCanvasData,
  currentPageId,
  currentPageType,
  allPagesInfo,
  availableLayouts,
  switchPage,
  initializePages,
  exportAllPagesData,
  createPage,
  createLayout,
  removePage,
  removeLayout,
  updatePageLayout,
  canvasHasUnsavedChanges,
  markCanvasSaved,
} = useCanvasState();

// 变量管理状态
const showVariableManager = ref(false);
const showVariableValueEditor = ref(false);

// 模板选择弹窗状态
const showTemplateModal = ref(false);

// 导入导出弹窗状态
const showExportModal = ref(false);
const showImportModal = ref(false);

// 兼容旧代码：customVariables 从 variableSchema 获取
const customVariables = computed(() => variableSchema.value);

// 数据加载状态
const isLoading = ref(false);
const loadError = ref<string | null>(null);

/** 解析后端返回的 JSON 字符串字段 */
function parseConfigJson<T>(raw: string | T | undefined | null, fallback: T): T {
  if (raw == null || raw === "") return fallback;
  if (typeof raw !== "string") return raw as T;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

// 从数据库加载落地页主题配置（站点 + SPU）
async function loadThemeFromServer() {
  if (!query.value?.subDomainId || !query.value?.spuId) {
    console.log("[ThemeEditor] 缺少必要参数，跳过加载");
    return;
  }

  isLoading.value = true;
  loadError.value = null;

  try {
    const params = new URLSearchParams({
      subDomainId: query.value.subDomainId,
      spuId: query.value.spuId,
      landingType: query.value.landingType || "LAND",
    });

    const response = await fetch(`/api/builder/load?${params}`);

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const result = await response.json();

    if (result.success && result.data) {
      // 使用 loadFullData 加载所有数据
      loadFullData({
        variableSchema: result.data.variableSchema || [],
        siteConfig: result.data.siteConfig || {},
        variableValues: result.data.variableValues || {},
      });

      // 加载画布数据（使用多页面初始化）
      const themeConfig = result.data.themeConfig;
      if (themeConfig) {
        // 使用 initializePages 初始化所有页面和布局
        initializePages({
          pages: themeConfig.pages || [],
          layouts: themeConfig.layouts || [],
        });
        console.log("[ThemeEditor] 多页面数据加载成功");
      } else {
        // 没有数据时创建默认页面
        initializePages({ pages: [], layouts: [] });
        console.log("[ThemeEditor] 创建默认页面结构");
      }

      console.log(
        "[ThemeEditor] 加载成功，变量数量:",
        variableSchema.value.length,
      );
    } else {
      // 没有数据时创建默认页面
      initializePages({ pages: [], layouts: [] });
      console.log("[ThemeEditor] 无数据，创建默认页面结构");
    }
  } catch (error: any) {
    console.error("[ThemeEditor] 加载主题配置失败:", error);
    loadError.value = error.message || "加载失败";
  } finally {
    isLoading.value = false;
  }
}

/**
 * 从管理后台加载主题模板（BUILDER_INIT 需为 mode=TEMPLATE、templateId、apiBaseUrl）
 * 对应接口：GET /theme-templates/{id}
 */
async function loadThemeTemplateFromServer() {
  const id = templateId.value;
  if (!id) {
    console.log("[ThemeEditor] 主题模板模式缺少 templateId，跳过加载");
    return;
  }
  if (!apiBaseUrl.value) {
    console.warn("[ThemeEditor] 主题模板模式缺少 apiBaseUrl，无法请求后台");
    loadError.value = "缺少 API 地址，无法加载主题模板";
    initializePages({ pages: [], layouts: [] });
    return;
  }

  isLoading.value = true;
  loadError.value = null;

  try {
    const data = await authFetch<{
      themeConfig?: string;
      variableSchema?: string;
      siteConfig?: string;
      variableValues?: string;
    }>(`/theme-templates/${id}`);

    if (!data) {
      initializePages({ pages: [], layouts: [] });
      return;
    }

    const variableSchema = parseConfigJson(data.variableSchema, []);
    const siteConfig = parseConfigJson(data.siteConfig, {});
    const variableValues = parseConfigJson(data.variableValues, {});

    loadFullData({
      variableSchema,
      siteConfig,
      variableValues,
    });

    const rawTc = data.themeConfig;
    const themeConfig =
      typeof rawTc === "string"
        ? parseConfigJson<Record<string, unknown> | null>(rawTc, null)
        : rawTc && typeof rawTc === "object"
          ? (rawTc as Record<string, unknown>)
          : null;

    if (
      themeConfig &&
      (Array.isArray(themeConfig.pages) || Array.isArray(themeConfig.layouts))
    ) {
      initializePages({
        pages: (themeConfig.pages as []) || [],
        layouts: (themeConfig.layouts as []) || [],
      });
      console.log("[ThemeEditor] 主题模板画布加载成功");
    } else {
      initializePages({ pages: [], layouts: [] });
      console.log("[ThemeEditor] 主题模板无画布数据，创建默认结构");
    }
  } catch (error: any) {
    console.error("[ThemeEditor] 加载主题模板失败:", error);
    loadError.value = error.message || "加载失败";
    initializePages({ pages: [], layouts: [] });
  } finally {
    isLoading.value = false;
  }
}

// 监听认证状态，准备好后按模式加载数据
watch(
  isReady,
  (ready) => {
    if (!ready) return;
    if (isLandingMode.value) {
      loadThemeFromServer();
    } else if (isTemplateMode.value) {
      loadThemeTemplateFromServer();
    }
  },
  { immediate: true },
);

// 变量管理操作
function handleOpenVariables() {
  showVariableManager.value = true;
}

// 打开变量值设置弹窗
function handleOpenVariableValues() {
  showVariableValueEditor.value = true;
}

// 打开模板选择弹窗
function handleOpenTemplates() {
  showTemplateModal.value = true;
}

// 应用模板数据
function handleApplyTemplate(data: {
  themeConfig: any;
  variableSchema: any[];
  siteConfig: any;
  variableValues: any;
}) {
  // 加载变量和站点配置
  loadFullData({
    variableSchema: data.variableSchema,
    siteConfig: data.siteConfig,
    variableValues: data.variableValues,
  });

  // 加载画布数据
  if (data.themeConfig?.pages || data.themeConfig?.layouts) {
    initializePages({
      pages: data.themeConfig.pages || [],
      layouts: data.themeConfig.layouts || [],
    });
  }

  showTemplateModal.value = false;
  console.log("[ThemeEditor] 模板应用成功");
}

function handleDeleteVariable(key: string) {
  removeGlobalVariable(key);
}

function handleSaveVariable(variable: CustomVariable) {
  const existingIndex = variableSchema.value.findIndex(
    (v) => v.key === variable.key,
  );
  if (existingIndex >= 0) {
    updateGlobalVariable(variable.key, variable);
  } else {
    addGlobalVariable(variable);
  }
}

// 落地页类型标签映射
const landingTypeLabels: Record<string, string> = {
  LAND: "落地页",
  CLOAK: "风险页",
  BLACKLISTED: "黑名单页",
};

// 动态计算主题名称
const themeName = computed(() => {
  if (isTemplateMode.value) {
    return contextName.value || "主题模板";
  }
  return "主题编辑器";
});

// 动态计算上下文信息（模板：名称 + ID；落地页：类型 · 域名 · SPU）
const contextInfo = computed(() => {
  // 主题模板模式：模板名称 · ID
  if (isTemplateMode.value) {
    const parts: string[] = [];
    if (contextName.value) parts.push(contextName.value);
    if (templateId.value) parts.push(`ID: ${templateId.value}`);
    return parts.length ? parts.join(" · ") : undefined;
  }

  // 落地页模式：落地页类型 - 域名 · SPU 名称 · SPU ID
  if (isLandingMode.value && query.value) {
    const q = query.value;
    const parts: string[] = [];

    const typeLabel = landingTypeLabels[q.landingType || "LAND"] || "落地页";
    parts.push(typeLabel);

    if (q.subDomainName) {
      parts.push(q.subDomainName);
    }

    if (q.spuName) {
      parts.push(q.spuName);
    }

    if (q.spuId) {
      parts.push(q.spuId);
    }

    if (parts.length > 1) {
      return parts.join(" · ");
    }
  }

  return undefined;
});

// 面板宽度配置
const LEFT_MIN = 220;
const LEFT_MAX = 520;
const RIGHT_MIN = 260;
const RIGHT_MAX = 620;
const CENTER_MIN = 360;

const leftPanelWidth = ref(280);
const rightPanelWidth = ref(320);

// 从 localStorage 恢复面板宽度
onMounted(() => {
  try {
    const savedLeft = localStorage.getItem("builder:leftPanelWidth");
    const savedRight = localStorage.getItem("builder:rightPanelWidth");
    if (savedLeft) leftPanelWidth.value = Number(savedLeft);
    if (savedRight) rightPanelWidth.value = Number(savedRight);
  } catch {
    // ignore
  }
});

// 保存面板宽度到 localStorage
function savePanelWidths() {
  try {
    localStorage.setItem(
      "builder:leftPanelWidth",
      String(leftPanelWidth.value),
    );
    localStorage.setItem(
      "builder:rightPanelWidth",
      String(rightPanelWidth.value),
    );
  } catch {
    // ignore
  }
}

// 限制数值范围
function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

// 拖拽调整面板宽度
function startResize(side: "left" | "right", event: PointerEvent) {
  const target = event.currentTarget as HTMLElement;
  const mainEl = target.closest(".editor-main") as HTMLElement;
  if (!mainEl) return;

  const rect = mainEl.getBoundingClientRect();
  const startX = event.clientX;
  const startLeft = leftPanelWidth.value;
  const startRight = rightPanelWidth.value;

  target.setPointerCapture(event.pointerId);
  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";

  const onMove = (e: PointerEvent) => {
    const dx = e.clientX - startX;
    const totalWidth = rect.width;

    if (side === "left") {
      const newLeft = clamp(startLeft + dx, LEFT_MIN, LEFT_MAX);
      const maxByCenter = totalWidth - rightPanelWidth.value - CENTER_MIN;
      leftPanelWidth.value = clamp(
        newLeft,
        LEFT_MIN,
        Math.min(LEFT_MAX, maxByCenter),
      );
    } else {
      const newRight = clamp(startRight - dx, RIGHT_MIN, RIGHT_MAX);
      const maxByCenter = totalWidth - leftPanelWidth.value - CENTER_MIN;
      rightPanelWidth.value = clamp(
        newRight,
        RIGHT_MIN,
        Math.min(RIGHT_MAX, maxByCenter),
      );
    }
  };

  const onUp = () => {
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    savePanelWidths();
  };

  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", onUp);
}

// 编辑状态（合并主题配置和画布状态的未保存状态）
const hasUnsavedChanges = computed(
  () => themeHasUnsavedChanges.value || canvasHasUnsavedChanges.value,
);
const isSaving = ref(false);

// 从 useCanvasState 获取当前页面 ID
const currentTabKey = computed(() => currentPageId.value);

// 从 useCanvasState 获取 TAB 列表
const tabs = computed<TabItem[]>(() => {
  return allPagesInfo.value.map((info) => ({
    key: info.id,
    label: info.name,
    type: info.type,
    removable: info.removable,
  }));
});

// 检查是否已存在收银台页面
const hasCheckout = computed(() =>
  allPagesInfo.value.some((p) => p.pageType === "checkout"),
);

// 获取当前页面的业务类型（用于加载预设数据）
const currentPageBusinessType = computed<string>(() => {
  const pageInfo = allPagesInfo.value.find((p) => p.id === currentPageId.value);
  return pageInfo?.pageType || "home";
});

// 获取当前页面的预设数据集 ID 列表（用于自定义页面）
const currentPagePresetIds = computed<string[]>(() => {
  const pageInfo = allPagesInfo.value.find((p) => p.id === currentPageId.value);
  return pageInfo?.presetIds || [];
});

// 添加页面弹窗状态
const showAddDialog = ref(false);
const addDialogType = ref<"custom" | "layout">("custom");

// 页面设置弹窗状态
const showPageSettings = ref(false);
const settingsPageId = ref("");
const settingsPageName = ref("");
const settingsCurrentLayoutId = ref<string | undefined>(undefined);

// 事件处理
function handleClose() {
  if (hasUnsavedChanges.value) {
    if (!confirm("有未保存的更改，确定要关闭吗？")) return;
  }
  window.parent.postMessage({ type: "themeEditor", action: "close" }, "*");
}

/** 保存主题模板到管理后台（POST /theme-templates/updateConfig） */
async function handleSaveThemeTemplate() {
  const id = templateId.value;
  if (!id) {
    alert("缺少模板 ID，无法保存");
    return;
  }
  if (!apiBaseUrl.value) {
    alert("缺少 API 地址，无法保存主题模板");
    return;
  }

  isSaving.value = true;

  try {
    const fullData = exportFullData();
    const { pages, layouts } = exportAllPagesData();
    const now = new Date().toISOString();

    const themeConfig = {
      id: `theme_template_${id}`,
      name: contextName.value || "主题模板",
      version: "1.0",
      layouts,
      pages,
      updatedAt: now,
    };

    await authFetch("/theme-templates/updateConfig", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: Number(id),
        themeConfig: JSON.stringify(themeConfig),
        variableSchema: JSON.stringify(fullData.variableSchema ?? []),
        siteConfig: JSON.stringify(fullData.siteConfig ?? {}),
        variableValues: JSON.stringify(fullData.variableValues ?? {}),
      }),
    });

    markAsSaved();
    markCanvasSaved();
    console.log("[ThemeEditor] 主题模板保存成功");
  } catch (error: any) {
    console.error("[ThemeEditor] 主题模板保存失败:", error);
    alert("保存失败: " + (error.message || "未知错误"));
  } finally {
    isSaving.value = false;
  }
}

async function handleSave() {
  if (isTemplateMode.value) {
    await handleSaveThemeTemplate();
    return;
  }

  if (!query.value?.subDomainId || !query.value?.spuId) {
    alert("缺少必要参数，无法保存");
    return;
  }

  isSaving.value = true;

  try {
    // 使用 exportFullData 导出所有数据
    const fullData = exportFullData();

    // 导出所有页面和布局数据
    const { pages, layouts } = exportAllPagesData();
    const now = new Date().toISOString();

    // 构建符合 ThemeConfig 类型的数据结构
    const themeConfig = {
      id: `theme_${query.value.spuId}`,
      name: "落地页主题",
      version: "1.0",
      layouts,
      pages,
      updatedAt: now,
    };

    const response = await fetch("/api/builder/save", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        subDomainId: query.value.subDomainId,
        spuId: query.value.spuId,
        landingType: query.value.landingType || "LAND",
        themeConfig,
        variableSchema: fullData.variableSchema,
        siteConfig: fullData.siteConfig,
        variableValues: fullData.variableValues,
      }),
    });

    const result = await response.json();

    if (result.success) {
      markAsSaved();
      markCanvasSaved();
      console.log("[ThemeEditor] 保存成功");
    } else {
      throw new Error(result.message || "保存失败");
    }
  } catch (error: any) {
    console.error("[ThemeEditor] 保存失败:", error);
    alert("保存失败: " + (error.message || "未知错误"));
  } finally {
    isSaving.value = false;
  }
}

function handleSwitchTab(key: string) {
  // 根据 key 查找页面类型
  const pageInfo = allPagesInfo.value.find((p) => p.id === key);
  if (pageInfo) {
    switchPage(key, pageInfo.type);
  }
}

function handleRemoveTab(key: string) {
  const pageInfo = allPagesInfo.value.find((p) => p.id === key);
  if (!pageInfo) return;

  if (!confirm(`确定要删除"${pageInfo.name}"吗？`)) return;

  if (pageInfo.type === "layout") {
    removeLayout(key);
  } else {
    removePage(key);
  }
}

function handleAddPage(type: "checkout" | "custom" | "layout") {
  if (type === "checkout") {
    // 收银台直接添加
    const pageId = `checkout_${Date.now()}`;
    createPage(pageId, "收银台", "checkout");
    switchPage(pageId, "page");
  } else {
    // 自定义页面和布局需要弹窗输入信息
    addDialogType.value = type;
    showAddDialog.value = true;
  }
}

// 弹窗确认回调
function handleDialogConfirm(data: {
  name: string;
  path?: string;
  description?: string;
  layoutId?: string;
  presetIds?: string[];
}) {
  const now = Date.now();

  if (addDialogType.value === "layout") {
    const layoutId = `layout_${now}`;
    createLayout(layoutId, data.name, data.description);
    switchPage(layoutId, "layout");
  } else {
    const pageId = `custom_${now}`;
    createPage(pageId, data.name, "custom", data.layoutId, data.presetIds);
    switchPage(pageId, "page");
  }

  showAddDialog.value = false;
}

// 页面设置图标点击
function handlePageSettings(key: string) {
  const pageInfo = allPagesInfo.value.find((p) => p.id === key);
  if (!pageInfo || pageInfo.type !== "page") return;

  settingsPageId.value = key;
  settingsPageName.value = pageInfo.name;
  settingsCurrentLayoutId.value = pageInfo.layoutId;
  showPageSettings.value = true;
}

// 页面设置确认回调
function handlePageSettingsConfirm(layoutId: string | undefined) {
  if (settingsPageId.value) {
    updatePageLayout(settingsPageId.value, layoutId);
    console.log(
      `[ThemeEditor] 更新页面 ${settingsPageId.value} 的布局为: ${layoutId || "无"}`,
    );
  }
  showPageSettings.value = false;
}

// 导出相关
function handleOpenExport() {
  showExportModal.value = true;
}

// 获取导出数据
const exportThemeData = computed(() => {
  const fullData = exportFullData();
  const { pages, layouts } = exportAllPagesData();
  return {
    pages,
    layouts,
    siteConfig: fullData.siteConfig,
    variableSchema: fullData.variableSchema,
    variableValues: fullData.variableValues,
  };
});

// 导入相关
function handleOpenImport() {
  showImportModal.value = true;
}

// 处理导入数据
import type { ThemeImportData } from "~/utils/theme-export";

function handleImportData(importData: ThemeImportData) {
  const { includes, data } = importData;

  // 导入站点配置、变量定义、变量值
  if (includes.siteConfig || includes.variableSchema || includes.variableValues) {
    loadFullData({
      variableSchema: includes.variableSchema ? data.variableSchema : undefined,
      siteConfig: includes.siteConfig ? data.siteConfig : undefined,
      variableValues: includes.variableValues ? data.variableValues : undefined,
    });
  }

  // 导入页面和布局
  if (includes.pages || includes.layouts) {
    const themeConfig = {
      pages: includes.pages ? data.pages : [],
      layouts: includes.layouts ? data.layouts : [],
    };
    initializePages(themeConfig);
  }

  console.log("[ThemeEditor] 导入完成", { includes });
}
</script>

<template>
  <div class="theme-editor">
    <!-- 顶部工具栏 -->
    <BuilderEditorHeader
      :theme-name="themeName"
      :context-info="contextInfo"
      :has-unsaved-changes="hasUnsavedChanges"
      :is-saving="isSaving"
      @close="handleClose"
      @save="handleSave"
      @open-templates="handleOpenTemplates"
      @open-variables="handleOpenVariables"
      @open-variable-values="handleOpenVariableValues"
      @open-export="handleOpenExport"
      @open-import="handleOpenImport"
    />

    <!-- 页面 Tab 栏 -->
    <BuilderEditorTabs
      :tabs="tabs"
      :active-key="currentTabKey"
      :has-checkout="hasCheckout"
      @switch="handleSwitchTab"
      @remove="handleRemoveTab"
      @add="handleAddPage"
      @settings="handlePageSettings"
    />

    <!-- 编辑器主体 -->
    <main class="editor-main">
      <!-- 左侧组件面板 -->
      <aside class="panel-left" :style="{ width: `${leftPanelWidth}px` }">
        <BuilderComponentPanel :page-type="currentPageBusinessType" />
      </aside>

      <!-- 左侧分隔条 -->
      <div
        class="panel-resizer"
        @pointerdown.prevent="startResize('left', $event)"
      >
        <div class="resizer-line"></div>
      </div>

      <!-- 中间画布 -->
      <section class="panel-center">
        <BuilderCanvas />
      </section>

      <!-- 右侧分隔条 -->
      <div
        class="panel-resizer"
        @pointerdown.prevent="startResize('right', $event)"
      >
        <div class="resizer-line"></div>
      </div>

      <!-- 右侧属性面板 -->
      <aside class="panel-right" :style="{ width: `${rightPanelWidth}px` }">
        <BuilderPropertyPanel
          :page-type="currentPageBusinessType"
          :preset-ids="currentPagePresetIds"
        />
      </aside>
    </main>

    <!-- 变量管理弹窗 -->
    <BuilderVariableManager
      :visible="showVariableManager"
      :variables="customVariables"
      @close="showVariableManager = false"
      @save="handleSaveVariable"
      @delete="handleDeleteVariable"
    />

    <!-- 变量值设置弹窗 -->
    <BuilderVariableValueEditor
      :visible="showVariableValueEditor"
      @close="showVariableValueEditor = false"
    />

    <!-- 添加页面/布局弹窗 -->
    <BuilderPageAddDialog
      :visible="showAddDialog"
      :type="addDialogType"
      :layouts="availableLayouts"
      @close="showAddDialog = false"
      @confirm="handleDialogConfirm"
    />

    <!-- 页面设置弹窗 -->
    <BuilderPageSettingsDialog
      :visible="showPageSettings"
      :page-name="settingsPageName"
      :current-layout-id="settingsCurrentLayoutId"
      :layouts="availableLayouts"
      @close="showPageSettings = false"
      @confirm="handlePageSettingsConfirm"
    />

    <!-- 模板选择弹窗 -->
    <BuilderTemplateSelectModal
      :visible="showTemplateModal"
      @close="showTemplateModal = false"
      @apply="handleApplyTemplate"
    />

    <!-- 导出弹窗 -->
    <BuilderExportModal
      :visible="showExportModal"
      :theme-data="exportThemeData"
      @close="showExportModal = false"
      @exported="() => console.log('[ThemeEditor] 导出完成')"
    />

    <!-- 导入弹窗 -->
    <BuilderImportModal
      :visible="showImportModal"
      @close="showImportModal = false"
      @import="handleImportData"
    />
  </div>
</template>

<style scoped>
.theme-editor {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  background: #0f172a;
  color: #e2e8f0;
  overflow: hidden;
}

/* 编辑器主体 */
.editor-main {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 左侧面板 */
.panel-left {
  flex-shrink: 0;
  height: 100%;
  overflow: hidden;
  background: #1e293b;
  border-right: 1px solid rgba(71, 85, 105, 0.3);
}

/* 中间画布 */
.panel-center {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}

/* 右侧面板 */
.panel-right {
  flex-shrink: 0;
  height: 100%;
  overflow: hidden;
  background: #1e293b;
  border-left: 1px solid rgba(71, 85, 105, 0.3);
}

/* 分隔条 */
.panel-resizer {
  position: relative;
  width: 8px;
  flex-shrink: 0;
  cursor: col-resize;
  z-index: 10;
}

.panel-resizer:hover .resizer-line,
.panel-resizer:active .resizer-line {
  background: #3b82f6;
  opacity: 1;
}

.resizer-line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 3px;
  width: 2px;
  background: rgba(71, 85, 105, 0.5);
  border-radius: 1px;
  opacity: 0.5;
  transition: all 0.15s ease;
}
</style>
