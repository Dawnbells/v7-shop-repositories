import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/aiTokenUsageRecord/page',
    method: 'post',
    data,
  })
}
