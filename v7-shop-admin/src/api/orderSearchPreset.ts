import request from '/@/utils/request'

export type OrderSearchPresetPageType = 'ORDER_MANAGER' | 'ORDER_AUDIT'
export type OrderSearchPresetTimeMode = 'ABSOLUTE' | 'RELATIVE'

export interface OrderSearchPreset {
  id: string
  name: string
  pageType: OrderSearchPresetPageType
  timeMode: OrderSearchPresetTimeMode
  queryParams: Record<string, any>
  lastUsedTime?: string
  createTime?: string
}

export interface SaveOrderSearchPresetRequest {
  pageType: OrderSearchPresetPageType
  name: string
  timeMode: OrderSearchPresetTimeMode
  queryParams: Record<string, any>
}

// 后端列表接口统一经 CommonResult + ListWrapper 包装，数组在 data.list
export interface OrderSearchPresetListData {
  list: OrderSearchPreset[]
  total: number
}

export function listOrderSearchPresets(pageType: OrderSearchPresetPageType) {
  return request({
    url: '/order-search-presets',
    method: 'get',
    params: { pageType },
  }) as Promise<{ data: OrderSearchPresetListData }>
}

export function saveOrderSearchPreset(data: SaveOrderSearchPresetRequest) {
  return request({
    url: '/order-search-presets',
    method: 'post',
    data,
  })
}

export function deleteOrderSearchPreset(id: string) {
  return request({
    url: `/order-search-presets/${id}`,
    method: 'delete',
  })
}

export function useOrderSearchPreset(id: string) {
  return request({
    url: `/order-search-presets/${id}/use`,
    method: 'post',
  }) as Promise<{ data: OrderSearchPreset }>
}
