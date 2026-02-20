<script setup lang="ts">
/**
 * ThemeEditor - 主题编辑器主容器
 * 组合所有子组件，实现可拖拽面板布局
 */

import type { TabItem } from './EditorTabs.vue'

// 面板宽度配置
const LEFT_MIN = 220
const LEFT_MAX = 520
const RIGHT_MIN = 260
const RIGHT_MAX = 620
const CENTER_MIN = 360

const leftPanelWidth = ref(280)
const rightPanelWidth = ref(320)

// 从 localStorage 恢复面板宽度
onMounted(() => {
  try {
    const savedLeft = localStorage.getItem('builder:leftPanelWidth')
    const savedRight = localStorage.getItem('builder:rightPanelWidth')
    if (savedLeft) leftPanelWidth.value = Number(savedLeft)
    if (savedRight) rightPanelWidth.value = Number(savedRight)
  } catch {
    // ignore
  }
})

// 保存面板宽度到 localStorage
function savePanelWidths() {
  try {
    localStorage.setItem('builder:leftPanelWidth', String(leftPanelWidth.value))
    localStorage.setItem('builder:rightPanelWidth', String(rightPanelWidth.value))
  } catch {
    // ignore
  }
}

// 限制数值范围
function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

// 拖拽调整面板宽度
function startResize(side: 'left' | 'right', event: PointerEvent) {
  const target = event.currentTarget as HTMLElement
  const mainEl = target.closest('.editor-main') as HTMLElement
  if (!mainEl) return

  const rect = mainEl.getBoundingClientRect()
  const startX = event.clientX
  const startLeft = leftPanelWidth.value
  const startRight = rightPanelWidth.value

  target.setPointerCapture(event.pointerId)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'

  const onMove = (e: PointerEvent) => {
    const dx = e.clientX - startX
    const totalWidth = rect.width

    if (side === 'left') {
      const newLeft = clamp(startLeft + dx, LEFT_MIN, LEFT_MAX)
      const maxByCenter = totalWidth - rightPanelWidth.value - CENTER_MIN
      leftPanelWidth.value = clamp(newLeft, LEFT_MIN, Math.min(LEFT_MAX, maxByCenter))
    } else {
      const newRight = clamp(startRight - dx, RIGHT_MIN, RIGHT_MAX)
      const maxByCenter = totalWidth - leftPanelWidth.value - CENTER_MIN
      rightPanelWidth.value = clamp(newRight, RIGHT_MIN, Math.min(RIGHT_MAX, maxByCenter))
    }
  }

  const onUp = () => {
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    savePanelWidths()
  }

  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

// 模拟数据
const themeName = ref('我的主题')
const contextInfo = ref('落地页 - 商品详情')
const hasUnsavedChanges = ref(true)
const isSaving = ref(false)

const currentTabKey = ref('home')
const mockTabs: TabItem[] = [
  { key: 'layout-default', label: '默认布局', type: 'layout' },
  { key: 'home', label: '首页', type: 'page' },
  { key: 'product', label: '商品详情', type: 'page' },
  { key: 'orderResult', label: '订单结果', type: 'page' },
  { key: 'article', label: '文章', type: 'page' },
  { key: 'checkout', label: '收银台', type: 'page', removable: true },
]

// 事件处理
function handleClose() {
  if (hasUnsavedChanges.value) {
    if (!confirm('有未保存的更改，确定要关闭吗？')) return
  }
  window.parent.postMessage({ type: 'themeEditor', action: 'close' }, '*')
}

function handleSave() {
  isSaving.value = true
  setTimeout(() => {
    isSaving.value = false
    hasUnsavedChanges.value = false
  }, 1500)
}

function handleSwitchTab(key: string) {
  currentTabKey.value = key
}

function handleRemoveTab(key: string) {
  console.log('Remove tab:', key)
}

function handleAddPage() {
  console.log('Add page')
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
      @open-templates="() => {}"
      @open-variables="() => {}"
      @open-variable-values="() => {}"
    />

    <!-- 页面 Tab 栏 -->
    <BuilderEditorTabs
      :tabs="mockTabs"
      :active-key="currentTabKey"
      @switch="handleSwitchTab"
      @remove="handleRemoveTab"
      @add="handleAddPage"
    />

    <!-- 编辑器主体 -->
    <main class="editor-main">
      <!-- 左侧组件面板 -->
      <aside 
        class="panel-left"
        :style="{ width: `${leftPanelWidth}px` }"
      >
        <BuilderComponentPanel />
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
      <aside 
        class="panel-right"
        :style="{ width: `${rightPanelWidth}px` }"
      >
        <BuilderPropertyPanel />
      </aside>
    </main>
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
