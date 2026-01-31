/**
 * 运行时环境变量获取工具
 * 优先从 window.__ENV__ 获取（Docker 运行时注入），fallback 到 import.meta.env（构建时注入）
 */

declare global {
  interface Window {
    __ENV__?: Record<string, string>
  }
}

/**
 * 获取环境变量值
 * @param key 环境变量名称
 * @param defaultValue 默认值
 * @returns 环境变量值
 */
export function getEnv(key: string, defaultValue = ''): string {
  const windowEnv = window.__ENV__?.[key]
  // 检查是否为有效值（非占位符，占位符格式为 __KEY__）
  if (windowEnv && !windowEnv.startsWith('__')) {
    return windowEnv
  }
  return import.meta.env[key] || defaultValue
}
