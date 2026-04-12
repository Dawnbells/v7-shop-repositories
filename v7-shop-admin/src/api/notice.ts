import request from '/@/utils/request'

export const getList = () => {
  return request({
    url: '/notice/getList',
    method: 'get',
  })
}

export const getUnreadCount = () => {
  return request({
    url: '/notice/unreadCount',
    method: 'get',
  })
}

export const markAsRead = (id: string | number) => {
  return request({
    url: `/notice/markAsRead/${id}`,
    method: 'post',
  })
}

export const markAllAsRead = () => {
  return request({
    url: '/notice/markAllAsRead',
    method: 'post',
  })
}
