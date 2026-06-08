import request from '/@/utils/request'

export type PushPlusTemplate = 'markdown' | 'html' | 'txt' | 'json'

export interface PushPlusNotificationConfig {
  open: boolean
  serverIpSwitchOpen: boolean
  tokenSet: boolean
  template: PushPlusTemplate
}

export interface SavePushPlusNotificationConfig {
  open: boolean
  serverIpSwitchOpen: boolean
  token?: string
  template: PushPlusTemplate
}

export interface PushPlusNotificationTestResult {
  success: boolean
  message?: string
  shortCode?: string
}

export function getPushPlusNotificationConfig() {
  return request({
    url: '/pushplus-notification/config',
    method: 'get',
  })
}

export function savePushPlusNotificationConfig(data: SavePushPlusNotificationConfig) {
  return request({
    url: '/pushplus-notification/config',
    method: 'post',
    data,
  })
}

export function testPushPlusNotification(content: string) {
  return request({
    url: '/pushplus-notification/test-send',
    method: 'post',
    data: {
      content,
    },
  })
}
