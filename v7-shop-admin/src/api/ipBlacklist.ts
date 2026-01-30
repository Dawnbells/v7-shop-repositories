import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/ip-blacklist/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/ip-blacklist/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/ip-blacklist/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/ip-blacklist/switchValidity',
    method: 'post',
    data,
  })
}
