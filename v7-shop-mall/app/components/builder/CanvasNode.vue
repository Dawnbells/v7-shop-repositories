<script setup lang="ts">
/**
 * CanvasNode - 递归渲染画布组件节点
 * 支持选中高亮、嵌套子组件、容器拖放
 */

import type { ComponentNode } from '~/types/component-meta'
import { useBlockRegistry } from '~/composables/useBlockRegistry'
import { useCanvasState } from '~/composables/useCanvasState'

interface Props {
  node: ComponentNode
  selectedId: string | null
  depth?: number
  isEditMode?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  depth: 0,
  isEditMode: true,
})

const emit = defineEmits<{
  select: [nodeId: string]
}>()

const { getBlock, getBlockMeta } = useBlockRegistry()
const { createNode, addNode, selectNode, removeNode, moveNode, findParentNode, rootNodes } = useCanvasState()

// 获取组件
const blockComponent = computed(() => {
  return getBlock(props.node.type)
})

// 获取组件元数据
const blockMeta = computed(() => {
  return getBlockMeta(props.node.type)
})

// 是否为容器组件
const isContainer = computed(() => {
  return blockMeta.value?.isContainer ?? false
})

// 是否被选中
const isSelected = computed(() => {
  return props.selectedId === props.node.id
})

// 是否隐藏
const isHidden = computed(() => {
  return props.node.hidden ?? false
})

// 容器是否为空
const isEmptyContainer = computed(() => {
  return isContainer.value && (!props.node.children || props.node.children.length === 0)
})

// 计算节点样式（使用 base 样式，后续可扩展响应式）
const nodeStyle = computed(() => {
  const style = props.node.style
  return style?.base || {}
})

// 点击选中节点
function onNodeClick(event: MouseEvent) {
  event.stopPropagation()
  emit('select', props.node.id)
}

// 显示名称
const displayName = computed(() => {
  return props.node.name || blockMeta.value?.name || props.node.type
})

// ============ 节点排序和删除逻辑 ============

// 获取当前节点在父容器中的位置信息
const nodePosition = computed(() => {
  const result = findParentNode(props.node.id)
  if (!result) return null
  const siblings = result.parent?.children || rootNodes.value
  return {
    index: result.index,
    total: siblings.length,
    parentId: result.parent?.id || null
  }
})

// 是否可以上移
const canMoveUp = computed(() => {
  return nodePosition.value !== null && nodePosition.value.index > 0
})

// 是否可以下移
const canMoveDown = computed(() => {
  return nodePosition.value !== null && nodePosition.value.index < nodePosition.value.total - 1
})

// 上移节点
function onMoveUp() {
  if (!nodePosition.value || !canMoveUp.value) return
  moveNode(props.node.id, nodePosition.value.parentId, nodePosition.value.index - 1)
}

// 下移节点（moveNode 先移除再插入，移除后下方元素索引减1，所以 +1 即可）
function onMoveDown() {
  if (!nodePosition.value || !canMoveDown.value) return
  moveNode(props.node.id, nodePosition.value.parentId, nodePosition.value.index + 1)
}

// 删除节点
function onDeleteNode() {
  removeNode(props.node.id)
}

// ============ 节点拖拽排序逻辑 ============

const isDragging = ref(false)
const dropPosition = ref<'before' | 'after' | null>(null)

// 开始拖拽节点
function onNodeDragStart(event: DragEvent) {
  if (!props.isEditMode || props.node.locked) {
    event.preventDefault()
    return
  }
  
  isDragging.value = true
  
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('application/json', JSON.stringify({
      action: 'move',
      nodeId: props.node.id,
      parentId: nodePosition.value?.parentId || null
    }))
  }
}

// 结束拖拽
function onNodeDragEnd() {
  isDragging.value = false
}

// 节点上拖拽经过
function onNodeDragOver(event: DragEvent) {
  if (!props.isEditMode) return
  
  // 检查是否是移动操作
  try {
    const types = event.dataTransfer?.types || []
    if (!types.includes('application/json')) return
  } catch {
    return
  }
  
  event.preventDefault()
  event.stopPropagation()
  
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  
  // 计算拖放位置（上半部 = before，下半部 = after）
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const midY = rect.top + rect.height / 2
  dropPosition.value = event.clientY < midY ? 'before' : 'after'
}

// 离开节点
function onNodeDragLeave(event: DragEvent) {
  // 检查是否真的离开了元素（而不是进入子元素）
  const relatedTarget = event.relatedTarget as Node | null
  const currentTarget = event.currentTarget as Node
  if (relatedTarget && currentTarget.contains(relatedTarget)) {
    return
  }
  dropPosition.value = null
}

// 在节点上放置
function onNodeDrop(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  
  const currentDropPosition = dropPosition.value
  dropPosition.value = null
  
  if (!event.dataTransfer || !currentDropPosition) return
  
  try {
    const data = JSON.parse(event.dataTransfer.getData('application/json'))
    
    // 处理移动操作
    if (data.action === 'move' && data.nodeId) {
      // 不能拖到自己身上
      if (data.nodeId === props.node.id) return
      
      // 计算目标位置
      const targetPosition = nodePosition.value
      if (!targetPosition) return
      
      let targetIndex = targetPosition.index
      if (currentDropPosition === 'after') {
        targetIndex += 1
      }
      
      // 如果是从同一个父容器内移动，且源位置在目标位置之前，需要调整索引
      if (data.parentId === targetPosition.parentId) {
        const sourceResult = findParentNode(data.nodeId)
        if (sourceResult && sourceResult.index < targetIndex) {
          targetIndex -= 1
        }
      }
      
      moveNode(data.nodeId, targetPosition.parentId, targetIndex)
      selectNode(data.nodeId)
      
      console.log('[CanvasNode] 移动组件:', data.nodeId, '-> 位置:', targetIndex)
    }
  } catch (error) {
    console.error('[CanvasNode] 解析拖放数据失败:', error)
  }
}

// ============ 容器拖放逻辑 ============

const isContainerDragOver = ref(false)

function onContainerDragOver(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
  isContainerDragOver.value = true
}

function onContainerDragLeave(event: DragEvent) {
  event.stopPropagation()
  isContainerDragOver.value = false
}

function onContainerDrop(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  isContainerDragOver.value = false

  if (!event.dataTransfer) return

  try {
    const data = JSON.parse(event.dataTransfer.getData('application/json'))
    
    if (data && data.type) {
      const newNode = createNode(
        data.type,
        data.defaultProps || {},
        data.defaultStyle || {},
        data.name
      )
      
      addNode(newNode, props.node.id)
      selectNode(newNode.id)
      
      console.log('[CanvasNode] 添加组件到容器:', data.type, '-> 容器:', props.node.id)
    }
  } catch (error) {
    console.error('[CanvasNode] 解析拖放数据失败:', error)
  }
}
</script>

<template>
  <div
    v-if="!isHidden"
    class="canvas-node"
    :class="{
      'is-selected': isSelected,
      'is-container': isContainer,
      'is-locked': node.locked,
      'is-dragging': isDragging,
    }"
    :data-node-id="node.id"
    :data-node-type="node.type"
    :draggable="isEditMode && !node.locked"
    @click="onNodeClick"
    @dragstart="onNodeDragStart"
    @dragend="onNodeDragEnd"
    @dragover="onNodeDragOver"
    @dragleave="onNodeDragLeave"
    @drop="onNodeDrop"
  >
    <!-- 拖放位置指示器 - 上方 -->
    <div v-if="dropPosition === 'before'" class="drop-indicator top" />
    <!-- 选中边框与操作栏 -->
    <div v-if="isSelected" class="node-selection-frame">
      <div class="node-label">
        <span v-if="blockMeta?.icon" :class="blockMeta.icon" class="node-icon" />
        <span class="node-name">{{ displayName }}</span>
      </div>
      <div class="node-actions">
        <button 
          class="action-btn" 
          :disabled="!canMoveUp" 
          title="上移" 
          @click.stop="onMoveUp"
        >
          <span class="i-carbon-arrow-up" />
        </button>
        <button 
          class="action-btn" 
          :disabled="!canMoveDown" 
          title="下移" 
          @click.stop="onMoveDown"
        >
          <span class="i-carbon-arrow-down" />
        </button>
        <button 
          class="action-btn delete-btn" 
          title="删除" 
          @click.stop="onDeleteNode"
        >
          <span class="i-carbon-trash-can" />
        </button>
      </div>
    </div>

    <!-- 动态渲染组件 -->
    <component
      :is="blockComponent"
      v-if="blockComponent"
      v-bind="node.props"
      :style="nodeStyle"
      class="node-content"
      @dragover="isContainer && isEditMode ? onContainerDragOver($event) : undefined"
      @dragleave="isContainer && isEditMode ? onContainerDragLeave($event) : undefined"
      @drop="isContainer && isEditMode ? onContainerDrop($event) : undefined"
    >
      <!-- 容器组件渲染子节点 -->
      <template v-if="isContainer">
        <!-- 渲染所有子节点 -->
        <BuilderCanvasNode
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :selected-id="selectedId"
          :depth="depth + 1"
          :is-edit-mode="isEditMode"
          @select="emit('select', $event)"
        />
        
        <!-- 编辑模式下显示拖放区域（有子组件时默认隐藏） -->
        <div
          v-if="isEditMode"
          class="container-drop-zone"
          :class="{ 
            'drag-over': isContainerDragOver,
            'is-empty': !node.children?.length 
          }"
          @dragover="onContainerDragOver"
          @dragleave="onContainerDragLeave"
          @drop="onContainerDrop"
        >
          <span class="i-carbon-add drop-zone-icon" />
          <span v-if="!node.children?.length" class="drop-zone-text">拖拽组件到此处</span>
        </div>
      </template>
    </component>

    <!-- 组件未找到的回退显示 -->
    <div v-else class="node-fallback">
      <span class="i-carbon-warning-alt" />
      <span>组件未找到: {{ node.type }}</span>
    </div>

    <!-- 拖放位置指示器 - 下方 -->
    <div v-if="dropPosition === 'after'" class="drop-indicator bottom" />
  </div>
</template>

<style scoped>
.canvas-node {
  position: relative;
  cursor: pointer;
  transition: outline 0.15s ease;
}

.canvas-node:hover {
  outline: 1px dashed #94a3b8;
  outline-offset: 2px;
}

.canvas-node.is-selected {
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}

.canvas-node.is-locked {
  cursor: not-allowed;
  opacity: 0.7;
}

/* 拖拽中状态 */
.canvas-node.is-dragging {
  opacity: 0.5;
}

/* 拖放位置指示器 */
.drop-indicator {
  position: absolute;
  left: 0;
  right: 0;
  height: 3px;
  background: #3b82f6;
  border-radius: 2px;
  pointer-events: none;
  z-index: 50;
}

.drop-indicator.top {
  top: -2px;
}

.drop-indicator.bottom {
  bottom: -2px;
}

/* 选中框标签 */
.node-selection-frame {
  position: absolute;
  top: -24px;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  pointer-events: none;
}

.node-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #fff;
  background: #3b82f6;
  border-radius: 4px 4px 0 0;
  white-space: nowrap;
}

.node-icon {
  font-size: 12px;
}

.node-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 节点操作按钮组 */
.node-actions {
  display: flex;
  gap: 2px;
  pointer-events: auto;
}

.node-actions .action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: #3b82f6;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.node-actions .action-btn:hover:not(:disabled) {
  background: #2563eb;
}

.node-actions .action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.node-actions .action-btn.delete-btn:hover:not(:disabled) {
  background: #ef4444;
}

/* 组件内容 */
.node-content {
  width: 100%;
}

/* 回退样式 */
.node-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  font-size: 14px;
  color: #ef4444;
  background: #fef2f2;
  border: 1px dashed #fca5a5;
  border-radius: 6px;
}

.node-fallback span[class^="i-"] {
  font-size: 18px;
}

/* 容器组件内边距 */
.canvas-node.is-container > .node-content {
  min-height: 60px;
  padding: 8px;
}

/* 容器拖放区域 - 有子组件时默认隐藏 */
.container-drop-zone {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #94a3b8;
  border-radius: 6px;
  transition: all 0.2s ease;
  cursor: default;
  /* 默认隐藏（有子组件时） */
  min-height: 0;
  padding: 0;
  margin: 0;
  border: none;
  opacity: 0;
  overflow: hidden;
}

/* 拖拽悬停时显示 */
.container-drop-zone.drag-over {
  min-height: 32px;
  padding: 8px 16px;
  border: 1px dashed #3b82f6;
  opacity: 1;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
}

/* 空容器时始终显示占位区域 */
.container-drop-zone.is-empty {
  flex-direction: column;
  min-height: 80px;
  padding: 24px;
  border: 2px dashed #cbd5e1;
  background: rgba(148, 163, 184, 0.05);
  opacity: 1;
}

.container-drop-zone.is-empty:hover {
  background: rgba(148, 163, 184, 0.1);
  border-color: #94a3b8;
}

.container-drop-zone.is-empty.drag-over {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
}

.drop-zone-icon {
  font-size: 16px;
}

.container-drop-zone.is-empty .drop-zone-icon {
  font-size: 24px;
}

.drop-zone-text {
  font-size: 13px;
  font-weight: 500;
}
</style>
