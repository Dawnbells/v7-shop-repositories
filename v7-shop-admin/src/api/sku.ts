import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/product-sku/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/product-sku/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/product-sku/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/product-sku/switchValidity',
    method: 'post',
    data,
  })
}
export function getRemoteQuery(query: string) {
  return request({
    url: '/product-sku/remoteQuery?',
    method: 'get',
    params: { query },
  })
}
export function getReplaceTargetQuery(query: string) {
  return request({
    url: '/product-sku/replace-target-query',
    method: 'get',
    params: { query },
  })
}
export function replaceDistribution(data: any) {
  return request({
    url: '/product-sku/replace-distribution',
    method: 'post',
    data,
  })
}
export function replaceSku(data: any) {
  return request({
    url: '/product-sku/replace',
    method: 'post',
    data,
  })
}
