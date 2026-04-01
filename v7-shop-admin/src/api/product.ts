import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/product/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  console.log('data', data)
  return request({
    url: '/product/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/product/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/product/switchValidity',
    method: 'post',
    data,
  })
}

export function getRemoteQueryMerchandise(query: string) {
  return request({
    url: '/product/remoteQueryMerchandise?',
    method: 'get',
    params: { query },
  })
}

export function translateByAI(data: { productId: string; countryId: string; languageId: string }) {
  return request({
    url: '/product/translateByAI',
    method: 'post',
    data,
  })
}
