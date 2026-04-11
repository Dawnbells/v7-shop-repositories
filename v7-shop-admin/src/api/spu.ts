import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/spu/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/spu/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/spu/doDelete',
    method: 'post',
    data,
  })
}
export function switchOpen(data: any) {
  return request({
    url: '/spu/switchOpen',
    method: 'post',
    data,
  })
}

export function getRemoteQuery(query: string, countryId?: string | number) {
  const params: Record<string, any> = { query }
  if (countryId) params.countryId = countryId
  return request({
    url: '/spu/remoteQuery',
    method: 'get',
    params,
  })
}

export function shareSpu(data: any) {
  return request({
    url: '/spu/shareSpu',
    method: 'post',
    data,
  })
}
export function generateSharedUrl(data: any) {
  return request({
    url: '/spu/generateSharedUrl',
    method: 'post',
    data,
  })
}
