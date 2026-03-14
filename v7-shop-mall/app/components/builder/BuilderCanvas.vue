<script setup lang="ts">
/**
 * BuilderCanvas - 中间画布区域
 * 用于预览和编辑页面，支持拖放添加组件
 */

import { useCanvasState } from '~/composables/useCanvasState'

type DeviceType = 'desktop' | 'tablet' | 'mobile' | 'custom'

const currentDevice = ref<DeviceType>('desktop')
const zoom = ref(100)
const customWidth = ref(800)
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)
const canvasViewportRef = ref<HTMLElement | null>(null)

const deviceConfigs = {
  desktop: { width: '100%', icon: 'i-carbon-laptop', label: '桌面' },
  tablet: { width: '768px', icon: 'i-carbon-tablet', label: '平板' },
  mobile: { width: '375px', icon: 'i-carbon-mobile', label: '手机' },
  custom: { width: 'custom', icon: 'i-carbon-fit-to-width', label: '自定义' }
}

// 画布状态
const {
  rootNodes,
  isEmpty,
  selectedNodeId,
  createNode,
  addNode,
  selectNode,
} = useCanvasState()

// 拖放状态
const isDragOver = ref(false)

function setDevice(device: DeviceType) {
  currentDevice.value = device
}

function zoomIn() {
  if (zoom.value < 150) zoom.value += 10
}

function zoomOut() {
  if (zoom.value > 50) zoom.value -= 10
}

function resetZoom() {
  zoom.value = 100
}

const canvasWidth = computed(() => {
  if (currentDevice.value === 'custom') {
    return `${customWidth.value}px`
  }
  return deviceConfigs[currentDevice.value].width
})

const maxCanvasWidth = computed(() => {
  if (!canvasViewportRef.value) return 1200
  return canvasViewportRef.value.clientWidth - 80
})

function startResize(event: MouseEvent, side: 'left' | 'right') {
  event.preventDefault()
  isResizing.value = true
  resizeStartX.value = event.clientX
  
  if (currentDevice.value === 'custom') {
    resizeStartWidth.value = customWidth.value
  } else {
    const config = deviceConfigs[currentDevice.value]
    if (config.width === '100%') {
      resizeStartWidth.value = maxCanvasWidth.value
    } else {
      resizeStartWidth.value = parseInt(config.width)
    }
  }
  
  const onMouseMove = (e: MouseEvent) => {
    if (!isResizing.value) return
    
    const deltaX = side === 'right' 
      ? e.clientX - resizeStartX.value 
      : resizeStartX.value - e.clientX
    
    const scaledDelta = deltaX * 2 / (zoom.value / 100)
    let newWidth = resizeStartWidth.value + scaledDelta
    
    newWidth = Math.max(320, Math.min(newWidth, maxCanvasWidth.value))
    customWidth.value = Math.round(newWidth)
    currentDevice.value = 'custom'
  }
  
  const onMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  document.body.style.cursor = 'ew-resize'
  document.body.style.userSelect = 'none'
}

// 拖拽进入画布
function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
  isDragOver.value = true
}

// 拖拽离开画布
function onDragLeave(event: DragEvent) {
  // 确保是离开画布区域而不是进入子元素
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const x = event.clientX
  const y = event.clientY
  
  if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) {
    isDragOver.value = false
  }
}

// 放置组件
function onDrop(event: DragEvent) {
  event.preventDefault()
  isDragOver.value = false

  if (!event.dataTransfer) return

  try {
    const data = JSON.parse(event.dataTransfer.getData('application/json'))
    
    if (data && data.type) {
      // 创建新节点
      const newNode = createNode(
        data.type,
        data.defaultProps || {},
        data.defaultStyle || {},
        data.name
      )
      
      // 添加到画布
      addNode(newNode)
      
      // 选中新添加的节点
      selectNode(newNode.id)
      
      console.log('[BuilderCanvas] 添加组件:', data.type, newNode.id)
    }
  } catch (error) {
    console.error('[BuilderCanvas] 解析拖放数据失败:', error)
  }
}

// 点击画布空白区域取消选中
function onCanvasClick(event: MouseEvent) {
  // 如果点击的是画布背景（不是组件节点），取消选中
  if (event.target === event.currentTarget) {
    selectNode(null)
  }
}
</script>

<template>
  <div class="builder-canvas">
    <!-- 画布主体 -->
    <div ref="canvasViewportRef" class="canvas-viewport">
      <!-- 画布容器（包含拖拽手柄） -->
      <div class="canvas-wrapper" :style="{ transform: `scale(${zoom / 100})` }">
        <!-- 左侧拖拽手柄 -->
        <div 
          class="resize-handle resize-handle-left"
          :class="{ active: isResizing }"
          @mousedown="startResize($event, 'left')"
        >
          <div class="resize-handle-bar"></div>
        </div>
        
        <div 
          class="canvas-frame"
          :class="{ 'drag-over': isDragOver }"
          :style="{ width: canvasWidth }"
          @dragover.prevent="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
          @click="onCanvasClick"
        >
        <!-- 空状态提示 -->
        <div v-if="isEmpty" class="empty-state">
          <div class="empty-icon">
            <span class="i-carbon-add-large"></span>
          </div>
          <h3 class="empty-title">开始构建页面</h3>
          <p class="empty-desc">从左侧拖拽组件到此处，或点击组件添加</p>
        </div>

        <!-- 组件节点渲染 -->
        <div v-else class="canvas-content">
          <BuilderCanvasNode
            v-for="node in rootNodes"
            :key="node.id"
            :node="node"
            :selected-id="selectedNodeId"
            :is-edit-mode="true"
            @select="selectNode"
          />
        </div>

        <!-- 拖放提示遮罩 -->
        <div v-if="isDragOver" class="drop-overlay">
          <div class="drop-hint">
            <span class="i-carbon-add-large"></span>
            <span>放置组件</span>
          </div>
        </div>
      </div>
      
      <!-- 右侧拖拽手柄 -->
      <div 
        class="resize-handle resize-handle-right"
        :class="{ active: isResizing }"
        @mousedown="startResize($event, 'right')"
      >
        <div class="resize-handle-bar"></div>
      </div>
    </div>
    
    <!-- 宽度指示器 -->
    <div v-if="currentDevice === 'custom' || isResizing" class="width-indicator">
      {{ customWidth }}px
    </div>
  </div>

    <!-- 底部工具栏 -->
    <div class="canvas-toolbar">
      <!-- 设备切换 -->
      <div class="device-switcher">
        <button
          v-for="(config, device) in deviceConfigs"
          :key="device"
          class="device-btn"
          :class="{ active: currentDevice === device }"
          :title="config.label"
          @click="setDevice(device as DeviceType)"
        >
          <span :class="config.icon"></span>
        </button>
      </div>

      <!-- 缩放控制 -->
      <div class="zoom-controls">
        <button class="zoom-btn" title="缩小" @click="zoomOut">
          <span class="i-carbon-zoom-out"></span>
        </button>
        <button class="zoom-value" title="重置缩放" @click="resetZoom">
          {{ zoom }}%
        </button>
        <button class="zoom-btn" title="放大" @click="zoomIn">
          <span class="i-carbon-zoom-in"></span>
        </button>
      </div>

      <!-- 视图选项 -->
      <div class="view-options">
        <button class="option-btn" title="显示网格">
          <span class="i-carbon-grid"></span>
        </button>
        <button class="option-btn" title="显示边框">
          <span class="i-carbon-select-window"></span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.builder-canvas {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0f172a;
}

.canvas-viewport {
  position: relative;
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 24px;
  overflow: auto;
  background-image: 
    radial-gradient(circle at 1px 1px, rgba(71, 85, 105, 0.3) 1px, transparent 0);
  background-size: 20px 20px;
}

.canvas-wrapper {
  display: flex;
  align-items: stretch;
  transform-origin: top center;
  transition: transform 0.2s ease;
}

.canvas-frame {
  position: relative;
  min-height: 600px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 
    0 0 0 1px rgba(71, 85, 105, 0.2),
    0 20px 40px rgba(0, 0, 0, 0.3);
  transition: width 0.15s ease;
  overflow: hidden;
}

/* 拖拽手柄 */
.resize-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  width: 16px;
  height: 100px;
  cursor: ew-resize;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.canvas-wrapper:hover .resize-handle,
.resize-handle.active {
  opacity: 1;
}

.resize-handle-left {
  margin-right: 8px;
}

.resize-handle-right {
  margin-left: 8px;
}

.resize-handle-bar {
  width: 4px;
  height: 40px;
  background: rgba(148, 163, 184, 0.5);
  border-radius: 2px;
  transition: all 0.15s ease;
}

.resize-handle:hover .resize-handle-bar,
.resize-handle.active .resize-handle-bar {
  width: 6px;
  height: 60px;
  background: #3b82f6;
}

/* 宽度指示器 */
.width-indicator {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #f1f5f9;
  background: rgba(30, 41, 59, 0.9);
  border-radius: 4px;
  pointer-events: none;
  z-index: 10;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 600px;
  padding: 40px;
  text-align: center;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
  font-size: 32px;
  color: #94a3b8;
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  border-radius: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.empty-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #334155;
}

.empty-desc {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

/* 底部工具栏 */
.canvas-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 12px 16px;
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.95) 0%, #1e293b 100%);
  border-top: 1px solid rgba(71, 85, 105, 0.3);
}

.device-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: rgba(15, 23, 42, 0.5);
  border-radius: 8px;
}

.device-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 32px;
  font-size: 18px;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.device-btn:hover {
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
}

.device-btn.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.15);
}

.zoom-controls {
  display: flex;
  align-items: center;
  gap: 4px;
}

.zoom-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.5);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.zoom-btn:hover {
  color: #f1f5f9;
  background: rgba(51, 65, 85, 0.5);
}

.zoom-value {
  min-width: 56px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.5);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.zoom-value:hover {
  color: #f1f5f9;
  background: rgba(51, 65, 85, 0.5);
}

.view-options {
  display: flex;
  align-items: center;
  gap: 4px;
}

.option-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.option-btn:hover {
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.5);
}

.option-btn.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.15);
}

/* 自定义滚动条 */
.canvas-viewport::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.canvas-viewport::-webkit-scrollbar-track {
  background: transparent;
}

.canvas-viewport::-webkit-scrollbar-thumb {
  background: rgba(71, 85, 105, 0.5);
  border-radius: 4px;
}

.canvas-viewport::-webkit-scrollbar-thumb:hover {
  background: rgba(71, 85, 105, 0.8);
}

.canvas-viewport::-webkit-scrollbar-corner {
  background: transparent;
}

/* 拖放相关样式 */
.canvas-frame.drag-over {
  outline: 2px dashed #3b82f6;
  outline-offset: -2px;
}

.canvas-content {
  min-height: 600px;
  padding: 0;
}

.drop-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 130, 246, 0.1);
  pointer-events: none;
}

.drop-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 500;
  color: #3b82f6;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.drop-hint span[class^="i-"] {
  font-size: 20px;
}
</style>
