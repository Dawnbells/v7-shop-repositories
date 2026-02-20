<script setup lang="ts">
/**
 * PropertyPanel - 右侧属性面板
 * 用于编辑选中组件的属性
 */

type TabType = 'style' | 'data' | 'action'

const activeTab = ref<TabType>('style')

const tabs: { key: TabType; label: string; icon: string }[] = [
  { key: 'style', label: '样式', icon: 'i-carbon-paint-brush' },
  { key: 'data', label: '数据', icon: 'i-carbon-data-base' },
  { key: 'action', label: '交互', icon: 'i-carbon-touch-interaction' }
]

const hasSelection = ref(false)
</script>

<template>
  <div class="property-panel">
    <div class="panel-header">
      <span class="i-carbon-settings panel-icon"></span>
      <span class="panel-title">属性</span>
    </div>

    <!-- Tab 切换 -->
    <div class="panel-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="panel-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <span :class="tab.icon"></span>
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <!-- 内容区域 -->
    <div class="panel-content">
      <!-- 无选中状态 -->
      <div v-if="!hasSelection" class="empty-state">
        <div class="empty-icon">
          <span class="i-carbon-cursor-1"></span>
        </div>
        <p class="empty-text">选择一个组件以编辑属性</p>
      </div>

      <!-- 样式面板 -->
      <div v-else-if="activeTab === 'style'" class="tab-content">
        <div class="property-section">
          <div class="section-header">
            <span class="section-title">尺寸</span>
          </div>
          <div class="property-grid">
            <div class="property-item">
              <label class="property-label">宽度</label>
              <div class="property-input-group">
                <input type="text" class="property-input" value="auto" />
                <select class="property-unit">
                  <option>px</option>
                  <option>%</option>
                  <option>auto</option>
                </select>
              </div>
            </div>
            <div class="property-item">
              <label class="property-label">高度</label>
              <div class="property-input-group">
                <input type="text" class="property-input" value="auto" />
                <select class="property-unit">
                  <option>px</option>
                  <option>%</option>
                  <option>auto</option>
                </select>
              </div>
            </div>
          </div>
        </div>

        <div class="property-section">
          <div class="section-header">
            <span class="section-title">边距</span>
          </div>
          <div class="spacing-editor">
            <div class="spacing-box">
              <input type="text" class="spacing-input top" placeholder="0" />
              <input type="text" class="spacing-input right" placeholder="0" />
              <input type="text" class="spacing-input bottom" placeholder="0" />
              <input type="text" class="spacing-input left" placeholder="0" />
              <div class="spacing-center">
                <span class="spacing-label">margin</span>
              </div>
            </div>
          </div>
        </div>

        <div class="property-section">
          <div class="section-header">
            <span class="section-title">背景</span>
          </div>
          <div class="property-row">
            <label class="property-label">颜色</label>
            <div class="color-picker">
              <div class="color-preview" style="background: #ffffff"></div>
              <input type="text" class="color-input" value="#ffffff" />
            </div>
          </div>
        </div>
      </div>

      <!-- 数据面板 -->
      <div v-else-if="activeTab === 'data'" class="tab-content">
        <div class="empty-state small">
          <span class="i-carbon-data-base empty-icon-small"></span>
          <p class="empty-text">暂无数据绑定</p>
        </div>
      </div>

      <!-- 交互面板 -->
      <div v-else-if="activeTab === 'action'" class="tab-content">
        <div class="empty-state small">
          <span class="i-carbon-touch-interaction empty-icon-small"></span>
          <p class="empty-text">暂无交互事件</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.property-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e293b;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.panel-icon {
  font-size: 18px;
  color: #8b5cf6;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
}

/* Tab 切换 */
.panel-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  background: rgba(15, 23, 42, 0.3);
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.panel-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  justify-content: center;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.panel-tab:hover {
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
}

.panel-tab.active {
  color: #f1f5f9;
  background: rgba(51, 65, 85, 0.5);
}

.panel-tab span:first-child {
  font-size: 14px;
}

/* 内容区域 */
.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.tab-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-state.small {
  padding: 32px 24px;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  font-size: 28px;
  color: #475569;
  background: rgba(51, 65, 85, 0.3);
  border-radius: 16px;
}

.empty-icon-small {
  font-size: 32px;
  color: #475569;
  margin-bottom: 12px;
}

.empty-text {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

/* 属性区块 */
.property-section {
  background: rgba(15, 23, 42, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.2);
  border-radius: 8px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: rgba(51, 65, 85, 0.2);
  border-bottom: 1px solid rgba(71, 85, 105, 0.2);
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.property-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 12px;
}

.property-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
}

.property-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.property-label {
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
}

.property-input-group {
  display: flex;
  gap: 4px;
}

.property-input {
  flex: 1;
  min-width: 0;
  padding: 6px 8px;
  font-size: 12px;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
  transition: border-color 0.15s ease;
}

.property-input:focus {
  border-color: rgba(59, 130, 246, 0.5);
}

.property-unit {
  width: 56px;
  padding: 6px 4px;
  font-size: 11px;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
  cursor: pointer;
}

/* 边距编辑器 */
.spacing-editor {
  padding: 16px;
}

.spacing-box {
  position: relative;
  width: 100%;
  aspect-ratio: 2 / 1;
  min-height: 100px;
  border: 2px dashed rgba(71, 85, 105, 0.5);
  border-radius: 8px;
}

.spacing-input {
  position: absolute;
  width: 40px;
  padding: 4px;
  font-size: 11px;
  text-align: center;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
}

.spacing-input.top {
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
}

.spacing-input.right {
  top: 50%;
  right: -20px;
  transform: translateY(-50%);
}

.spacing-input.bottom {
  bottom: -12px;
  left: 50%;
  transform: translateX(-50%);
}

.spacing-input.left {
  top: 50%;
  left: -20px;
  transform: translateY(-50%);
}

.spacing-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.spacing-label {
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
}

/* 颜色选择器 */
.color-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.color-preview {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 2px solid rgba(71, 85, 105, 0.5);
  cursor: pointer;
}

.color-input {
  flex: 1;
  padding: 6px 8px;
  font-size: 12px;
  font-family: monospace;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 4px;
  outline: none;
}

/* 自定义滚动条 */
.panel-content::-webkit-scrollbar {
  width: 6px;
}

.panel-content::-webkit-scrollbar-track {
  background: transparent;
}

.panel-content::-webkit-scrollbar-thumb {
  background: rgba(71, 85, 105, 0.5);
  border-radius: 3px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: rgba(71, 85, 105, 0.8);
}
</style>
