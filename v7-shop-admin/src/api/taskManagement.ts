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

export function downloadFile(taskId: string) {
  const href = `${import.meta.env.VITE_APP_BASE_URL}/tasks/download/${taskId}`
  return fetch(href)
}
