import request from '/@/utils/request'

export type ConfigCenterName = string

/**
 * 获取配置中心 schema（动态表单定义）
 * GET /config-center/{configName}
 */
export function getConfigCenterSchema(configName: ConfigCenterName) {
  return request({
    url: `/config-center/${configName}`,
    method: 'get',
  })
}

/**
 * 刷新配置中心 schema（让后端重新生成/同步 schema）
 * POST /config-center/{configName}/refresh
 */
export function refreshConfigCenterSchema(configName: ConfigCenterName) {
  return request({
    url: `/config-center/${configName}/refresh`,
    method: 'post',
  })
}

/**
 * 获取已保存的配置值
 * GET /config-center/{configName}/value?departmentId=...
 */
export function getConfigCenterValue(
  configName: ConfigCenterName,
  params: {
    departmentId?: number
  }
) {
  return request({
    url: `/config-center/${configName}/value`,
    method: 'get',
    params,
  })
}

/**
 * 保存配置
 * POST /config-center/save
 */
export function saveConfigCenter(data: {
  configName: ConfigCenterName
  configValue: any
  departmentId?: number
}) {
  return request({
    url: `/config-center/save`,
    method: 'post',
    data,
  })
}

/**
 * 获取当前登录用户所在部门信息（由配置中心模块提供）
 * GET /config-center/departmentInfo
 */
export function getConfigCenterDepartmentInfo() {
  return request({
    url: '/config-center/departmentInfo',
    method: 'get',
  })
}
