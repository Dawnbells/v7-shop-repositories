import request from '/@/utils/request'

export type StatisticsGranularity = 'DAY' | 'MONTH'
export type StatisticsDimension = 'EMPLOYEE' | 'DEPARTMENT'
export type StatisticsPlatform = 'V7_SHOP' | 'SHOPLINE' | 'XYZ'

export interface StatisticsConfig {
  defaultTargetCurrencyCode: string
  timeZoneId: string
  exchangeRates: Record<string, string>
}

export interface StatisticsContext {
  requesterUserId: string
  requesterName: string
  dimensions: StatisticsDimension[]
  employeeLocked: boolean
  allowUnassigned: boolean
  websiteScoped: boolean
  websiteId?: string
  platforms: StatisticsPlatform[]
  dayRangeMaxMonths: number
  monthRangeMaxYears: number
}

export interface StatisticsOption {
  id: string
  name: string
  parentId?: string
  departmentId?: string
  departmentName?: string
  telephone?: string
  status?: string
  historical: boolean
  disabled: boolean
  children?: StatisticsOption[]
}

export interface CurrencyOption {
  code: string
  name: string
  symbol?: string
  exchangeRate?: string
  fractionDigits: number
}

export interface StatisticsMetrics {
  orderCount: number
  validOrderCount: number
  invalidOrderCount: number
  deliveredOrderCount: number
  undeliveredOrderCount: number
  deliveryRate?: string
  totalSalesAmount?: string
  invalidSalesAmount?: string
  undeliveredSalesAmount?: string
  deliveredSalesAmount?: string
  missingRateOrderCount: number
}

export interface StatisticsBucket {
  key: string
  startAt: string
  endAt: string
  partial: boolean
  metrics: StatisticsMetrics
}

export interface StatisticsGroup {
  groupKey: string
  id?: string
  name: string
  historical: boolean
  metrics: StatisticsMetrics
}

export interface StatisticsBucketGroup extends StatisticsGroup {
  bucketKey: string
}

export interface StatisticsPageRequest {
  pageNo: number
  pageSize: number
  sortBy?: string
}

export interface StatisticsPageResponse<T> {
  list: T[]
  total: number
  pageNo: number
  pageSize: number
  totalPages: number
}
export interface OriginalCurrencySummary {
  currencyCode: string
  orderCount: number
  totalAmount: string
  invalidAmount: string
  undeliveredAmount: string
  deliveredAmount: string
}

export interface MissingRateSummary {
  currencyCode?: string
  reason: string
  orderCount: number
  originalAmount: string
}

export interface StatisticsResult {
  generatedAt?: string
  timeZoneId?: string
  targetCurrencyCode: string
  summary: StatisticsMetrics
  buckets: StatisticsBucket[]
  groups: StatisticsGroup[]
  bucketGroups?: StatisticsBucketGroup[]
  originalCurrencies: OriginalCurrencySummary[]
  missingRates: MissingRateSummary[]
}

export interface StatisticsQuery {
  startDate: string
  endDate: string
  granularity: StatisticsGranularity
  dimension: StatisticsDimension
  employeeIds: string[]
  departmentIds: string[]
  includeUnassigned: boolean
  platforms: StatisticsPlatform[]
  domains: string[]
  targetCurrencyCode: string
  temporaryExchangeRates: Record<string, string>
  forceRefresh: boolean
}

export interface StatisticsQueryResponse {
  state: 'COMPLETED' | 'PROCESSING' | 'CANCELLED'
  resultToken?: string
  queryJobId?: string
  snapshotExpiresAt?: string
  result?: StatisticsResult
  cached: boolean
  degraded: boolean
  message?: string
}

export interface StatisticsQueryJob {
  queryJobId: string
  state: 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  createdAt: string
  finishedAt?: string
  resultToken?: string
  message?: string
}

export function getStatisticsConfig(timeZoneId?: string) {
  return request({
    url: '/order-statistics/config',
    method: 'get',
    headers: timeZoneId ? { 'X-Browser-Time-Zone': timeZoneId } : undefined,
  })
}

export function saveStatisticsConfig(data: StatisticsConfig) {
  return request({
    url: '/order-statistics/config',
    method: 'put',
    data,
  })
}

export function getStatisticsContext() {
  return request({
    url: '/order-statistics/options/context',
    method: 'get',
  })
}

export function getStatisticsCurrencies() {
  return request({
    url: '/order-statistics/options/currencies',
    method: 'get',
  })
}

export function getStatisticsEmployees(keyword = '', includeHistorical = false) {
  return request({
    url: '/order-statistics/options/employees',
    method: 'get',
    params: { keyword, includeHistorical },
  })
}

export function getStatisticsDepartments(keyword = '', includeHistorical = false) {
  return request({
    url: '/order-statistics/options/departments',
    method: 'get',
    params: { keyword, includeHistorical },
  })
}

export function getStatisticsDomains(keyword = '') {
  return request({
    url: '/order-statistics/options/domains',
    method: 'get',
    params: { keyword },
  })
}

export function queryOrderStatistics(data: StatisticsQuery) {
  return request({
    url: '/order-statistics/query',
    method: 'post',
    data,
  })
}

export function getOrderStatisticsResult(resultToken: string) {
  return request({
    url: `/order-statistics/results/${resultToken}`,
    method: 'get',
  })
}


export function getOrderStatisticsGroupsPage(resultToken: string, data: StatisticsPageRequest) {
  return request({
    url: `/order-statistics/results/${resultToken}/groups/page`,
    method: 'post',
    data,
  })
}

export function getOrderStatisticsBucketGroupsPage(
  resultToken: string,
  data: StatisticsPageRequest
) {
  return request({
    url: `/order-statistics/results/${resultToken}/bucket-groups/page`,
    method: 'post',
    data,
  })
}
export function getStatisticsQueryJob(queryJobId: string) {
  return request({
    url: `/order-statistics/query-jobs/${queryJobId}`,
    method: 'get',
  })
}

export function cancelStatisticsQueryJob(queryJobId: string) {
  return request({
    url: `/order-statistics/query-jobs/${queryJobId}/cancel`,
    method: 'post',
  })
}

export function exportOrderStatistics(resultToken: string) {
  return request({
    url: `/order-statistics/results/${resultToken}/export`,
    method: 'post',
  })
}
