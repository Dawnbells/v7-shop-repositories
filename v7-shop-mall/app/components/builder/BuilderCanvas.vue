<script setup lang="ts">
/**
 * BuilderCanvas - 中间画布区域
 * 用于预览和编辑页面
 */

type DeviceType = 'desktop' | 'tablet' | 'mobile'

const currentDevice = ref<DeviceType>('desktop')
const zoom = ref(100)

const deviceConfigs = {
  desktop: { width: '100%', icon: 'i-carbon-laptop', label: '桌面' },
  tablet: { width: '768px', icon: 'i-carbon-tablet', label: '平板' },
  mobile: { width: '375px', icon: 'i-carbon-mobile', label: '手机' }
}

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
  return deviceConfigs[currentDevice.value].width
})
</script>

<template>
  <div class="builder-canvas">
    <!-- 画布主体 -->
    <div class="canvas-viewport">
      <div 
        class="canvas-frame"
        :style="{ 
          width: canvasWidth,
          transform: `scale(${zoom / 100})`
        }"
      >
        <!-- 空状态提示 -->
        <div class="empty-state">
          <div class="empty-icon">
            <span class="i-carbon-add-large"></span>
          </div>
          <h3 class="empty-title">开始构建页面</h3>
          <p class="empty-desc">从左侧拖拽组件到此处，或点击组件添加</p>
        </div>
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

.canvas-frame {
  min-height: 600px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 
    0 0 0 1px rgba(71, 85, 105, 0.2),
    0 20px 40px rgba(0, 0, 0, 0.3);
  transform-origin: top center;
  transition: width 0.3s ease, transform 0.2s ease;
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
</style>
