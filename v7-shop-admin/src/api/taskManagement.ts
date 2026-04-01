import { getEnv } from '/@/utils/env'
import request from '/@/utils/request'

export function getList(params?: any) {
  return request({
    url: '/taskManagement/getList',
    method: 'get',
    params,
  })
}

export function status(taskId: string) {
  return request({
    url: `/tasks/status/${taskId}`,
    method: 'get',
  })
}

export function cancelTask(taskId: string) {
  return request({
    url: `/tasks/cancel/${taskId}`,
    method: 'post',
  })
}

export function listTasks(params: { state?: string; page?: number; size?: number }) {
  return request({
    url: '/tasks/list',
    method: 'get',
    params,
  })
}

export function downloadTaskFile(taskId: string | number) {
  const href = `${getEnv('VITE_API_BASE_URL', window.location.origin)}/tasks/download/${taskId}`
  window.open(href, '_blank')
}

export function downloadFile(taskId: string) {
  const href = `${getEnv('VITE_API_BASE_URL', window.location.origin)}/tasks/download/${taskId}`
  return fetch(href)
}

export function fetchUnacknowledged(params?: { page?: number; size?: number }) {
  return request({
    url: '/tasks/unacknowledged',
    method: 'get',
    params,
  })
}

export function acknowledgeTask(taskId: string | number) {
  return request({
    url: `/tasks/acknowledge/${taskId}`,
    method: 'post',
  })
}

export function acknowledgeAllCompleted() {
  return request({
    url: '/tasks/acknowledge-all-completed',
    method: 'post',
  })
}

export function switchToDirectTranslate(taskId: string | number) {
  return request({
    url: `/tasks/switch-to-direct/${taskId}`,
    method: 'post',
  })
}

export function retryTask(taskId: string | number) {
  return request({
    url: `/tasks/retry/${taskId}`,
    method: 'post',
  })
}
