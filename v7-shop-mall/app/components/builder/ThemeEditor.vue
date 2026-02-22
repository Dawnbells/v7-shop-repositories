<script setup lang="ts">
/**
 * ThemeEditor - 主题编辑器主容器
 * 组合所有子组件，实现可拖拽面板布局
 */

import type { TabItem } from './EditorTabs.vue'
import type { CustomVariable } from '~/types/data-context'
import { useIframeAuth } from '~/composables/useIframeAuth'
import { useThemeSchema } from '~/composables/useThemeSchema'

// 获取 iframe 认证信息
const { 
  mode, 
  contextName, 
  query, 
  isTemplateMode, 
  isLandingMode,
  isReady
} = useIframeAuth()

// 主题状态管理
const {
  variableSchema,
  loadFullData,
  exportFullData,
  addGlobalVariable,
  updateGlobalVariable,
  removeGlobalVariable,
  hasUnsavedChanges: themeHasUnsavedChanges,
  markAsSaved,
} = useThemeSchema()

// 变量管理状态
const showVariableManager = ref(false)
const showVariableValueEditor = ref(false)

// 兼容旧代码：customVariables 从 variableSchema 获取
const customVariables = computed(() => variableSchema.value)

// 数据加载状态
const isLoading = ref(false)
const loadError = ref<string | null>(null)

// 从数据库加载主题配置
async function loadThemeFromServer() {
  if (!query.value?.subDomainId || !query.value?.spuId) {
    console.log('[ThemeEditor] 缺少必要参数，跳过加载')
    return
  }

  isLoading.value = true
  loadError.value = null

  try {
    const params = new URLSearchParams({
      subDomainId: query.value.subDomainId,
      spuId: query.value.spuId,
      landingType: query.value.landingType || 'LAND',
    })

    const response = await fetch(`/api/builder/load?${params}`)
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const result = await response.json()

    if (result.success && result.data) {
      // 使用 loadFullData 加载所有数据
      loadFullData({
        variableSchema: result.data.variableSchema || [],
        siteConfig: result.data.siteConfig || {},
        siteConfigI18n: result.data.siteConfigI18n || {},
        variableValues: result.data.variableValues || {},
        variableValuesI18n: result.data.variableValuesI18n || {},
      })
      console.log('[ThemeEditor] 加载成功，变量数量:', variableSchema.value.length)
    } else {
      console.log('[ThemeEditor] 无数据或加载失败:', result.message)
    }
  } catch (error: any) {
    console.error('[ThemeEditor] 加载主题配置失败:', error)
    loadError.value = error.message || '加载失败'
  } finally {
    isLoading.value = false
  }
}

// 监听认证状态，准备好后加载数据
watch(isReady, (ready) => {
  if (ready && isLandingMode.value) {
    loadThemeFromServer()
  }
}, { immediate: true })

// 变量管理操作
function handleOpenVariables() {
  showVariableManager.value = true
}

// 打开变量值设置弹窗
function handleOpenVariableValues() {
  showVariableValueEditor.value = true
}

function handleDeleteVariable(key: string) {
  removeGlobalVariable(key)
}

function handleSaveVariable(variable: CustomVariable) {
  const existingIndex = variableSchema.value.findIndex(v => v.key === variable.key)
  if (existingIndex >= 0) {
    updateGlobalVariable(variable.key, variable)
  } else {
    addGlobalVariable(variable)
  }
}

// 落地页类型标签映射
const landingTypeLabels: Record<string, string> = {
  'LAND': '落地页',
  'CLOAK': '风险页',
  'BLACKLISTED': '黑名单页',
}

// 动态计算主题名称
const themeName = computed(() => {
  if (isTemplateMode.value) {
    return contextName.value || '主题模板'
  }
  return '主题编辑器'
})

// 动态计算上下文信息
const contextInfo = computed(() => {
  // 模板模式：显示模板名称
  if (isTemplateMode.value && contextName.value) {
    return contextName.value
  }
  
  // 落地页模式：显示 "落地页类型 - 域名"
  if (isLandingMode.value && query.value?.subDomainName) {
    const typeLabel = landingTypeLabels[query.value.landingType || 'LAND'] || '落地页'
    return `${typeLabel} - ${query.value.subDomainName}`
  }
  
  return undefined
})

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

// 编辑状态
const hasUnsavedChanges = computed(() => themeHasUnsavedChanges.value)
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

async function handleSave() {
  if (!query.value?.subDomainId || !query.value?.spuId) {
    alert('缺少必要参数，无法保存')
    return
  }

  isSaving.value = true

  try {
    // 使用 exportFullData 导出所有数据
    const fullData = exportFullData()
    
    const response = await fetch('/api/builder/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subDomainId: query.value.subDomainId,
        spuId: query.value.spuId,
        landingType: query.value.landingType || 'LAND',
        themeConfig: {},
        variableSchema: fullData.variableSchema,
        siteConfig: fullData.siteConfig,
        siteConfigI18n: fullData.siteConfigI18n,
        variableValues: fullData.variableValues,
        variableValuesI18n: fullData.variableValuesI18n,
      }),
    })

    const result = await response.json()

    if (result.success) {
      markAsSaved()
      console.log('[ThemeEditor] 保存成功')
    } else {
      throw new Error(result.message || '保存失败')
    }
  } catch (error: any) {
    console.error('[ThemeEditor] 保存失败:', error)
    alert('保存失败: ' + (error.message || '未知错误'))
  } finally {
    isSaving.value = false
  }
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
      @open-variables="handleOpenVariables"
      @open-variable-values="handleOpenVariableValues"
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
