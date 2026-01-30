<script setup lang="ts">
/**
 * 组件面板 - 左侧
 * 展示可用组件，支持拖拽到画布
 */

import type { ComponentMeta } from "~/types/builder";

// 组件注册表
const { getCategorizedComponents, componentCount } = useComponentRegistry();

// 当前页面状态
const { currentPageKey } = useCurrentPage();

// 是否在编辑布局
const isEditingLayout = computed(() => {
  return currentPageKey.value?.startsWith("layout-") ?? false;
});

// 拖拽
const { startDragNewComponent, endDrag } = useDragDrop();

// 分类后的组件（根据是否编辑布局显示不同组件）
const categorizedComponents = computed(() =>
  getCategorizedComponents(isEditingLayout.value)
);

// 搜索
const searchKeyword = ref("");

// 过滤后的组件
const filteredComponents = computed(() => {
  if (!searchKeyword.value) return categorizedComponents.value;

  const keyword = searchKeyword.value.toLowerCase();
  return categorizedComponents.value
    .map((group) => ({
      ...group,
      components: group.components.filter(
        (comp) =>
          comp.name.toLowerCase().includes(keyword) ||
          comp.type.toLowerCase().includes(keyword)
      ),
    }))
    .filter((group) => group.components.length > 0);
});

// 开始拖拽
function handleDragStart(event: DragEvent, meta: ComponentMeta) {
  startDragNewComponent(meta);
  event.dataTransfer?.setData("text/plain", meta.type);
}

// 结束拖拽
function handleDragEnd() {
  endDrag();
}
</script>

<template>
  <div class="component-panel">
    <div class="panel-header">
      <h3 class="panel-title">组件</h3>
      <span class="component-count">{{ componentCount }}</span>
    </div>

    <!-- 搜索 -->
    <div class="search-box">
      <span class="i-carbon-search search-icon"></span>
      <input
        v-model="searchKeyword"
        type="text"
        class="search-input"
        placeholder="搜索组件..."
      />
    </div>

    <!-- 组件列表 -->
    <div class="component-list">
      <template v-if="filteredComponents.length > 0">
        <div
          v-for="group in filteredComponents"
          :key="group.category"
          class="component-group"
        >
          <h4 class="group-title">{{ group.label }}</h4>
          <div class="group-items">
            <div
              v-for="comp in group.components"
              :key="comp.type"
              class="component-item"
              draggable="true"
              @dragstart="handleDragStart($event, comp)"
              @dragend="handleDragEnd"
            >
              <span :class="comp.icon" class="component-icon"></span>
              <span class="component-name">{{ comp.name }}</span>
            </div>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <span class="i-carbon-cube text-4xl text-gray-600 mb-2"></span>
        <p v-if="searchKeyword">未找到匹配的组件</p>
        <p v-else>暂无可用组件</p>
        <p class="text-sm text-gray-600 mt-1">请先注册组件到组件库</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.component-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* 确保所有元素使用 border-box */
.component-panel *,
.component-panel *::before,
.component-panel *::after {
  box-sizing: border-box;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #334155;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.component-count {
  padding: 2px 8px;
  font-size: 12px;
  background-color: #334155;
  border-radius: 10px;
  color: #94a3b8;
}

.search-box {
  position: relative;
  padding: 12px 16px;
  flex-shrink: 0;
}

.search-icon {
  position: absolute;
  left: 28px;
  top: 50%;
  transform: translateY(-50%);
  color: #64748b;
}

.search-input {
  width: 100%;
  padding: 8px 12px 8px 36px;
  font-size: 14px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #e2e8f0;
  outline: none;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #3b82f6;
}

.search-input::placeholder {
  color: #64748b;
}

.component-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px 16px;
  /* Firefox 滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.component-list::-webkit-scrollbar {
  width: 8px;
}

.component-list::-webkit-scrollbar-track {
  background: transparent;
}

.component-list::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.component-list::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

.component-group {
  margin-bottom: 16px;
}

.group-title {
  padding: 8px 0;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.group-items {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.component-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 8px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.component-item:hover {
  border-color: #3b82f6;
  background-color: #1e3a5f;
}

.component-item:active {
  cursor: grabbing;
}

.component-icon {
  font-size: 24px;
  color: #94a3b8;
}

.component-name {
  font-size: 12px;
  color: #cbd5e1;
  text-align: center;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
  color: #94a3b8;
}
</style>
