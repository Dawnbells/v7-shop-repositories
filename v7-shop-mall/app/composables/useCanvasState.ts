/**
 * 画布状态管理
 * 管理画布上的组件节点树、选中状态、多页面数据等
 */

import type { ComponentNode, ResponsiveStyle, PageData, LayoutData, ThemeConfig, PageType } from '~/types/component-meta'
import { useBlockRegistry } from '~/composables/useBlockRegistry'

// 生成唯一 ID
function generateId(): string {
  return `node_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
}

// ============ 多页面数据存储 ============

/**
 * 页面信息（用于 TAB 显示）
 */
export interface PageInfo {
  id: string
  name: string
  type: 'page' | 'layout'
  pageType?: PageType
  removable?: boolean
  layoutId?: string
  presetIds?: string[]
}

// 页面数据存储（key 为页面 ID）
const pagesData = ref<Map<string, ComponentNode[]>>(new Map())

// 布局数据存储（key 为布局 ID）
const layoutsData = ref<Map<string, ComponentNode[]>>(new Map())

// 页面元信息存储（用于生成 TAB）
const pagesInfo = ref<Map<string, Omit<PageInfo, 'type'> & { pageType: PageType; layoutId?: string; presetIds?: string[] }>>(new Map())
const layoutsInfo = ref<Map<string, { id: string; name: string; description?: string }>>(new Map())

// 当前激活的页面/布局 ID
const currentPageId = ref<string>('home')

// 当前页面类型（page 或 layout）
const currentPageType = ref<'page' | 'layout'>('page')

// ============ 单页面状态 ============

// 画布组件节点树（根节点列表）- 当前页面的节点
const rootNodes = ref<ComponentNode[]>([])

// 当前选中的节点 ID
const selectedNodeId = ref<string | null>(null)

// 悬停的节点 ID（用于拖拽提示）
const hoveredNodeId = ref<string | null>(null)

// 画布脏状态（是否有未保存的更改）
const canvasDirty = ref(false)

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
      const node = nodes[i]!
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
    console.log('[useCanvasState] addNode 调用:', { 
      nodeId: node.id, 
      nodeType: node.type, 
      parentId, 
      index 
    })
    
    if (parentId) {
      const parent = findNodeById(parentId)
      if (!parent) {
        console.log('[useCanvasState] addNode 失败: 找不到父节点', parentId)
        return false
      }
      console.log('[useCanvasState] addNode 找到父节点:', { 
        parentId: parent.id, 
        parentType: parent.type,
        currentChildrenCount: parent.children?.length || 0
      })

      if (!parent.children) {
        parent.children = []
      }

      if (index !== undefined && index >= 0 && index <= parent.children.length) {
        parent.children.splice(index, 0, node)
        console.log('[useCanvasState] addNode 插入到索引:', index)
      } else {
        parent.children.push(node)
        console.log('[useCanvasState] addNode 添加到末尾')
      }
      console.log('[useCanvasState] addNode 完成, 新子节点数量:', parent.children.length)
    } else {
      if (index !== undefined && index >= 0 && index <= rootNodes.value.length) {
        rootNodes.value.splice(index, 0, node)
      } else {
        rootNodes.value.push(node)
      }
      console.log('[useCanvasState] addNode 添加到根节点, 总数:', rootNodes.value.length)
    }
    canvasDirty.value = true
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

    canvasDirty.value = true
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

    canvasDirty.value = true
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
    console.log('[useCanvasState] moveNode 调用:', { nodeId, newParentId, newIndex })
    
    const node = findNodeById(nodeId)
    if (!node) {
      console.log('[useCanvasState] moveNode 失败: 找不到节点', nodeId)
      return false
    }
    console.log('[useCanvasState] moveNode 找到节点:', { id: node.id, type: node.type })

    // 先移除节点
    const removed = removeNode(nodeId)
    if (!removed) {
      console.log('[useCanvasState] moveNode 失败: 移除节点失败')
      return false
    }
    console.log('[useCanvasState] moveNode 节点已移除')

    // 添加到新位置
    const addResult = addNode(node, newParentId || undefined, newIndex)
    console.log('[useCanvasState] moveNode addNode 结果:', addResult)
    
    return addResult
  }

  /**
   * 清空画布
   */
  function clearCanvas() {
    rootNodes.value = []
    selectedNodeId.value = null
    hoveredNodeId.value = null
    canvasDirty.value = true
  }

  /**
   * 导出画布数据（用于保存到数据库）
   * 非容器组件不保存 children 字段
   */
  function exportCanvasData(): ComponentNode[] {
    const { getBlockMeta } = useBlockRegistry()
    
    function cleanNode(node: ComponentNode): ComponentNode {
      const meta = getBlockMeta(node.type)
      const isContainer = meta?.isContainer ?? false
      
      const cleaned: ComponentNode = {
        id: node.id,
        type: node.type,
        name: node.name,
        props: node.props,
        style: node.style,
        bindings: node.bindings,
        styleBindings: node.styleBindings,
        events: node.events,
        locked: node.locked,
        hidden: node.hidden,
      }
      
      // 只有容器组件才保留 children
      if (isContainer && node.children?.length) {
        cleaned.children = node.children.map(cleanNode)
      }
      
      // 移除 undefined 字段
      return JSON.parse(JSON.stringify(cleaned))
    }
    
    return rootNodes.value.map(cleanNode)
  }

  /**
   * 导入画布数据（用于从数据库加载）
   */
  function importCanvasData(nodes: ComponentNode[]) {
    rootNodes.value = nodes || []
    selectedNodeId.value = null
    hoveredNodeId.value = null
  }

  // ============ 多页面管理方法 ============

  /**
   * 保存当前页面数据到存储
   */
  function saveCurrentPage() {
    const data = exportCanvasData()
    if (currentPageType.value === 'layout') {
      layoutsData.value.set(currentPageId.value, data)
    } else {
      pagesData.value.set(currentPageId.value, data)
    }
  }

  /**
   * 从存储加载指定页面数据到画布
   */
  function loadPageToCanvas(pageId: string, type: 'page' | 'layout') {
    let nodes: ComponentNode[] = []
    if (type === 'layout') {
      nodes = layoutsData.value.get(pageId) || []
    } else {
      nodes = pagesData.value.get(pageId) || []
    }
    importCanvasData(JSON.parse(JSON.stringify(nodes)))
  }

  /**
   * 切换页面
   * 自动保存当前页面并加载目标页面
   */
  function switchPage(pageId: string, type: 'page' | 'layout') {
    if (pageId === currentPageId.value && type === currentPageType.value) {
      return
    }

    // 保存当前页面
    saveCurrentPage()

    // 更新当前页面标识
    currentPageId.value = pageId
    currentPageType.value = type

    // 加载目标页面
    loadPageToCanvas(pageId, type)

    console.log(`[CanvasState] 切换到 ${type}: ${pageId}`)
  }

  /**
   * 从 ThemeConfig 初始化所有页面数据
   */
  function initializePages(config: { pages?: PageData[]; layouts?: LayoutData[] }) {
    // 清空现有数据
    pagesData.value.clear()
    layoutsData.value.clear()
    pagesInfo.value.clear()
    layoutsInfo.value.clear()

    // 初始化布局数据
    if (config.layouts?.length) {
      for (const layout of config.layouts) {
        const nodes = layout.root?.children || []
        layoutsData.value.set(layout.id, nodes)
        layoutsInfo.value.set(layout.id, {
          id: layout.id,
          name: layout.name,
          description: layout.description,
        })
      }
    }

    // 初始化页面数据
    if (config.pages?.length) {
      for (const page of config.pages) {
        const nodes = page.root?.children || []
        pagesData.value.set(page.id, nodes)
        pagesInfo.value.set(page.id, {
          id: page.id,
          name: page.name,
          pageType: page.type,
          removable: page.type === 'custom' || page.type === 'checkout',
          layoutId: page.layoutId,
        })
      }
    }

    // 如果没有任何页面，创建默认页面
    if (pagesInfo.value.size === 0) {
      createDefaultPages()
    }

    // 加载第一个页面（优先首页）
    const homePageId = Array.from(pagesInfo.value.entries())
      .find(([_, info]) => info.pageType === 'home')?.[0]
    const firstPageId = homePageId || pagesInfo.value.keys().next().value || 'home'
    
    currentPageId.value = firstPageId
    currentPageType.value = 'page'
    loadPageToCanvas(firstPageId, 'page')

    // 初始化后重置脏状态
    canvasDirty.value = false

    console.log('[CanvasState] 页面初始化完成:', {
      layouts: layoutsInfo.value.size,
      pages: pagesInfo.value.size,
      currentPage: currentPageId.value,
    })
  }

  /**
   * 创建默认页面结构
   */
  function createDefaultPages() {
    const defaultPages: Array<{ id: string; name: string; pageType: PageType; removable: boolean }> = [
      { id: 'home', name: '首页', pageType: 'home', removable: false },
      { id: 'product', name: '商品详情', pageType: 'product-detail', removable: false },
      { id: 'orderResult', name: '订单结果', pageType: 'order-result', removable: false },
      { id: 'article', name: '文章', pageType: 'article', removable: false },
    ]

    for (const page of defaultPages) {
      pagesData.value.set(page.id, [])
      pagesInfo.value.set(page.id, page)
    }

    // 创建默认布局
    layoutsData.value.set('layout-default', [])
    layoutsInfo.value.set('layout-default', {
      id: 'layout-default',
      name: '默认布局',
    })
  }

  /**
   * 导出所有页面/布局数据为 ThemeConfig 格式
   */
  function exportAllPagesData(): { pages: PageData[]; layouts: LayoutData[] } {
    // 先保存当前页面
    saveCurrentPage()

    const { getBlockMeta } = useBlockRegistry()

    function cleanNode(node: ComponentNode): ComponentNode {
      const meta = getBlockMeta(node.type)
      const isContainer = meta?.isContainer ?? false

      const cleaned: ComponentNode = {
        id: node.id,
        type: node.type,
        name: node.name,
        props: node.props,
        style: node.style,
        bindings: node.bindings,
        styleBindings: node.styleBindings,
        events: node.events,
        locked: node.locked,
        hidden: node.hidden,
      }

      if (isContainer && node.children?.length) {
        cleaned.children = node.children.map(cleanNode)
      }

      return JSON.parse(JSON.stringify(cleaned))
    }

    const now = new Date().toISOString()

    // 导出页面
    const pages: PageData[] = []
    for (const [pageId, nodes] of pagesData.value.entries()) {
      const info = pagesInfo.value.get(pageId)
      if (!info) continue

      pages.push({
        id: pageId,
        name: info.name,
        type: info.pageType,
        layoutId: info.layoutId,
        root: {
          id: `root_${pageId}`,
          type: 'column',
          props: {},
          style: {},
          children: nodes.map(cleanNode),
        },
        updatedAt: now,
      })
    }

    // 导出布局
    const layouts: LayoutData[] = []
    for (const [layoutId, nodes] of layoutsData.value.entries()) {
      const info = layoutsInfo.value.get(layoutId)
      if (!info) continue

      layouts.push({
        id: layoutId,
        name: info.name,
        description: info.description,
        root: {
          id: `root_${layoutId}`,
          type: 'column',
          props: {},
          style: {},
          children: nodes.map(cleanNode),
        },
        updatedAt: now,
      })
    }

    return { pages, layouts }
  }

  /**
   * 创建新页面
   */
  function createPage(
    pageId: string,
    name: string,
    pageType: PageType,
    layoutId?: string,
    presetIds?: string[]
  ): boolean {
    if (pagesData.value.has(pageId)) {
      console.warn(`[CanvasState] 页面 ${pageId} 已存在`)
      return false
    }

    pagesData.value.set(pageId, [])
    pagesInfo.value.set(pageId, {
      id: pageId,
      name,
      pageType,
      removable: true,
      layoutId,
      presetIds,
    })

    canvasDirty.value = true
    console.log(`[CanvasState] 创建页面: ${pageId}, presetIds: ${presetIds?.join(', ') || 'none'}`)
    return true
  }

  /**
   * 创建新布局
   */
  function createLayout(layoutId: string, name: string, description?: string): boolean {
    if (layoutsData.value.has(layoutId)) {
      console.warn(`[CanvasState] 布局 ${layoutId} 已存在`)
      return false
    }

    layoutsData.value.set(layoutId, [])
    layoutsInfo.value.set(layoutId, {
      id: layoutId,
      name,
      description,
    })

    canvasDirty.value = true
    console.log(`[CanvasState] 创建布局: ${layoutId}`)
    return true
  }

  /**
   * 删除页面
   */
  function removePage(pageId: string): boolean {
    const info = pagesInfo.value.get(pageId)
    if (!info || !info.removable) {
      console.warn(`[CanvasState] 页面 ${pageId} 不可删除`)
      return false
    }

    pagesData.value.delete(pageId)
    pagesInfo.value.delete(pageId)

    // 如果删除的是当前页面，切换到首页
    if (currentPageId.value === pageId && currentPageType.value === 'page') {
      const homeId = 'home'
      if (pagesData.value.has(homeId)) {
        switchPage(homeId, 'page')
      }
    }

    canvasDirty.value = true
    console.log(`[CanvasState] 删除页面: ${pageId}`)
    return true
  }

  /**
   * 删除布局
   */
  function removeLayout(layoutId: string): boolean {
    if (!layoutsData.value.has(layoutId)) {
      return false
    }

    layoutsData.value.delete(layoutId)
    layoutsInfo.value.delete(layoutId)

    // 如果删除的是当前布局，切换到首页
    if (currentPageId.value === layoutId && currentPageType.value === 'layout') {
      switchPage('home', 'page')
    }

    canvasDirty.value = true
    console.log(`[CanvasState] 删除布局: ${layoutId}`)
    return true
  }

  /**
   * 获取所有页面信息（用于 TAB 显示）
   */
  const allPagesInfo = computed<PageInfo[]>(() => {
    const result: PageInfo[] = []

    // 添加布局（需带 pageType: layout，供组件面板 allowedPages 与属性面板过滤）
    for (const [id, info] of layoutsInfo.value.entries()) {
      result.push({
        id,
        name: info.name,
        type: 'layout',
        pageType: 'layout',
        removable: false,
      })
    }

    // 添加页面
    for (const [id, info] of pagesInfo.value.entries()) {
      result.push({
        id,
        name: info.name,
        type: 'page',
        pageType: info.pageType,
        removable: info.removable,
        layoutId: info.layoutId,
        presetIds: info.presetIds,
      })
    }

    return result
  })

  /**
   * 获取可用布局列表（用于页面选择布局）
   */
  const availableLayouts = computed<Array<{ id: string; name: string }>>(() => {
    const result: Array<{ id: string; name: string }> = []
    for (const [id, info] of layoutsInfo.value.entries()) {
      result.push({ id, name: info.name })
    }
    return result
  })

  /**
   * 更新页面的布局选择
   */
  function updatePageLayout(pageId: string, layoutId: string | undefined): boolean {
    const info = pagesInfo.value.get(pageId)
    if (!info) {
      console.warn(`[CanvasState] 页面 ${pageId} 不存在`)
      return false
    }

    pagesInfo.value.set(pageId, {
      ...info,
      layoutId,
    })

    canvasDirty.value = true
    console.log(`[CanvasState] 更新页面 ${pageId} 的布局为: ${layoutId || '无'}`)
    return true
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

  /**
   * 画布是否有未保存的更改
   */
  const canvasHasUnsavedChanges = computed(() => canvasDirty.value)

  /**
   * 标记画布为已保存状态
   */
  function markCanvasSaved() {
    canvasDirty.value = false
  }

  return {
    // 单页面状态
    rootNodes: readonly(rootNodes),
    selectedNodeId: readonly(selectedNodeId),
    hoveredNodeId: readonly(hoveredNodeId),
    selectedNode,
    isEmpty,

    // 多页面状态
    currentPageId: readonly(currentPageId),
    currentPageType: readonly(currentPageType),
    allPagesInfo,
    availableLayouts,

    // 单页面方法
    createNode,
    addNode,
    removeNode,
    updateNode,
    selectNode,
    setHoveredNode,
    moveNode,
    clearCanvas,
    findNodeById,
    findParentNode,
    exportCanvasData,
    importCanvasData,

    // 多页面方法
    switchPage,
    initializePages,
    exportAllPagesData,
    createPage,
    createLayout,
    removePage,
    removeLayout,
    saveCurrentPage,
    updatePageLayout,

    // 未保存状态
    canvasHasUnsavedChanges,
    markCanvasSaved,
  }
}
