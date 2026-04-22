import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/pixel-account/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/pixel-account/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/pixel-account/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/pixel-account/switchValidity',
    method: 'post',
    data,
  })
}

export function getRemoteQuery(query: string, platform?: string) {
  return request({
    url: '/pixel-account/remoteQuery',
    method: 'get',
    params: { query, ...(platform ? { platform } : {}) },
  })
}
