import request from '/@/utils/request'

export function getList(params?: any) {
  return request({
    url: '/router/getList',
    method: 'get',
    params,
  })
}

export function getRouterList(data: any) {
  return request({
    url: '/router/getRouterList',
    method: 'post',
    data,
  })
}
