import request from '/@/utils/request'

export function getUnacknowledgedSwitches() {
  return request({
    url: '/front-server/unacknowledged-switches',
    method: 'get',
  })
}

export function getFrontServerHealthStatus() {
  return request({
    url: '/front-server/health-status',
    method: 'get',
  })
}

export function acknowledgeSwitch(id: number) {
  return request({
    url: `/front-server/acknowledge-switch/${id}`,
    method: 'post',
  })
}
