import { getEnv } from '/@/utils/env'
import request from '/@/utils/request'
import { useUserStore } from '/@/store/modules/user'

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
  // 统计导出文件需要后端做归属校验，下载请求必须携带登录 token（与 axios 拦截器同源）
  const { token } = useUserStore()
  return fetch(href, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
}

export function fetchUnacknowledged(params?: { pageNo?: number; pageSize?: number }) {
  return request({
    url: '/tasks/page',
    method: 'post',
    data: {
      pageNo: params?.pageNo ?? 1,
      pageSize: params?.pageSize ?? 50,
      unacknowledgedOnly: true,
      taskTypes: ['PRODUCT_AI_TRANSLATE', 'THIRD_PARTY_ORDER_SYNC', 'EMPLOYEE_SPU_COPY'],
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
      taskTypes: ['PRODUCT_AI_TRANSLATE', 'PRODUCT_AI_REALTIME_TRANSLATE'],
      ...data,
    },
  })
}
