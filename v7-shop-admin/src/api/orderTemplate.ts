import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/order-template/page',
    method: 'post',
    data,
  })
}

export function query(type: string, keyword: string) {
  return request({
    url: '/order-template/query',
    method: 'get',
    params: {
      type,
      keyword,
    },
  })
}

export function doEdit(data: any) {
  return request({
    url: '/order-template/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/order-template/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/order-template/switchValidity',
    method: 'post',
    data,
  })
}

export interface OrderTemplateColumn {
  id: number
  headerName: string
  fieldKey: string
}
