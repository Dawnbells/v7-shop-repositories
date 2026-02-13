<script setup lang="ts">
/**
 * BuilderMain - 主题编辑器主界面
 *
 * 认证成功后显示的编辑器界面，包含：
 * - 标题区：显示主题名称和编辑模式
 * - 左中右三栏布局（可拖拽调整宽度）
 */

import { useIframeAuth } from "@/composables/base/useIframeAuth";
import PageTabs from "./PageTabs.vue";

defineOptions({
  name: "BuilderMain",
});

const {
  token,
  isTemplateMode,
  isLandingMode,
  contextName,
  query: iframeQuery,
} = useIframeAuth();

// ==================== 可拖拽面板宽度 ====================

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
    localStorage.setItem(
      "builder:leftPanelWidth",
      String(leftPanelWidth.value)
    );
    localStorage.setItem(
      "builder:rightPanelWidth",
      String(rightPanelWidth.value)
    );
  } catch {
    // ignore
  }
}

function startResize(side: "left" | "right", e: PointerEvent) {
  const handleEl = e.currentTarget as HTMLElement | null;
  const mainEl = handleEl?.closest(".builder-main") as HTMLElement | null;
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
</script>

<template>
  <div class="builder-layout">
    <!-- 标题区 -->
    <header class="builder-header">
      <div class="header-left">
        <h1 class="header-title">主题编辑器</h1>
        <span v-if="contextName" class="header-context" :title="contextName">
          {{ contextName }}
        </span>
      </div>
      <div class="header-right">
        <span class="header-mode">{{
          isTemplateMode ? "模板编辑" : "落地页编辑"
        }}</span>
      </div>
    </header>

    <!-- 页面 Tab 切换 -->
    <PageTabs />

    <!-- 编辑区：左中右三栏（可拖拽） -->
    <main class="builder-main">
      <!-- 左侧组件面板 -->
      <aside
        class="builder-sidebar left"
        :style="{ width: leftPanelWidth + 'px' }"
      >
        <div class="panel-placeholder">左侧组件面板</div>
      </aside>

      <!-- 左侧拖拽条 -->
      <div
        class="panel-resizer"
        title="拖拽调整左侧宽度"
        @pointerdown.prevent="startResize('left', $event as PointerEvent)"
      ></div>

      <!-- 中间画布区域 -->
      <section class="builder-canvas">
        <div class="canvas-placeholder">中间画布区域</div>
      </section>

      <!-- 右侧拖拽条 -->
      <div
        class="panel-resizer"
        title="拖拽调整右侧宽度"
        @pointerdown.prevent="startResize('right', $event as PointerEvent)"
      ></div>

      <!-- 右侧属性面板 -->
      <aside
        class="builder-sidebar right"
        :style="{ width: rightPanelWidth + 'px' }"
      >
        <div class="panel-placeholder">右侧属性面板</div>
      </aside>
    </main>
  </div>
</template>

<style scoped>
/* 编辑器布局 */
.builder-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* 标题区 */
.builder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
}

.header-context {
  padding: 4px 10px;
  font-size: 12px;
  color: #94a3b8;
  background-color: #334155;
  border-radius: 4px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-mode {
  padding: 4px 12px;
  font-size: 12px;
  color: #94a3b8;
  background-color: #334155;
  border-radius: 4px;
}

/* 编辑区 */
.builder-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 侧边栏 */
.builder-sidebar {
  flex-shrink: 0;
  background-color: #1e293b;
  overflow-x: hidden;
  overflow-y: auto;
}

.builder-sidebar.left {
  border-right: 1px solid #334155;
}

.builder-sidebar.right {
  border-left: 1px solid #334155;
}

/* 面板占位符 */
.panel-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #64748b;
  font-size: 14px;
}

/* 拖拽条 */
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

/* 自定义滚动条样式 */
.builder-sidebar::-webkit-scrollbar {
  width: 8px;
}

.builder-sidebar::-webkit-scrollbar-track {
  background: transparent;
}

.builder-sidebar::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.builder-sidebar::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

.builder-sidebar {
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

/* 画布区域 */
.builder-canvas {
  flex: 1;
  min-width: 0;
  background-color: #0f172a;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.canvas-placeholder {
  color: #64748b;
  font-size: 14px;
}
</style>
