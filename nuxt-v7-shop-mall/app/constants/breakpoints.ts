/**
 * 响应式断点配置
 *
 * 功能说明：
 * 定义主题编辑器和前端渲染的响应式断点，用于：
 * 1. 编辑器设备切换器 (DeviceSwitcher) 的预览宽度
 * 2. 前端设备检测 (useDeviceDetect) 的判断阈值
 * 3. 响应式样式计算 (useResponsive) 的媒体查询生成
 *
 * 断点设计说明：
 * 采用移动优先 (Mobile First) 的设计理念：
 * - mobile: < 768px   (手机)
 * - tablet: 768-1023px (平板)
 * - pc: >= 1024px     (桌面)
 *
 * 与 CSS 媒体查询的对应关系：
 * - mobile: @media (max-width: 767px)
 * - tablet: @media (min-width: 768px) and (max-width: 1023px)
 * - pc: @media (min-width: 1024px)
 */

import type { DeviceType, StyleDeviceType } from "~/types/schema";

/**
 * 断点配置接口
 */
export interface BreakpointConfig {
  /**
   * 预览宽度 (px)
   * 编辑器中该设备类型的默认预览宽度
   */
  width: number;

  /**
   * 显示标签
   * 用于 UI 展示的中文名称
   */
  label: string;

  /**
   * 图标类名
   * 用于 UI 展示的 UnoCSS 图标类名
   */
  icon: string;

  /**
   * 最小宽度 (px)
   * 对应 CSS: @media (min-width: ${minWidth}px)
   * 可选，mobile 设备没有最小宽度限制
   */
  minWidth?: number;

  /**
   * 最大宽度 (px)
   * 对应 CSS: @media (max-width: ${maxWidth}px)
   * 可选，pc 设备没有最大宽度限制
   */
  maxWidth?: number;
}

/**
 * 断点配置表
 *
 * 各设备类型的断点配置：
 *
 * | 设备   | 预览宽度 | 范围        | 媒体查询                              |
 * |--------|----------|-------------|---------------------------------------|
 * | mobile | 375px    | < 768px     | @media (max-width: 767px)             |
 * | tablet | 768px    | 768-1023px  | @media (min-width: 768px) and (max-width: 1023px) |
 * | pc     | 1024px   | >= 1024px   | @media (min-width: 1024px)            |
 *
 * 预览宽度说明：
 * - mobile: 375px 是 iPhone 6/7/8 的宽度，是最常见的移动设备基准
 * - tablet: 768px 是 iPad 竖屏的宽度
 * - pc: 1024px 是常见的桌面最小宽度
 */
export const BREAKPOINTS: Record<DeviceType, BreakpointConfig> = {
  mobile: {
    width: 375,      // iPhone 6/7/8 宽度
    label: "手机",
    icon: "i-carbon-phone",  // 使用 phone 而非 mobile
    maxWidth: 767,   // < 768px 为手机
  },
  tablet: {
    width: 768,      // iPad 竖屏宽度
    label: "平板",
    icon: "i-carbon-tablet",
    minWidth: 768,   // >= 768px
    maxWidth: 1023,  // < 1024px
  },
  pc: {
    width: 1024,     // 常见桌面最小宽度
    label: "电脑",
    icon: "i-carbon-laptop",
    minWidth: 1024,  // >= 1024px
  },
};

/**
 * 设备类型列表
 * 按从小到大的顺序排列，用于遍历和切换
 */
export const DEVICE_TYPES: DeviceType[] = ["mobile", "tablet", "pc"];

/**
 * 设备列表（别名）
 * 与 DEVICE_TYPES 相同，为了兼容性保留
 */
export const DEVICE_LIST: DeviceType[] = DEVICE_TYPES;

/**
 * 样式编辑设备配置（含通用 base）
 * 用于 PropertyPanel 的样式设备切换
 */
export const STYLE_BREAKPOINTS: Record<StyleDeviceType, { label: string; icon: string }> = {
  base: { label: "通用", icon: "i-carbon-globe" },
  mobile: { label: "手机", icon: "i-carbon-phone" },
  tablet: { label: "平板", icon: "i-carbon-tablet" },
  pc: { label: "电脑", icon: "i-carbon-laptop" },
};

/**
 * 样式编辑设备列表
 * 包含 base（通用）在最前面，按从小到大的顺序排列
 */
export const STYLE_DEVICE_LIST: StyleDeviceType[] = ["base", "mobile", "tablet", "pc"];

/**
 * 根据视口宽度获取设备类型
 *
 * 用于前端自动检测当前设备，以应用对应的响应式样式
 *
 * @param width - 视口宽度 (window.innerWidth)
 * @returns 设备类型
 *
 * @example
 * // 在 useDeviceDetect 中使用
 * const device = getDeviceType(window.innerWidth);
 * // width = 320 -> "mobile"
 * // width = 800 -> "tablet"
 * // width = 1440 -> "pc"
 */
export function getDeviceType(width: number): DeviceType {
  if (width < 768) return "mobile";
  if (width < 1024) return "tablet";
  return "pc";
}
