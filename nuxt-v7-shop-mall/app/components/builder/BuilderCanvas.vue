<script setup lang="ts">
/**
 * 画布区 - 中间
 * 预览和编辑页面组件
 * 支持自由拖拽调整画布宽度
 */

import type { ComponentNode } from "~/types/builder";
import { BREAKPOINTS, getDeviceType } from "~/constants";

// 当前页面状态
const {
  currentPage,
  currentLayout,
  isEditingLayout,
  components,
  currentDevice,
  switchDevice,
  selectedComponentId,
  selectedComponent,
  selectComponent,
  addComponent,
  removeComponent,
  moveComponentUp,
  moveComponentDown,
  canMoveUp,
  canMoveDown,
} = useCurrentPage();

// 主题状态
const { theme } = useThemeSchema();

// 拖拽状态
const { isDragging, dragState, endDrag } = useDragDrop();

// 组件注册表
const { getComponentMeta } = useComponentRegistry();

// 画布容器引用
const canvasRef = ref<HTMLElement | null>(null);
const deviceFrameRef = ref<HTMLElement | null>(null);

// ============ 画布宽度管理 ============

// 最小和最大宽度限制
const MIN_WIDTH = 320;
const MAX_WIDTH = 1920;

// 自定义画布宽度（null 表示使用设备预设）
const customWidth = ref<number | null>(null);

// 实际画布宽度
const canvasWidth = computed(() => {
  if (customWidth.value !== null) {
    return customWidth.value;
  }
  return BREAKPOINTS[currentDevice.value].width;
});

// 当前设备类型标签（根据实际宽度计算）
const currentDeviceLabel = computed(() => {
  const width = canvasWidth.value;
  const device = getDeviceType(width);
  return BREAKPOINTS[device].label;
});

// 是否正在调整宽度
const isResizing = ref(false);

// 开始调整宽度
function startResize(event: PointerEvent, side: 'left' | 'right') {
  event.preventDefault();
  
  const startX = event.clientX;
  const startWidth = canvasWidth.value;
  
  isResizing.value = true;
  
  const onMove = (e: PointerEvent) => {
    // 根据拖拽方向计算宽度变化
    // 左边拖拽：向左拉增加宽度，向右拉减少宽度
    // 右边拖拽：向右拉增加宽度，向左拉减少宽度
    const dx = e.clientX - startX;
    const delta = side === 'right' ? dx : -dx;
    
    // 双边同时调整，所以变化量翻倍
    let newWidth = startWidth + delta * 2;
    
    // 限制宽度范围
    newWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, newWidth));
    
    customWidth.value = Math.round(newWidth);
    
    // 根据宽度自动更新设备类型
    const device = getDeviceType(newWidth);
    if (device !== currentDevice.value) {
      switchDevice(device);
    }
  };
  
  const onUp = () => {
    isResizing.value = false;
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
  };
  
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
}

// 重置为设备预设宽度
function resetToPreset() {
  customWidth.value = null;
}

// 设置指定宽度
function setWidth(width: number) {
  customWidth.value = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
  const device = getDeviceType(width);
  switchDevice(device);
}

// 处理组件点击
function handleComponentClick(component: ComponentNode) {
  selectComponent(component.id);
}

// 处理画布点击（取消选中）
function handleCanvasClick(event: MouseEvent) {
  if (event.target === canvasRef.value) {
    selectComponent(null);
  }
}

// 处理放置
function handleDrop(event: DragEvent) {
  event.preventDefault();

  if (dragState.value.dragData && dragState.value.dragType === "new") {
    const meta = dragState.value.dragData as any;
    addComponent(meta.type, { ...meta.defaultProps }, { ...meta.defaultStyle });
  }

  endDrag();
}

// 处理拖拽经过
function handleDragOver(event: DragEvent) {
  event.preventDefault();
}

// 获取选中组件的元数据
const selectedComponentMeta = computed(() => {
  if (!selectedComponent.value) return null;
  return getComponentMeta(selectedComponent.value.type);
});

// 是否可以上移
const canMoveSelectedUp = computed(() => {
  if (!selectedComponentId.value) return false;
  return canMoveUp(selectedComponentId.value);
});

// 是否可以下移
const canMoveSelectedDown = computed(() => {
  if (!selectedComponentId.value) return false;
  return canMoveDown(selectedComponentId.value);
});

// 上移选中组件
function handleMoveUp() {
  if (selectedComponentId.value) {
    moveComponentUp(selectedComponentId.value);
  }
}

// 下移选中组件
function handleMoveDown() {
  if (selectedComponentId.value) {
    moveComponentDown(selectedComponentId.value);
  }
}

// 删除选中组件
function handleDeleteComponent() {
  if (selectedComponentId.value) {
    if (confirm("确定要删除这个组件吗？")) {
      removeComponent(selectedComponentId.value);
    }
  }
}

// 宽度输入处理
const widthInputValue = ref('');

function handleWidthInputFocus() {
  widthInputValue.value = String(canvasWidth.value);
}

function handleWidthInputBlur() {
  const value = parseInt(widthInputValue.value, 10);
  if (!isNaN(value) && value >= MIN_WIDTH && value <= MAX_WIDTH) {
    setWidth(value);
  }
  widthInputValue.value = '';
}

function handleWidthInputKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    (event.target as HTMLInputElement).blur();
  }
  if (event.key === 'Escape') {
    widthInputValue.value = '';
    (event.target as HTMLInputElement).blur();
  }
}
</script>

<template>
  <div class="builder-canvas-container">
    <!-- 画布包装器 -->
    <div
      ref="canvasRef"
      class="canvas-wrapper"
      :class="{ 'is-dragging': isDragging, 'is-resizing': isResizing }"
      @click="handleCanvasClick"
      @drop="handleDrop"
      @dragover="handleDragOver"
    >
      <!-- 设备框架容器（包含拖拽手柄） -->
      <div class="device-frame-container">
        <!-- 左侧拖拽手柄 -->
        <div
          class="resize-handle resize-handle-left"
          @pointerdown.prevent="startResize($event, 'left')"
        >
          <div class="resize-handle-bar"></div>
        </div>
        
        <!-- 设备框架 -->
        <div
          ref="deviceFrameRef"
          class="device-frame"
          :style="{ width: `${canvasWidth}px` }"
        >
          <!-- 页面内容 -->
          <div class="canvas-content">
            <!-- 编辑布局时 -->
            <template v-if="isEditingLayout && currentLayout">
              <template v-if="currentLayout.components.length > 0">
                <PageRenderer
                  :schema="currentLayout"
                  :global-style="theme?.globalStyle"
                  :preview-device="currentDevice"
                  :is-editor="true"
                  @component-click="handleComponentClick"
                />
              </template>
              <!-- 布局空状态 -->
              <div v-else class="empty-canvas">
                <div class="empty-content">
                  <span class="i-carbon-template text-5xl text-purple-400 mb-4"></span>
                  <p class="text-lg text-gray-400 mb-2">拖拽组件到这里</p>
                  <p class="text-sm text-gray-500">
                    布局中的组件将应用于使用此布局的所有页面
                  </p>
                </div>
              </div>
            </template>

            <!-- 编辑页面时 -->
            <template v-else-if="currentPage">
              <template v-if="currentPage.components.length > 0">
                <PageRenderer
                  :schema="currentPage"
                  :global-style="theme?.globalStyle"
                  :preview-device="currentDevice"
                  :is-editor="true"
                  @component-click="handleComponentClick"
                />
              </template>
              <!-- 页面空状态 -->
              <div v-else class="empty-canvas">
                <div class="empty-content">
                  <span class="i-carbon-drag-horizontal text-5xl text-gray-500 mb-4"></span>
                  <p class="text-lg text-gray-400 mb-2">拖拽组件到这里</p>
                  <p class="text-sm text-gray-500">
                    从左侧面板拖拽组件开始搭建页面
                  </p>
                </div>
              </div>
            </template>

            <!-- 无内容 -->
            <div v-else class="empty-canvas">
              <div class="empty-content">
                <span class="i-carbon-warning text-5xl text-amber-500 mb-4"></span>
                <p class="text-lg text-gray-400 mb-2">无法加载内容</p>
              </div>
            </div>
          </div>

          <!-- 选中组件高亮 -->
          <div v-if="selectedComponentId" class="selection-overlay">
            <!-- 选中框通过 CSS 实现 -->
          </div>
        </div>
        
        <!-- 右侧拖拽手柄 -->
        <div
          class="resize-handle resize-handle-right"
          @pointerdown.prevent="startResize($event, 'right')"
        >
          <div class="resize-handle-bar"></div>
        </div>
      </div>
    </div>

    <!-- 组件操作工具栏 -->
    <div
      v-if="selectedComponentId && selectedComponentMeta"
      class="component-toolbar"
    >
      <div class="toolbar-info">
        <span :class="selectedComponentMeta.icon" class="toolbar-icon"></span>
        <span class="toolbar-name">{{ selectedComponentMeta.name }}</span>
      </div>
      <div class="toolbar-actions">
        <button
          class="toolbar-btn"
          :disabled="!canMoveSelectedUp"
          title="上移"
          @click="handleMoveUp"
        >
          <span class="i-carbon-arrow-up"></span>
        </button>
        <button
          class="toolbar-btn"
          :disabled="!canMoveSelectedDown"
          title="下移"
          @click="handleMoveDown"
        >
          <span class="i-carbon-arrow-down"></span>
        </button>
        <button
          class="toolbar-btn toolbar-btn-danger"
          title="删除"
          @click="handleDeleteComponent"
        >
          <span class="i-carbon-trash-can"></span>
        </button>
      </div>
    </div>

    <!-- 画布信息栏 -->
    <div class="canvas-info">
      <!-- 设备类型 -->
      <span class="device-label">{{ currentDeviceLabel }}</span>
      <span class="divider">|</span>
      
      <!-- 宽度输入/显示 -->
      <div class="width-control">
        <input
          type="text"
          class="width-input"
          :value="widthInputValue || canvasWidth"
          :placeholder="String(canvasWidth)"
          @focus="handleWidthInputFocus"
          @blur="handleWidthInputBlur"
          @keydown="handleWidthInputKeydown"
        />
        <span class="width-unit">px</span>
      </div>
      
      <!-- 重置按钮 -->
      <button
        v-if="customWidth !== null"
        class="reset-btn"
        title="重置为设备预设宽度"
        @click="resetToPreset"
      >
        <span class="i-carbon-reset"></span>
      </button>
      
      <span class="divider">|</span>
      
      <!-- 快捷预设按钮 -->
      <div class="preset-buttons">
        <button
          class="preset-btn"
          :class="{ active: canvasWidth === BREAKPOINTS.mobile.width }"
          title="手机 (375px)"
          @click="setWidth(BREAKPOINTS.mobile.width)"
        >
          <span class="i-carbon-phone"></span>
        </button>
        <button
          class="preset-btn"
          :class="{ active: canvasWidth === BREAKPOINTS.tablet.width }"
          title="平板 (768px)"
          @click="setWidth(BREAKPOINTS.tablet.width)"
        >
          <span class="i-carbon-tablet"></span>
        </button>
        <button
          class="preset-btn"
          :class="{ active: canvasWidth === BREAKPOINTS.pc.width }"
          title="电脑 (1024px)"
          @click="setWidth(BREAKPOINTS.pc.width)"
        >
          <span class="i-carbon-laptop"></span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.builder-canvas-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  min-width: 0;
  position: relative;
}

.canvas-wrapper {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 24px;
  overflow: auto;
  min-width: 0;
  background-color: #0f172a;
  background-image: linear-gradient(45deg, #1e293b 25%, transparent 25%),
    linear-gradient(-45deg, #1e293b 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #1e293b 75%),
    linear-gradient(-45deg, transparent 75%, #1e293b 75%);
  background-size: 20px 20px;
  background-position: 0 0, 0 10px, 10px -10px, -10px 0;
  /* Firefox 滚动条 */
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

/* 画布包装器滚动条 - 深色风格 (WebKit) */
.canvas-wrapper::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.canvas-wrapper::-webkit-scrollbar-track {
  background: transparent;
}

.canvas-wrapper::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.canvas-wrapper::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

.canvas-wrapper::-webkit-scrollbar-corner {
  background: transparent;
}

.canvas-wrapper.is-dragging {
  background-color: #1e3a5f;
}

.canvas-wrapper.is-resizing {
  cursor: ew-resize;
  user-select: none;
}

/* 设备框架容器 */
.device-frame-container {
  display: flex;
  align-items: stretch;
  max-height: calc(100% - 48px);
}

/* 拖拽手柄 */
.resize-handle {
  width: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: ew-resize;
  opacity: 0;
  transition: opacity 0.2s;
}

.device-frame-container:hover .resize-handle,
.canvas-wrapper.is-resizing .resize-handle {
  opacity: 1;
}

.resize-handle-bar {
  width: 4px;
  height: 48px;
  background-color: #3b82f6;
  border-radius: 2px;
  transition: background-color 0.2s, transform 0.2s;
}

.resize-handle:hover .resize-handle-bar {
  background-color: #60a5fa;
  transform: scaleY(1.2);
}

.resize-handle-left {
  margin-right: 8px;
}

.resize-handle-right {
  margin-left: 8px;
}

.device-frame {
  flex-shrink: 0;
  max-width: 100%;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.15s ease;
  /* Firefox 滚动条 */
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

/* 设备框架滚动条 - 浅色风格 (WebKit) */
.device-frame::-webkit-scrollbar {
  width: 6px;
}

.device-frame::-webkit-scrollbar-track {
  background: transparent;
}

.device-frame::-webkit-scrollbar-thumb {
  background-color: #cbd5e1;
  border-radius: 3px;
}

.device-frame::-webkit-scrollbar-thumb:hover {
  background-color: #94a3b8;
}

.canvas-wrapper.is-resizing .device-frame {
  transition: none;
}

.canvas-content {
  min-height: 600px;
  position: relative;
}

.empty-canvas {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 600px;
  background-color: #f8fafc;
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.selection-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

/* 画布信息栏 */
.canvas-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 16px;
  font-size: 12px;
  color: #64748b;
  background-color: #1e293b;
  border-top: 1px solid #334155;
}

.device-label {
  color: #94a3b8;
  font-weight: 500;
}

.divider {
  color: #475569;
}

/* 宽度控制 */
.width-control {
  display: flex;
  align-items: center;
  gap: 2px;
}

.width-input {
  width: 50px;
  padding: 2px 6px;
  font-size: 12px;
  text-align: right;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 4px;
  color: #e2e8f0;
  outline: none;
  transition: border-color 0.2s;
}

.width-input:focus {
  border-color: #3b82f6;
}

.width-unit {
  color: #64748b;
}

/* 重置按钮 */
.reset-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 14px;
  color: #64748b;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.reset-btn:hover {
  color: #3b82f6;
  background-color: #334155;
}

/* 预设按钮 */
.preset-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.preset-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 16px;
  color: #64748b;
  background: none;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.preset-btn:hover {
  color: #94a3b8;
  background-color: #334155;
}

.preset-btn.active {
  color: #3b82f6;
  border-color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

/* 选中组件样式（全局作用于 PageRenderer 内的组件） */
:deep([data-component-id].selected) {
  outline: 2px solid #3b82f6 !important;
  outline-offset: 2px;
}

/* 组件操作工具栏 */
.component-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background-color: #1e293b;
  border-top: 1px solid #334155;
}

.toolbar-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #e2e8f0;
  font-size: 14px;
}

.toolbar-icon {
  font-size: 16px;
  color: #94a3b8;
}

.toolbar-name {
  font-weight: 500;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: #94a3b8;
  background: none;
  border: 1px solid #334155;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.toolbar-btn:hover:not(:disabled) {
  color: #e2e8f0;
  background-color: #334155;
  border-color: #475569;
}

.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.toolbar-btn-danger:hover:not(:disabled) {
  color: #ef4444;
  border-color: #ef4444;
  background-color: rgba(239, 68, 68, 0.1);
}
</style>
