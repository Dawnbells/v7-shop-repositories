/**
 * 保存主题配置 API
 * POST /api/builder/save
 *
 * 表结构：
 * PRIMARY KEY (`landing_page_type`, `spu_id`, `sub_domain_id`)
 * `landing_page_type` enum('LAND','CLOAK','BLACKLISTED')
 *
 * 数据分离设计：
 * - theme_config: 页面布局、组件、样式（前端渲染 + 编辑器）
 * - variable_schema: 变量定义结构（仅编辑器）
 * - site_config: 站点配置值（前端渲染 + 编辑器）
 * - variable_values: 变量实际值（前端渲染 + 编辑器）
 */

import { saveLandingPageConfig } from '../../repositories/landingPageRepository'

const VALID_LANDING_TYPES = ['LAND', 'CLOAK', 'BLACKLISTED'] as const
type LandingPageType = (typeof VALID_LANDING_TYPES)[number]

interface VariableDefinition {
  key: string
  defaultValue?: any
}

interface SaveThemeRequest {
  subDomainId: string | number
  spuId: string | number
  landingType: string
  landingPageProductId?: string | number | null
  themeConfig: object
  variableSchema?: VariableDefinition[]
  siteConfig?: object
  variableValues?: Record<string, any>
}

function fillVariableDefaults(
  variableValues: Record<string, any> | undefined,
  variableSchema: VariableDefinition[] | undefined
): Record<string, any> {
  const result: Record<string, any> = { ...(variableValues || {}) }

  if (!variableSchema || !Array.isArray(variableSchema)) {
    return result
  }

  for (const variable of variableSchema) {
    if (result[variable.key] === undefined || result[variable.key] === null) {
      if (variable.defaultValue !== undefined) {
        result[variable.key] = variable.defaultValue
      }
    }
  }

  return result
}

export default defineEventHandler(async (event) => {
  const body = await readBody<SaveThemeRequest>(event)

  if (!body.subDomainId || !body.spuId || !body.landingType || !body.themeConfig) {
    throw createError({
      statusCode: 400,
      statusMessage:
        'Missing required fields: subDomainId, spuId, landingType, themeConfig',
    })
  }

  if (!VALID_LANDING_TYPES.includes(body.landingType as LandingPageType)) {
    throw createError({
      statusCode: 400,
      statusMessage: `Invalid landingType: ${body.landingType}. Must be one of: ${VALID_LANDING_TYPES.join(', ')}`,
    })
  }

  const subDomainId = BigInt(body.subDomainId)
  const spuId = BigInt(body.spuId)
  const landingPageProductId = body.landingPageProductId
    ? BigInt(body.landingPageProductId)
    : null

  if (typeof body.themeConfig !== 'object' || body.themeConfig === null) {
    throw createError({
      statusCode: 400,
      statusMessage: 'themeConfig must be a valid JSON object',
    })
  }

  const filledVariableValues = fillVariableDefaults(
    body.variableValues,
    body.variableSchema
  )

  const themeConfigJson = JSON.stringify(body.themeConfig)
  const variableSchemaJson = body.variableSchema
    ? JSON.stringify(body.variableSchema)
    : '[]'
  const siteConfigJson = body.siteConfig ? JSON.stringify(body.siteConfig) : '{}'
  const variableValuesJson = JSON.stringify(filledVariableValues)

  try {
    await saveLandingPageConfig({
      subDomainId,
      spuId,
      landingType: body.landingType,
      landingPageProductId,
      themeConfig: themeConfigJson,
      variableSchema: variableSchemaJson,
      siteConfig: siteConfigJson,
      variableValues: variableValuesJson,
    })

    return {
      success: true,
      message: 'Theme configuration saved successfully',
    }
  } catch (error: any) {
    console.error('[Builder API] Save theme error:', error)

    if (error.code === 'ER_NO_REFERENCED_ROW_2') {
      throw createError({
        statusCode: 400,
        statusMessage:
          'Invalid reference: subDomainId, spuId, or landingPageProductId does not exist',
      })
    }

    if (error.statusCode) {
      throw error
    }

    throw createError({
      statusCode: 500,
      statusMessage: 'Failed to save theme configuration',
    })
  }
})
