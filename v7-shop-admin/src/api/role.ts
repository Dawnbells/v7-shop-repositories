import request from '/@/utils/request'

export interface Role {
  /*ID:ID */
  id: number

  /*ID序列号:ID的Base62格式 */
  compactId: string

  /*状态:状态,可用值:VALID,INVALID,DELETED */
  status: string

  /*角色名称: */
  name: string

  /*角色描述: */
  description: string
}

export function getList() {
  return request({
    url: '/role/getAll',
    method: 'post',
  })
}

export function page(data: any) {
  return request({
    url: '/role/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/role/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/role/doDelete',
    method: 'post',
    data,
  })
}

export function switchValidity(data: any) {
  return request({
    url: '/role/switchValidity',
    method: 'post',
    data,
  })
}

export function grantRouters(data: any) {
  return request({
    url: '/role/grantRouters',
    method: 'post',
    data,
  })
}
