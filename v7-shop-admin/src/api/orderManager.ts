import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/orders/page',
    method: 'post',
    data,
  })
}

export function download(data: any) {
  return request({
    url: '/orders/download',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/orders/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/orders/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/orders/switchValidity',
    method: 'post',
    data,
  })
}

export function updateOrderStatus(data: any) {
  return request({
    url: `/orders/updateOrderStatus`,
    method: 'post',
    data,
  })
}

export function updateOrderCheckRemark(ids: string[], remark: string) {
  return request({
    url: `/orders/updateOrderCheckRemark`,
    method: 'post',
    data: {
      ids,
      remark,
    },
  })
}

export function updateContactStatus(data: any) {
  return request({
    url: '/orders/updateContactStatus',
    method: 'post',
    data,
  })
}

export function updateContactRemark(ids: string[], remark: string) {
  return request({
    url: '/orders/updateContactRemark',
    method: 'post',
    data: { ids, remark },
  })
}
