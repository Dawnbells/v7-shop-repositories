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

// Shopify 公开应用：新增返回连接链接（店铺在 Shopify 中选择），带 id 重新连接返回授权地址
export function shopifyInstallUrl(data: {
  id?: string
  nickName?: string
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
