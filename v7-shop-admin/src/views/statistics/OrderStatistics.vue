<template>
  <div class="statistics-page">
    <section class="report-hero">
      <div>
        <span class="eyebrow">ORDER INTELLIGENCE</span>
        <h1>订单统计分析</h1>
        <p>按员工或部门汇总订单当前状态，金额统一换算后展示。</p>
      </div>
      <div class="hero-meta">
        <span>{{ context?.websiteScoped ? '当前网站范围' : '公司权限范围' }}</span>
        <strong>{{ config.timeZoneId || browserTimeZone }}</strong>
      </div>
    </section>

    <el-card class="filter-card" shadow="never">
      <el-form label-position="top" :model="form">
        <div class="filter-grid">
          <el-form-item label="统计日期">
            <el-date-picker
              v-model="form.dateRange"
              end-placeholder="结束日期"
              start-placeholder="开始日期"
              type="daterange"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>

          <el-form-item label="时间粒度">
            <el-segmented
              v-model="form.granularity"
              :options="[
                { label: '按天', value: 'DAY' },
                { label: '按月', value: 'MONTH' },
              ]"
            />
          </el-form-item>

          <el-form-item v-if="!context?.employeeLocked" label="统计方式">
            <el-segmented
              v-model="form.dimension"
              :options="dimensionOptions"
              @change="handleDimensionChange"
            />
          </el-form-item>

          <el-form-item v-if="form.dimension === 'EMPLOYEE'" label="员工">
            <el-select
              v-model="form.employeeIds"
              collapse-tags
              collapse-tags-tooltip
              filterable
              :loading="employeeLoading"
              multiple
              placeholder="搜索姓名、手机号或内部 ID"
              remote
              :remote-method="loadEmployees"
              :disabled="context?.employeeLocked"
            >
              <el-option
                v-for="item in employeeOptions"
                :key="item.id"
                :label="optionLabel(item)"
                :value="item.id"
              >
                <div class="option-row">
                  <span>{{ item.name }}</span>
                  <small>{{ item.departmentName || item.telephone || item.id }}</small>
                </div>
              </el-option>
            </el-select>
            <el-checkbox
              v-if="!context?.employeeLocked"
              v-model="form.includeHistorical"
              class="history-toggle"
            >
              包含历史员工
            </el-checkbox>
          </el-form-item>

          <el-form-item v-else label="部门">
            <el-tree-select
              ref="departmentTreeRef"
              v-model="form.departmentIds"
              check-strictly
              collapse-tags
              collapse-tags-tooltip
              :data="departmentTree"
              filterable
              multiple
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              show-checkbox
              @check="handleDepartmentCheck"
            />
            <el-checkbox v-model="form.includeHistorical" class="history-toggle">
              包含历史部门
            </el-checkbox>
          </el-form-item>

          <el-form-item label="订单平台">
            <el-select v-model="form.platforms" collapse-tags multiple placeholder="全部平台">
              <el-option
                v-for="platform in context?.platforms || []"
                :key="platform"
                :label="platformLabels[platform]"
                :value="platform"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="历史下单域名">
            <el-select
              v-model="form.domains"
              allow-create
              collapse-tags
              filterable
              :loading="domainLoading"
              multiple
              placeholder="精确匹配，可多选"
              remote
              :remote-method="loadDomains"
            >
              <el-option v-for="domain in domainOptions" :key="domain" :label="domain" :value="domain" />
            </el-select>
          </el-form-item>

          <el-form-item label="目标币种">
            <el-select v-model="form.targetCurrencyCode" filterable>
              <el-option
                v-for="currency in currencies"
                :key="currency.code"
                :label="`${currency.code} · ${currency.name}`"
                :value="currency.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="context?.allowUnassigned" label="未归属订单">
            <el-switch
              v-model="form.includeUnassigned"
              active-text="纳入统计"
              inactive-text="不统计"
            />
          </el-form-item>
        </div>

        <el-collapse class="rate-collapse">
          <el-collapse-item name="rates" title="本次查询临时汇率（可选）">
            <div class="rate-grid">
              <label v-for="currency in currencies" :key="currency.code">
                <span>{{ currency.code }}</span>
                <el-input
                  v-model="temporaryRates[currency.code]"
                  :disabled="currency.code === 'USD'"
                  placeholder="使用个人/系统汇率"
                />
              </label>
            </div>
          </el-collapse-item>
        </el-collapse>

        <div class="filter-actions">
          <span class="range-hint">
            按天最多 {{ context?.dayRangeMaxMonths || 2 }} 个月；按月最多
            {{ context?.monthRangeMaxYears || 5 }} 年
          </span>
          <div>
            <el-button :disabled="!result" @click="clearResult">清空结果</el-button>
            <el-button
              v-if="querying && activeQueryJobId"
              type="danger"
              plain
              @click="cancelActiveQuery"
            >
              <el-icon><CircleClose /></el-icon>
              取消查询
            </el-button>
            <el-button :loading="querying" type="primary" @click="runQuery(false)">
              <el-icon><Search /></el-icon>
              查询
            </el-button>
            <el-button :disabled="!result" :loading="querying" @click="runQuery(true)">
              <el-icon><Refresh /></el-icon>
              刷新当前状态
            </el-button>
          </div>
        </div>
      </el-form>
    </el-card>

    <el-alert
      v-if="queryMeta.message"
      class="result-alert"
      :closable="false"
      :title="queryMeta.message"
      :type="queryMeta.degraded ? 'warning' : 'info'"
      show-icon
    />

    <template v-if="result">
      <div class="result-meta">
        <span>{{ queryMeta.cached ? '一分钟缓存结果' : '新生成结果' }}</span>
        <span v-if="queryMeta.snapshotExpiresAt">
          快照有效至 {{ formatDateTime(queryMeta.snapshotExpiresAt) }}
        </span>
        <span>目标币种 {{ result.targetCurrencyCode }}</span>
        <el-button
          :disabled="queryMeta.degraded || !currentResultToken"
          :loading="exportLoading"
          size="small"
          type="primary"
          plain
          @click="exportResult"
        >
          <el-icon><Download /></el-icon>
          {{ exportLoading ? `导出中 ${exportProgress}%` : '导出 Excel' }}
        </el-button>
      </div>

      <section class="metric-grid">
        <article v-for="metric in metricCards" :key="metric.label" class="metric-card">
          <span>{{ metric.label }}</span>
          <strong :class="metric.tone">{{ metric.value }}</strong>
          <small>{{ metric.note }}</small>
        </article>
      </section>

      <section class="chart-grid">
        <el-card shadow="never">
          <template #header><strong>订单趋势</strong></template>
          <vab-chart :option="orderChartOption" />
        </el-card>
        <el-card shadow="never">
          <template #header><strong>签收率趋势</strong></template>
          <vab-chart :option="deliveryChartOption" />
        </el-card>
        <el-card class="sales-chart" shadow="never">
          <template #header><strong>销售额趋势</strong></template>
          <vab-chart :option="salesChartOption" />
        </el-card>
      </section>

      <el-card class="table-card" shadow="never">
        <template #header>
          <div class="table-header">
            <div>
              <strong>{{ form.dimension === 'EMPLOYEE' ? '员工汇总' : '部门汇总' }}</strong>
              <span>共 {{ groupPage.total || result.groups.length }} 组</span>
            </div>
            <el-tag v-if="result.summary.missingRateOrderCount" type="warning">
              {{ result.summary.missingRateOrderCount }} 笔缺少汇率
            </el-tag>
          </div>
        </template>
        <el-table v-loading="groupPage.loading" :data="groupPage.list" stripe>
          <el-table-column label="名称" min-width="180">
            <template #default="{ row }">
              <span>{{ row.name }}</span>
              <el-tag v-if="row.historical" class="history-tag" size="small" type="info">
                历史
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="订单数" prop="metrics.orderCount" sortable width="110" />
          <el-table-column label="有效订单" prop="metrics.validOrderCount" sortable width="120" />
          <el-table-column label="无效订单" prop="metrics.invalidOrderCount" sortable width="120" />
          <el-table-column label="签收率" width="110">
            <template #default="{ row }">{{ formatRate(row.metrics.deliveryRate) }}</template>
          </el-table-column>
          <el-table-column label="总销售额" min-width="150">
            <template #default="{ row }">
              {{ formatMoney(row.metrics.totalSalesAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="签收销售额" min-width="150">
            <template #default="{ row }">
              {{ formatMoney(row.metrics.deliveredSalesAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="未签收销售额" min-width="160">
            <template #default="{ row }">
              {{ formatMoney(row.metrics.undeliveredSalesAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="无效销售额" min-width="150">
            <template #default="{ row }">
              {{ formatMoney(row.metrics.invalidSalesAmount) }}
            </template>
          </el-table-column>
        </el-table>
        <div
          v-if="!queryMeta.degraded && currentResultToken && groupPage.total > groupPage.pageSize"
          class="table-pagination"
        >
          <el-pagination
            v-model:current-page="groupPage.pageNo"
            v-model:page-size="groupPage.pageSize"
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[20, 50, 100]"
            :total="groupPage.total"
            @current-change="handleGroupPageChange"
            @size-change="handleGroupPageSizeChange"
          />
        </div>
      </el-card>

      <div v-if="result.missingRates.length" class="missing-rate-block">
        <h3>缺失汇率</h3>
        <el-table :data="result.missingRates" size="small">
          <el-table-column label="币种" prop="currencyCode" />
          <el-table-column label="原因" prop="reason" />
          <el-table-column label="订单数" prop="orderCount" />
          <el-table-column label="原币金额" prop="originalAmount" />
        </el-table>
      </div>
    </template>

    <el-empty v-else class="empty-state" description="设置筛选条件后点击查询，页面不会自动统计" />
  </div>
</template>

<script lang="ts" setup>
import { CircleClose, Download, Refresh, Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import type {
  CurrencyOption,
  StatisticsContext,
  StatisticsPageResponse,
  StatisticsGroup,
  StatisticsDimension,
  StatisticsOption,
  StatisticsPlatform,
  StatisticsQuery,
  StatisticsQueryJob,
  StatisticsQueryResponse,
  StatisticsResult,
} from '/@/api/orderStatistics'
import { downloadFile, status as getTaskStatus } from '/@/api/taskManagement'
import {
  cancelStatisticsQueryJob,
  exportOrderStatistics,
  getOrderStatisticsResult,
  getOrderStatisticsGroupsPage,
  getStatisticsConfig,
  getStatisticsContext,
  getStatisticsCurrencies,
  getStatisticsDepartments,
  getStatisticsDomains,
  getStatisticsEmployees,
  getStatisticsQueryJob,
  queryOrderStatistics,
} from '/@/api/orderStatistics'

defineOptions({ name: 'OrderStatistics' })

const STORAGE_KEY = 'order-statistics:filters'
const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'
const today = dayjs()
const context = ref<StatisticsContext>()
const config = reactive({
  defaultTargetCurrencyCode: 'USD',
  timeZoneId: browserTimeZone,
  exchangeRates: {} as Record<string, string>,
})
const currencies = ref<CurrencyOption[]>([])
const employeeOptions = ref<StatisticsOption[]>([])
const departmentTree = ref<StatisticsOption[]>([])
const domainOptions = ref<string[]>([])
const employeeLoading = ref(false)
const domainLoading = ref(false)
const querying = ref(false)
const exportLoading = ref(false)
const exportProgress = ref(0)
const activeQueryJobId = ref('')
const currentResultToken = ref('')
let queryPollVersion = 0
const result = ref<StatisticsResult>()
const groupPage = reactive({
  list: [] as StatisticsGroup[],
  total: 0,
  pageNo: 1,
  pageSize: 20,
  totalPages: 0,
  loading: false,
})
const departmentTreeRef = ref<any>()
const temporaryRates = reactive<Record<string, string>>({ USD: '1' })
const queryMeta = reactive({
  cached: false,
  degraded: false,
  snapshotExpiresAt: '',
  message: '',
})

const form = reactive({
  dateRange: [today.subtract(29, 'day').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')],
  granularity: 'DAY' as 'DAY' | 'MONTH',
  dimension: 'EMPLOYEE' as StatisticsDimension,
  employeeIds: [] as string[],
  departmentIds: [] as string[],
  includeHistorical: false,
  includeUnassigned: false,
  platforms: [] as StatisticsPlatform[],
  domains: [] as string[],
  targetCurrencyCode: 'USD',
})

const platformLabels: Record<StatisticsPlatform, string> = {
  V7_SHOP: 'V7 Shop',
  SHOPLINE: 'SHOPLINE',
  XYZ: '小宇宙',
}

const dimensionOptions = computed(() =>
  (context.value?.dimensions || ['EMPLOYEE']).map((value) => ({
    value,
    label: value === 'EMPLOYEE' ? '按员工' : '按部门',
  }))
)

const responseData = <T,>(response: any): T => (response?.data ?? response) as T
const responseList = <T,>(response: any): T[] => {
  const data = response?.data ?? response
  return (data?.list || data || []) as T[]
}

const optionLabel = (item: StatisticsOption) =>
  `${item.name}${item.historical ? '（历史）' : ''}`

const mergeOptions = (current: StatisticsOption[], incoming: StatisticsOption[]) => {
  const map = new Map(current.map((item) => [item.id, item]))
  incoming.forEach((item) => map.set(item.id, item))
  return [...map.values()]
}

const buildDepartmentTree = (items: StatisticsOption[]) => {
  const map = new Map<string, StatisticsOption>()
  items.forEach((item) => map.set(item.id, { ...item, disabled: false, children: [] }))
  const roots: StatisticsOption[] = []
  map.forEach((item) => {
    const parent = item.parentId ? map.get(item.parentId) : undefined
    if (parent) parent.children?.push(item)
    else roots.push(item)
  })
  return roots
}

const loadEmployees = async (keyword = '') => {
  employeeLoading.value = true
  try {
    const list = responseList<StatisticsOption>(
      await getStatisticsEmployees(keyword, form.includeHistorical)
    )
    employeeOptions.value = mergeOptions(
      employeeOptions.value.filter((item) => form.employeeIds.includes(item.id)),
      list
    )
  } finally {
    employeeLoading.value = false
  }
}

const loadDepartments = async () => {
  const list = responseList<StatisticsOption>(
    await getStatisticsDepartments('', form.includeHistorical)
  )
  departmentTree.value = buildDepartmentTree(list)
}

const loadDomains = async (keyword = '') => {
  domainLoading.value = true
  try {
    domainOptions.value = responseList<string>(await getStatisticsDomains(keyword))
  } finally {
    domainLoading.value = false
  }
}

const restoreFilters = () => {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return false
  try {
    Object.assign(form, JSON.parse(raw))
    return true
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return false
  }
}

const initialize = async () => {
  const restored = restoreFilters()
  const [contextResponse, configResponse, currencyResponse] = await Promise.all([
    getStatisticsContext(),
    getStatisticsConfig(browserTimeZone),
    getStatisticsCurrencies(),
  ])
  context.value = responseData<StatisticsContext>(contextResponse)
  Object.assign(config, responseData<typeof config>(configResponse))
  currencies.value = responseList<CurrencyOption>(currencyResponse)
  if (!restored) {
    form.targetCurrencyCode = config.defaultTargetCurrencyCode || 'USD'
  }
  currencies.value.forEach((currency) => {
    temporaryRates[currency.code] = currency.code === 'USD' ? '1' : ''
  })
  if (context.value.employeeLocked) {
    form.dimension = 'EMPLOYEE'
    form.employeeIds = [context.value.requesterUserId]
  } else if (!context.value.dimensions.includes(form.dimension)) {
    form.dimension = context.value.dimensions[0]
  }
  await Promise.all([loadEmployees(), loadDepartments(), loadDomains()])
}

const handleDimensionChange = () => {
  form.includeHistorical = false
  form.includeUnassigned = false
  if (form.dimension === 'EMPLOYEE') form.departmentIds = []
  else form.employeeIds = []
}

const descendantIds = (node: StatisticsOption): string[] =>
  (node.children || []).flatMap((child) => [child.id, ...descendantIds(child)])

const handleDepartmentCheck = (node: StatisticsOption, detail: { checkedKeys: string[] }) => {
  const checked = detail.checkedKeys.includes(node.id)
  const descendants = descendantIds(node)
  const keys = checked
    ? [...new Set([...detail.checkedKeys, ...descendants])]
    : detail.checkedKeys.filter((key) => !descendants.includes(key))
  departmentTreeRef.value?.setCheckedKeys(keys)
  form.departmentIds = keys
}

const validateQuery = () => {
  if (!form.dateRange?.[0] || !form.dateRange?.[1]) return '请选择统计日期'
  const start = dayjs(form.dateRange[0])
  const end = dayjs(form.dateRange[1])
  if (end.isBefore(start)) return '结束日期不能早于开始日期'
  if (form.granularity === 'DAY' && end.diff(start, 'day') + 1 > 62)
    return '按天统计最多选择两个月'
  if (form.granularity === 'MONTH' && end.diff(start, 'month') + 1 > 60)
    return '按月统计最多选择五年'
  if (
    form.dimension === 'EMPLOYEE' &&
    !form.employeeIds.length &&
    !form.includeUnassigned
  )
    return '至少选择一个员工'
  if (
    form.dimension === 'DEPARTMENT' &&
    !form.departmentIds.length &&
    !form.includeUnassigned
  )
    return '至少选择一个部门'
  for (const [code, rate] of Object.entries(temporaryRates)) {
    if (!rate || code === 'USD') continue
    if (!/^\d+(\.\d{1,8})?$/.test(rate) || Number(rate) <= 0)
      return `${code} 临时汇率必须大于 0，且最多 8 位小数`
  }
  return ''
}

const sleep = (milliseconds: number) =>
  new Promise((resolve) => setTimeout(resolve, milliseconds))

const resetGroupPage = () => {
  Object.assign(groupPage, {
    list: [],
    total: 0,
    pageNo: 1,
    totalPages: 0,
    loading: false,
  })
}

const useLocalGroupPage = () => {
  const list = result.value?.groups || []
  Object.assign(groupPage, {
    list,
    total: list.length,
    pageNo: 1,
    totalPages: list.length ? 1 : 0,
    loading: false,
  })
}

const loadGroupPage = async (pageNo = 1) => {
  if (!result.value) {
    resetGroupPage()
    return
  }
  if (!currentResultToken.value || queryMeta.degraded) {
    useLocalGroupPage()
    return
  }
  groupPage.loading = true
  try {
    const page = responseData<StatisticsPageResponse<StatisticsGroup>>(
      await getOrderStatisticsGroupsPage(currentResultToken.value, {
        pageNo,
        pageSize: groupPage.pageSize,
      })
    )
    Object.assign(groupPage, {
      list: page.list || [],
      total: page.total || 0,
      pageNo: page.pageNo || pageNo,
      pageSize: page.pageSize || groupPage.pageSize,
      totalPages: page.totalPages || 0,
    })
  } finally {
    groupPage.loading = false
  }
}

const handleGroupPageChange = (pageNo: number) => {
  void loadGroupPage(pageNo)
}

const handleGroupPageSizeChange = (pageSize: number) => {
  groupPage.pageSize = pageSize
  void loadGroupPage(1)
}
const applyCompletedResponse = (response: StatisticsQueryResponse) => {
  result.value = response.result
  currentResultToken.value = response.resultToken || ''
  Object.assign(queryMeta, {
    cached: response.cached,
    degraded: response.degraded,
    snapshotExpiresAt: response.snapshotExpiresAt || '',
    message: response.message || '',
  })
}

const pollQueryJob = async (queryJobId: string, version: number) => {
  while (version === queryPollVersion && activeQueryJobId.value === queryJobId) {
    const job = responseData<StatisticsQueryJob>(await getStatisticsQueryJob(queryJobId))
    if (job.state === 'COMPLETED' && job.resultToken) {
      const response = responseData<StatisticsResult>(
        await getOrderStatisticsResult(job.resultToken)
      )
      result.value = response
      currentResultToken.value = job.resultToken
      activeQueryJobId.value = ''
      querying.value = false
      Object.assign(queryMeta, {
        cached: false,
        degraded: false,
        snapshotExpiresAt: '',
        message: '',
      })
      await loadGroupPage(1)
      ElMessage.success('统计完成')
      return
    }
    if (job.state === 'FAILED' || job.state === 'CANCELLED') {
      activeQueryJobId.value = ''
      querying.value = false
      queryMeta.message = job.message || (job.state === 'CANCELLED' ? '查询已取消' : '统计失败')
      if (job.state === 'FAILED') ElMessage.error(queryMeta.message)
      return
    }
    await sleep(800)
  }
}

const cancelActiveQuery = async () => {
  const jobId = activeQueryJobId.value
  queryPollVersion++
  activeQueryJobId.value = ''
  querying.value = false
  if (jobId) {
    await cancelStatisticsQueryJob(jobId)
    queryMeta.message = '查询已取消'
  }
}

const runQuery = async (forceRefresh: boolean) => {
  const error = validateQuery()
  if (error) {
    ElMessage.warning(error)
    return
  }
  if (activeQueryJobId.value) await cancelActiveQuery()
  const rates = Object.fromEntries(
    Object.entries(temporaryRates).filter(
      ([code, rate]) => code !== 'USD' && Boolean(rate)
    )
  )
  const payload: StatisticsQuery = {
    startDate: form.dateRange[0],
    endDate: form.dateRange[1],
    granularity: form.granularity,
    dimension: form.dimension,
    employeeIds: form.dimension === 'EMPLOYEE' ? form.employeeIds : [],
    departmentIds: form.dimension === 'DEPARTMENT' ? form.departmentIds : [],
    includeUnassigned: form.includeUnassigned,
    platforms: form.platforms,
    domains: form.domains.map((domain) => domain.trim().toLowerCase()),
    targetCurrencyCode: form.targetCurrencyCode,
    temporaryExchangeRates: rates,
    forceRefresh,
  }
  querying.value = true
  const version = ++queryPollVersion
  try {
    const response = responseData<StatisticsQueryResponse>(
      await queryOrderStatistics(payload)
    )
    if (response.state === 'PROCESSING' && response.queryJobId) {
      activeQueryJobId.value = response.queryJobId
      queryMeta.message = '统计任务正在后台处理，可随时取消'
      void pollQueryJob(response.queryJobId, version)
      return
    }
    applyCompletedResponse(response)
    await loadGroupPage(1)
    querying.value = false
    ElMessage.success(response.cached ? '已读取最近统计结果' : '统计完成')
  } catch (error) {
    querying.value = false
    throw error
  }
}

const exportResult = async () => {
  if (!currentResultToken.value || queryMeta.degraded) return
  exportLoading.value = true
  exportProgress.value = 0
  try {
    const taskId = String(responseData<string | number>(
      await exportOrderStatistics(currentResultToken.value)
    ))
    while (true) {
      const task = responseData<any>(await getTaskStatus(taskId))
      exportProgress.value = Number(task.progress || 0)
      if (task.state === 'COMPLETED') {
        const response = await downloadFile(taskId)
        if (!response.ok) throw new Error('导出文件下载失败')
        const blob = await response.blob()
        const link = document.createElement('a')
        link.href = URL.createObjectURL(blob)
        link.download = `订单统计_${dayjs().format('YYYYMMDD_HHmm')}.xlsx`
        link.click()
        URL.revokeObjectURL(link.href)
        ElMessage.success('统计报表已导出')
        return
      }
      if (task.state === 'FAILED' || task.state === 'CANCELLED') {
        throw new Error(task.message || '统计导出失败')
      }
      await sleep(800)
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '统计导出失败')
  } finally {
    exportLoading.value = false
  }
}

const clearResult = () => {
  currentResultToken.value = ''
  result.value = undefined
  Object.assign(queryMeta, {
    cached: false,
    degraded: false,
    snapshotExpiresAt: '',
    message: '',
  })
  resetGroupPage()
}

watch(
  form,
  () => sessionStorage.setItem(STORAGE_KEY, JSON.stringify(form)),
  { deep: true }
)
watch(
  () => form.includeHistorical,
  () => {
    if (form.dimension === 'EMPLOYEE') loadEmployees()
    else loadDepartments()
  }
)

const formatRate = (value?: string) =>
  value == null ? '—' : `${(Number(value) * 100).toFixed(2)}%`
const formatMoney = (value?: string) => {
  if (value == null) return '—'
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: result.value?.targetCurrencyCode || form.targetCurrencyCode,
    maximumFractionDigits: 2,
  }).format(Number(value))
}
const formatDateTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')

const metricCards = computed(() => {
  const metrics = result.value?.summary
  if (!metrics) return []
  return [
    { label: '订单数', value: metrics.orderCount.toLocaleString(), note: '当前筛选全部订单', tone: '' },
    { label: '有效订单数', value: metrics.validOrderCount.toLocaleString(), note: '排除 INVALID', tone: 'positive' },
    { label: '无效订单数', value: metrics.invalidOrderCount.toLocaleString(), note: 'OrderStatus.INVALID', tone: 'negative' },
    { label: '签收率', value: formatRate(metrics.deliveryRate), note: '签收 ÷ 有效订单', tone: 'accent' },
    { label: '总销售额', value: formatMoney(metrics.totalSalesAmount), note: '统一目标币种', tone: '' },
    { label: '签收销售额', value: formatMoney(metrics.deliveredSalesAmount), note: '已签收有效订单', tone: 'positive' },
    { label: '未签收销售额', value: formatMoney(metrics.undeliveredSalesAmount), note: '有效但尚未签收', tone: 'accent' },
    { label: '无效订单销售额', value: formatMoney(metrics.invalidSalesAmount), note: '无效订单金额', tone: 'negative' },
  ]
})

const chartBase = {
  tooltip: { trigger: 'axis' },
  grid: { top: 28, right: 18, bottom: 32, left: 54 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: computed(() => result.value?.buckets.map((bucket) => bucket.key) || []),
  },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1f5' } } },
}

const orderChartOption = computed(() => ({
  ...chartBase,
  legend: { data: ['订单数', '有效订单', '无效订单'] },
  xAxis: { ...chartBase.xAxis, data: result.value?.buckets.map((bucket) => bucket.key) || [] },
  series: [
    { name: '订单数', type: 'line', smooth: true, data: result.value?.buckets.map((bucket) => bucket.metrics.orderCount) || [] },
    { name: '有效订单', type: 'line', smooth: true, data: result.value?.buckets.map((bucket) => bucket.metrics.validOrderCount) || [] },
    { name: '无效订单', type: 'line', smooth: true, data: result.value?.buckets.map((bucket) => bucket.metrics.invalidOrderCount) || [] },
  ],
}))

const deliveryChartOption = computed(() => ({
  ...chartBase,
  tooltip: { trigger: 'axis', valueFormatter: (value: number) => `${value.toFixed(2)}%` },
  xAxis: { ...chartBase.xAxis, data: result.value?.buckets.map((bucket) => bucket.key) || [] },
  yAxis: { ...chartBase.yAxis, axisLabel: { formatter: '{value}%' }, max: 100 },
  series: [
    {
      name: '签收率',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.12 },
      data: result.value?.buckets.map((bucket) => Number(bucket.metrics.deliveryRate || 0) * 100) || [],
    },
  ],
}))

const salesChartOption = computed(() => ({
  ...chartBase,
  legend: { data: ['总销售额', '签收销售额', '未签收销售额', '无效销售额'] },
  xAxis: { ...chartBase.xAxis, data: result.value?.buckets.map((bucket) => bucket.key) || [] },
  series: [
    { name: '总销售额', type: 'line', smooth: true, data: result.value?.buckets.map((bucket) => Number(bucket.metrics.totalSalesAmount || 0)) || [] },
    { name: '签收销售额', type: 'line', smooth: true, data: result.value?.buckets.map((bucket) => Number(bucket.metrics.deliveredSalesAmount || 0)) || [] },
    { name: '未签收销售额', type: 'line', smooth: true, data: result.value?.buckets.map((bucket) => Number(bucket.metrics.undeliveredSalesAmount || 0)) || [] },
    { name: '无效销售额', type: 'line', smooth: true, data: result.value?.buckets.map((bucket) => Number(bucket.metrics.invalidSalesAmount || 0)) || [] },
  ],
}))

onMounted(() => {
  initialize().catch(() => ElMessage.error('统计页面初始化失败'))
})

onBeforeUnmount(() => {
  queryPollVersion++
})
</script>

<style lang="scss" scoped>
.statistics-page {
  --ledger-ink: #1d2939;
  --ledger-muted: #667085;
  --ledger-line: #e4e9ef;
  --ledger-blue: #315f8c;
  --ledger-amber: #b7791f;
  display: grid;
  gap: 18px;
  padding-bottom: 24px;
}

.report-hero {
  position: relative;
  display: flex;
  align-items: end;
  justify-content: space-between;
  min-height: 132px;
  padding: 28px 32px;
  overflow: hidden;
  color: #f8fafc;
  background:
    linear-gradient(105deg, rgba(18, 40, 62, 0.98), rgba(49, 95, 140, 0.9)),
    repeating-linear-gradient(90deg, transparent 0 42px, rgba(255, 255, 255, 0.04) 43px);
  border-radius: 14px;
}

.report-hero::after {
  position: absolute;
  right: -36px;
  bottom: -80px;
  width: 260px;
  height: 260px;
  content: '';
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 50%;
  box-shadow: 0 0 0 42px rgba(255, 255, 255, 0.035);
}

.eyebrow {
  font-family: 'Courier New', monospace;
  font-size: 11px;
  letter-spacing: 0.18em;
  opacity: 0.65;
}

.report-hero h1 {
  margin: 8px 0 5px;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 30px;
  font-weight: 650;
}

.report-hero p {
  margin: 0;
  font-size: 14px;
  opacity: 0.75;
}

.hero-meta {
  z-index: 1;
  display: grid;
  gap: 6px;
  padding: 12px 16px;
  text-align: right;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
}

.hero-meta span {
  font-size: 11px;
  opacity: 0.64;
}

.filter-card,
.table-card,
.chart-grid :deep(.el-card) {
  border-color: var(--ledger-line);
  border-radius: 12px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(190px, 1fr));
  gap: 0 18px;
}

.filter-grid :deep(.el-select),
.filter-grid :deep(.el-date-editor),
.filter-grid :deep(.el-tree-select) {
  width: 100%;
}

.history-toggle {
  margin-top: 7px;
}

.option-row {
  display: flex;
  justify-content: space-between;
  width: 100%;
  gap: 16px;
}

.option-row small {
  color: var(--el-text-color-secondary);
}

.rate-collapse {
  margin-top: 4px;
  border-top: 1px dashed var(--ledger-line);
}

.rate-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
}

.rate-grid label {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--ledger-muted);
}

.filter-actions,
.result-meta,
.table-header,
.table-header > div {
  display: flex;
  align-items: center;
}

.filter-actions {
  justify-content: space-between;
  padding-top: 18px;
}

.range-hint {
  font-size: 12px;
  color: var(--ledger-muted);
}

.result-meta {
  gap: 18px;
  padding: 0 4px;
  font-size: 12px;
  color: var(--ledger-muted);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.metric-card {
  display: grid;
  min-height: 112px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--ledger-line);
  border-radius: 10px;
  box-shadow: 0 3px 10px rgba(20, 38, 56, 0.035);
}

.metric-card span,
.metric-card small {
  color: var(--ledger-muted);
}

.metric-card strong {
  align-self: center;
  font-family: 'DIN Alternate', 'Arial Narrow', sans-serif;
  font-size: 24px;
  color: var(--ledger-ink);
}

.metric-card strong.positive {
  color: #287052;
}

.metric-card strong.negative {
  color: #a33a3a;
}

.metric-card strong.accent {
  color: var(--ledger-amber);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.chart-grid :deep(.el-card__body) {
  height: 290px;
}

.chart-grid .sales-chart {
  grid-column: 1 / -1;
}

.table-header {
  justify-content: space-between;
}

.table-header > div {
  gap: 10px;
}

.table-header span {
  font-size: 12px;
  color: var(--ledger-muted);
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
.history-tag {
  margin-left: 8px;
}

.missing-rate-block {
  padding: 20px;
  background: #fff9ed;
  border: 1px solid #f0d8a6;
  border-radius: 10px;
}

.missing-rate-block h3 {
  margin-top: 0;
  color: #8a5a12;
}

.empty-state {
  min-height: 260px;
  background: #fff;
  border: 1px dashed var(--ledger-line);
  border-radius: 12px;
}

@media (max-width: 1200px) {
  .filter-grid,
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .report-hero,
  .filter-actions {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }

  .filter-grid,
  .metric-grid,
  .rate-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .chart-grid .sales-chart {
    grid-column: auto;
  }
}
</style>
