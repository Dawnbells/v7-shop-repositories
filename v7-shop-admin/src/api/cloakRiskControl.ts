import request from '/@/utils/request'

/**
 * 获取临时风控页面 URL
 * @returns {Promise} 返回包含临时 URL 的响应
 */
export function getTemporaryRiskControlUrl() {
  return request({
    url: '/cloak-risk-control/temporary-url',
    method: 'get',
  })
}

