/**
 * iframe 嵌入安全头中间件
 * 对 /builder 路径允许同主域名下的 iframe 嵌入
 * 其他路径保持 X-Frame-Options: SAMEORIGIN
 */

export default defineEventHandler((event) => {
  const path = event.path

  if (path.startsWith('/builder')) {
    const host = getRequestHost(event, { xForwardedHost: true })
    const parts = host.split('.')
    const mainDomain =
      parts.length >= 2 ? parts.slice(-2).join('.') : host

    removeResponseHeader(event, 'x-frame-options')
    setResponseHeader(
      event,
      'content-security-policy',
      `frame-ancestors 'self' *.${mainDomain} ${mainDomain}`
    )
  }
})
