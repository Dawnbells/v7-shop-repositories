/**
 * 页面和组件 Schema 类型定义
 */

import type { CSSProperties } from "vue";
import type { EventBinding } from "./events";
import type { PageDataContext } from "./data-context";
import type { GlobalStyle } from "./theme";

// 全局样式引用（用于颜色等属性绑定到全局样式变量）
export interface GlobalStyleRef {
  type: 'global'
  key: keyof GlobalStyle  // 如 'primaryColor', 'secondaryColor' 等
}

// 颜色值类型：可以是静态值或全局样式引用
export type ColorValue = string | GlobalStyleRef

// 判断是否为全局样式引用
export function isGlobalStyleRef(value: any): value is GlobalStyleRef {
  return value && typeof value === 'object' && value.type === 'global' && 'key' in value
}

// 页面类型
export type PageType =
  | "home"
  | "product"
  | "checkout"
  | "order-result"
  | "article"
  | "custom";

// 设备类型
export type DeviceType = "pc" | "tablet" | "mobile";

// 样式编辑设备类型（含通用 base）
export type StyleDeviceType = "base" | DeviceType;

// 响应式样式
export interface ResponsiveStyle {
  base: CSSProperties; // 基础样式（所有设备）
  pc?: CSSProperties; // PC端样式 (>= 1024px)
  tablet?: CSSProperties; // 平板样式 (768px - 1023px)
  mobile?: CSSProperties; // 手机样式 (< 768px)
}

// 组件节点
export interface ComponentNode {
  id: string; // 唯一标识
  type: string; // 组件类型
  props: Record<string, any>; // 组件属性
  style: ResponsiveStyle; // 响应式样式
  events?: EventBinding[]; // 事件绑定
  children?: ComponentNode[]; // 子组件（容器类型）
  hidden?: boolean; // 是否隐藏
  locked?: boolean; // 是否锁定（编辑器中不可选中）
}

// 页面元信息
export interface PageMeta {
  title?: string;
  description?: string;
  keywords?: string;
}

// 页面 Schema
export interface PageSchema {
  id: string;
  name: string; // 页面名称
  pageType: PageType; // 页面类型
  components: ComponentNode[]; // 组件树
  meta?: PageMeta; // SEO 元信息
  dataContext?: PageDataContext; // 数据上下文
  layoutId?: string; // 可选：关联的布局 ID，undefined 表示不使用布局
}

// 自定义页面 Schema（带路由配置）
export interface CustomPageSchema extends PageSchema {
  slug: string; // 自定义路径，如 'activity/double11'
}

// 布局 Schema
export interface LayoutSchema {
  id: string;                     // 唯一标识
  name: string;                   // 布局名称，如 "default"
  components: ComponentNode[];    // 布局组件，包含 page-slot 占位
}

// 创建默认响应式样式
export function createDefaultStyle(base: CSSProperties = {}): ResponsiveStyle {
  return {
    base,
    pc: undefined,
    tablet: undefined,
    mobile: undefined,
  };
}

// 创建空页面 Schema
export function createEmptyPageSchema(
  id: string,
  name: string,
  pageType: PageType
): PageSchema {
  return {
    id,
    name,
    pageType,
    components: [],
    meta: {
      title: name,
    },
    dataContext: {
      preset: undefined,
      variables: [],
    },
  };
}
