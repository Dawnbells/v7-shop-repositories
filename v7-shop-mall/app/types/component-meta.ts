/**
 * 组件元数据类型定义
 * 用于描述组件的可编辑属性、样式、事件、页面和主题配置
 */

// ============ 基础类型 ============

/**
 * 属性编辑器类型
 */
export type PropEditorType =
  | "text"      // 单行文本
  | "textarea"  // 多行文本
  | "number"    // 数字
  | "switch"    // 开关
  | "select"    // 下拉选择
  | "radio"     // 单选
  | "color"     // 颜色选择器
  | "image"     // 图片上传
  | "richtext"  // 富文本
  | "json"      // JSON 编辑器
  | "icon"      // 图标选择器

/**
 * 下拉/单选选项
 */
export interface SelectOption {
  label: string
  value: any
}

/**
 * 组件分类
 */
export type ComponentCategory =
  | "basic"      // 基础组件：文本、图片、按钮、图标
  | "layout"     // 布局组件：容器、栅格、分割线、间距
  | "business"   // 业务组件：商品卡片、轮播图、导航栏、页脚
  | "marketing"  // 营销组件：优惠券、倒计时、促销标签
  | "form"       // 表单组件：输入框、选择器、复选框

/**
 * 页面范围（组件可用的页面类型）
 */
export type PageScope =
  | "layout"          // 布局页面
  | "home"            // 首页
  | "product-detail"  // 商品详情页
  | "order-result"    // 订单结果页
  | "article"         // 文章页
  | "checkout"        // 收银台页
  | "custom"          // 自定义页面

/**
 * 页面范围选项（用于 UI 选择器）
 */
export const PAGE_SCOPE_OPTIONS: { value: PageScope; label: string }[] = [
  { value: "layout", label: "布局" },
  { value: "home", label: "首页" },
  { value: "product-detail", label: "商品详情页" },
  { value: "order-result", label: "订单结果页" },
  { value: "article", label: "文章页" },
  { value: "checkout", label: "收银台" },
  { value: "custom", label: "自定义页面" },
]

/**
 * 样式编辑器类型
 */
export type StyleEditorType =
  | "size"        // 尺寸（宽高）
  | "spacing"     // 间距（margin/padding）
  | "background"  // 背景（颜色/图片/渐变）
  | "border"      // 边框（宽度/颜色/圆角）
  | "typography"  // 排版（字体/大小/颜色/对齐）
  | "shadow"      // 阴影
  | "position"    // 定位
  | "display"     // 显示模式
  | "flex"        // Flex 布局
  | "custom"      // 自定义 CSS

// ============ Schema 类型 ============

/**
 * 属性校验规则
 */
export interface PropValidation {
  pattern?: string   // 正则表达式字符串
  message?: string   // 校验失败提示信息
}

/**
 * 属性 Schema - 定义组件可编辑属性的结构
 */
export interface PropSchema {
  key: string                   // 属性键名
  label: string                 // 显示标签
  type: PropEditorType          // 编辑器类型
  defaultValue?: any            // 默认值
  placeholder?: string          // 占位符
  options?: SelectOption[]      // 下拉/单选选项
  description?: string          // 描述说明
  required?: boolean            // 是否必填
  group?: string                // 属性分组
  showIf?: string               // 条件显示表达式
  min?: number                  // 数字最小值
  max?: number                  // 数字最大值
  step?: number                 // 数字步进
  validation?: PropValidation   // 正则校验规则
}

/**
 * 样式 Schema - 定义组件可编辑样式的结构
 * 使用与 PropSchema 相同的编辑器类型，便于复用 PropertyField 组件
 */
export interface StyleSchema {
  key: string                   // CSS 属性名（如 'backgroundColor'）
  label: string                 // 显示标签
  type: PropEditorType          // 使用与属性相同的编辑器类型
  defaultValue?: any            // 默认值
  placeholder?: string          // 占位符
  options?: SelectOption[]      // 用于 select 类型
  group?: string                // 样式分组（如 'size', 'text', 'background'）
  responsive?: boolean          // 是否支持响应式
}

/**
 * 响应式样式 - 支持不同设备的样式配置
 * 渲染时按 { ...base, ...deviceStyle } 合并，设备特定样式覆盖通用样式
 */
export interface ResponsiveStyle {
  base?: Record<string, any>     // 通用样式（所有设备的默认值）
  desktop?: Record<string, any>  // 桌面端样式（覆盖 base）
  tablet?: Record<string, any>   // 平板端样式（覆盖 base）
  mobile?: Record<string, any>   // 移动端样式（覆盖 base）
}

// ============ 事件类型 ============

/**
 * 事件类型
 */
export type EventType =
  | "click"    // 点击
  | "hover"    // 悬停
  | "load"     // 加载完成
  | "visible"  // 进入视口

/**
 * 动作类型
 */
export type ActionType =
  | "navigate"   // 页面跳转
  | "openUrl"    // 打开链接
  | "showModal"  // 显示弹窗
  | "hideModal"  // 隐藏弹窗
  | "scrollTo"   // 滚动到指定位置
  | "addToCart"  // 加入购物车
  | "custom"     // 自定义 JS

/**
 * 事件 Schema - 定义组件支持的事件类型
 */
export interface EventSchema {
  event: EventType              // 事件类型（click/hover/load/visible）
  label: string                 // 显示名称
  description?: string          // 事件描述
}

/**
 * 事件触发器 - 定义组件交互事件配置
 */
export interface EventTrigger {
  id: string                    // 事件 ID
  event: EventType              // 触发事件
  action: ActionType            // 执行动作
  params?: Record<string, any>  // 动作参数
}

/**
 * 数据绑定 - 定义组件属性与变量的绑定关系
 */
export interface DataBinding {
  propKey: string      // 绑定的属性键
  variableKey: string  // 变量键名
  transform?: string   // 数据转换表达式
}

// ============ 组件类型 ============

/**
 * 组件元数据 - 组件的完整定义
 */
export interface ComponentMeta {
  type: string                        // 组件类型标识（唯一）
  name: string                        // 显示名称
  icon: string                        // 图标类名
  category: ComponentCategory         // 组件分类
  description?: string                // 组件描述

  propsSchema: PropSchema[]           // 可编辑属性定义
  styleSchema?: StyleSchema[]         // 可编辑样式定义（组件自定义）
  eventsSchema?: EventSchema[]        // 可触发事件定义

  defaultProps?: Record<string, any>  // 默认属性值
  defaultStyle?: ResponsiveStyle      // 默认样式值

  isContainer?: boolean               // 是否为容器组件
  allowChildren?: string[]            // 允许的子组件类型（空数组表示允许所有）
  maxChildren?: number                // 最大子组件数量

  allowedPages?: PageScope[]          // 允许使用的页面范围
                                      // undefined 或空数组表示不限制，所有页面可用

  singleton?: boolean                 // 是否单例（页面中只能存在一个）
  allowedPosition?: 'first' | 'last' | 'any'  // 允许的位置（默认 'any'）

  preview?: string                    // 预览图 URL
  tags?: string[]                     // 搜索标签
}

/**
 * 组件节点 - 画布中组件实例的数据结构
 */
export interface ComponentNode {
  id: string                     // 节点唯一 ID
  type: string                   // 组件类型（对应 ComponentMeta.type）
  name?: string                  // 自定义名称（用于图层面板）

  props: Record<string, any>     // 属性值
  style: ResponsiveStyle         // 样式值

  bindings?: DataBinding[]       // 属性数据绑定
  styleBindings?: DataBinding[]  // 样式数据绑定
  events?: EventTrigger[]        // 事件配置

  children?: ComponentNode[]     // 子组件

  locked?: boolean               // 是否锁定
  hidden?: boolean               // 是否隐藏
}

// ============ 页面类型 ============

/**
 * 页面类型
 */
export type PageType =
  | "layout"          // 布局页面（特殊，不能有 layoutId）
  | "home"            // 首页
  | "product-detail"  // 商品详情页
  | "order-result"    // 订单结果页
  | "article"         // 文章页
  | "checkout"        // 收银台页
  | "custom"          // 自定义页面

/**
 * 页面类型选项（用于 UI 选择器）
 */
export const PAGE_TYPE_OPTIONS: { value: PageType; label: string }[] = [
  { value: "layout", label: "布局" },
  { value: "home", label: "首页" },
  { value: "product-detail", label: "商品详情页" },
  { value: "order-result", label: "订单结果页" },
  { value: "article", label: "文章页" },
  { value: "checkout", label: "收银台" },
  { value: "custom", label: "自定义页面" },
]

/**
 * 页面 SEO 配置
 */
export interface PageSeo {
  title?: string
  description?: string
  keywords?: string
}

/**
 * 页面数据 - 完整页面的数据结构
 */
export interface PageData {
  id: string              // 页面 ID
  name: string            // 页面名称
  type: PageType          // 页面类型
  path?: string           // 页面路径

  layoutId?: string       // 布局页面 ID
                          // undefined 表示不使用布局
                          // 设置后该页面内容将嵌入到布局的插槽中

  root: ComponentNode     // 根组件节点

  seo?: PageSeo           // SEO 配置

  createdAt?: string
  updatedAt?: string
}

/**
 * 布局数据 - 布局页面的数据结构
 */
export interface LayoutData {
  id: string              // 布局 ID
  name: string            // 布局名称
  description?: string    // 布局描述

  root: ComponentNode     // 根组件节点（包含插槽占位符）

  createdAt?: string
  updatedAt?: string
}

// ============ 主题配置 ============

/**
 * 主题配置 - 整合页面和布局配置
 * 用于保存和加载主题的页面结构数据
 *
 * 注意：不包含以下内容（这些单独存储和管理）：
 * - 自定义变量定义和值（variables, variableValues）
 * - 站点配置（siteConfig）
 */
export interface ThemeConfig {
  id: string              // 主题 ID
  name: string            // 主题名称
  description?: string    // 主题描述
  version?: string        // 版本号

  layouts: LayoutData[]   // 所有布局数据
  pages: PageData[]       // 所有页面数据（非布局类型）

  createdAt?: string
  updatedAt?: string
  publishedAt?: string    // 发布时间
}
