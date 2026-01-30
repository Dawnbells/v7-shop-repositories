import request from '/@/utils/request'

export function doBind(data: any) {
  return request({
    url: `/spu/bind-spu/${data.id}`,
    method: 'post',
  })
}

export function doUnbind(data: any) {
  return request({
    url: '/spu/unbind-spus',
    method: 'post',
    data,
  })
}

export function getRemoteQuery(query: string, inside?: boolean | undefined) {
  return request({
    url: '/spu/remoteQuerySimple',
    method: 'get',
    params: { query, inside },
  })
}
