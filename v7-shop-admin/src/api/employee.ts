import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/employee/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/employee/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/employee/doDelete',
    method: 'post',
    data,
  })
}

export function switchValidity(data: any) {
  return request({
    url: '/employee/switchValidity',
    method: 'post',
    data,
  })
}

export function grantRole(data: any) {
  return request({
    url: '/employee/grantRole',
    method: 'post',
    data: {
      id: data.id,
      roleIds: data.roles,
    },
  })
}

export function dispatchDepartment(id: number, departmentIds: number[]) {
  console.log(`id = ${id}, departments = ${departmentIds}`)
  return request({
    url: '/employee/dispatchDepartment',
    method: 'post',
    data: {
      id,
      departmentId: departmentIds[0],
    },
  })
}
export function getRemoteQuery(query: string) {
  return request({
    url: '/employee/remoteQuery?',
    method: 'get',
    params: { query },
  })
}

export function remoteQuerySpuSharedUser(query: string, spuId: string) {
  return request({
    url: '/employee/remoteQuerySpuSharedUser?',
    method: 'get',
    params: { query, spuId },
  })
}

export function setAiCredits(data: { id: number; monthlyAiCredits: number }) {
  return request({
    url: '/employee/setAiCredits',
    method: 'post',
    data,
  })
}
