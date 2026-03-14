/**
 * 样式规范化工具
 * 将样式对象转换为 Vue :style 绑定兼容的格式
 */

/**
 * 需要自动添加 px 单位的 CSS 属性
 * 这些属性如果值是纯数字，需要添加 px 单位
 */
const PIXEL_PROPERTIES = new Set([
  // 尺寸
  'width', 'height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight',
  // 内边距
  'padding', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
  // 外边距
  'margin', 'marginTop', 'marginRight', 'marginBottom', 'marginLeft',
  // 边框
  'borderWidth', 'borderTopWidth', 'borderRightWidth', 'borderBottomWidth', 'borderLeftWidth',
  'borderRadius', 'borderTopLeftRadius', 'borderTopRightRadius', 'borderBottomLeftRadius', 'borderBottomRightRadius',
  // 定位
  'top', 'right', 'bottom', 'left',
  // 间距
  'gap', 'rowGap', 'columnGap',
  // 字体（注意：lineHeight 可以是无单位数字表示倍数，不在此列表中）
  'fontSize', 'letterSpacing',
  // 其他
  'outlineWidth', 'outlineOffset',
])

/**
 * 检查值是否为纯数字（不含单位）
 * @param value 样式值
 * @returns 是否为纯数字
 */
function isNumericValue(value: string | number): boolean {
  if (typeof value === 'number') return true
  if (typeof value !== 'string') return false
  
  const trimmed = value.trim()
  if (trimmed === '' || trimmed === '0') return false
  
  return /^-?\d+(\.\d+)?$/.test(trimmed)
}

/**
 * 规范化单个样式值
 * @param key 样式属性名
 * @param value 样式值
 * @returns 规范化后的值
 */
function normalizeStyleValue(key: string, value: unknown): string | number | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined
  }
  
  const stringValue = String(value)
  
  if (PIXEL_PROPERTIES.has(key) && isNumericValue(stringValue)) {
    return `${stringValue}px`
  }
  
  return stringValue
}

/**
 * 规范化样式对象
 * - 为纯数字值添加 px 单位
 * - 移除空值
 * - 保留 CSS 变量（以 -- 开头的属性）
 * 
 * @param style 原始样式对象
 * @returns 规范化后的样式对象
 */
export function normalizeStyle(style: Record<string, unknown> | undefined | null): Record<string, string | number> {
  if (!style) return {}
  
  const normalized: Record<string, string | number> = {}
  
  for (const [key, value] of Object.entries(style)) {
    if (value === null || value === undefined || value === '') {
      continue
    }
    
    // CSS 变量保持原样（Vue 3 支持在 :style 中使用 CSS 变量）
    if (key.startsWith('--')) {
      normalized[key] = String(value)
      continue
    }
    
    const normalizedValue = normalizeStyleValue(key, value)
    if (normalizedValue !== undefined) {
      normalized[key] = normalizedValue
    }
  }
  
  // 调试信息
  if (Object.keys(normalized).length > 0) {
    console.log('[normalizeStyle] Input:', JSON.stringify(style))
    console.log('[normalizeStyle] Output:', JSON.stringify(normalized))
  }
  
  return normalized
}

/**
 * 合并并规范化多个样式对象
 * @param styles 样式对象数组（后面的覆盖前面的）
 * @returns 合并并规范化后的样式对象
 */
export function mergeAndNormalizeStyles(
  ...styles: (Record<string, unknown> | undefined | null)[]
): Record<string, string | number> {
  const merged: Record<string, unknown> = {}
  
  for (const style of styles) {
    if (style) {
      Object.assign(merged, style)
    }
  }
  
  return normalizeStyle(merged)
}
