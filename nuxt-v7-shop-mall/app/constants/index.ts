/**
 * 常量统一导出入口
 *
 * 功能说明：
 * 将 constants 目录下的所有常量模块统一导出，
 * 方便其他模块通过单一入口导入所需常量。
 *
 * 使用方式：
 * import { BREAKPOINTS, DEVICE_TYPES, getDeviceType } from "~/constants";
 *
 * 导出模块：
 * - breakpoints: 响应式断点配置
 *   - BREAKPOINTS: 各设备类型的断点配置
 *   - DEVICE_TYPES: 设备类型列表
 *   - getDeviceType: 根据宽度获取设备类型的函数
 */

export * from "./breakpoints";
