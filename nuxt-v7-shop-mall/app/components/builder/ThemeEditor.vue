<script setup lang="ts">
/**
 * 主题编辑器主容器
 */

import { PAGE_TYPE_LABELS, REQUIRED_PAGE_TYPES } from "~/types/theme";
import {
  provideEditorDataContext,
  generatePageContextFields,
  generateVariableFields,
} from "~/composables/useDataContext";
import { useIframeAuth } from "~/composables/useIframeAuth";

// 获取 iframe 认证信息
const { query: iframeQuery } = useIframeAuth();

// 落地页类型显示名称
const landingTypeLabels: Record<string, string> = {
  'LAND': '落地页',
  'CLOAK': '风险页',
  'BLACKLISTED': '黑名单页',
};

// 上下文显示文本
const contextInfo = computed(() => {
  const q = iframeQuery.value;
  if (!q?.subDomainName) return null;
  
  const typeLabel = landingTypeLabels[q.landingType] || q.landingType;
  return `${q.subDomainName} - ${typeLabel} - ${q.spuName || 'SPU'}`;
});

// 从 iframe postMessage 获取查询参数
const subDomainId = computed(() => iframeQuery.value?.subDomainId);
const spuId = computed(() => iframeQuery.value?.spuId);
const landingType = computed(() => iframeQuery.value?.landingType || "LAND");

// 主题状态
const {
  theme,
  hasUnsavedChanges,
  exportTheme,
  addCustomPage,
  removeCustomPage,
  enableCheckoutPage,
  disableCheckoutPage,
  layouts,
  addLayout,
  removeLayout,
  setPageLayout,
  setCustomPageLayout,
  markAsSaved,
  globalData,
} = useThemeSchema();

// 保存状态
const isSaving = ref(false);
const saveMessage = ref<{ type: "success" | "error"; text: string } | null>(null);

// 当前页面状态
const { currentPageKey, switchPage, currentDevice, switchDevice } =
  useCurrentPage();

// ============ 编辑器数据上下文 ============
// 生成可绑定字段列表
const bindableFields = computed(() => {
  const pageFields = generatePageContextFields();
  const variableFields = globalData.value?.variables
    ? generateVariableFields(globalData.value.variables)
    : [];
  return [...pageFields, ...variableFields];
});

// 提供编辑器数据上下文（供 PropertyPanel 等子组件使用）
provideEditorDataContext({
  mockData: {},
  bindableFields: bindableFields.value,
});

// ============ 左/右面板宽度（可拖拽） ============
const leftPanelWidth = ref(280);
const rightPanelWidth = ref(320);

const LEFT_MIN = 220;
const LEFT_MAX = 520;
const RIGHT_MIN = 260;
const RIGHT_MAX = 620;
const CENTER_MIN = 360;

onMounted(() => {
  try {
    const lw = Number(localStorage.getItem("builder:leftPanelWidth"));
    const rw = Number(localStorage.getItem("builder:rightPanelWidth"));
    if (!Number.isNaN(lw) && lw > 0) leftPanelWidth.value = lw;
    if (!Number.isNaN(rw) && rw > 0) rightPanelWidth.value = rw;
  } catch {
    // ignore
  }
});

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n));
}

function savePanelWidths() {
  try {
    localStorage.setItem("builder:leftPanelWidth", String(leftPanelWidth.value));
    localStorage.setItem("builder:rightPanelWidth", String(rightPanelWidth.value));
  } catch {
    // ignore
  }
}

function startResize(side: "left" | "right", e: PointerEvent) {
  const handleEl = e.currentTarget as HTMLElement | null;
  const mainEl = handleEl?.closest(".editor-main") as HTMLElement | null;
  if (!mainEl) return;

  const rect = mainEl.getBoundingClientRect();
  const startX = e.clientX;
  const startLeft = leftPanelWidth.value;
  const startRight = rightPanelWidth.value;

  handleEl?.setPointerCapture?.(e.pointerId);

  const onMove = (ev: PointerEvent) => {
    const dx = ev.clientX - startX;
    const total = rect.width;

    if (side === "left") {
      const nextLeft = clamp(startLeft + dx, LEFT_MIN, LEFT_MAX);
      const maxLeftByCenter = total - rightPanelWidth.value - CENTER_MIN;
      leftPanelWidth.value = clamp(
        nextLeft,
        LEFT_MIN,
        Math.min(LEFT_MAX, maxLeftByCenter)
      );
    } else {
      const nextRight = clamp(startRight - dx, RIGHT_MIN, RIGHT_MAX);
      const maxRightByCenter = total - leftPanelWidth.value - CENTER_MIN;
      rightPanelWidth.value = clamp(
        nextRight,
        RIGHT_MIN,
        Math.min(RIGHT_MAX, maxRightByCenter)
      );
    }
  };

  const onUp = () => {
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    savePanelWidths();
  };

  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", onUp);
}

// 添加页面下拉菜单
const showAddPageMenu = ref(false);
const addPageBtnRef = ref<HTMLElement | null>(null);
const dropdownPosition = ref({ top: 0, left: 0 });

// 添加自定义页面弹窗
const showAddPageModal = ref(false);
const newPageName = ref("");
const newPageSlug = ref("");

// 添加布局弹窗
const showAddLayoutModal = ref(false);
const newLayoutName = ref("");

// 变量管理弹窗
const showVariableManager = ref(false);

// 变量值设置弹窗
const showVariableValueEditor = ref(false);

// 布局选择下拉菜单
const showLayoutSelectMenu = ref(false);
const layoutSelectBtnRef = ref<HTMLElement | null>(null);
const layoutSelectPosition = ref({ top: 0, left: 0 });
const layoutSelectPageKey = ref<string | null>(null);

// 点击外部关闭下拉菜单
function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement;
  if (!target.closest(".add-page-dropdown")) {
    showAddPageMenu.value = false;
  }
  if (
    !target.closest(".layout-select-btn") &&
    !target.closest(".layout-select-menu")
  ) {
    showLayoutSelectMenu.value = false;
  }
}

// 切换下拉菜单
function toggleAddPageMenu() {
  if (!showAddPageMenu.value && addPageBtnRef.value) {
    const rect = addPageBtnRef.value.getBoundingClientRect();
    dropdownPosition.value = {
      top: rect.bottom + 4,
      left: rect.left,
    };
  }
  showAddPageMenu.value = !showAddPageMenu.value;
}

// 添加收银台页面
function handleAddCheckout() {
  enableCheckoutPage();
  switchPage("checkout");
  showAddPageMenu.value = false;
}

onMounted(() => {
  document.addEventListener("click", handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener("click", handleClickOutside);
});

// Tab 类型
type TabType = "layout" | "page";

// 页面 Tab 列表
const pageTabs = computed(() => {
  if (!theme.value) return [];

  const tabs: Array<{
    key: string;
    label: string;
    required: boolean;
    removable: boolean;
    type: TabType;
  }> = [];

  // 布局 Tab（在首页之前）
  for (const layout of theme.value.pages.layouts) {
    tabs.push({
      key: `layout-${layout.id}`,
      label: layout.name,
      required: layout.name === "default", // default 布局不可删除
      removable: layout.name !== "default",
      type: "layout",
    });
  }

  // 必选页面
  for (const pageType of REQUIRED_PAGE_TYPES) {
    const key = pageType === "orderResult" ? "orderResult" : pageType;
    tabs.push({
      key,
      label: PAGE_TYPE_LABELS[pageType],
      required: true,
      removable: false,
      type: "page",
    });
  }

  // 可选收银台
  if (theme.value.pages.checkout) {
    tabs.push({
      key: "checkout",
      label: "收银台",
      required: false,
      removable: true,
      type: "page",
    });
  }

  // 自定义页面
  for (const customPage of theme.value.pages.custom) {
    tabs.push({
      key: `custom-${customPage.id}`,
      label: customPage.name,
      required: false,
      removable: true,
      type: "page",
    });
  }

  return tabs;
});

// 当前是否在编辑布局
const isEditingLayout = computed(() => {
  return currentPageKey.value?.startsWith("layout-") ?? false;
});

// 获取导出数据的方法
const { exportFullData } = useThemeSchema();

// 保存主题
async function handleSave() {
  if (!subDomainId.value || !spuId.value) {
    saveMessage.value = { type: "error", text: "缺少必要参数，无法保存" };
    setTimeout(() => (saveMessage.value = null), 3000);
    return;
  }

  isSaving.value = true;
  saveMessage.value = null;

  try {
    // 使用分离的数据导出方法
    const fullData = exportFullData();

    const response = await $fetch("/api/builder/save", {
      method: "POST",
      body: {
        subDomainId: subDomainId.value,
        spuId: spuId.value,
        landingType: landingType.value,
        // 分离的 4 个字段
        themeConfig: fullData.themeConfig,
        variableSchema: fullData.variableSchema,
        siteConfig: fullData.siteConfig,
        variableValues: fullData.variableValues,
      },
    });

    if ((response as any).success) {
      markAsSaved();
      saveMessage.value = { type: "success", text: "保存成功" };
      // 通知父窗口保存成功
      window.parent.postMessage({ type: "themeEditor", action: "save" }, "*");
    } else {
      saveMessage.value = { type: "error", text: "保存失败" };
    }
  } catch (error: any) {
    console.error("[ThemeEditor] Save error:", error);
    saveMessage.value = {
      type: "error",
      text: error.data?.statusMessage || "保存失败，请重试",
    };
  } finally {
    isSaving.value = false;
    setTimeout(() => (saveMessage.value = null), 3000);
  }
}

// 关闭编辑器
function handleClose() {
  if (hasUnsavedChanges.value) {
    const answer = window.confirm("有未保存的更改，确定要关闭吗？");
    if (!answer) {
      return;
    }
  }
  // 通知父窗口关闭
  window.parent.postMessage({ type: "themeEditor", action: "close" }, "*");
}

// 添加自定义页面
function handleAddCustomPage() {
  if (!newPageName.value || !newPageSlug.value) return;

  const page = addCustomPage(newPageSlug.value, newPageName.value);
  switchPage(`custom-${page.id}`);

  newPageName.value = "";
  newPageSlug.value = "";
  showAddPageModal.value = false;
}

// 删除页面或布局
function handleRemovePage(key: string) {
  if (key === "checkout") {
    if (confirm("确定要删除收银台页面吗？")) {
      disableCheckoutPage();
      switchPage("home");
    }
  } else if (key.startsWith("custom-")) {
    const customId = key.replace("custom-", "");
    if (confirm("确定要删除这个页面吗？")) {
      removeCustomPage(customId);
      switchPage("home");
    }
  } else if (key.startsWith("layout-")) {
    const layoutId = key.replace("layout-", "");
    if (
      confirm("确定要删除这个布局吗？使用该布局的页面将不再关联任何布局。")
    ) {
      removeLayout(layoutId);
      // 切换到第一个布局或首页
      const firstLayout = layouts.value[0];
      if (firstLayout) {
        switchPage(`layout-${firstLayout.id}`);
      } else {
        switchPage("home");
      }
    }
  }
}

// 添加布局
function handleAddLayout() {
  if (!newLayoutName.value) return;

  const layout = addLayout(newLayoutName.value);
  switchPage(`layout-${layout.id}`);

  newLayoutName.value = "";
  showAddLayoutModal.value = false;
}

// 打开布局选择菜单
function openLayoutSelectMenu(event: MouseEvent, pageKey: string) {
  const target = event.currentTarget as HTMLElement;
  const rect = target.getBoundingClientRect();
  layoutSelectPosition.value = {
    top: rect.bottom + 4,
    left: rect.left,
  };
  layoutSelectPageKey.value = pageKey;
  showLayoutSelectMenu.value = true;
}

// 关闭布局选择菜单
function closeLayoutSelectMenu() {
  showLayoutSelectMenu.value = false;
  layoutSelectPageKey.value = null;
}

// 选择布局
function handleSelectLayout(layoutId: string | undefined) {
  if (!layoutSelectPageKey.value) return;

  const pageKey = layoutSelectPageKey.value;

  if (pageKey.startsWith("custom-")) {
    const customId = pageKey.replace("custom-", "");
    setCustomPageLayout(customId, layoutId);
  } else {
    const key = pageKey as
      | "home"
      | "product"
      | "orderResult"
      | "article"
      | "checkout";
    setPageLayout(key, layoutId);
  }

  closeLayoutSelectMenu();
}

// 获取页面当前使用的布局
function getPageLayoutId(pageKey: string): string | undefined {
  if (!theme.value) return undefined;

  if (pageKey.startsWith("custom-")) {
    const customId = pageKey.replace("custom-", "");
    const page = theme.value.pages.custom.find((p) => p.id === customId);
    return page?.layoutId;
  }

  const key = pageKey as keyof typeof theme.value.pages;
  const page = theme.value.pages[key];
  if (Array.isArray(page)) return undefined;
  return page?.layoutId;
}

// 获取布局名称
function getLayoutName(layoutId: string | undefined): string {
  if (!layoutId) return "无";
  const layout = layouts.value.find((l) => l.id === layoutId);
  return layout?.name || "未知";
}
</script>

<template>
  <div class="theme-editor">
    <!-- 顶部工具栏 -->
    <header class="editor-header">
      <div class="header-left">
        <button class="close-btn" title="关闭编辑器" @click="handleClose">
          <span class="i-carbon-close"></span>
        </button>
        <h1 class="theme-name">{{ theme?.name || "主题编辑器" }}</h1>
        <span v-if="contextInfo" class="context-info">{{ contextInfo }}</span>
        <span v-if="hasUnsavedChanges" class="unsaved-badge">未保存</span>
      </div>

      <div class="header-center">
        <!-- 设备切换已移至画布底部 -->
      </div>

      <div class="header-right">
        <!-- 保存状态消息 -->
        <Transition name="fade">
          <span
            v-if="saveMessage"
            class="save-message"
            :class="saveMessage.type"
          >
            {{ saveMessage.text }}
          </span>
        </Transition>

        <button class="btn btn-secondary" @click="showVariableManager = true">
          <span class="i-carbon-parameter mr-1"></span>
          变量管理
        </button>
        <button class="btn btn-secondary" @click="showVariableValueEditor = true">
          <span class="i-carbon-settings-adjust mr-1"></span>
          变量值
        </button>
        <button
          class="btn btn-primary"
          :disabled="isSaving"
          @click="handleSave"
        >
          <span v-if="isSaving" class="i-carbon-circle-dash animate-spin mr-1"></span>
          <span v-else class="i-carbon-save mr-1"></span>
          {{ isSaving ? "保存中..." : "保存" }}
        </button>
      </div>
    </header>

    <!-- 页面 Tab 切换 -->
    <nav class="page-tabs">
      <div v-for="tab in pageTabs" :key="tab.key" class="page-tab-wrapper">
        <button
          class="page-tab"
          :class="{
            active: currentPageKey === tab.key,
            'is-layout': tab.type === 'layout',
          }"
          @click="switchPage(tab.key)"
        >
          <span
            v-if="tab.type === 'layout'"
            class="i-carbon-template tab-icon"
          ></span>
          {{ tab.label }}
          <span
            v-if="tab.removable"
            class="tab-close"
            @click.stop="handleRemovePage(tab.key)"
          >
            <span class="i-carbon-close"></span>
          </span>
        </button>
        <!-- 布局选择按钮 - 仅在当前激活的页面 Tab 显示（布局不显示） -->
        <button
          v-if="currentPageKey === tab.key && tab.type === 'page'"
          class="tab-layout-btn layout-select-btn"
          :title="'布局: ' + getLayoutName(getPageLayoutId(tab.key))"
          @click.stop="openLayoutSelectMenu($event, tab.key)"
        >
          <span class="i-carbon-template"></span>
          <span class="layout-name">{{
            getLayoutName(getPageLayoutId(tab.key))
          }}</span>
        </button>
      </div>

      <div class="add-page-dropdown">
        <button
          ref="addPageBtnRef"
          class="page-tab add-tab"
          @click="toggleAddPageMenu"
        >
          <span class="i-carbon-add"></span>
          添加页面
          <span
            class="i-carbon-chevron-down dropdown-icon"
            :class="{ open: showAddPageMenu }"
          ></span>
        </button>
      </div>
    </nav>

    <!-- 添加页面下拉菜单 (Teleport 到 body 避免 overflow 裁剪) -->
    <Teleport to="body">
      <div
        v-if="showAddPageMenu"
        class="dropdown-menu"
        :style="{
          top: dropdownPosition.top + 'px',
          left: dropdownPosition.left + 'px',
        }"
      >
        <button
          v-if="!theme?.pages.checkout"
          class="dropdown-item"
          @click="handleAddCheckout"
        >
          <span class="i-carbon-shopping-cart mr-2"></span>
          收银台
        </button>
        <button
          class="dropdown-item"
          @click="
            showAddPageModal = true;
            showAddPageMenu = false;
          "
        >
          <span class="i-carbon-document-add mr-2"></span>
          自定义页面
        </button>
        <div class="dropdown-divider"></div>
        <button
          class="dropdown-item"
          @click="
            showAddLayoutModal = true;
            showAddPageMenu = false;
          "
        >
          <span class="i-carbon-template mr-2"></span>
          添加布局
        </button>
      </div>
    </Teleport>

    <!-- 布局选择下拉菜单 -->
    <Teleport to="body">
      <div
        v-if="showLayoutSelectMenu"
        class="dropdown-menu layout-select-menu"
        :style="{
          top: layoutSelectPosition.top + 'px',
          left: layoutSelectPosition.left + 'px',
        }"
      >
        <button
          class="dropdown-item"
          :class="{ active: !getPageLayoutId(layoutSelectPageKey || '') }"
          @click="handleSelectLayout(undefined)"
        >
          <span class="i-carbon-close-outline mr-2"></span>
          无布局
        </button>
        <div v-if="layouts.length > 0" class="dropdown-divider"></div>
        <button
          v-for="layout in layouts"
          :key="layout.id"
          class="dropdown-item"
          :class="{
            active: getPageLayoutId(layoutSelectPageKey || '') === layout.id,
          }"
          @click="handleSelectLayout(layout.id)"
        >
          <span class="i-carbon-template mr-2"></span>
          {{ layout.name }}
        </button>
      </div>
    </Teleport>

    <!-- 编辑器主体 -->
    <main class="editor-main">
      <!-- 左侧组件面板 -->
      <aside class="panel-left" :style="{ width: leftPanelWidth + 'px' }">
        <ComponentPanel />
      </aside>

      <div
        class="panel-resizer"
        title="拖拽调整左侧宽度"
        @pointerdown.prevent="startResize('left', $event as PointerEvent)"
      ></div>

      <!-- 中间画布区 -->
      <section class="panel-center">
        <BuilderCanvas />
      </section>

      <div
        class="panel-resizer"
        title="拖拽调整右侧宽度"
        @pointerdown.prevent="startResize('right', $event as PointerEvent)"
      ></div>

      <!-- 右侧属性面板 -->
      <aside class="panel-right" :style="{ width: rightPanelWidth + 'px' }">
        <PropertyPanel />
      </aside>
    </main>

    <!-- 添加自定义页面弹窗 -->
    <div
      v-if="showAddPageModal"
      class="modal-overlay"
      @click.self="showAddPageModal = false"
    >
      <div class="modal-content">
        <h3 class="modal-title">添加自定义页面</h3>
        <div class="form-group">
          <label>页面名称</label>
          <input
            v-model="newPageName"
            type="text"
            class="property-input"
            placeholder="如：双11活动页"
          />
        </div>
        <div class="form-group">
          <label>页面路径</label>
          <div class="input-prefix">
            <span class="prefix">/p/</span>
            <input
              v-model="newPageSlug"
              type="text"
              class="property-input"
              placeholder="如：activity/double11"
            />
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showAddPageModal = false">
            取消
          </button>
          <button class="btn btn-primary" @click="handleAddCustomPage">
            确定
          </button>
        </div>
      </div>
    </div>

    <!-- 添加布局弹窗 -->
    <div
      v-if="showAddLayoutModal"
      class="modal-overlay"
      @click.self="showAddLayoutModal = false"
    >
      <div class="modal-content">
        <h3 class="modal-title">添加布局</h3>
        <div class="form-group">
          <label>布局名称</label>
          <input
            v-model="newLayoutName"
            type="text"
            class="property-input"
            placeholder="如：商品详情布局"
          />
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showAddLayoutModal = false">
            取消
          </button>
          <button class="btn btn-primary" @click="handleAddLayout">确定</button>
        </div>
      </div>
    </div>

    <!-- 变量管理弹窗 -->
    <VariableManager
      :visible="showVariableManager"
      @close="showVariableManager = false"
    />

    <!-- 变量值设置弹窗 -->
    <VariableValueEditor
      :visible="showVariableValueEditor"
      @close="showVariableValueEditor = false"
    />
  </div>
</template>

<style scoped>
.theme-editor {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #0f172a;
  color: #e2e8f0;
}

/* 顶部工具栏 */
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: #94a3b8;
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  background-color: #334155;
  color: #f1f5f9;
}

.theme-name {
  font-size: 16px;
  font-weight: 600;
}

.unsaved-badge {
  padding: 2px 8px;
  font-size: 12px;
  background-color: #f59e0b;
  color: #1e293b;
  border-radius: 4px;
}

.context-info {
  padding: 4px 10px;
  font-size: 12px;
  color: #94a3b8;
  background-color: #334155;
  border-radius: 4px;
}

.header-center {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 保存状态消息 */
.save-message {
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 6px;
  font-weight: 500;
}

.save-message.success {
  background-color: rgba(34, 197, 94, 0.2);
  color: #22c55e;
}

.save-message.error {
  background-color: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 旋转动画 */
.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 按钮 */
.btn {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn-primary:disabled {
  background-color: #64748b;
  cursor: not-allowed;
}

.btn-secondary {
  background-color: #334155;
  color: #e2e8f0;
}

.btn-secondary:hover {
  background-color: #475569;
}

/* 页面 Tab */
.page-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 16px;
  height: 44px;
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
  overflow-x: auto;
}

.page-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 14px;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 6px 6px 0 0;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.page-tab:hover {
  color: #e2e8f0;
  background-color: #334155;
}

.page-tab.active {
  color: #3b82f6;
  background-color: #0f172a;
}

.page-tab.is-layout {
  border-left: 2px solid #8b5cf6;
}

.page-tab.is-layout.active {
  color: #8b5cf6;
}

.tab-icon {
  font-size: 14px;
  margin-right: 4px;
}

.tab-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.page-tab:hover .tab-close {
  opacity: 1;
}

.tab-close:hover {
  background-color: #ef4444;
  color: white;
}

/* 页面 Tab 包装器 */
.page-tab-wrapper {
  display: flex;
  align-items: center;
  position: relative;
}

/* 布局选择按钮 */
.tab-layout-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  margin-left: 2px;
  font-size: 12px;
  color: #64748b;
  background: none;
  border: 1px solid #334155;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-layout-btn:hover {
  color: #8b5cf6;
  border-color: #8b5cf6;
  background-color: rgba(139, 92, 246, 0.1);
}

.tab-layout-btn .layout-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.add-tab {
  color: #64748b;
}

.add-tab:hover {
  color: #3b82f6;
}

/* 添加页面下拉菜单 */
.add-page-dropdown {
  position: relative;
}

.dropdown-icon {
  font-size: 12px;
  margin-left: 4px;
  transition: transform 0.2s;
}

.dropdown-icon.open {
  transform: rotate(180deg);
}

/* 编辑器主体 */
.editor-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.panel-left {
  flex-shrink: 0;
  background-color: #1e293b;
  border-right: 1px solid #334155;
  overflow-x: hidden;
  overflow-y: auto;
}

.panel-center {
  flex: 1;
  min-width: 0;
  background-color: #0f172a;
  overflow: hidden;
}

.panel-right {
  flex-shrink: 0;
  background-color: #1e293b;
  border-left: 1px solid #334155;
  overflow-x: hidden;
  overflow-y: auto;
}

/* 自定义滚动条样式 - 适配深色主题 */
.panel-left::-webkit-scrollbar,
.panel-right::-webkit-scrollbar {
  width: 8px;
}

.panel-left::-webkit-scrollbar-track,
.panel-right::-webkit-scrollbar-track {
  background: transparent;
}

.panel-left::-webkit-scrollbar-thumb,
.panel-right::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.panel-left::-webkit-scrollbar-thumb:hover,
.panel-right::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

/* Firefox 滚动条样式 */
.panel-left,
.panel-right {
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.panel-resizer {
  width: 6px;
  cursor: col-resize;
  background-color: transparent;
  position: relative;
  flex: 0 0 auto;
  user-select: none;
  touch-action: none;
}

.panel-resizer::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 2px;
  width: 2px;
  background-color: rgba(51, 65, 85, 0.8);
  transition: background-color 0.2s;
}

.panel-resizer:hover::after {
  background-color: rgba(59, 130, 246, 0.9);
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.6);
  z-index: 1000;
}

.modal-content {
  width: 400px;
  padding: 24px;
  background-color: #1e293b;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3);
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #94a3b8;
}

.input-prefix {
  display: flex;
  align-items: center;
}

.input-prefix .prefix {
  padding: 8px 12px;
  background-color: #334155;
  border: 1px solid #475569;
  border-right: none;
  border-radius: 6px 0 0 6px;
  color: #94a3b8;
  font-size: 14px;
}

.input-prefix .property-input {
  border-radius: 0 6px 6px 0;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
}

/* 属性输入框 */
.property-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 14px;
  background-color: #0f172a;
  border: 1px solid #475569;
  border-radius: 6px;
  color: #e2e8f0;
  outline: none;
  transition: border-color 0.2s;
}

.property-input:focus {
  border-color: #3b82f6;
}

.property-input::placeholder {
  color: #64748b;
}
</style>

<style>
/* 下拉菜单样式 (不使用 scoped，因为 Teleport 到 body) */
.dropdown-menu {
  position: fixed;
  min-width: 160px;
  padding: 4px;
  background-color: #1e293b;
  border: 1px solid #334155;
  border-radius: 8px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);
  z-index: 1000;
}

.dropdown-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #e2e8f0;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
  white-space: nowrap;
}

.dropdown-item:hover {
  background-color: #334155;
}

.dropdown-item.active {
  color: #8b5cf6;
  background-color: rgba(139, 92, 246, 0.1);
}

.dropdown-divider {
  height: 1px;
  margin: 4px 0;
  background-color: #334155;
}
</style>
