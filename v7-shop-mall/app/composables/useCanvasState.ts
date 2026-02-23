/**
 * 画布状态管理
 * 管理画布上的组件节点树、选中状态等
 */

import type { ComponentNode, ResponsiveStyle } from '~/types/component-meta'

// 生成唯一 ID
function generateId(): string {
  return `node_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
}

// 画布组件节点树（根节点列表）
const rootNodes = ref<ComponentNode[]>([])

// 当前选中的节点 ID
const selectedNodeId = ref<string | null>(null)

// 悬停的节点 ID（用于拖拽提示）
const hoveredNodeId = ref<string | null>(null)

/**
 * 画布状态管理 composable
 */
export function useCanvasState() {
  /**
   * 根据 ID 查找节点（递归）
   */
  function findNodeById(
    nodeId: string,
    nodes: ComponentNode[] = rootNodes.value
  ): ComponentNode | null {
    for (const node of nodes) {
      if (node.id === nodeId) {
        return node
      }
      if (node.children?.length) {
        const found = findNodeById(nodeId, node.children)
        if (found) return found
      }
    }
    return null
  }

  /**
   * 查找节点的父节点
   */
  function findParentNode(
    nodeId: string,
    nodes: ComponentNode[] = rootNodes.value,
    parent: ComponentNode | null = null
  ): { parent: ComponentNode | null; index: number } | null {
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i]
      if (node.id === nodeId) {
        return { parent, index: i }
      }
      if (node.children?.length) {
        const found = findParentNode(nodeId, node.children, node)
        if (found) return found
      }
    }
    return null
  }

  /**
   * 创建新节点
   */
  function createNode(
    type: string,
    props: Record<string, any> = {},
    style: ResponsiveStyle = {},
    name?: string
  ): ComponentNode {
    return {
      id: generateId(),
      type,
      name,
      props,
      style,
      children: [],
    }
  }

  /**
   * 添加节点到画布
   * @param node 要添加的节点
   * @param parentId 父节点 ID（可选，不传则添加到根节点）
   * @param index 插入位置（可选，不传则添加到末尾）
   */
  function addNode(
    node: ComponentNode,
    parentId?: string,
    index?: number
  ): boolean {
    if (parentId) {
      const parent = findNodeById(parentId)
      if (!parent) return false

      if (!parent.children) {
        parent.children = []
      }

      if (index !== undefined && index >= 0 && index <= parent.children.length) {
        parent.children.splice(index, 0, node)
      } else {
        parent.children.push(node)
      }
    } else {
      if (index !== undefined && index >= 0 && index <= rootNodes.value.length) {
        rootNodes.value.splice(index, 0, node)
      } else {
        rootNodes.value.push(node)
      }
    }
    return true
  }

  /**
   * 删除节点
   */
  function removeNode(nodeId: string): boolean {
    const result = findParentNode(nodeId)
    if (!result) return false

    const { parent, index } = result
    if (parent) {
      parent.children?.splice(index, 1)
    } else {
      rootNodes.value.splice(index, 1)
    }

    // 如果删除的是选中节点，清除选中状态
    if (selectedNodeId.value === nodeId) {
      selectedNodeId.value = null
    }

    return true
  }

  /**
   * 更新节点属性
   */
  function updateNode(
    nodeId: string,
    updates: Partial<Pick<ComponentNode, 'props' | 'style' | 'name' | 'locked' | 'hidden'>>
  ): boolean {
    const node = findNodeById(nodeId)
    if (!node) return false

    if (updates.props !== undefined) {
      node.props = { ...node.props, ...updates.props }
    }
    if (updates.style !== undefined) {
      node.style = { ...node.style, ...updates.style }
    }
    if (updates.name !== undefined) {
      node.name = updates.name
    }
    if (updates.locked !== undefined) {
      node.locked = updates.locked
    }
    if (updates.hidden !== undefined) {
      node.hidden = updates.hidden
    }

    return true
  }

  /**
   * 选中节点
   */
  function selectNode(nodeId: string | null) {
    selectedNodeId.value = nodeId
  }

  /**
   * 设置悬停节点
   */
  function setHoveredNode(nodeId: string | null) {
    hoveredNodeId.value = nodeId
  }

  /**
   * 移动节点到新位置
   */
  function moveNode(
    nodeId: string,
    newParentId: string | null,
    newIndex: number
  ): boolean {
    const node = findNodeById(nodeId)
    if (!node) return false

    // 先移除节点
    const removed = removeNode(nodeId)
    if (!removed) return false

    // 添加到新位置
    return addNode(node, newParentId || undefined, newIndex)
  }

  /**
   * 清空画布
   */
  function clearCanvas() {
    rootNodes.value = []
    selectedNodeId.value = null
    hoveredNodeId.value = null
  }

  /**
   * 获取选中的节点
   */
  const selectedNode = computed(() => {
    if (!selectedNodeId.value) return null
    return findNodeById(selectedNodeId.value)
  })

  /**
   * 画布是否为空
   */
  const isEmpty = computed(() => rootNodes.value.length === 0)

  return {
    // 状态
    rootNodes: readonly(rootNodes),
    selectedNodeId: readonly(selectedNodeId),
    hoveredNodeId: readonly(hoveredNodeId),
    selectedNode,
    isEmpty,

    // 方法
    createNode,
    addNode,
    removeNode,
    updateNode,
    selectNode,
    setHoveredNode,
    moveNode,
    clearCanvas,
    findNodeById,
  }
}
