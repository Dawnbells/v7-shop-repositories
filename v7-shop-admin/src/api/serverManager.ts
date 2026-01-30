import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/front-server/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/front-server/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/front-server/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/front-server/switchValidity',
    method: 'post',
    data,
  })
}
export function getRemoteQueryFrontServer(query: string) {
  return request({
    url: '/front-server/remoteQuery?',
    method: 'get',
    params: { query },
  })
}
