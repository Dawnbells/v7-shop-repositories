import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/currency/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/currency/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/currency/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/currency/switchValidity',
    method: 'post',
    data,
  })
}
export function getRemoteQueryCurrency(query: string) {
  return request({
    url: '/currency/remoteQuery?',
    method: 'get',
    params: { query },
  })
}

export function getRecommendCurrencyByLanguage(id: string) {
  return request({
    url: '/currency/recommendByLang/' + id,
    method: 'post',
  })
}
