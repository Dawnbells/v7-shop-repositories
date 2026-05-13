import { loginRSA } from '/@/config'
import { encryptedData } from '/@/utils/encrypt'
import request from '/@/utils/request'

interface FormType {
  password: string
  password2?: string
  phone: string
  phoneCode: string
  username: string
  verificationCode: string
}

export async function login(data: any) {
  if (loginRSA) {
    data = { ...data, password: await encryptedData(data) }
  }
  return request({
    url: '/systemUser/login',
    method: 'post',
    data: {
      telephone: data.username,
      password: data.password,
    },
  })
}

export function getUserInfo() {
  return request({
    url: '/systemUser/userInfo',
    method: 'get',
  })
}

export function logout() {
  return request({
    url: '/systemUser/logout',
    method: 'get',
  })
}

export function register(data: any) {
  return request({
    url: '/systemUser/register',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/systemUser/switchValidity',
    method: 'post',
    data,
  })
}

export function getTicket() {
  return request({
    url: '/systemUser/getTicket',
    method: 'get',
  })
}

export function getWebsiteTicket(websiteId: string | number) {
  return request({
    url: `/systemUser/getWebsiteTicket/${websiteId}`,
    method: 'get',
  })
}

export function loginByTicket(ticket: string) {
  return request({
    url: `/systemUser/loginByTicket/${ticket}`,
    method: 'get',
  })
}

export const password = (data: FormType) => {
  return request({
    url: '/password',
    method: 'post',
    data,
  })
}

export const switchViewMode = (viewMode: string) => {
  return request({
    url: `/systemUser/switchViewMode/${viewMode}`,
    method: 'post',
  })
}

export const getViewMode = () => {
  return request({
    url: `/systemUser/getViewMode`,
    method: 'post',
  })
}

export const lock = () => {
  return request({
    url: '/lock',
    method: 'get',
  })
}
