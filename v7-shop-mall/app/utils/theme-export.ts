/**
 * 主题导入导出工具函数
 */

import type { CustomVariable, SiteConfig, VariableValues } from '~/types/data-context'
import type { PageData, LayoutData } from '~/types/component-meta'

// 导出文件版本
const EXPORT_VERSION = '1.0'

// 重新导出类型供外部使用
export type { PageData, LayoutData }

// 导出选项
export interface ExportOptions {
  pages: boolean
  layouts: boolean
  siteConfig: boolean
  variableSchema: boolean
  variableValues: boolean
}

// 导出数据结构
export interface ThemeExportData {
  version: string
  exportedAt: string
  includes: ExportOptions
  data: {
    pages?: PageData[]
    layouts?: LayoutData[]
    siteConfig?: SiteConfig
    variableSchema?: CustomVariable[]
    variableValues?: VariableValues
  }
}

// 导入数据结构（与导出相同）
export type ThemeImportData = ThemeExportData

// 验证结果
export interface ValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
  data?: ThemeImportData
}

// 原始数据输入
export interface ThemeRawData {
  pages: PageData[]
  layouts: LayoutData[]
  siteConfig: SiteConfig
  variableSchema: CustomVariable[]
  variableValues: VariableValues
}

/**
 * 导出主题为 JSON 字符串
 */
export function exportThemeAsJson(
  rawData: ThemeRawData,
  options: ExportOptions
): string {
  const exportData: ThemeExportData = {
    version: EXPORT_VERSION,
    exportedAt: new Date().toISOString(),
    includes: { ...options },
    data: {},
  }

  if (options.pages) {
    exportData.data.pages = rawData.pages
  }
  if (options.layouts) {
    exportData.data.layouts = rawData.layouts
  }
  if (options.siteConfig) {
    exportData.data.siteConfig = rawData.siteConfig
  }
  if (options.variableSchema) {
    exportData.data.variableSchema = rawData.variableSchema
  }
  if (options.variableValues) {
    exportData.data.variableValues = rawData.variableValues
  }

  return JSON.stringify(exportData, null, 2)
}

/**
 * 从 JSON 字符串导入主题
 */
export function importThemeFromJson(jsonString: string): ThemeImportData {
  const data = JSON.parse(jsonString)
  return data as ThemeImportData
}

/**
 * 验证导入数据
 */
export function validateImportData(data: unknown): ValidationResult {
  const errors: string[] = []
  const warnings: string[] = []

  // 检查是否为对象
  if (!data || typeof data !== 'object') {
    return {
      valid: false,
      errors: ['导入数据格式无效：不是有效的 JSON 对象'],
      warnings: [],
    }
  }

  const obj = data as Record<string, unknown>

  // 检查版本
  if (!obj.version) {
    warnings.push('导入数据缺少版本号，可能是旧版本格式')
  } else if (obj.version !== EXPORT_VERSION) {
    warnings.push(`导入数据版本 (${obj.version}) 与当前版本 (${EXPORT_VERSION}) 不同`)
  }

  // 检查 includes 字段
  if (!obj.includes || typeof obj.includes !== 'object') {
    errors.push('导入数据缺少 includes 字段')
  }

  // 检查 data 字段
  if (!obj.data || typeof obj.data !== 'object') {
    errors.push('导入数据缺少 data 字段')
  } else {
    const dataObj = obj.data as Record<string, unknown>
    const includes = (obj.includes || {}) as Record<string, boolean>

    // 验证 pages
    if (includes.pages) {
      if (!Array.isArray(dataObj.pages)) {
        errors.push('pages 数据格式无效：应为数组')
      } else {
        dataObj.pages.forEach((page: any, index: number) => {
          if (!page.id) {
            errors.push(`pages[${index}] 缺少 id 字段`)
          }
          if (!page.root) {
            errors.push(`pages[${index}] 缺少 root 字段`)
          }
        })
      }
    }

    // 验证 layouts
    if (includes.layouts) {
      if (!Array.isArray(dataObj.layouts)) {
        errors.push('layouts 数据格式无效：应为数组')
      } else {
        dataObj.layouts.forEach((layout: any, index: number) => {
          if (!layout.id) {
            errors.push(`layouts[${index}] 缺少 id 字段`)
          }
          if (!layout.root) {
            errors.push(`layouts[${index}] 缺少 root 字段`)
          }
        })
      }
    }

    // 验证 siteConfig
    if (includes.siteConfig) {
      if (dataObj.siteConfig && typeof dataObj.siteConfig !== 'object') {
        errors.push('siteConfig 数据格式无效：应为对象')
      }
    }

    // 验证 variableSchema
    if (includes.variableSchema) {
      if (!Array.isArray(dataObj.variableSchema)) {
        errors.push('variableSchema 数据格式无效：应为数组')
      } else {
        dataObj.variableSchema.forEach((variable: any, index: number) => {
          if (!variable.key) {
            errors.push(`variableSchema[${index}] 缺少 key 字段`)
          }
          if (!variable.type) {
            errors.push(`variableSchema[${index}] 缺少 type 字段`)
          }
        })
      }
    }

    // 验证 variableValues
    if (includes.variableValues) {
      if (dataObj.variableValues && typeof dataObj.variableValues !== 'object') {
        errors.push('variableValues 数据格式无效：应为对象')
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
    data: errors.length === 0 ? (data as ThemeImportData) : undefined,
  }
}

/**
 * 触发 JSON 文件下载
 */
export function downloadJson(content: string, filename: string): void {
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * 读取文件内容
 */
export function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(file)
  })
}

/**
 * 计算数据大小（字节）
 */
export function calculateDataSize(data: unknown): number {
  return new Blob([JSON.stringify(data)]).size
}

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  } else if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  } else {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }
}

/**
 * 生成导出文件名
 */
export function generateExportFilename(prefix = 'theme-export'): string {
  const now = new Date()
  const timestamp = now.toISOString().replace(/[:.]/g, '-').slice(0, 19)
  return `${prefix}-${timestamp}.json`
}
