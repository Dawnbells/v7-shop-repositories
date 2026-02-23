<script setup lang="ts">
/**
 * ComponentPanel - 左侧组件面板
 * 用于展示可拖拽的组件列表
 * 数据源：useBlockRegistry
 */

import type { ComponentMeta, ComponentCategory } from '~/types/component-meta'
import { useBlockRegistry } from '~/composables/useBlockRegistry'

// 分类配置：显示名称和图标
const CATEGORY_CONFIG: Record<ComponentCategory, { name: string; icon: string }> = {
  basic: { name: '基础组件', icon: 'i-carbon-cube' },
  layout: { name: '布局组件', icon: 'i-carbon-grid' },
  business: { name: '业务组件', icon: 'i-carbon-shopping-cart' },
  marketing: { name: '营销组件', icon: 'i-carbon-gift' },
  form: { name: '表单组件', icon: 'i-carbon-text-input' },
}

// 分类显示顺序
const CATEGORY_ORDER: ComponentCategory[] = ['basic', 'layout', 'business', 'marketing', 'form']

// 从注册表获取组件元数据
const { getGroupedBlockMetas } = useBlockRegistry()

// 搜索关键词
const searchQuery = ref('')

// 按分类分组的组件元数据
const groupedMetas = computed(() => getGroupedBlockMetas())

// 组件分组（带搜索过滤）
interface ComponentGroup {
  key: ComponentCategory
  name: string
  icon: string
  items: ComponentMeta[]
}

const componentGroups = computed<ComponentGroup[]>(() => {
  const query = searchQuery.value.trim().toLowerCase()
  const groups: ComponentGroup[] = []

  for (const category of CATEGORY_ORDER) {
    const config = CATEGORY_CONFIG[category]
    const metas = groupedMetas.value[category] || []

    // 过滤组件
    const filteredMetas = query
      ? metas.filter(meta => 
          meta.name.toLowerCase().includes(query) ||
          meta.type.toLowerCase().includes(query) ||
          meta.tags?.some(tag => tag.toLowerCase().includes(query))
        )
      : metas

    // 只添加有组件的分组
    if (filteredMetas.length > 0) {
      groups.push({
        key: category,
        name: config.name,
        icon: config.icon,
        items: filteredMetas,
      })
    }
  }

  return groups
})

// 默认展开所有分组
const expandedGroups = ref<string[]>(Object.values(CATEGORY_CONFIG).map(c => c.name))

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

// 拖拽开始事件
function onDragStart(event: DragEvent, meta: ComponentMeta) {
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData('application/json', JSON.stringify({
      type: meta.type,
      name: meta.name,
      defaultProps: meta.defaultProps,
      defaultStyle: meta.defaultStyle,
    }))
  }
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
        v-model="searchQuery"
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
                :key="item.type"
                class="component-item"
                draggable="true"
                :title="item.description"
                @dragstart="onDragStart($event, item)"
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
