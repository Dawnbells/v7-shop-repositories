import request from '/@/utils/request'

export const getList = (data: any) => {
  return request({
    url: '/websiteProtocol/page',
    method: 'post',
    data,
  })
}

export const doEdit = (data: any) => {
  return request({
    url: '/websiteProtocol/doEdit',
    method: 'post',
    data,
  })
}

export const doDelete = (data: any) => {
  return request({
    url: '/websiteProtocol/doDelete',
    method: 'post',
    data,
  })
}
export function getRemoteQuery(query: string, languageId: string | undefined) {
  return request({
    url: '/article/remoteQueryProtocol',
    method: 'get',
    params: { query, languageId },
  })
}

export function doBind(protocolId: string, articleId: string) {
  return request({
    url: `/websiteProtocol/bind-article/${protocolId}/${articleId}`,
    method: 'post',
  })
}

export function doUnbind(protocolId: string, articleId: string) {
  return request({
    url: `/websiteProtocol/unbind-article/${protocolId}/${articleId}`,
    method: 'post',
  })
}
