import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/country/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/country/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/country/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/country/switchValidity',
    method: 'post',
    data,
  })
}
export function getRemoteQuery(query: string) {
  return request({
    url: '/country/remoteQuery?',
    method: 'get',
    params: { query },
  })
}
