import request from '/@/utils/request'

export function identity() {
  return request({
    url: '/company/identity',
    method: 'post',
  })
}

export function page(data: any) {
  return request({
    url: '/company/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/company/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/company/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/company/switchValidity',
    method: 'post',
    data,
  })
}
