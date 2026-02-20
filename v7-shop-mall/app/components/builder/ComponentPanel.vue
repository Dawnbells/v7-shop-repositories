<script setup lang="ts">
/**
 * ComponentPanel - 左侧组件面板
 * 用于展示可拖拽的组件列表
 */

const componentGroups = [
  {
    name: '基础组件',
    icon: 'i-carbon-cube',
    items: [
      { name: '文本', icon: 'i-carbon-text-font' },
      { name: '图片', icon: 'i-carbon-image' },
      { name: '按钮', icon: 'i-carbon-touch-1' },
      { name: '图标', icon: 'i-carbon-star' },
    ]
  },
  {
    name: '布局组件',
    icon: 'i-carbon-grid',
    items: [
      { name: '容器', icon: 'i-carbon-box' },
      { name: '栅格', icon: 'i-carbon-column' },
      { name: '分割线', icon: 'i-carbon-subtract' },
      { name: '间距', icon: 'i-carbon-arrows-vertical' },
    ]
  },
  {
    name: '业务组件',
    icon: 'i-carbon-shopping-cart',
    items: [
      { name: '商品卡片', icon: 'i-carbon-product' },
      { name: '轮播图', icon: 'i-carbon-carousel-horizontal' },
      { name: '导航栏', icon: 'i-carbon-menu' },
      { name: '页脚', icon: 'i-carbon-footer' },
    ]
  }
]

const expandedGroups = ref<string[]>(['基础组件', '布局组件', '业务组件'])

function toggleGroup(name: string) {
  const index = expandedGroups.value.indexOf(name)
  if (index > -1) {
    expandedGroups.value.splice(index, 1)
  } else {
    expandedGroups.value.push(name)
  }
}

function isExpanded(name: string) {
  return expandedGroups.value.includes(name)
}
</script>

<template>
  <div class="component-panel">
    <div class="panel-header">
      <span class="i-carbon-apps panel-icon"></span>
      <span class="panel-title">组件</span>
    </div>

    <div class="panel-search">
      <span class="i-carbon-search search-icon"></span>
      <input 
        type="text" 
        class="search-input" 
        placeholder="搜索组件..."
      />
    </div>

    <div class="component-groups">
      <div 
        v-for="group in componentGroups" 
        :key="group.name"
        class="component-group"
      >
        <button 
          class="group-header"
          @click="toggleGroup(group.name)"
        >
          <span :class="group.icon" class="group-icon"></span>
          <span class="group-name">{{ group.name }}</span>
          <span 
            class="i-carbon-chevron-down expand-icon"
            :class="{ expanded: isExpanded(group.name) }"
          ></span>
        </button>

        <Transition name="collapse">
          <div v-if="isExpanded(group.name)" class="group-content">
            <div class="component-grid">
              <div 
                v-for="item in group.items"
                :key="item.name"
                class="component-item"
                draggable="true"
              >
                <div class="item-icon">
                  <span :class="item.icon"></span>
                </div>
                <span class="item-name">{{ item.name }}</span>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<style scoped>
.component-panel {
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
  color: #3b82f6;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
}

.panel-search {
  position: relative;
  padding: 12px 16px;
}

.search-icon {
  position: absolute;
  left: 28px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: #64748b;
}

.search-input {
  width: 100%;
  padding: 8px 12px 8px 36px;
  font-size: 13px;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s ease;
}

.search-input::placeholder {
  color: #64748b;
}

.search-input:focus {
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(15, 23, 42, 0.8);
}

.component-groups {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.component-group {
  margin-bottom: 4px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.group-header:hover {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.3);
}

.group-icon {
  font-size: 16px;
}

.group-name {
  flex: 1;
  text-align: left;
}

.expand-icon {
  font-size: 12px;
  transition: transform 0.2s ease;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.group-content {
  padding: 8px 0 8px 8px;
}

.component-grid {
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
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(71, 85, 105, 0.2);
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s ease;
}

.component-item:hover {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.component-item:active {
  cursor: grabbing;
  transform: scale(0.98);
}

.item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 18px;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.component-item:hover .item-icon {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.15);
}

.item-name {
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}

.component-item:hover .item-name {
  color: #e2e8f0;
}

/* 折叠动画 */
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  max-height: 0;
}

.collapse-enter-to,
.collapse-leave-from {
  opacity: 1;
  max-height: 500px;
}

/* 自定义滚动条 */
.component-groups::-webkit-scrollbar {
  width: 6px;
}

.component-groups::-webkit-scrollbar-track {
  background: transparent;
}

.component-groups::-webkit-scrollbar-thumb {
  background: rgba(71, 85, 105, 0.5);
  border-radius: 3px;
}

.component-groups::-webkit-scrollbar-thumb:hover {
  background: rgba(71, 85, 105, 0.8);
}
</style>
