import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/article/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/article/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/article/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/article/switchValidity',
    method: 'post',
    data,
  })
}
