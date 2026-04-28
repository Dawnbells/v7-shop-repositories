import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/ai-account/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/ai-account/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/ai-account/doDelete',
    method: 'post',
    data,
  })
}

export function switchValidity(data: any) {
  return request({
    url: '/ai-account/switchValidity',
    method: 'post',
    data,
  })
}
