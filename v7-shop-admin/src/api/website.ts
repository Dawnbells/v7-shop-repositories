import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/website/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/website/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/website/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/website/switchValidity',
    method: 'post',
    data,
  })
}
export function transfer(data: any) {
  return request({
    url: '/website/transfer',
    method: 'post',
    data,
  })
}
