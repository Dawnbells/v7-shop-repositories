import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/sub-domain/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/sub-domain/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/sub-domain/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/sub-domain/switchValidity',
    method: 'post',
    data,
  })
}

export function bindTheme(id: string | number, themeId: string | number) {
  return request({
    url: `/sub-domain/bindTheme/${id}/${themeId}`,
    method: 'post',
  })
}

export function bindPixels(data: any) {
  return request({
    url: '/sub-domain/bindPixels',
    method: 'post',
    data,
  })
}

export function bindSpu(subDomainId: number | string, spuId: number | string) {
  return request({
    url: `/sub-domain/bindSpu/${subDomainId}/${spuId}`,
    method: 'post',
  })
}

export function unbindSpu(subDomainId: number | string, spuId: number | string) {
  return request({
    url: `/sub-domain/unbindSpu/${subDomainId}/${spuId}`,
    method: 'post',
  })
}

export function getBoundSpus(subDomainId: number | string, keyword?: string) {
  return request({
    url: `/sub-domain/getBoundSpus/${subDomainId}`,
    method: 'get',
    params: keyword ? { keyword } : {},
  })
}

/**
 * 获取子域名绑定SPU的详情信息
 * @param subDomainId 子域名ID
 * @param spuId SPU ID
 * @returns 返回数据结构:
 * {
 *   // 落地页SPU配置
 *   realLandingPageSpu: { id, name, code, previewUrl } | null,      // 真实落地页SPU
 *   crawlerLandingPageSpu: { id, name, code, previewUrl } | null,   // 爬虫落地页SPU
 *   riskUserLandingPageSpu: { id, name, code, previewUrl } | null,  // 风险用户落地页SPU
 *   blacklistLandingPageSpu: { id, name, code, previewUrl } | null, // 黑名单落地页SPU
 *   // 主题信息
 *   theme: { id, name } | null,
 *   // 像素列表
 *   pixels: Array<{ id, name, pixelId, platform }>,
 * }
 */
export function getBoundSpuDetail(subDomainId: number | string, spuId: number | string) {
  return request({
    url: `/sub-domain/getBoundSpuDetail/${subDomainId}/${spuId}`,
    method: 'get',
  })
}

/**
 * 绑定像素到子域名SPU
 * @param data { subDomainId, spuId, pixelId }
 */
export function bindSpuPixel(data: {
  subDomainId: number | string
  spuId: number | string
  pixelId: number | string
}) {
  return request({
    url: '/sub-domain/bindSpuPixel',
    method: 'post',
    data,
  })
}

/**
 * 新增像素并绑定到子域名SPU
 */
export function createAndBindSpuPixel(data: any) {
  return request({
    url: '/sub-domain/createAndBindSpuPixel',
    method: 'post',
    data,
  })
}

/**
 * 解绑子域名SPU的像素
 * @param data { subDomainId, spuId, pixelId }
 */
export function unbindSpuPixel(data: {
  subDomainId: number | string
  spuId: number | string
  pixelId: number | string
}) {
  return request({
    url: '/sub-domain/unbindSpuPixel',
    method: 'post',
    data,
  })
}

/**
 * 绑定落地页SPU
 * @param data 绑定参数
 * - subDomainId: 子域名ID
 * - spuId: 当前绑定的SPU ID
 * - landingSpuId: 落地页SPU ID
 * - landingPageType: 落地页类型 ('LAND' | 'CLOAK' | 'BLACKLISTED')
 */
export function bindLandingPageSpu(data: {
  subDomainId: number | string
  spuId: number | string
  landingSpuId: number | string
  landingPageType: 'LAND' | 'CLOAK' | 'BLACKLISTED'
}) {
  return request({
    url: '/sub-domain/bindLandingPageSpu',
    method: 'post',
    data,
  })
}

/**
 * 绑定协议到落地页
 * @param data 绑定参数
 * - subDomainId: 子域名ID
 * - spuId: 当前绑定的SPU ID
 * - landingPageType: 落地页类型 ('LAND' | 'CLOAK' | 'BLACKLISTED')
 * - protocolId: 协议ID
 * - placeholderValues: 占位符值
 */
export function bindLandingPageProtocol(data: {
  subDomainId: number | string
  spuId: number | string
  landingPageType: 'LAND' | 'CLOAK' | 'BLACKLISTED'
  protocolId: string
  placeholderValues: Record<string, string>
}) {
  return request({
    url: '/sub-domain/bindLandingPageProtocol',
    method: 'post',
    data,
  })
}

/**
 * 解绑落地页SPU（使用默认配置）
 * @param data 解绑参数
 * - subDomainId: 子域名ID
 * - spuId: 当前绑定的SPU ID
 * - landingPageType: 落地页类型 ('LAND' | 'CLOAK' | 'BLACKLISTED')
 */
export function unbindLandingPageSpu(data: {
  subDomainId: number | string
  spuId: number | string
  landingPageType: 'LAND' | 'CLOAK' | 'BLACKLISTED'
}) {
  return request({
    url: '/sub-domain/unbindLandingPageSpu',
    method: 'post',
    data,
  })
}

export function saveAdConfig(data: {
  subDomainId: number | string
  spuId: number | string
  adPlatform?: string | null
  medium?: string | null
  cloakStrategy?: string | null
  campaign?: string | null
}) {
  return request({
    url: '/sub-domain/saveAdConfig',
    method: 'post',
    data,
  })
}
