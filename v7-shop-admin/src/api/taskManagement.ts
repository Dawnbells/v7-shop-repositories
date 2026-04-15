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

export function listTasks(data: {
  state?: string
  pageNo?: number
  pageSize?: number
  unacknowledgedOnly?: boolean
}) {
  return request({
    url: '/tasks/page',
    method: 'post',
    data,
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

export function fetchUnacknowledged(params?: { pageNo?: number; pageSize?: number }) {
  return request({
    url: '/tasks/page',
    method: 'post',
    data: {
      pageNo: params?.pageNo ?? 1,
      pageSize: params?.pageSize ?? 50,
      unacknowledgedOnly: true,
      taskTypes: ['PRODUCT_AI_TRANSLATE', 'PRODUCT_AI_TRANSLATE_DIRECT', 'THIRD_PARTY_ORDER_SYNC'],
    },
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

export function listAiTranslateTasks(data: any) {
  return request({
    url: '/tasks/page',
    method: 'post',
    data: {
      ...data,
      taskTypes: ['PRODUCT_AI_TRANSLATE', 'PRODUCT_AI_TRANSLATE_DIRECT'],
    },
  })
}
