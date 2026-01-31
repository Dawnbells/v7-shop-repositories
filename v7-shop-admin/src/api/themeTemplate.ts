import request from '/@/utils/request'

/**
 * 主题模板相关接口
 */

export interface ThemeTemplateQuery {
  name?: string
  shareType?: 'PRIVATE' | 'DEPARTMENT' | 'COMPANY'
  current?: number
  size?: number
}

export interface ThemeTemplateEditRequest {
  id?: string
  name: string
  description?: string
  coverImage?: string
  shareType?: 'PRIVATE' | 'DEPARTMENT' | 'COMPANY'
  copyFromId?: number
}

export interface CopyThemeTemplateRequest {
  sourceId: number
  name: string
}

export interface UpdateThemeConfigRequest {
  id: number
  themeConfig?: string
  variableSchema?: string
  siteConfig?: string
  variableValues?: string
}

export interface ThemeTemplateResponse {
  id: string
  name: string
  description?: string
  coverImage?: string
  themeConfig?: string
  variableSchema?: string
  siteConfig?: string
  variableValues?: string
  shareType?: 'PRIVATE' | 'DEPARTMENT' | 'COMPANY'
  shareTypeName?: string
  sharedFromId?: number
  sharedFromName?: string
  ownerName?: string
  ownerDepartment?: string
  createTime?: string
  updateTime?: string
}

/**
 * 分页查询主题模板
 */
export function page(data: ThemeTemplateQuery) {
  return request({
    url: '/theme-templates/page',
    method: 'post',
    data,
  })
}

/**
 * 新增/编辑主题模板
 */
export function doEdit(data: ThemeTemplateEditRequest) {
  return request({
    url: '/theme-templates/doEdit',
    method: 'post',
    data,
  })
}

/**
 * 删除主题模板
 */
export function doDelete(data: { ids: string[] }) {
  return request({
    url: '/theme-templates/doDelete',
    method: 'post',
    data,
  })
}

/**
 * 获取模板详情
 */
export function getById(id: string | number) {
  return request({
    url: `/theme-templates/${id}`,
    method: 'get',
  })
}

/**
 * 从现有模板复制创建新模板
 */
export function copyFromTemplate(data: CopyThemeTemplateRequest) {
  return request({
    url: '/theme-templates/copy',
    method: 'post',
    data,
  })
}

/**
 * 更新模板的主题配置
 */
export function updateConfig(data: UpdateThemeConfigRequest) {
  return request({
    url: '/theme-templates/updateConfig',
    method: 'post',
    data,
  })
}

/**
 * 远程搜索模板
 */
export function remoteQuery(query?: string) {
  return request({
    url: '/theme-templates/remoteQuery',
    method: 'get',
    params: { query },
  })
}
