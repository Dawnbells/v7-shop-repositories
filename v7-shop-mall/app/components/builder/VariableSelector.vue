<script setup lang="ts">
/**
 * VariableSelector - 层级变量选择器
 * 以树形结构展示可绑定的数据源，支持：
 * - 按数据来源分组（页面预设数据、自定义变量、全局配置、全局样式）
 * - 按业务分类二级分组（产品信息、域名信息等）
 * - 搜索过滤
 * - 类型兼容性过滤
 */

import {
  type BindableDataSource,
  type SourceGroupHierarchy,
  type CategoryGroup,
  DATA_SOURCE_GROUP_CONFIG,
  groupDataSourcesHierarchically,
} from '~/utils/type-matching'

interface Props {
  dataSources: BindableDataSource[]
  modelValue: string
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '选择数据源',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'select': [source: BindableDataSource]
}>()

// 弹出层状态
const isOpen = ref(false)
const triggerRef = ref<HTMLElement | null>(null)

// 搜索关键词
const searchKeyword = ref('')

// 展开状态（记录哪些分组/分类是展开的）
const expandedGroups = ref<Set<string>>(new Set(['preset', 'variable']))
const expandedCategories = ref<Set<string>>(new Set())

// 层级分组后的数据
const groupedSources = computed(() => {
  return groupDataSourcesHierarchically(props.dataSources)
})

// 过滤后的数据
const filteredGroups = computed((): SourceGroupHierarchy[] => {
  const keyword = searchKeyword.value.toLowerCase().trim()
  if (!keyword) return groupedSources.value

  return groupedSources.value
    .map(group => ({
      ...group,
      categories: group.categories
        .map(category => ({
          ...category,
          sources: category.sources.filter(source =>
            source.label.toLowerCase().includes(keyword) ||
            source.key.toLowerCase().includes(keyword)
          ),
        }))
        .filter(category => category.sources.length > 0),
    }))
    .filter(group => group.categories.length > 0)
})

// 当前选中的数据源
const selectedSource = computed(() => {
  return props.dataSources.find(s => s.key === props.modelValue)
})

// 切换分组展开状态
function toggleGroup(groupKey: string) {
  if (expandedGroups.value.has(groupKey)) {
    expandedGroups.value.delete(groupKey)
  } else {
    expandedGroups.value.add(groupKey)
  }
}

// 切换分类展开状态
function toggleCategory(categoryKey: string) {
  if (expandedCategories.value.has(categoryKey)) {
    expandedCategories.value.delete(categoryKey)
  } else {
    expandedCategories.value.add(categoryKey)
  }
}

// 选择数据源
function selectSource(source: BindableDataSource) {
  emit('update:modelValue', source.key)
  emit('select', source)
  isOpen.value = false
  searchKeyword.value = ''
}

// 清除选择
function clearSelection() {
  emit('update:modelValue', '')
}

// 打开弹出层
function openPopover() {
  isOpen.value = true
  // 自动展开包含当前选中项的分组
  if (selectedSource.value) {
    expandedGroups.value.add(selectedSource.value.group)
    if (selectedSource.value.category) {
      expandedCategories.value.add(selectedSource.value.category)
    }
  }
}

// 关闭弹出层
function closePopover() {
  isOpen.value = false
  searchKeyword.value = ''
}

// 点击外部关闭
function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (triggerRef.value && !triggerRef.value.contains(target)) {
    closePopover()
  }
}

// 监听点击外部
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div ref="triggerRef" class="variable-selector">
    <!-- 触发按钮 -->
    <button
      type="button"
      class="selector-trigger"
      :class="{ 'has-value': selectedSource }"
      @click.stop="openPopover"
    >
      <template v-if="selectedSource">
        <span class="selected-category">{{ selectedSource.categoryLabel || selectedSource.group }}</span>
        <span class="selected-label">{{ selectedSource.label }}</span>
      </template>
      <span v-else class="placeholder">{{ placeholder }}</span>
      <span class="trigger-icon i-carbon-chevron-down" />
    </button>

    <!-- 清除按钮 -->
    <button
      v-if="selectedSource"
      type="button"
      class="clear-btn"
      title="取消绑定"
      @click.stop="clearSelection"
    >
      <span class="i-carbon-close" />
    </button>

    <!-- 弹出层 -->
    <Teleport to="body">
      <div v-if="isOpen" class="selector-popover" @click.stop>
        <!-- 搜索框 -->
        <div class="search-box">
          <span class="search-icon i-carbon-search" />
          <input
            v-model="searchKeyword"
            type="text"
            class="search-input"
            placeholder="搜索变量..."
            @keydown.esc="closePopover"
          />
        </div>

        <!-- 树形列表 -->
        <div class="tree-container">
          <template v-if="filteredGroups.length > 0">
            <div v-for="group in filteredGroups" :key="group.group" class="tree-group">
              <!-- 一级分组：数据来源 -->
              <button
                type="button"
                class="tree-group-header"
                @click="toggleGroup(group.group)"
              >
                <span
                  class="expand-icon"
                  :class="expandedGroups.has(group.group) ? 'i-carbon-chevron-down' : 'i-carbon-chevron-right'"
                />
                <span :class="group.icon" class="group-icon" />
                <span class="group-label">{{ group.label }}</span>
                <span class="group-count">{{ group.categories.reduce((sum, c) => sum + c.sources.length, 0) }}</span>
              </button>

              <!-- 展开的分组内容 -->
              <div v-show="expandedGroups.has(group.group)" class="tree-group-content">
                <template v-for="category in group.categories" :key="category.key">
                  <!-- 二级分类（如果只有一个分类且为默认分类 'other'，则不显示二级，直接展示字段） -->
                  <template v-if="group.categories.length > 1 || (category.key !== 'other' && category.label !== group.label)">
                    <button
                      type="button"
                      class="tree-category-header"
                      @click="toggleCategory(category.key)"
                    >
                      <span
                        class="expand-icon"
                        :class="expandedCategories.has(category.key) ? 'i-carbon-chevron-down' : 'i-carbon-chevron-right'"
                      />
                      <span class="category-label">{{ category.label }}</span>
                      <span class="category-count">{{ category.sources.length }}</span>
                    </button>

                    <div v-show="expandedCategories.has(category.key)" class="tree-category-content">
                      <button
                        v-for="source in category.sources"
                        :key="source.key"
                        type="button"
                        class="tree-item"
                        :class="{ active: source.key === modelValue }"
                        @click="selectSource(source)"
                      >
                        <span class="item-label">{{ source.label }}</span>
                        <span class="item-path">{{ source.key }}</span>
                      </button>
                    </div>
                  </template>

                  <!-- 单分类时直接显示字段 -->
                  <template v-else>
                    <button
                      v-for="source in category.sources"
                      :key="source.key"
                      type="button"
                      class="tree-item flat"
                      :class="{ active: source.key === modelValue }"
                      @click="selectSource(source)"
                    >
                      <span class="item-label">{{ source.label }}</span>
                      <span class="item-path">{{ source.key }}</span>
                    </button>
                  </template>
                </template>
              </div>
            </div>
          </template>

          <!-- 空状态 -->
          <div v-else class="empty-state">
            <span class="i-carbon-warning-alt empty-icon" />
            <p>{{ searchKeyword ? '未找到匹配的变量' : '暂无可绑定的数据源' }}</p>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.variable-selector {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
}

.selector-trigger {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  font-size: 13px;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.3);
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.selector-trigger:hover {
  border-color: rgba(71, 85, 105, 0.5);
}

.selector-trigger:focus {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
  outline: none;
}

.selector-trigger.has-value {
  border-color: rgba(59, 130, 246, 0.3);
  background: rgba(59, 130, 246, 0.05);
}

.selected-category {
  padding: 2px 6px;
  font-size: 10px;
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.1);
  border-radius: 4px;
  flex-shrink: 0;
}

.selected-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #3b82f6;
}

.placeholder {
  flex: 1;
  color: #64748b;
}

.trigger-icon {
  flex-shrink: 0;
  font-size: 12px;
  color: #64748b;
  transition: transform 0.15s ease;
}

.clear-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 14px;
  color: #94a3b8;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.clear-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

/* 弹出层 */
.selector-popover {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 9999;
  width: 360px;
  max-height: 480px;
  background: #1e293b;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 搜索框 */
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.search-icon {
  font-size: 14px;
  color: #64748b;
}

.search-input {
  flex: 1;
  padding: 0;
  font-size: 13px;
  color: #e2e8f0;
  background: transparent;
  border: none;
  outline: none;
}

.search-input::placeholder {
  color: #64748b;
}

/* 树形容器 */
.tree-container {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

/* 分组 */
.tree-group {
  margin-bottom: 4px;
}

.tree-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.3);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease;
}

.tree-group-header:hover {
  background: rgba(51, 65, 85, 0.5);
}

.expand-icon {
  font-size: 10px;
  color: #64748b;
}

.group-icon {
  font-size: 14px;
  color: #94a3b8;
}

.group-label {
  flex: 1;
}

.group-count {
  padding: 2px 6px;
  font-size: 10px;
  color: #64748b;
  background: rgba(100, 116, 139, 0.2);
  border-radius: 10px;
}

.tree-group-content {
  padding-left: 12px;
  margin-top: 4px;
}

/* 分类 */
.tree-category-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  font-size: 11px;
  color: #94a3b8;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease;
}

.tree-category-header:hover {
  background: rgba(51, 65, 85, 0.3);
}

.category-label {
  flex: 1;
}

.category-count {
  font-size: 10px;
  color: #64748b;
}

.tree-category-content {
  padding-left: 12px;
  margin-top: 2px;
}

/* 树项 */
.tree-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 6px 10px;
  font-size: 12px;
  color: #cbd5e1;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.tree-item.flat {
  padding-left: 22px;
}

.tree-item:hover {
  background: rgba(59, 130, 246, 0.1);
  color: #e2e8f0;
}

.tree-item.active {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.item-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-path {
  font-size: 10px;
  color: #64748b;
  font-family: 'Monaco', 'Menlo', monospace;
  flex-shrink: 0;
  margin-left: 8px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  color: #64748b;
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-state p {
  margin: 0;
  font-size: 13px;
}
</style>
