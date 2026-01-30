// src/utils/queryParams.ts

export function getQueryParams(url: string = globalThis.location.href): Record<string, string> {
  const queryParams: Record<string, string> = {}
  const urlObj = new URL(url.replace('/#/', '/'))
  const params = new URLSearchParams(urlObj.search)
  params.forEach((value, key) => {
    queryParams[key] = value
  })

  return queryParams
}

export function getTicketParams(): string {
  let params = getQueryParams()
  return params['ticket']
}

export function reloadNoTicket(url: string = globalThis.location.href) {
  const urlObj = new URL(url.replace('/#/', '/'))
  const params = new URLSearchParams(urlObj.search)
  params.delete('ticket')
  let paramsStr = params.toString()
  paramsStr = paramsStr ? `?${paramsStr}` : ''
  let path = urlObj.pathname
  path = path == '/' ? '/index' : path
  try {
    if (urlObj.hostname.startsWith('admin') && urlObj.hostname.split('.').length > 3) {
      const hostParts = urlObj.hostname.split('.')
      hostParts.shift() // Remove the adminxxxx part
      const newHost = ['admin', ...hostParts].join('.')
      urlObj.hostname = newHost
    }
    globalThis.location.replace(`${urlObj.origin}/#${path}${paramsStr}`)
  } catch (error) {
    console.error('Redirect failed:', error)
    // 处理错误情况，例如显示错误信息或者备用操作
  }
}
