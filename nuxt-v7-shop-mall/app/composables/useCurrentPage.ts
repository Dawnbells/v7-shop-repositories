/**
 * 当前页面状态管理
 */

import { nanoid } from "nanoid";
import type {
  DeviceType,
  ComponentNode,
  ResponsiveStyle,
  PageSchema,
  LayoutSchema,
} from "~/types/builder";
import { createDefaultStyle } from "~/types/schema";

// 当前页面 key（如 'home', 'product', 'custom-xxx', 'layout-xxx'）
const currentPageKey = ref<string>("product");

// 当前设备类型
const currentDevice = ref<DeviceType>("mobile");

// 当前选中的组件 ID
const selectedComponentId = ref<string | null>(null);

// 剪贴板（用于复制粘贴）
const clipboard = ref<ComponentNode | null>(null);

export function useCurrentPage() {
  const { theme } = useThemeSchema();

  // 是否在编辑布局
  const isEditingLayout = computed(() => {
    return currentPageKey.value?.startsWith("layout-") ?? false;
  });

  // 当前布局（如果在编辑布局）
  const currentLayout = computed<LayoutSchema | null>(() => {
    if (!isEditingLayout.value || !theme.value) return null;
    const layoutId = currentPageKey.value.replace("layout-", "");
    return theme.value.pages.layouts.find((l) => l.id === layoutId) || null;
  });

  // 当前页面（如果在编辑页面）
  const currentPage = computed<PageSchema | null>(() => {
    if (isEditingLayout.value || !theme.value) return null;

    const key = currentPageKey.value;

    // 自定义页面
    if (key.startsWith("custom-")) {
      const customId = key.replace("custom-", "");
      return theme.value.pages.custom.find((p) => p.id === customId) || null;
    }

    // 标准页面
    const pageKey = key as keyof typeof theme.value.pages;
    const page = theme.value.pages[pageKey];

    if (Array.isArray(page)) return null;
    return page || null;
  });

  // 当前编辑的组件列表
  const components = computed<ComponentNode[]>(() => {
    if (isEditingLayout.value) {
      return currentLayout.value?.components || [];
    }
    return currentPage.value?.components || [];
  });

  // 当前选中的组件
  const selectedComponent = computed<ComponentNode | null>(() => {
    if (!selectedComponentId.value) return null;
    return findComponentById(components.value, selectedComponentId.value);
  });

  // 切换页面
  function switchPage(pageKey: string) {
    currentPageKey.value = pageKey;
    selectedComponentId.value = null;
  }

  // 切换设备
  function switchDevice(device: DeviceType) {
    currentDevice.value = device;
  }

  // 选中组件
  function selectComponent(componentId: string | null) {
    selectedComponentId.value = componentId;
  }

  // 递归查找组件
  function findComponentById(
    nodes: ComponentNode[],
    id: string
  ): ComponentNode | null {
    for (const node of nodes) {
      if (node.id === id) return node;
      if (node.children) {
        const found = findComponentById(node.children, id);
        if (found) return found;
      }
    }
    return null;
  }

  // 获取组件的父组件
  function findParentComponent(
    nodes: ComponentNode[],
    id: string,
    parent: ComponentNode | null = null
  ): ComponentNode | null {
    for (const node of nodes) {
      if (node.id === id) return parent;
      if (node.children) {
        const found = findParentComponent(node.children, id, node);
        if (found !== undefined) return found;
      }
    }
    return null;
  }

  // 添加组件
  function addComponent(
    type: string,
    props: Record<string, any> = {},
    style: ResponsiveStyle = createDefaultStyle(),
    parentId?: string,
    index?: number
  ): ComponentNode | null {
    if (!theme.value) return null;

    const newComponent: ComponentNode = {
      id: nanoid(),
      type,
      props,
      style,
    };

    const targetList = getTargetComponentList();
    if (!targetList) return null;

    if (parentId) {
      const parent = findComponentById(targetList, parentId);
      if (parent) {
        if (!parent.children) parent.children = [];
        if (index !== undefined) {
          parent.children.splice(index, 0, newComponent);
        } else {
          parent.children.push(newComponent);
        }
      }
    } else {
      if (index !== undefined) {
        targetList.splice(index, 0, newComponent);
      } else {
        targetList.push(newComponent);
      }
    }

    markThemeChanged();
    return newComponent;
  }

  // 获取目标组件列表
  function getTargetComponentList(): ComponentNode[] | null {
    if (!theme.value) return null;

    if (isEditingLayout.value) {
      const layoutId = currentPageKey.value.replace("layout-", "");
      const layout = theme.value.pages.layouts.find((l) => l.id === layoutId);
      return layout?.components || null;
    }

    const key = currentPageKey.value;
    if (key.startsWith("custom-")) {
      const customId = key.replace("custom-", "");
      const page = theme.value.pages.custom.find((p) => p.id === customId);
      return page?.components || null;
    }

    const pageKey = key as "home" | "product" | "orderResult" | "article" | "checkout";
    const page = theme.value.pages[pageKey];
    if (page && !Array.isArray(page)) {
      return page.components;
    }

    return null;
  }

  // 标记主题已更改
  function markThemeChanged() {
    if (theme.value) {
      theme.value.updatedAt = new Date().toISOString();
    }
  }

  // 更新组件属性
  function updateComponentProps(
    componentId: string,
    props: Record<string, any>
  ) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const component = findComponentById(targetList, componentId);
    if (component) {
      Object.assign(component.props, props);
      markThemeChanged();
    }
  }

  // 更新组件样式
  function updateComponentStyle(
    componentId: string,
    style: Partial<ResponsiveStyle>
  ) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const component = findComponentById(targetList, componentId);
    if (component) {
      Object.assign(component.style, style);
      markThemeChanged();
    }
  }

  // 删除组件
  function removeComponent(componentId: string) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const removed = removeComponentFromList(targetList, componentId);
    if (removed) {
      if (selectedComponentId.value === componentId) {
        selectedComponentId.value = null;
      }
      markThemeChanged();
    }
  }

  // 从列表中递归删除组件
  function removeComponentFromList(
    nodes: ComponentNode[],
    id: string
  ): boolean {
    const index = nodes.findIndex((n) => n.id === id);
    if (index !== -1) {
      nodes.splice(index, 1);
      return true;
    }

    for (const node of nodes) {
      if (node.children) {
        if (removeComponentFromList(node.children, id)) {
          return true;
        }
      }
    }

    return false;
  }

  // 移动组件
  function moveComponent(
    componentId: string,
    targetParentId: string | null,
    targetIndex: number
  ) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const component = findComponentById(targetList, componentId);
    if (!component) return;

    // 先删除
    removeComponentFromList(targetList, componentId);

    // 再添加到目标位置
    if (targetParentId) {
      const parent = findComponentById(targetList, targetParentId);
      if (parent) {
        if (!parent.children) parent.children = [];
        parent.children.splice(targetIndex, 0, component);
      }
    } else {
      targetList.splice(targetIndex, 0, component);
    }

    markThemeChanged();
  }

  // 上移组件
  function moveComponentUp(componentId: string) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const { list, index } = findComponentInList(targetList, componentId);
    if (list && index > 0) {
      const temp = list[index];
      list[index] = list[index - 1];
      list[index - 1] = temp;
      markThemeChanged();
    }
  }

  // 下移组件
  function moveComponentDown(componentId: string) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const { list, index } = findComponentInList(targetList, componentId);
    if (list && index < list.length - 1) {
      const temp = list[index];
      list[index] = list[index + 1];
      list[index + 1] = temp;
      markThemeChanged();
    }
  }

  // 在列表中查找组件及其索引
  function findComponentInList(
    nodes: ComponentNode[],
    id: string
  ): { list: ComponentNode[] | null; index: number } {
    const index = nodes.findIndex((n) => n.id === id);
    if (index !== -1) {
      return { list: nodes, index };
    }

    for (const node of nodes) {
      if (node.children) {
        const result = findComponentInList(node.children, id);
        if (result.list) return result;
      }
    }

    return { list: null, index: -1 };
  }

  // 是否可以上移
  function canMoveUp(componentId: string): boolean {
    const targetList = getTargetComponentList();
    if (!targetList) return false;

    const { list, index } = findComponentInList(targetList, componentId);
    return list !== null && index > 0;
  }

  // 是否可以下移
  function canMoveDown(componentId: string): boolean {
    const targetList = getTargetComponentList();
    if (!targetList) return false;

    const { list, index } = findComponentInList(targetList, componentId);
    return list !== null && index < list.length - 1;
  }

  // 复制组件到剪贴板
  function copyComponent(componentId: string) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const component = findComponentById(targetList, componentId);
    if (component) {
      // 深拷贝组件
      clipboard.value = JSON.parse(JSON.stringify(component));
    }
  }

  // 粘贴组件
  function pasteComponent(parentId?: string, index?: number): ComponentNode | null {
    const targetList = getTargetComponentList();
    if (!clipboard.value || !targetList) return null;

    // 深拷贝并重新生成 ID
    const regenerateIds = (node: ComponentNode): ComponentNode => {
      const newNode = { ...node, id: nanoid() };
      if (newNode.children) {
        newNode.children = newNode.children.map(regenerateIds);
      }
      return newNode;
    };

    const newComponent = regenerateIds(clipboard.value);

    if (parentId) {
      const parent = findComponentById(targetList, parentId);
      if (parent) {
        if (!parent.children) parent.children = [];
        if (typeof index === "number") {
          parent.children.splice(index, 0, newComponent);
        } else {
          parent.children.push(newComponent);
        }
      }
    } else {
      if (typeof index === "number") {
        targetList.splice(index, 0, newComponent);
      } else {
        targetList.push(newComponent);
      }
    }

    markThemeChanged();
    selectComponent(newComponent.id);
    return newComponent;
  }

  // 更新当前设备的样式
  function updateComponentDeviceStyle(
    componentId: string,
    deviceStyle: Record<string, any>
  ) {
    const targetList = getTargetComponentList();
    if (!targetList) return;

    const component = findComponentById(targetList, componentId);
    if (component) {
      const device = currentDevice.value;
      if (device === "pc") {
        component.style.pc = { ...component.style.pc, ...deviceStyle };
      } else if (device === "tablet") {
        component.style.tablet = { ...component.style.tablet, ...deviceStyle };
      } else {
        component.style.mobile = { ...component.style.mobile, ...deviceStyle };
      }
      markThemeChanged();
    }
  }

  return {
    // 状态
    currentPageKey: readonly(currentPageKey),
    currentDevice: readonly(currentDevice),
    selectedComponentId: readonly(selectedComponentId),
    clipboard: readonly(clipboard),

    // 计算属性
    isEditingLayout,
    currentLayout,
    currentPage,
    components,
    selectedComponent,

    // 页面/设备切换
    switchPage,
    switchDevice,

    // 组件选择
    selectComponent,

    // 组件操作
    addComponent,
    updateComponentProps,
    updateComponentStyle,
    updateComponentDeviceStyle,
    removeComponent,
    moveComponent,
    moveComponentUp,
    moveComponentDown,
    canMoveUp,
    canMoveDown,

    // 复制粘贴
    copyComponent,
    pasteComponent,

    // 工具函数
    findComponentById,
    findParentComponent,
  };
}
