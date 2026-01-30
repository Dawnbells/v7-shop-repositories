import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/product-category/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/product-category/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/product-category/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/product-category/switchValidity',
    method: 'post',
    data,
  })
}
export function remoteQuery(query: string) {
  return request({
    url: '/product-category/remoteQuery?',
    method: 'get',
    params: { query },
  })
}
