/**
 * 组件注册表 Composable
 *
 * 功能说明：
 * 管理主题编辑器和前端渲染所需的组件信息，采用双注册表设计：
 *
 * 1. 元数据注册表 (componentRegistry)
 *    - 存储组件的配置信息（属性定义、样式定义、分类等）
 *    - 用于编辑器的组件面板展示和属性面板编辑
 *    - 数据类型: ComponentMeta
 *
 * 2. 实例注册表 (componentInstanceRegistry)
 *    - 存储实际的 Vue 组件实例
 *    - 用于 ComponentRenderer 动态渲染组件
 *    - 数据类型: Vue Component
 *
 * 为什么需要两个注册表？
 * - 元数据用于编辑时的 UI 展示（图标、名称、可编辑属性等）
 * - 实例用于运行时的组件渲染（实际的 Vue 组件）
 * - 两者可以独立注册，支持懒加载场景
 *
 * 使用场景：
 * - 编辑器组件面板: getCategorizedComponents() 获取分类后的组件列表
 * - 属性面板: getComponentMeta(type) 获取组件的属性定义
 * - 动态渲染: getComponentInstance(type) 获取 Vue 组件进行渲染
 */

import type { Component } from "vue";
import type { ComponentMeta, ComponentCategory } from "~/types/builder";
import { COMPONENT_CATEGORY_LABELS } from "~/types/component-meta";

// ============================================================
// 全局状态（在 composable 外部定义，确保单例）
// ============================================================

/**
 * 组件元数据注册表
 * Map<组件类型, 组件元数据>
 * 例如: Map { "notice-bar" => { type: "notice-bar", name: "通知栏", ... } }
 */
const componentRegistry = ref<Map<string, ComponentMeta>>(new Map());

/**
 * 组件实例注册表
 * Map<组件类型, Vue组件>
 * 例如: Map { "notice-bar" => NoticeBar (Vue Component) }
 */
const componentInstanceRegistry = ref<Map<string, Component>>(new Map());

/**
 * 组件注册表 Composable
 *
 * @returns 注册表操作方法和状态
 */
export function useComponentRegistry() {
  // ============================================================
  // 元数据注册操作
  // ============================================================

  /**
   * 注册组件元数据
   * 通常在定义新组件时调用，提供组件的配置信息
   *
   * @param meta - 组件元数据，包含类型、名称、属性定义等
   *
   * @example
   * registerComponent({
   *   type: "notice-bar",
   *   name: "通知栏",
   *   icon: "i-carbon-notification",
   *   category: "marketing",
   *   propsSchema: [...],
   *   styleSchema: [...],
   *   defaultProps: { text: "公告内容" },
   *   defaultStyle: { base: {} }
   * });
   */
  function registerComponent(meta: ComponentMeta) {
    componentRegistry.value.set(meta.type, meta);
  }

  /**
   * 注销组件元数据
   * 用于动态移除组件（较少使用）
   *
   * @param type - 组件类型标识
   */
  function unregisterComponent(type: string) {
    componentRegistry.value.delete(type);
  }

  /**
   * 获取组件元数据
   * 用于属性面板获取组件的可编辑属性定义
   *
   * @param type - 组件类型标识
   * @returns 组件元数据，如果未注册则返回 undefined
   */
  function getComponentMeta(type: string): ComponentMeta | undefined {
    return componentRegistry.value.get(type);
  }

  // ============================================================
  // 组件实例管理
  // ============================================================

  /**
   * 注册组件实例（Vue 组件）
   * 由 register-components.ts 插件自动调用
   *
   * @param type - 组件类型标识（kebab-case 格式）
   * @param component - Vue 组件实例
   *
   * @example
   * // 在插件中自动注册
   * registerComponentInstance("notice-bar", NoticeBar);
   */
  function registerComponentInstance(type: string, component: Component) {
    componentInstanceRegistry.value.set(type, component);
  }

  /**
   * 批量注册组件实例
   * 用于一次性注册多个组件
   *
   * @param components - 组件映射对象 { 类型: 组件 }
   *
   * @example
   * registerComponentInstances({
   *   "notice-bar": NoticeBar,
   *   "product-card": ProductCard
   * });
   */
  function registerComponentInstances(components: Record<string, Component>) {
    for (const [type, component] of Object.entries(components)) {
      componentInstanceRegistry.value.set(type, component);
    }
  }

  /**
   * 注销组件实例
   *
   * @param type - 组件类型标识
   */
  function unregisterComponentInstance(type: string) {
    componentInstanceRegistry.value.delete(type);
  }

  /**
   * 获取组件实例
   * ComponentRenderer 使用此方法获取要渲染的 Vue 组件
   *
   * @param type - 组件类型标识
   * @returns Vue 组件实例，如果未注册则返回 undefined
   *
   * @example
   * // 在 ComponentRenderer 中
   * const instance = getComponentInstance(node.type);
   * // 然后使用 <component :is="instance" v-bind="props" />
   */
  function getComponentInstance(type: string): Component | undefined {
    return componentInstanceRegistry.value.get(type);
  }

  /**
   * 检查组件实例是否已注册
   *
   * @param type - 组件类型标识
   * @returns 是否已注册
   */
  function hasComponentInstance(type: string): boolean {
    return componentInstanceRegistry.value.has(type);
  }

  // ============================================================
  // 组件查询
  // ============================================================

  /**
   * 获取所有已注册的组件元数据
   *
   * @returns 组件元数据数组
   */
  function getAllComponents(): ComponentMeta[] {
    return Array.from(componentRegistry.value.values());
  }

  /**
   * 获取分类后的组件列表
   * 用于编辑器组件面板的分组展示
   *
   * @param isEditingLayout - 是否正在编辑布局
   *   - true: 显示所有组件（包括布局专用组件如 PageSlot）
   *   - false: 只显示非布局专用组件
   *
   * @returns 按分类分组的组件列表
   *
   * @example
   * // 返回值格式
   * [
   *   {
   *     category: "layout",
   *     label: "布局组件",
   *     components: [{ type: "page-slot", ... }]
   *   },
   *   {
   *     category: "marketing",
   *     label: "营销组件",
   *     components: [{ type: "notice-bar", ... }, { type: "countdown", ... }]
   *   }
   * ]
   */
  function getCategorizedComponents(isEditingLayout: boolean = false): Array<{
    category: ComponentCategory;
    label: string;
    components: ComponentMeta[];
  }> {
    const allComponents = getAllComponents();

    // 根据编辑模式过滤组件
    // layoutOnly 为 true 的组件（如 PageSlot）只在编辑布局时显示
    const filteredComponents = allComponents.filter((comp) => {
      if (isEditingLayout) {
        // 编辑布局时，显示所有组件
        return true;
      } else {
        // 编辑页面时，不显示布局专用组件
        return !comp.layoutOnly;
      }
    });

    // 按分类分组
    const categoryMap = new Map<ComponentCategory, ComponentMeta[]>();

    for (const comp of filteredComponents) {
      const list = categoryMap.get(comp.category) || [];
      list.push(comp);
      categoryMap.set(comp.category, list);
    }

    // 定义分类显示顺序
    // 布局组件放最前面，表单组件放最后面
    const categories: ComponentCategory[] = [
      "layout",    // 布局组件
      "basic",     // 基础组件
      "business",  // 业务组件
      "marketing", // 营销组件
      "form",      // 表单组件
    ];

    // 转换为数组格式，过滤掉没有组件的分类
    return categories
      .filter((cat) => categoryMap.has(cat))
      .map((cat) => ({
        category: cat,
        label: COMPONENT_CATEGORY_LABELS[cat],
        components: categoryMap.get(cat) || [],
      }));
  }

  // ============================================================
  // 计算属性
  // ============================================================

  /**
   * 已注册的组件元数据总数
   */
  const componentCount = computed(() => componentRegistry.value.size);

  // ============================================================
  // 返回公开的 API
  // ============================================================

  return {
    // 元数据注册操作
    registerComponent,
    unregisterComponent,

    // 元数据查询
    getComponentMeta,
    getAllComponents,
    getCategorizedComponents,

    // 组件实例注册操作
    registerComponentInstance,
    registerComponentInstances,
    unregisterComponentInstance,

    // 组件实例查询
    getComponentInstance,
    hasComponentInstance,

    // 统计
    componentCount,
  };
}
