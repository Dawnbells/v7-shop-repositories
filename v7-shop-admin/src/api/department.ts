import request from '/@/utils/request'

export interface Department {
  /*ID:ID */
  id: number

  /*ID序列号:ID的Base62格式 */
  compactId: string

  /*状态:状态,可用值:VALID,INVALID,DELETED */
  status: string

  /*部门名称: */
  name: string

  /*部门描述: */
  description: string

  /*是否私域部门 */
  isPrivateDomain?: boolean
  /**
   * 子部门
   */
  children: Department[]
}

export function page(data: any) {
  return request({
    url: '/department/page',
    method: 'post',
    data,
  })
}

export function getTree(data: any = undefined) {
  return request({
    url: '/department/getTree',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/department/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/department/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/department/switchValidity',
    method: 'post',
    data,
  })
}

export function switchPrivateDomain(data: any) {
  return request({
    url: '/department/switchPrivateDomain',
    method: 'post',
    data,
  })
}

/**
 * 获取当前登录用户所在部门信息
 */
export function getCurrentDepartmentInfo() {
  return request({
    url: '/department/info',
    method: 'get',
  })
}
