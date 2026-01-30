import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/website-domain/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/website-domain/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/website-domain/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/website-domain/switchValidity',
    method: 'post',
    data,
  })
}

export function queryDomains(keyword: string) {
  return request({
    url: '/website-domain/query',
    method: 'post',
    data: { keyword: keyword },
  })
}

export function queryRelayDomains(keyword: string) {
  return request({
    url: '/website-domain/queryRelay',
    method: 'post',
    data: { keyword: keyword },
  })
}
