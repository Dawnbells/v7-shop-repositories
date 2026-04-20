import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/top-level-domain/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/top-level-domain/doEdit',
    method: 'post',
    data,
  })
}

export function transfer(data: any) {
  return request({
    url: '/top-level-domain/transfer',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/top-level-domain/doDelete',
    method: 'post',
    data,
  })
}

export function getCertificate(data: any) {
  return request({
    url: '/top-level-domain/getCertificate',
    method: 'post',
    data,
  })
}

export function updateCertificate(data: any) {
  return request({
    url: '/top-level-domain/updateCertificate',
    method: 'post',
    data,
  })
}

export function renewCertificate(data: any) {
  return request({
    url: '/top-level-domain/renew_certificate',
    method: 'post',
    data: { id: data.id },
  })
}

export function parseCertificate(data: any) {
  return request({
    url: '/top-level-domain/parseCertificate',
    method: 'post',
    data,
  })
}

export function bindProtocol(data: any) {
  return request({
    url: '/top-level-domain/bindProtocol',
    method: 'post',
    data,
  })
}

export function nginxConfig(id: string | number, type: 'vike' | 'thymeleaf' | 'nuxt_mall') {
  return request({
    url: `/top-level-domain/nginx-config/${id}/${type}`,
    method: 'post',
  })
}

export function bindPixels(data: any) {
  return request({
    url: '/top-level-domain/bindPixels',
    method: 'post',
    data,
  })
}
