<script setup lang="ts">
/**
 * CanvasNode - 递归渲染画布组件节点
 * 支持选中高亮、嵌套子组件、容器拖放
 */

import type { ComponentNode } from '~/types/component-meta'
import { useBlockRegistry } from '~/composables/useBlockRegistry'
import { useCanvasState } from '~/composables/useCanvasState'
import { useBindingResolver, type BindingContext } from '~/composables/useBindingResolver'
import { useThemeSchema } from '~/composables/useThemeSchema'
import { normalizeStyle } from '~/utils/style-normalizer'

interface Props {
  node: ComponentNode
  selectedId: string | null
  depth?: number
  isEditMode?: boolean
  device?: 'desktop' | 'tablet' | 'mobile' | 'custom'
  canvasWidth?: number
}

const props = withDefaults(defineProps<Props>(), {
  depth: 0,
  isEditMode: true,
  device: 'desktop',
  canvasWidth: 1200,
})

// 根据宽度判断实际设备类型
function getDeviceByWidth(width: number): 'mobile' | 'tablet' | 'desktop' {
  if (width <= 640) return 'mobile'
  if (width <= 768) return 'tablet'
  return 'desktop'
}

const emit = defineEmits<{
  select: [nodeId: string]
}>()

const { getBlock, getBlockMeta } = useBlockRegistry()
const { createNode, addNode, selectNode, removeNode, moveNode, findParentNode, findNodeById, rootNodes } = useCanvasState()

// 标记编辑器模式，让子组件知道当前在编辑器中
provide("isInEditor", ref(true));

// ============ 绑定解析（客户端渲染时使用） ============

const { variableValues, siteConfig } = useThemeSchema()
const { resolveNodeBindings, resolveNodeStyleBindings } = useBindingResolver()

// 注入页面数据（由页面级组件 provide）
const productData = inject<Ref<Record<string, any>>>('productData', ref({}))
const articleData = inject<Ref<Record<string, any>>>('articleData', ref({}))

// 构建绑定解析上下文
const bindingContext = computed<BindingContext>(() => ({
  custom: variableValues.value || {},
  siteConfig: siteConfig.value?.globalConfig || siteConfig.value || {},
  globalStyle: siteConfig.value?.globalStyle || {},
  product: productData.value,
  article: articleData.value,
}))

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

// 计算节点样式（解析绑定，实现所见即所得）
const nodeStyle = computed(() => {
  // 获取组件默认样式作为回退
  const defaultBaseStyle = blockMeta.value?.defaultStyle?.base || {}
  // 合并：默认样式 + 节点样式（节点样式优先）
  const baseStyle = { ...defaultBaseStyle, ...(props.node.style?.base || {}) }
  
  // 合并设备样式（mobile/tablet 等）
  const deviceKey = props.device === 'custom' 
    ? getDeviceByWidth(props.canvasWidth) 
    : props.device
  const defaultDeviceStyle = blockMeta.value?.defaultStyle?.[deviceKey] || {}
  const nodeDeviceStyle = props.node.style?.[deviceKey] || {}
  const deviceStyle = { ...defaultDeviceStyle, ...nodeDeviceStyle }
  
  const mergedStyle = { ...baseStyle, ...deviceStyle }
  
  // 解析样式绑定并合并（绑定值覆盖静态值）
  const boundStyle = resolveNodeStyleBindings(props.node, bindingContext.value)
  const finalStyle = { ...mergedStyle, ...boundStyle }
  
  // 规范化样式
  return normalizeStyle(finalStyle)
})

// 计算节点属性（解析绑定，实现所见即所得）
const nodeProps = computed(() => {
  // 获取组件默认属性作为回退
  const defaultProps = blockMeta.value?.defaultProps || {}
  // 合并：默认属性 + 节点属性（节点属性优先）
  const baseProps = { ...defaultProps, ...props.node.props }
  
  // 解析属性绑定并合并（绑定值覆盖静态值）
  const boundProps = resolveNodeBindings(props.node, bindingContext.value)
  return { ...baseProps, ...boundProps }
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
  selectNode(props.node.id)
}

// 下移节点（moveNode 先移除再插入，移除后下方元素索引减1，所以 +1 即可）
function onMoveDown() {
  if (!nodePosition.value || !canMoveDown.value) return
  moveNode(props.node.id, nodePosition.value.parentId, nodePosition.value.index + 1)
  selectNode(props.node.id)
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
  // 阻止事件冒泡，确保只有实际被拖拽的组件触发事件
  event.stopPropagation()
  
  if (!props.isEditMode || props.node.locked) {
    event.preventDefault()
    return
  }
  
  isDragging.value = true
  
  const dragData = {
    action: 'move',
    nodeId: props.node.id,
    parentId: nodePosition.value?.parentId || null
  }
  
  console.log('[CanvasNode] 开始拖拽节点:', {
    nodeId: props.node.id,
    nodeType: props.node.type,
    parentId: dragData.parentId,
    dragData
  })
  
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('application/json', JSON.stringify(dragData))
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
  
  console.log('[CanvasNode] onNodeDrop 被调用:', {
    targetNodeId: props.node.id,
    targetNodeType: props.node.type,
    currentDropPosition,
    hasDataTransfer: !!event.dataTransfer
  })
  
  if (!event.dataTransfer || !currentDropPosition) {
    console.log('[CanvasNode] onNodeDrop 提前返回: dataTransfer=', !!event.dataTransfer, 'dropPosition=', currentDropPosition)
    return
  }
  
  try {
    const data = JSON.parse(event.dataTransfer.getData('application/json'))
    console.log('[CanvasNode] onNodeDrop 解析数据:', data)
    
    // 处理移动操作
    if (data.action === 'move' && data.nodeId) {
      // 不能拖到自己身上
      if (data.nodeId === props.node.id) {
        console.log('[CanvasNode] onNodeDrop 阻止: 不能拖到自己身上')
        return
      }
      
      // 检查目标节点是否是被拖拽节点的后代（防止循环引用）
      // 如果把父节点拖到子节点旁边，会导致父节点被移除后子节点也消失
      const isTargetDescendantOfDragged = (targetId: string, draggedId: string): boolean => {
        let current = findParentNode(targetId)
        while (current) {
          if (current.parent?.id === draggedId) return true
          current = current.parent ? findParentNode(current.parent.id) : null
        }
        return false
      }
      
      if (isTargetDescendantOfDragged(props.node.id, data.nodeId)) {
        console.warn('[CanvasNode] onNodeDrop 阻止: 不能将父节点移动到其子节点旁边')
        return
      }
      
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

  console.log('[CanvasNode] onContainerDrop 被调用:', {
    containerId: props.node.id,
    containerType: props.node.type,
    childrenCount: props.node.children?.length || 0,
    hasDataTransfer: !!event.dataTransfer
  })

  if (!event.dataTransfer) {
    console.log('[CanvasNode] onContainerDrop 提前返回: 没有 dataTransfer')
    return
  }

  try {
    const rawData = event.dataTransfer.getData('application/json')
    console.log('[CanvasNode] onContainerDrop 原始数据:', rawData)
    
    const data = JSON.parse(rawData)
    console.log('[CanvasNode] onContainerDrop 解析数据:', data)
    
    // 处理移动现有组件到容器
    if (data.action === 'move' && data.nodeId) {
      console.log('[CanvasNode] 检测到移动操作，目标节点:', data.nodeId)
      
      // 不能拖到自己内部
      if (data.nodeId === props.node.id) {
        console.log('[CanvasNode] 阻止：不能拖到自己内部')
        return
      }
      
      // 检查是否是拖拽到自己的子节点（避免循环引用）
      const draggedNode = findNodeById(data.nodeId)
      console.log('[CanvasNode] 查找被拖拽节点:', draggedNode ? '找到' : '未找到')
      
      if (draggedNode) {
        // 检查当前容器是否是被拖拽节点的后代
        const isDescendant = (nodeId: string, ancestorId: string): boolean => {
          let current = findParentNode(nodeId)
          while (current) {
            if (current.parent?.id === ancestorId) return true
            current = current.parent ? findParentNode(current.parent.id) : null
          }
          return false
        }
        
        if (isDescendant(props.node.id, data.nodeId)) {
          console.warn('[CanvasNode] 阻止：不能将节点移动到其子节点内')
          return
        }
      }
      
      // 移动到容器末尾（传入当前子节点数量作为索引，添加到末尾）
      const targetIndex = props.node.children?.length || 0
      console.log('[CanvasNode] 执行 moveNode:', {
        nodeId: data.nodeId,
        targetContainerId: props.node.id,
        targetIndex
      })
      
      const moveResult = moveNode(data.nodeId, props.node.id, targetIndex)
      console.log('[CanvasNode] moveNode 结果:', moveResult)
      
      selectNode(data.nodeId)
      
      console.log('[CanvasNode] 移动组件到容器完成:', data.nodeId, '-> 容器:', props.node.id)
      return
    }
    
    // 处理添加新组件
    if (data && data.type) {
      console.log('[CanvasNode] 检测到添加新组件操作，类型:', data.type)
      
      const newNode = createNode(
        data.type,
        data.defaultProps || {},
        data.defaultStyle || {},
        data.name
      )
      
      addNode(newNode, props.node.id)
      selectNode(newNode.id)
      
      console.log('[CanvasNode] 添加组件到容器:', data.type, '-> 容器:', props.node.id)
    } else {
      console.log('[CanvasNode] 未识别的拖放数据格式:', data)
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
      v-bind="nodeProps"
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
          :device="device"
          :canvas-width="canvasWidth"
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
