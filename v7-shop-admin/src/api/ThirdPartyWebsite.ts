import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/third-party-website/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/third-party-website/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/third-party-website/doDelete',
    method: 'post',
    data,
  })
}

export function countOrders(data: any) {
  return request({
    url: '/third-party-website/count-orders',
    method: 'post',
    data,
  })
}

export function submitSyncOrders(data: any) {
  return request({
    url: '/third-party-website/submit-sync-orders',
    method: 'post',
    data,
  })
}

export function switchValidity(data: any) {
  return request({
    url: '/third-party-website/switchValidity',
    method: 'post',
    data,
  })
}
