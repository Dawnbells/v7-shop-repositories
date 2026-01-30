<template>
  <div class="order-manager-container auto-height-container">
    <el-progress v-if="taskDownloading" :percentage="downloadPercentage" />
    <order-query-param-layout
      v-model="queryForm"
      :is-audit="isAudit"
      :list-loading="listLoading"
      :task-downloading="taskDownloading"
      :updating-order-status="updatingOrderStatus"
      @on-download="handleDownload"
      @on-search="queryData"
    />
    <el-table
      ref="tableRef"
      v-loading="listLoading"
      border
      :data="list"
      size="default"
      @selection-change="setSelectRows"
    >
      <el-table-column type="selection" width="38" />
      <el-table-column align="center" label="产品信息" min-width="150">
        <template #default="{ row }">
          <div class="text-left">
            <el-tooltip :content="row.items[0].merchandise" placement="top">
              <el-button
                style="padding-left: 0"
                target="_blank"
                text
                type="primary"
                :underline="false"
                @click="generateProductUrl(row.fromUrl)"
              >
                <span>产品名称: {{ row.items[0].merchandise }}</span>
              </el-button>
            </el-tooltip>
          </div>
          <div class="text-left">
            产品价格:
            <span>{{ row.items.length }}</span>
            /
            <span class="text-danger">
              {{ row.contextInfo.currencySymbol }} {{ row.financialInfo.totalAmount }}
              {{ row.contextInfo.currencyCode }}
            </span>
          </div>
          <div class="text-left">
            <span>SKU名称: {{ row.items[0].skuName }}</span>
          </div>
          <div class="text-left">
            <span>SKU代码: {{ row.items[0].skuCode }}</span>
          </div>
          <div class="text-left">
            <span>面单品名: {{ row.items[0].waybillProductName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="重复提示">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <el-tooltip
              v-if="
                row.botOrderCheckInfo.earlierOrderByDeviceId &&
                row.botOrderCheckInfo.earlierOrderByDeviceId.length > 1
              "
              :content="row.botOrderCheckInfo.earlierOrderByDeviceId.join(',')"
              placement="top"
            >
              <el-button
                class="text-left text-danger pointer-cursor"
                text
                @click="
                  onFilterOrder(
                    row,
                    'deviceId',
                    row.botOrderCheckInfo.earlierOrderByDeviceId.join(',')
                  )
                "
              >
                {{ `终端(${row.botOrderCheckInfo.earlierOrderByDeviceId.length})` }}
              </el-button>
            </el-tooltip>
            <el-tooltip
              v-if="
                row.botOrderCheckInfo.earlierOrderIdsByName &&
                row.botOrderCheckInfo.earlierOrderIdsByName.length > 1
              "
              :content="row.botOrderCheckInfo.earlierOrderIdsByName.join(',')"
              placement="top"
            >
              <el-button
                class="text-left text-danger pointer-cursor"
                text
                @click="
                  onFilterOrder(row, 'name', row.botOrderCheckInfo.earlierOrderIdsByName.join(','))
                "
              >
                {{ `名字(${row.botOrderCheckInfo.earlierOrderIdsByName.length})` }}
              </el-button>
            </el-tooltip>
            <el-tooltip
              v-if="
                row.botOrderCheckInfo.earlierOrderIdsByPhone &&
                row.botOrderCheckInfo.earlierOrderIdsByPhone.length > 1
              "
              :content="row.botOrderCheckInfo.earlierOrderIdsByPhone.join(',')"
              placement="top"
            >
              <el-button
                class="text-left text-danger pointer-cursor"
                text
                @click="
                  onFilterOrder(
                    row,
                    'phone',
                    row.botOrderCheckInfo.earlierOrderIdsByPhone.join(',')
                  )
                "
              >
                {{ `电话(${row.botOrderCheckInfo.earlierOrderIdsByPhone.length})` }}
              </el-button>
            </el-tooltip>
            <el-tooltip
              v-if="
                row.botOrderCheckInfo.earlierOrderIdsByRemoteIp &&
                row.botOrderCheckInfo.earlierOrderIdsByRemoteIp.length > 1
              "
              :content="row.botOrderCheckInfo.earlierOrderIdsByRemoteIp.join(',')"
              placement="top"
            >
              <el-button
                class="text-left text-danger pointer-cursor"
                text
                @click="
                  onFilterOrder(
                    row,
                    'remoteIp',
                    row.botOrderCheckInfo.earlierOrderIdsByRemoteIp.join(',')
                  )
                "
              >
                {{ `远程IP(${row.botOrderCheckInfo.earlierOrderIdsByRemoteIp.length})` }}
              </el-button>
            </el-tooltip>
            <el-tooltip
              v-if="
                row.botOrderCheckInfo.earlierOrderIdsByRiskRemoteIp &&
                row.botOrderCheckInfo.earlierOrderIdsByRiskRemoteIp.length > 1
              "
              :content="row.botOrderCheckInfo.earlierOrderIdsByRiskRemoteIp.join(',')"
              placement="top"
            >
              <el-button
                class="text-left text-danger pointer-cursor"
                text
                @click="
                  onFilterOrder(
                    row,
                    'riskRemoteIp',
                    row.botOrderCheckInfo.earlierOrderIdsByRiskRemoteIp.join(',')
                  )
                "
              >
                {{ `分险IP(${row.botOrderCheckInfo.earlierOrderIdsByRiskRemoteIp.length})` }}
              </el-button>
            </el-tooltip>
            <div
              v-for="(orderIds, ip) in row.botOrderCheckInfo.earlierOrderIdsByRealIpsMap"
              :key="ip"
            >
              <el-tooltip v-if="orderIds.length > 1" :content="orderIds.join(',')" placement="top">
                <el-button
                  class="text-left text-danger pointer-cursor"
                  text
                  @click="onFilterOrder(row, 'realIp', orderIds.join(','))"
                >
                  {{ `${ip}(${orderIds.length})` }}
                </el-button>
              </el-tooltip>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="审单提示" min-width="150">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <el-link
              v-if="row.botOrderCheckInfo.invalidPhone"
              class="text-left text-danger"
              href="#"
              type="primary"
              :underline="false"
            >
              电话号码有误
            </el-link>
            <p
              v-if="row.botOrderCheckInfo.incompletePlainTextAddress"
              class="text-left text-danger"
            >
              地址不全-纯文字
            </p>
            <p
              v-if="row.botOrderCheckInfo.incompletePureNumbersAddress"
              class="text-left text-danger"
            >
              地址不全-纯数字
            </p>
            <p
              v-if="row.botOrderCheckInfo.emailMissing && row.deliveryInfo.email"
              class="text-left text-info"
            >
              邮箱缺失
            </p>
            <p v-else-if="row.botOrderCheckInfo.emailMissing" class="text-left text-danger">
              邮箱缺失
            </p>
            <p v-if="row.botOrderCheckInfo.invalidEmail" class="text-left text-danger">邮箱有误</p>
            <p v-if="row.botOrderCheckInfo.remoteArea" class="text-left text-danger">偏远地区</p>
            <p v-if="row.botOrderCheckInfo.ipConflict" class="text-left text-danger">IP不一致</p>
            <p v-if="row.botOrderCheckInfo.testOrder" class="text-left text-warning">测试单</p>
            <p v-if="row.botOrderCheckInfo.moreThanTwoProducts" class="text-left text-danger">
              产品数量＞2
            </p>
            <p
              v-if="row.botOrderCheckInfo.hasRemarkRisk"
              class="text-left"
              :class="remarkRiskClass(row.botOrderCheckInfo.remarkRiskDetail)"
            >
              <el-tooltip
                v-if="row.botOrderCheckInfo.remarkRiskDetail.labels"
                :content="row.botOrderCheckInfo.remarkRiskDetail.labels.join(', ')"
                placement="top"
              >
                <span>疑似客诉</span>
              </el-tooltip>
              <span v-else>疑似客诉</span>
            </p>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="IP信息" min-width="150">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <div>
              <el-tooltip :content="row.riskRecordInfo.remoteIp?.country" placement="top">
                <el-link
                  :href="`https://www.google.com/maps?q=${row.riskRecordInfo.remoteIp?.latitude},${row.riskRecordInfo.remoteIp?.longitude}`"
                  target="_blank"
                  type="primary"
                  :underline="false"
                >
                  远程IP({{ row.riskRecordInfo.remoteIp?.countryCode }}):
                  {{ row.riskRecordInfo.remoteIp?.ip }}
                </el-link>
              </el-tooltip>
            </div>
            <div v-if="row.riskRecordInfo?.remoteIp?.ip !== row.riskRecordInfo?.riskRemoteIp?.ip">
              <el-tooltip :content="row.riskRecordInfo.riskRemoteIp?.country" placement="top">
                <el-link
                  v-if="row.riskRecordInfo?.riskRemoteIp?.ip"
                  :href="`https://www.google.com/maps?q=${row.riskRecordInfo?.riskRemoteIp?.latitude},${row.riskRecordInfo?.riskRemoteIp.longitude}`"
                  target="_blank"
                  type="warning"
                  :underline="false"
                >
                  风险IP({{ row.riskRecordInfo?.riskRemoteIp?.countryCode }}):
                  {{ row.riskRecordInfo.riskRemoteIp?.ip }}
                </el-link>
              </el-tooltip>
            </div>
            <div v-for="realIp in row.riskRecordInfo.realIps" :key="realIp">
              <el-tooltip v-if="realIp" :content="realIp.country" placement="top">
                <el-link
                  :href="`https://www.google.com/maps?q=${realIp.latitude},${realIp.longitude}`"
                  target="_blank"
                  type="danger"
                  :underline="false"
                >
                  真实IP({{ realIp?.countryCode }}): {{ realIp?.ip }}
                </el-link>
              </el-tooltip>
            </div>

            <div>
              <span>下单平台: {{ row.riskRecordInfo.browserPlatform }}</span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="订单状态">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <div :class="orderStatusClass(row.orderStatus)">
              <span>{{ borderStatus(row.orderStatus) }}</span>
            </div>
            <div v-if="row.orderCheckInfo && row.orderCheckInfo.auditDate">
              <span>{{ row.orderCheckInfo.auditDate }}</span>
            </div>
            <div v-if="row.orderCheckRemark" class="text-warning">
              <span>{{ row.orderCheckRemark }}</span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="订单信息" min-width="150">
        <template #default="{ row }">
          <el-space alignment="start" direction="vertical" size="small" style="width: 100%">
            <div class="text-left">
              <span>订单号: {{ row.id }}</span>
            </div>
            <div v-if="row.deliveryInfo.firstName || row.deliveryInfo.lastName" class="text-left">
              <span>名字: {{ row.deliveryInfo.firstName }} {{ row.deliveryInfo.lastName }}</span>
            </div>
            <div v-if="row.deliveryInfo.phone" class="text-left">
              <span>电话: {{ row.deliveryInfo.phone }}</span>
            </div>
            <div v-if="row.contextInfo.department" class="text-left">
              <span>部门: {{ row.contextInfo.department }}</span>
            </div>
            <div v-if="row.contextInfo.salesPerson" class="text-left">
              <span>归属: {{ row.contextInfo.salesPerson }}</span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" fit label="客户信息" min-width="150">
        <template #default="{ row }">
          <div v-if="row.deliveryInfo.email" class="text-left">
            <span>邮箱: {{ row.deliveryInfo.email }}</span>
          </div>
          <el-space alignment="start" direction="vertical" size="small" style="width: 100%">
            <div v-if="row.deliveryInfo.province" class="text-left">
              <span>省份: {{ row.deliveryInfo.province }}</span>
            </div>
            <div v-if="row.deliveryInfo.city" class="text-left">
              <span>城市: {{ row.deliveryInfo.city }}</span>
            </div>
            <div v-if="row.deliveryInfo.region" class="text-left">
              <span>区域: {{ row.deliveryInfo.region }}</span>
            </div>
            <div v-if="row.deliveryInfo.address" class="text-left">
              <span>地址: {{ row.deliveryInfo.address }}</span>
            </div>
            <div v-if="row.deliveryInfo.postalCode" class="text-left">
              <span>邮编: {{ row.deliveryInfo.postalCode }}</span>
            </div>
            <div v-if="row.deliveryInfo.remark" class="text-left">
              <span>备注:</span>
              <span v-html="highlightRiskWords(row.deliveryInfo.remark, row.botOrderCheckInfo)" />
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="订单状态">
        <template #default="{ row }">
          <el-space alignment="start" direction="vertical" size="small" style="width: 100%">
            <div class="text-left"><span v-if="row.status === 'PENDING'">待审核</span></div>
            <div class="text-left">
              <span>{{ row.paymentInfo.paymentMethod }}</span>
            </div>
            <div class="text-left">
              <span>{{ row.paymentInfo.paymentStatus }}</span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="国家地区">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <div>
              <span>{{ row.contextInfo?.country }}</span>
            </div>
            <div>
              <span>
                <el-button
                  target="_blank"
                  text
                  type="primary"
                  :underline="false"
                  @click="generateProductUrl(row.fromUrl)"
                >
                  {{ row.fromUrl?.split('?')[0]?.replace('https://', '')?.split('/')[0] }}
                </el-button>
              </span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="时间" prop="createTime" />
      <template #empty>
        <el-empty class="vab-data-empty" description="暂无数据" />
      </template>
    </el-table>
    <vab-pagination
      :current-page="queryForm.pageNo"
      :page-size="queryForm.pageSize"
      size="small"
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
    <order-manager-edit ref="editRef" @fetch-data="fetchData" />
  </div>
</template>

<script lang="ts" setup>
import { useRoute } from 'vue-router'
import { downloadFile, status } from '~/src/api/taskManagement'
import { getTicket } from '~/src/api/user'
import { download, page } from '/@/api/orderManager'

const route = useRoute()
defineOptions({
  name: 'OrderManager',
})

const props = defineProps({
  isAudit: {
    type: Boolean,
    default: false,
  },
})

const { isAudit } = toRefs(props)

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const tableRef = ref<any>(null)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const downloadPercentage = ref<any>(null)
const taskDownloading = ref<boolean>(false)
const updatingOrderStatus = ref<boolean>(false)

// Set default date range from yesterday 9:00 to today 9:00
const today = new Date()
const yesterday = new Date(today)
yesterday.setDate(yesterday.getDate() - 1)
yesterday.setHours(9, 0, 0, 0)
today.setHours(9, 0, 0, 0)
const defaultDataRange = ref<[Date, Date]>()

const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
  isAudit: isAudit.value || undefined,
  dateRange: isAudit.value ? defaultDataRange.value : undefined,
  orderIds: undefined,
  telephones: undefined,
  firstName: undefined,
  lastName: undefined,
})

const fetchData = async () => {
  listLoading.value = true
  const { data } = await page(queryForm)
  if (data) {
    list.value = data.list
    total.value = data.total
  }
  listLoading.value = false
}

const handleSizeChange = (value: number) => {
  queryForm.pageNo = 1
  queryForm.pageSize = value
  fetchData()
}

const handleCurrentChange = (value: number) => {
  queryForm.pageNo = value
  fetchData()
}

const queryData = () => {
  queryForm.pageNo = 1
  fetchData()
}

const setSelectRows = (value: string) => {
  selectRows.value = value
}

const borderStatus = (status: string) => {
  if (status === 'PENDING') {
    return '待审核'
  }
  if (status === 'DUPLICATE') {
    return '只重单'
  }
  if (status === 'WARNING') {
    return '只提示'
  }
  if (status === 'DUPLICATE_WARNING') {
    return '重单提示'
  }
  if (status === 'CONFIRMED') {
    return '已确认'
  }
  if (status === 'INVALID') {
    return '无效单'
  }
  return '正常单'
}

const orderStatusClass = (status: any) => {
  if (status === 'INVALID') {
    return 'text-danger'
  }
  if (status === 'CONFIRMED') {
    return 'text-success'
  }
  return ''
}
const remarkRiskClass = (remarkRiskDetail: any) => {
  if (!remarkRiskDetail || !remarkRiskDetail.riskLevel) {
    return ''
  }
  if (remarkRiskDetail.riskLevel.toLowerCase() === 'high') {
    return 'text-danger'
  }
  if (remarkRiskDetail.riskLevel.toLowerCase() === 'medium') {
    return 'text-warning'
  }
  return 'text-info'
}
const remarkRiskStyle = (remarkRiskDetail: any) => {
  if (!remarkRiskDetail || !remarkRiskDetail.riskLevel) {
    return ''
  }
  if (remarkRiskDetail.riskLevel.toLowerCase() === 'high') {
    return 'color: #f56c6c'
  }
  if (remarkRiskDetail.riskLevel.toLowerCase() === 'medium') {
    return 'color: #e6a23c'
  }
  return 'color: #909399'
}

const onFilterOrder = (row: any, field: any, orderIds: any) => {
  const openOrderIdsInNewWindow = (orderIds: string) => {
    const url = `${globalThis.location.href.split('?')[0]}?orderIds=${orderIds}`
    window.open(url, '_blank')
  }

  // Call the function with the provided orderIds
  openOrderIdsInNewWindow(orderIds)
}

const handleDownload = async (type: string) => {
  if (taskDownloading.value) {
    return
  }
  let downloadQueryForm = { ...queryForm }
  if (type === 'selected') {
    downloadQueryForm.orderIds = undefined
    if (selectRows.value.length > 0) {
      const ids = selectRows.value.map((item: { id: any }) => item.id).join(',')
      $baseConfirm('您确定要下载选中项吗', null, async () => {
        downloadQueryForm.orderIds = ids.join(',')
        taskDownloading.value = true
        const { data } = await download(downloadQueryForm)
        waitingForDownload(data)
      })
    } else {
      $baseMessage('您未选中任何行', 'warning', 'hey')
      return
    }
  } else {
    taskDownloading.value = true
    const { data } = await download(downloadQueryForm)
    waitingForDownload(data)
  }
}

const waitingForDownload = async (taskId: any) => {
  const checkDownloadStatus = async (taskId: string) => {
    try {
      const response = await status(taskId)
      downloadPercentage.value = response.data.progress

      if (response.data.state === 'COMPLETED') {
        // Download completed, get file URL and trigger download
        downloadFile(taskId)
          .then((response) => response.blob())
          .then((blob) => {
            const link = document.createElement('a')
            link.href = URL.createObjectURL(blob)
            link.download = `订单下载_${new Date().toLocaleDateString()}.xlsx` // 设置下载文件的默认名称
            link.click()
          })
          .catch((error) => console.error('Download failed:', error))
        return true
      } else if (response.data.state === 'FAILED') {
        $baseMessage('下载失败，请重试', 'error', 'hey', 0)
        return true
      }
      return false
    } catch (error) {
      console.error('Check download status error:', error)
      $baseMessage('检查下载状态出错', 'error', 'hey', 0)
      return true
    }
  }

  const poll = async (taskId: string) => {
    while (true) {
      const finished = await checkDownloadStatus(taskId)
      if (finished) {
        taskDownloading.value = false
        break
      }
      await new Promise((resolve) => setTimeout(resolve, 500)) // Poll every 2 seconds
    }
  }

  await poll(taskId)
}

const generateProductUrl = async (fromUrl: string) => {
  if (!fromUrl || !fromUrl.trim()) {
    return '#'
  }
  const res = await getTicket()
  const url = `${fromUrl.split('?')[0]}?ticket=${encodeURIComponent(res.data.ticket)}`
  window.open(url, '_blank')
}
onActivated(() => {
  tableRef.value.doLayout()
})

onBeforeMount(() => {
  console.log(route.query)
  queryForm.orderIds = route.query.orderIds || undefined
  fetchData()
})

const highlightRiskWords = (remark: string, botOrderCheckInfo: any) => {
  if (
    !botOrderCheckInfo ||
    !botOrderCheckInfo.remarkRiskDetail ||
    !botOrderCheckInfo.remarkRiskDetail.riskWords
  ) {
    return remark
  }
  const riskWords = botOrderCheckInfo.remarkRiskDetail.riskWords
  const style = remarkRiskStyle(botOrderCheckInfo.remarkRiskDetail)
  // 构造正则表达式，忽略大小写，匹配风险词
  const regex = new RegExp(
    riskWords
      .map((word: string) => word.replaceAll(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`)) // 转义特殊字符
      .join('|'), // 将多个词用 | 分隔
    'gi' // 全局匹配，忽略大小写
  )
  // 替换匹配到的风险词并包装高亮 HTML
  const highlightedRemark = remark.replace(regex, (match) => {
    return `<span style="${style}">${match}</span>`
  })
  return highlightedRemark
}
</script>

<style scoped>
.text-left {
  width: 100%;
  text-align: left;
}
.product-name {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  word-wrap: break-word;
  white-space: normal;
}
.text-info {
  color: #8a8e99;
}
.text-warning {
  color: #e6a23c;
}
.text-danger {
  color: #fd4e4e;
}
.text-success {
  color: #67c23a;
}
.pointer-cursor {
  cursor: pointer;
}
</style>
