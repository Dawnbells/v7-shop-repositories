import type { OrderSearchPresetTimeMode } from '/@/api/orderSearchPreset'

const PRESET_FIELDS = [
  'searchType',
  'keywords',
  'orderStatus',
  'botOrderStatus',
  'repeatType',
  'countryId',
  'platform',
  'belongEmployeeIds',
  'belongDepartmentIds',
  'contacted',
] as const

interface RelativeDateEndpoint {
  dayOffset: number
  time: string
}

interface RelativeDateRange {
  mode: 'RELATIVE'
  start: RelativeDateEndpoint
  end: RelativeDateEndpoint
}

export const defaultOrderSearchPresetQuery = () => ({
  searchType: 'ORDER_ID',
  keywords: '',
  orderStatus: undefined,
  botOrderStatus: undefined,
  repeatType: undefined,
  countryId: undefined,
  platform: undefined,
  dateRange: undefined,
  belongEmployeeIds: undefined,
  belongDepartmentIds: undefined,
  contacted: undefined,
})

export const buildPresetQueryParams = (
  queryForm: Record<string, any>,
  timeMode: OrderSearchPresetTimeMode,
  now = new Date()
) => {
  const snapshot: Record<string, any> = {}

  for (const field of PRESET_FIELDS) {
    const value = queryForm[field]
    if (!isEmptyPresetValue(value)) {
      snapshot[field] = clonePresetValue(value)
    }
  }

  const dateRange = normalizeDateRange(queryForm.dateRange)
  if (dateRange) {
    snapshot.dateRange =
      timeMode === 'RELATIVE'
        ? toRelativeDateRange(dateRange[0], dateRange[1], now)
        : dateRange.map((date) => date.toISOString())
  }

  return snapshot
}

export const applyPresetQueryParams = (
  queryForm: Record<string, any>,
  queryParams: Record<string, any> | undefined,
  now = new Date()
) => {
  const pageSize = queryForm.pageSize
  Object.assign(queryForm, defaultOrderSearchPresetQuery(), {
    pageNo: 1,
    pageSize,
  })

  const params = queryParams || {}
  for (const field of PRESET_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(params, field)) {
      queryForm[field] = clonePresetValue(params[field])
    }
  }

  if (Object.prototype.hasOwnProperty.call(params, 'dateRange')) {
    queryForm.dateRange = restoreDateRange(params.dateRange, now)
  }
}

const isEmptyPresetValue = (value: any) => {
  if (value === false) return false
  if (value === 0) return false
  if (value === undefined || value === null) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

const clonePresetValue = (value: any): any => {
  if (Array.isArray(value)) {
    return value.map((item) => clonePresetValue(item))
  }
  if (value instanceof Date) {
    return value.toISOString()
  }
  if (value && typeof value === 'object') {
    return JSON.parse(JSON.stringify(value))
  }
  return value
}

const normalizeDateRange = (value: any): [Date, Date] | undefined => {
  if (!Array.isArray(value) || value.length !== 2) return undefined
  const start = toValidDate(value[0])
  const end = toValidDate(value[1])
  if (!start || !end) return undefined
  return [start, end]
}

const toValidDate = (value: any): Date | undefined => {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return undefined
  return date
}

const toRelativeDateRange = (start: Date, end: Date, now: Date): RelativeDateRange => ({
  mode: 'RELATIVE',
  start: {
    dayOffset: diffLocalDays(start, now),
    time: formatTime(start),
  },
  end: {
    dayOffset: diffLocalDays(end, now),
    time: formatTime(end),
  },
})

const restoreDateRange = (value: any, now: Date): [Date, Date] | undefined => {
  if (isRelativeDateRange(value)) {
    return [restoreRelativeEndpoint(value.start, now), restoreRelativeEndpoint(value.end, now)]
  }

  const normalized = normalizeDateRange(value)
  return normalized ? [normalized[0], normalized[1]] : undefined
}

const isRelativeDateRange = (value: any): value is RelativeDateRange =>
  value &&
  value.mode === 'RELATIVE' &&
  value.start &&
  value.end &&
  typeof value.start.dayOffset === 'number' &&
  typeof value.start.time === 'string' &&
  typeof value.end.dayOffset === 'number' &&
  typeof value.end.time === 'string'

const restoreRelativeEndpoint = (endpoint: RelativeDateEndpoint, now: Date) => {
  const date = startOfLocalDay(now)
  date.setDate(date.getDate() + endpoint.dayOffset)
  const [hour, minute, second] = endpoint.time.split(':').map((item) => Number(item))
  date.setHours(hour || 0, minute || 0, second || 0, 0)
  return date
}

const diffLocalDays = (date: Date, base: Date) => {
  const oneDay = 24 * 60 * 60 * 1000
  return Math.round((startOfLocalDay(date).getTime() - startOfLocalDay(base).getTime()) / oneDay)
}

const startOfLocalDay = (date: Date) => {
  const value = new Date(date)
  value.setHours(0, 0, 0, 0)
  return value
}

const formatTime = (date: Date) =>
  [date.getHours(), date.getMinutes(), date.getSeconds()]
    .map((item) => String(item).padStart(2, '0'))
    .join(':')
