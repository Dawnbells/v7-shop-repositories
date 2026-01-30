import request from '/@/utils/request'

export interface Language {
  /*ID:有id参数表示编辑或者删除，否则表示新增 */
  id: number

  /*语言名称: */
  name: string

  /*语言中问名称: */
  cname: string

  /*语言代码: */
  code: string
}

export function page(data: any) {
  return request({
    url: '/language/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/language/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/language/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/language/switchValidity',
    method: 'post',
    data,
  })
}
export function getRemoteQueryLanguage(query: string) {
  return request({
    url: '/language/remoteQuery?',
    method: 'get',
    params: { query },
  })
}
