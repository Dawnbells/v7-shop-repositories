import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/themes/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/themes/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/themes/doDelete',
    method: 'post',
    data,
  })
}

export function switchValidity(data: any) {
  return request({
    url: '/themes/switchValidity',
    method: 'post',
    data,
  })
}

export function setDefault(data: any) {
  return request({
    url: '/themes/setDefault',
    method: 'post',
    data,
  })
}

export function getTemplates() {
  return request({
    url: '/themes/templates',
    method: 'get',
  })
}

export function getThemeConfig(id: string | number) {
  return request({
    url: `/themes/${id}/config`,
    method: 'get',
  })
}

export function saveThemeConfig(data: any) {
  return request({
    url: '/themes/config',
    method: 'post',
    data,
  })
}

export function remoteQuery(query: string) {
  return request({
    url: '/themes/remoteQuery',
    method: 'get',
    params: { query },
  })
}
