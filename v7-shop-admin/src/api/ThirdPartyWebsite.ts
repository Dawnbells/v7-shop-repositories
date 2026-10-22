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

export function doEditShopify(data: any) {
  return request({
    url: '/third-party-website/shopify/doEdit',
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

// Shopify 公开应用：获取授权跳转地址（新增或重新连接）
export function shopifyInstallUrl(data: {
  id?: string
  nickName: string
  handle: string
  currencyMode: 'SHOP_MONEY' | 'PRESENTMENT_MONEY'
}) {
  return request({
    url: '/third-party-website/shopify/install-url',
    method: 'post',
    data,
  })
}

// Shopify 公开应用：用安装票据把店铺绑定到当前账号
export function shopifyBind(data: { ticket: string; nickName?: string; currencyMode?: string }) {
  return request({
    url: '/third-party-website/shopify/bind',
    method: 'post',
    data,
  })
}
