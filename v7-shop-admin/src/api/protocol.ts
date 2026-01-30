import request from '/@/utils/request'

export const getList = (data: any) => {
  return request({
    url: '/protocol/page',
    method: 'post',
    data,
  })
}

export const doEdit = (data: any) => {
  return request({
    url: '/protocol/doEdit',
    method: 'post',
    data,
  })
}

export const doDelete = (data: any) => {
  return request({
    url: '/protocol/doDelete',
    method: 'post',
    data,
  })
}

export const editProtocolTranslation = (data: any) => {
  return request({
    url: '/protocol/editProtocolTranslation',
    method: 'post',
    data,
  })
}

export function getRemoteQuery(query: string) {
  return request({
    url: '/protocol/remoteQuery?',
    method: 'get',
    params: { query },
  })
}
