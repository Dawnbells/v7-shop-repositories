<template>
  <div class="order-manager-container">
    <el-progress v-if="!isContact && taskDownloading" :percentage="downloadPercentage" />
    <order-query-param-layout
      v-model="queryForm"
      :is-audit="isAudit"
      :is-contact="isContact"
      :list-loading="listLoading"
      :task-downloading="taskDownloading"
      :updating-order-status="updatingOrderStatus"
      @on-batch-change-order-remark="handleBatchChangeOrderRemark"
      @on-batch-change-order-status="handleBatchChangeOrderStatus"
      @on-batch-contact-remark="handleBatchContactRemark"
      @on-batch-contact-status="handleBatchContactStatus"
      @on-download="handleDownload"
      @on-reset="onReset"
      @on-search="queryData"
    />
    <vab-pagination
      :background="false"
      :current-page="queryForm.pageNo"
      layout="total, prev, pager, next"
      :page-size="queryForm.pageSize"
      small
      style="justify-content: right; margin-top: -15px"
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
    <el-table
      ref="tableRef"
      v-loading="listLoading"
      border
      :data="list"
      size="default"
      @row-click="handleRowClick"
      @selection-change="setSelectRows"
    >
      <el-table-column type="selection" width="38" />
      <el-table-column align="center" label="序号" width="60">
        <template #default="scope">
          {{ (queryForm.pageNo - 1) * queryForm.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="产品信息" min-width="150">
        <template #default="{ row }">
          <div class="text-left">
            <el-tooltip :content="row.items[0].title" placement="top">
              <el-button
                style="
                  height: auto;
                  padding-left: 0;
                  line-height: 1.4;
                  text-align: left;
                  white-space: normal;
                "
                target="_blank"
                text
                type="primary"
                underline="never"
                @click.stop="generateProductUrl(row.fromUrl, row.platform)"
              >
                <span style="font-weight: normal">产品名称: {{ row.items[0].merchandise }}</span>
              </el-button>
            </el-tooltip>
          </div>
          <div class="text-left">
            产品价格:
            <span>{{ row.quantity }}</span>
            /
            <span class="text-danger">
              {{ row.contextInfo.currencySymbol }}
              {{ row.financialInfo.totalAmount }}
              {{ row.contextInfo.currencyCode }}
            </span>
          </div>
          <div v-if="!isAudit" class="text-left">
            <span>SKU名称: {{ row.skuNames }}</span>
          </div>
          <div class="text-left">
            <span>SKU代码: {{ row.skuCodes }}</span>
          </div>
          <div class="text-left">
            <span>面单品名: {{ row.items[0].waybillProductName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="重复提示" width="100">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <el-button
              v-if="row.botOrderCheckInfo.phoneRepeatCount > 1"
              class="text-left text-danger pointer-cursor compact bold"
              text
              @click.stop="onFilterOrder(row, 'PHONE')"
            >
              {{ `电话(${row.botOrderCheckInfo.phoneRepeatCount})` }}
            </el-button>
            <el-button
              v-if="row.botOrderCheckInfo.remoteIpRepeatCount > 1"
              class="text-left text-danger pointer-cursor compact bold"
              text
              @click.stop="onFilterOrder(row, 'IP')"
            >
              {{ `远程IP(${row.botOrderCheckInfo.remoteIpRepeatCount})` }}
            </el-button>

            <el-button
              v-if="row.botOrderCheckInfo.nameRepeatCount > 1"
              class="text-left text-danger pointer-cursor compact bold"
              text
              @click.stop="onFilterOrder(row, 'NAME')"
            >
              {{ `名字(${row.botOrderCheckInfo.nameRepeatCount})` }}
            </el-button>
            <el-button
              v-if="row.botOrderCheckInfo.realIpRepeatCount > 1"
              class="text-left text-danger pointer-cursor compact bold"
              text
              @click.stop="onFilterOrder(row, 'REAL_IP')"
            >
              {{ `真实IP(${row.botOrderCheckInfo.realIpRepeatCount})` }}
            </el-button>
            <el-button
              v-if="row.botOrderCheckInfo.deviceRepeatCount > 1"
              class="text-left text-danger pointer-cursor compact bold"
              text
              @click.stop="onFilterOrder(row, 'DEVICE')"
            >
              {{ `终端(${row.botOrderCheckInfo.deviceRepeatCount})` }}
            </el-button>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="审单提示" width="130">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <p
              v-if="row.botOrderCheckInfo.invalidPhone"
              class="text-left text-danger compact"
              href="#"
              type="primary"
              underline="never"
            >
              电话号码有误
            </p>
            <p
              v-if="row.botOrderCheckInfo.incompletePlainTextAddress"
              class="text-left text-danger compact"
            >
              地址不全-纯文字
            </p>
            <p
              v-if="row.botOrderCheckInfo.incompletePureNumbersAddress"
              class="text-left text-danger compact"
            >
              地址不全-纯数字
            </p>
            <p
              v-if="row.botOrderCheckInfo.emailMissing && row.deliveryInfo.email"
              class="text-left text-info compact"
            >
              邮箱缺失
            </p>
            <p v-else-if="row.botOrderCheckInfo.emailMissing" class="text-left text-danger compact">
              邮箱缺失
            </p>
            <p v-if="row.botOrderCheckInfo.invalidEmail" class="text-left text-danger compact">
              邮箱有误
            </p>
            <p v-if="row.botOrderCheckInfo.remoteArea" class="text-left text-danger compact">
              偏远地区
              <span v-if="row.botOrderCheckInfo.remoteTip">
                ({{ row.botOrderCheckInfo.remoteTip }})
              </span>
            </p>
            <p v-if="row.botOrderCheckInfo.ipConflict" class="text-left text-danger compact">
              IP不一致
            </p>
            <p v-if="row.botOrderCheckInfo.testOrder" class="text-left text-danger compact">
              测试单
            </p>
            <p v-if="row.botOrderCheckInfo.cloakOrder" class="text-left text-danger compact">
              斗篷单
            </p>
            <p
              v-if="row.botOrderCheckInfo.moreThanTwoProducts"
              class="text-left text-danger compact"
            >
              产品数量＞2
            </p>
            <p
              v-if="row.botOrderCheckInfo.hasRemarkRisk"
              class="text-left compact"
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
      <el-table-column align="center" label="IP信息" min-width="100">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <div>
              <span
                class="pointer-cursor text-copy compact el-link"
                @click.stop="copyText2Clipboard(row.riskRecordInfo.remoteIp?.ip)"
              >
                远程IP({{ row.riskRecordInfo.remoteIp?.countryCode }}):&nbsp;
              </span>
              <el-tooltip :content="row.riskRecordInfo.remoteIp?.country" placement="top">
                <el-link
                  :href="`https://www.google.com/maps?q=${row.riskRecordInfo.remoteIp?.latitude},${row.riskRecordInfo.remoteIp?.longitude}`"
                  target="_blank"
                  type="primary"
                  underline="never"
                >
                  {{ row.riskRecordInfo.remoteIp?.ip }}
                </el-link>
              </el-tooltip>
            </div>
            <div v-for="realIp in row.riskRecordInfo.realIps" :key="realIp">
              <el-tooltip v-if="realIp" :content="realIp.country" placement="top">
                <el-link
                  :href="`https://www.google.com/maps?q=${realIp.latitude},${realIp.longitude}`"
                  target="_blank"
                  type="danger"
                  underline="never"
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
      <el-table-column align="center" label="订单状态" width="120">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" style="width: 100%">
            <div v-if="row.isPrivateDomain">
              <transition name="contact-fade" mode="out-in">
                <el-tag
                  v-if="row.contacted"
                  key="contacted"
                  type="success"
                  effect="dark"
                  size="small"
                >
                  已建联
                </el-tag>
                <el-tag v-else key="uncontacted" type="danger" effect="dark" size="small">
                  未建联
                </el-tag>
              </transition>
            </div>
            <div class="bold" :class="orderStatusClass(row.orderStatus)">
              <span>{{ borderStatus(row.orderStatus) }}</span>
            </div>
            <div class="text-danger">
              <span
                style="white-space: pre-wrap; cursor: pointer"
                @click.stop="copyText2Clipboard(row.orderCheckRemark)"
              >
                {{ row.orderCheckRemark || '' }}
              </span>
            </div>
            <div v-if="row.contactRemark" style="color: #e6a23c">
              <span
                style="white-space: pre-wrap; cursor: pointer"
                @click.stop="copyText2Clipboard(row.contactRemark)"
              >
                {{ row.contactRemark }}
              </span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="订单信息" min-width="150">
        <template #default="{ row }">
          <el-space alignment="start" direction="vertical" size="small" style="width: 100%">
            <div class="text-left compact">
              <span>订单号: {{ row.id }}</span>
            </div>
            <div v-if="row.originOrderId" class="text-left compact">
              <span>原单号: {{ row.originOrderId }}</span>
            </div>
            <div
              v-if="row.deliveryInfo.firstName || row.deliveryInfo.lastName"
              class="text-left compact"
            >
              <span>名字: {{ row.deliveryInfo.firstName }} {{ row.deliveryInfo.lastName }}</span>
            </div>
            <div v-if="row.deliveryInfo.phone" class="text-left compact">
              <span>电话: {{ row.deliveryInfo.phone }}</span>
            </div>
            <div v-if="row.contextInfo.department" class="text-left compact">
              <span>部门: {{ row.contextInfo.department }}</span>
            </div>
            <div v-if="row.contextInfo.salesPerson" class="text-left compact">
              <span>归属: {{ row.contextInfo.salesPerson }}</span>
            </div>
            <div v-if="row.deliveryChannel" class="text-left compact">
              <span>渠道: {{ row.deliveryChannel }}</span>
            </div>
            <div v-if="row.storehouse" class="text-left compact">
              <span>仓库: {{ row.storehouse }}</span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" fit label="客户信息" min-width="150">
        <template #default="{ row }">
          <el-space alignment="start" direction="vertical" size="small" style="width: 100%">
            <div v-if="row.deliveryInfo.email" class="text-left compact">
              <span>邮箱: {{ row.deliveryInfo.email }}</span>
            </div>
            <div v-if="row.deliveryInfo.province" class="text-left compact">
              <span>省份: {{ row.deliveryInfo.province }}</span>
            </div>
            <div v-if="row.deliveryInfo.city" class="text-left compact">
              <span>城市: {{ row.deliveryInfo.city }}</span>
            </div>
            <div v-if="row.deliveryInfo.district" class="text-left compact">
              <span>区域: {{ row.deliveryInfo.district }}</span>
            </div>
            <div v-if="row.deliveryInfo.address" class="text-left compact">
              <span
                class="text-copy pointer-cursor compact el-link"
                @click.stop="copyText2Clipboard(row.deliveryInfo.address)"
              >
                地址：
              </span>
              <el-link
                :href="`https://www.google.com/maps?q=${row.deliveryInfo.address} ${row.deliveryInfo?.district || ''} ${row.deliveryInfo?.city || ''} ${row.deliveryInfo?.province || ''}`"
                target="_blank"
                type="primary"
                underline="never"
              >
                {{ row.deliveryInfo.address }}
              </el-link>
            </div>
            <div v-if="row.deliveryInfo.postalCode" class="text-left compact">
              <span>邮编: {{ row.deliveryInfo.postalCode }}</span>
            </div>
            <div v-if="row.deliveryInfo.remark" class="text-left compact">
              <span>备注:</span>
              <span
                class="pointer-cursor text-copy"
                @click.stop="copyText2Clipboard(row.deliveryInfo.remark)"
                v-html="highlightRiskWords(row.deliveryInfo.remark, row.botOrderCheckInfo)"
              />
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="订单来源">
        <template #default="{ row }">
          <el-space alignment="start" direction="vertical" size="small" style="width: 100%">
            <div v-if="row.from" class="text-left compact">
              <span>{{ row.from }}</span>
            </div>
            <!-- <div v-if="row.platform" class="text-left compact">
              <span>{{ row.platform }}</span>
            </div> -->
            <div v-if="row.paymentInfo.paymentMethod" class="text-left compact">
              <span>{{ row.paymentInfo.paymentMethod }}</span>
            </div>
            <div v-if="row.paymentInfo.paymentStatus" class="text-left compact">
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
                  underline="never"
                  @click.stop="searchForWebsiteUrl(row.contextInfo?.websiteUrl)"
                >
                  {{
                    row.contextInfo?.websiteUrl.replaceAll('https://', '').replaceAll('http://', '')
                  }}
                </el-button>
              </span>
            </div>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="时间" prop="createTime" width="150">
        <template #default="{ row }">
          <el-space alignment="center" direction="vertical" size="small" style="width: 100%">
            <span class="compact">{{ row.createTime }}</span>
            <span v-if="row.botOrderCheckInfo.phoneSecondsBetween" class="compact">电话间隔：</span>
            <span v-if="row.botOrderCheckInfo.phoneSecondsBetween" class="compact">
              {{ row.botOrderCheckInfo.phoneSecondsBetween }}
            </span>
            <span v-if="row.botOrderCheckInfo.ipSecondsBetween" class="compact">IP间隔：</span>
            <span v-if="row.botOrderCheckInfo.ipSecondsBetween" class="compact">
              {{ row.botOrderCheckInfo.ipSecondsBetween }}
            </span>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column v-if="isContact" align="center" label="操作" width="110">
        <template #default="{ row }">
          <el-button
            :loading="row.changingContactStatus"
            text
            type="success"
            @click.stop="handleContactStatus(row, true)"
          >
            已建联
          </el-button>
          <el-button
            :loading="row.changingContactStatus"
            text
            type="danger"
            @click.stop="handleContactStatus(row, false)"
          >
            未建联
          </el-button>
          <el-button
            v-if="!row.editingContactRemark"
            text
            type="primary"
            @click.stop="startContactRemark(row)"
          >
            备注
          </el-button>
          <el-input
            v-else
            ref="contactRemarkRef"
            v-model="row._contactRemark"
            :autosize="{ minRows: 1, maxRows: 3 }"
            :disabled="row._updatingContactRemark"
            size="small"
            style="width: 100%"
            type="textarea"
            @blur="handleUpdateContactRemark(row)"
          />
        </template>
      </el-table-column>
      <el-table-column v-if="isAudit" align="center" label="操作" width="110">
        <template #default="{ row }">
          <el-button
            class="text-primary"
            :loading="row.changingOrderStatus || updatingOrderStatus"
            text
            type="success"
            @click.stop="handleChangeOrderStatus(row, 'CONFIRMED')"
          >
            已确认
          </el-button>
          <el-button
            class="text-danger"
            :loading="row.changingOrderStatus || updatingOrderStatus"
            text
            type="danger"
            @click.stop="handleChangeOrderStatus(row, 'INVALID')"
          >
            无效单
          </el-button>
          <el-button
            class="text-info"
            :loading="row.changingOrderStatus || updatingOrderStatus"
            text
            type="info"
            @click.stop="handleChangeOrderStatus(row, 'PENDING')"
          >
            待审核
          </el-button>
          <el-button
            v-if="!isAudit || !row.editingOrderCheckRemark"
            class="text-received"
            text
            type="primary"
            @click.stop="startRemark(row)"
          >
            备注
          </el-button>
          <el-input
            v-else
            ref="remarkRef"
            v-model="row._orderCheckRemark"
            :autosize="{ minRows: 1, maxRows: 3 }"
            :disabled="row._updatingOrderCheckRemark"
            size="small"
            small
            style="width: 100%"
            type="textarea"
            @blur="handleUpdateOrderCheckRemark(row)"
          />
        </template>
      </el-table-column>

      <el-table-column
        v-if="hasOrderTemplateRoutePermission && !isAudit && !isContact"
        align="center"
        label="操作"
        width="100"
      >
        <template #default="{ row }">
          <el-popconfirm
            cancel-button-text="IP"
            cancel-button-type="danger"
            confirm-button-text="终端"
            confirm-button-type="danger"
            :icon="InfoFilled"
            icon-color="#626AEF"
            title="确定拉黑名单吗?"
            width="220"
            @cancel="handleAddDeviceBlacklist(row, 'ip')"
            @confirm="handleAddDeviceBlacklist(row, 'device')"
            @hide="ignoreRowSelect = false"
          >
            <template #reference>
              <el-button class="text-danger" text @click="ignoreRowSelect = true">黑名单</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty class="vab-data-empty" description="暂无数据" />
      </template>
    </el-table>
    <vab-pagination
      :current-page="queryForm.pageNo"
      :page-size="queryForm.pageSize"
      size="small"
      small
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
    <order-manager-edit ref="batchOrderManagerEdit" @fetch-data="fetchData" />

    <choose-order-template-dialog
      ref="chooseOrderTemplateDialogRef"
      @on-confirm="handleChooseOrderTemplateConfirm"
    />
  </div>
</template>

<script lang="ts" setup>
import { InfoFilled } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadFile, status } from '~/src/api/taskManagement'
import { getTicket } from '~/src/api/user'
import { useRoutesStore } from '~/src/store/modules/routes'
import { useMainDomain } from '~/src/utils/window'
import { doEdit as doEditIpBlacklist } from '/@/api/ipBlacklist'
import {
  download,
  page,
  updateContactRemark,
  updateContactStatus,
  updateOrderCheckRemark,
  updateOrderStatus,
} from '/@/api/orderManager'

const route = useRoute()
const router = useRouter()

const routesStore = useRoutesStore()
defineOptions({
  name: 'OrderManager',
})

const props = defineProps({
  isAudit: {
    type: Boolean,
    default: false,
  },
  isContact: {
    type: Boolean,
    default: false,
  },
})

const { isAudit, isContact } = toRefs(props)

const $baseMessage = inject<any>('$baseMessage')
const chooseOrderTemplateDialogRef = ref<any>()
const batchOrderManagerEdit = ref<any>()
const tableRef = ref<any>(null)
const remarkRef = ref<any>(null)
const contactRemarkRef = ref<any>(null)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const downloadPercentage = ref<any>(null)
const taskDownloading = ref<boolean>(false)
const ignoreRowSelect = ref<boolean>(false)
const updatingOrderStatus = ref<boolean>(false)

// Set default date range from yesterday 9:00 to today 9:00
const today = new Date()
const tomorrow = new Date(today)

const auditToday = new Date()
const auditYesterday = new Date(today)

today.setHours(0, 0, 0, 0)
tomorrow.setHours(0, 0, 0, 0)
tomorrow.setDate(auditYesterday.getDate() + 1)
auditToday.setHours(9, 0, 0, 0)
auditYesterday.setHours(9, 0, 0, 0)
auditYesterday.setDate(auditYesterday.getDate() - 1)
// const defaultDataRange = [today, tomorrow]
const auditDefaultDataRange = [auditYesterday, auditToday]
const hasOrderTemplateRoutePermission: boolean = routesStore.routes
  .find((item) => item.path === '/system')
  ?.children?.find((item: any) => item.path === '/system/orderTemplate')
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
  isAudit: isAudit.value || undefined,
  isContact: isContact.value || undefined,
  dateRange: isAudit.value ? auditDefaultDataRange : undefined,
  searchType: 'ORDER_ID',
  keywords: '',
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

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let skipAutoQuery = true

watch(
  () => ({ ...queryForm }),
  () => {
    updateQueryParams()
  },
  { deep: true }
)

watch(
  () => ({
    searchType: queryForm.searchType,
    keywords: queryForm.keywords,
    orderStatus: queryForm.orderStatus,
    botOrderStatus: queryForm.botOrderStatus,
    repeatType: queryForm.repeatType,
    countryId: queryForm.countryId,
    platform: queryForm.platform,
    dateRange: queryForm.dateRange,
    belongEmployeeIds: queryForm.belongEmployeeIds,
    belongDepartmentIds: queryForm.belongDepartmentIds,
    contacted: queryForm.contacted,
  }),
  () => {
    if (skipAutoQuery) return
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      queryData()
    }, 300)
  },
  { deep: true }
)

const handleAddDeviceBlacklist = async (row: any, type: string) => {
  ignoreRowSelect.value = false
  if (type === 'ip') {
    await doEditIpBlacklist({
      fingerprint: '',
      ipAddress: row.riskRecordInfo?.remoteIp?.ip,
      remark: `禁用订单IP，订单号：${row.id}`,
    })
  } else {
    if (!row.riskRecordInfo.deviceId || row.riskRecordInfo.deviceId === '') {
      $baseMessage('未知的终端信息', 'error', 'hey')
      return
    }
    await doEditIpBlacklist({
      ipAddress: '',
      fingerprint: row.riskRecordInfo?.deviceId,
      remark: `禁用订单终端，订单号：${row.id}`,
    })
  }
  $baseMessage('添加黑名单成功', 'success', 'hey')
}
const onReset = () => {
  queryForm.repeatType = undefined
  queryForm.repeatValue = undefined
  queryForm.pageNo = 1
  queryForm.pageSize = 20
  queryForm.searchType = 'ORDER_ID'
  queryForm.keywords = ''
}

const copyText2Clipboard = (text?: string) => {
  if (!text) return
  navigator.clipboard
    .writeText(text)
    .then(() => {
      $baseMessage('复制成功', 'success')
    })
    .catch(() => {
      $baseMessage('复制失败', 'danger')
    })
}

const searchForWebsiteUrl = (websiteUrl: string) => {
  queryForm.searchType = 'DOMAIN'
  queryForm.keywords = websiteUrl.replaceAll('https://', '').replaceAll('http://', '')
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
  if (status === 'SHIPPED') {
    return '已发货'
  }
  if (status === 'DELIVERED') {
    return '已签收'
  }
  if (status === 'REJECTED') {
    return '拒收'
  }
  if (status === 'LOST') {
    return '丢件'
  }
  if (status === 'CUSTOMER_CANCELLED') {
    return '客户取消'
  }
  return '正常单'
}

const handleBatchChangeOrderRemark = () => {
  if (selectRows.value.length > 0) {
    const ids = selectRows.value.map((item: { id: any }) => item.id)
    ElMessageBox.prompt('请输入备注', '修改备注', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    }).then(({ value }) => {
      updateOrderCheckRemark(ids, value).then(() => {
        selectRows.value.forEach((element: { orderCheckRemark: string }) => {
          element.orderCheckRemark = value
        })
      })
    })
  } else {
    $baseMessage('您未选中任何行', 'warning', 'hey')
    return
  }
}
const handleBatchChangeOrderStatus = (status: string) => {
  if (selectRows.value.length > 0) {
    const ids = selectRows.value.map((item: { id: any }) => item.id)
    batchOrderManagerEdit.value.showEdit(status, ids)
  } else {
    if (status === 'CONFIRMED' || status === 'INVALID') {
      $baseMessage('您未选中任何行', 'warning', 'hey')
      return
    }
    batchOrderManagerEdit.value.showEdit(status, '')
    return
  }
}

const handleChangeOrderStatus = (
  row: { id: string; orderStatus: string; changingOrderStatus: boolean },
  status: string
) => {
  row.changingOrderStatus = true
  updateOrderStatus({
    ids: [row.id],
    status,
  })
    .then(() => {
      row.orderStatus = status
      row.changingOrderStatus = false
    })
    .catch(() => {
      row.changingOrderStatus = false
    })
}

const handleContactStatus = (row: any, contacted: boolean) => {
  row.changingContactStatus = true
  updateContactStatus({ ids: [row.id], contacted })
    .then(() => {
      row.contacted = contacted
      row.changingContactStatus = false
    })
    .catch(() => {
      row.changingContactStatus = false
    })
}

const handleBatchContactStatus = (contacted: boolean) => {
  if (selectRows.value.length === 0) {
    $baseMessage('您未选中任何行', 'warning', 'hey')
    return
  }
  const ids = selectRows.value.map((item: { id: any }) => item.id)
  updatingOrderStatus.value = true
  updateContactStatus({ ids, contacted })
    .then(() => {
      selectRows.value.forEach((item: any) => {
        item.contacted = contacted
      })
      updatingOrderStatus.value = false
    })
    .catch(() => {
      updatingOrderStatus.value = false
    })
}

const startContactRemark = (row: any) => {
  row.editingContactRemark = true
  ignoreRowSelect.value = true
  row._contactRemark = `${row.contactRemark || ''}`
  nextTick(() => {
    contactRemarkRef.value?.focus()
  })
}

const handleUpdateContactRemark = (row: any) => {
  row._updatingContactRemark = true
  updateContactRemark([row.id], `${row._contactRemark}`)
    .then(() => {
      row.contactRemark = `${row._contactRemark}`
      row.editingContactRemark = false
      row._updatingContactRemark = false
      setTimeout(() => {
        ignoreRowSelect.value = false
      }, 300)
    })
    .catch(() => {
      row.editingContactRemark = false
      row._updatingContactRemark = false
      setTimeout(() => {
        ignoreRowSelect.value = false
      }, 300)
    })
}

const handleBatchContactRemark = () => {
  if (selectRows.value.length === 0) {
    $baseMessage('您未选中任何行', 'warning', 'hey')
    return
  }
  const ids = selectRows.value.map((item: { id: any }) => item.id)
  ElMessageBox.prompt('请输入备注', '批量建联备注', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(({ value }) => {
    updateContactRemark(ids, value).then(() => {
      selectRows.value.forEach((element: any) => {
        element.contactRemark = value
      })
    })
  })
}

const orderStatusClass = (status: any) => {
  if (status === 'INVALID') {
    return 'text-danger'
  }
  if (status === 'CONFIRMED') {
    return 'text-primary'
  }
  if (status === 'SHIPPED') {
    return 'text-received'
  }
  if (status === 'DELIVERED') {
    return 'text-success'
  }
  if (status === 'REJECTED') {
    return 'text-danger'
  }
  if (status === 'LOST') {
    return 'text-danger'
  }
  if (status === 'CUSTOMER_CANCELLED') {
    return 'text-danger'
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

const onFilterOrder = (row: any, field: any) => {
  const baseUrl = useMainDomain()
  const params = new URLSearchParams({
    searchType: 'REPEAT',
    repeatType: field,
    keywords: row.id, // 可能是 "-"
  })
  const page = isContact.value ? 'orderContact' : isAudit.value ? 'orderAudit' : 'orderManager'
  const fullUrl = `${baseUrl}/#/order/${page}?${params.toString()}`
  window.open(fullUrl, '_blank')
}

const handleChooseOrderTemplateConfirm = async (templateData: {
  templateId: string
  isDownloadTemplate: boolean
  downloadType: string | undefined
}) => {
  if (templateData.isDownloadTemplate) {
    taskDownloading.value = true
    let downloadQueryForm = {
      ...queryForm,
      templateId: templateData.templateId,
      isAudit: isAudit.value ? true : undefined,
    }
    if (templateData.downloadType === 'selected') {
      const ids = selectRows.value.map((item: { id: any }) => item.id)
      downloadQueryForm.keywords = ids.join(',')
      downloadQueryForm.searchType = 'ORDER_ID'
    }
    const { data } = await download(downloadQueryForm)
    waitingForDownload(data)
  }
}

const handleDownload = async (type: any) => {
  if (taskDownloading.value) {
    return
  }
  if (type === 'selected') {
    if (selectRows.value.length > 0) {
      chooseOrderTemplateDialogRef.value.showEdit(true, type, props.isAudit)
    } else {
      $baseMessage('您未选中任何行', 'warning', 'hey')
      return
    }
  } else {
    chooseOrderTemplateDialogRef.value.showEdit(true, type, props.isAudit)
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

const generateProductUrl = async (fromUrl: string, platform: string) => {
  if (!fromUrl || !fromUrl.trim()) {
    return
  }
  if (platform !== 'V7_SHOP') {
    window.open(fromUrl, '_blank')
    return
  }
  const res = await getTicket()
  const url = `${fromUrl.split('?')[0]}?ticket=${encodeURIComponent(res.data.ticket)}`
  window.open(url, '_blank')
}

onActivated(() => {
  tableRef.value.doLayout()
})

const initQueryParams = () => {
  const query = route.query
  queryForm.pageNo = Number(query.pageNo) || 1
  queryForm.pageSize = Number(query.pageSize) || 20
  queryForm.repeatType = query.repeatType || undefined
  queryForm.searchType = query.searchType || 'ORDER_ID'
  queryForm.keywords = query.keywords || undefined
  queryForm.orderStatus = query.orderStatus || undefined
  queryForm.botOrderStatus = query.botOrderStatus || undefined
  queryForm.countryId = query.countryId || undefined
  queryForm.platform = query.platform || undefined
  queryForm.dateRange =
    typeof query.dateRange === 'string'
      ? query.dateRange.split(',').map((dateStr: string) => new Date(dateStr))
      : isAudit.value
        ? auditDefaultDataRange
        : undefined
  queryForm.belongEmployeeIds =
    typeof query.belongEmployeeIds === 'string' ? query.belongEmployeeIds.split(',') : undefined
  queryForm.belongDepartmentIds =
    typeof query.belongDepartmentIds === 'string' ? query.belongDepartmentIds.split(',') : undefined
  queryForm.contacted =
    query.contacted === 'true' ? true : query.contacted === 'false' ? false : undefined
  if (queryForm.searchType === 'REPEAT') {
    queryForm.dateRange = undefined
  }
}

const updateQueryParams = () => {
  const dateRange =
    queryForm.dateRange &&
    queryForm.dateRange.length == 2 &&
    queryForm.dateRange.every((item: any) => item instanceof Date)
      ? `${queryForm.dateRange[0].toISOString()},${queryForm.dateRange[1].toISOString()}`
      : undefined

  const routePath = isContact.value
    ? '/order/orderContact'
    : isAudit.value
      ? '/order/orderAudit'
      : '/order/orderManager'
  router.replace({
    path: routePath,
    query: {
      pageNo: queryForm.pageNo,
      pageSize: queryForm.pageSize,
      repeatType: queryForm.repeatType,
      searchType: queryForm.searchType,
      keywords: queryForm.keywords,
      orderStatus: queryForm.orderStatus,
      botOrderStatus: queryForm.botOrderStatus,
      countryId: queryForm.countryId,
      platform: queryForm.platform,
      dateRange,
      belongEmployeeIds:
        queryForm.belongEmployeeIds && queryForm.belongEmployeeIds.length > 0
          ? queryForm.belongEmployeeIds.join(',')
          : undefined,
      belongDepartmentIds:
        queryForm.belongDepartmentIds && queryForm.belongDepartmentIds.length > 0
          ? queryForm.belongDepartmentIds.join(',')
          : undefined,
      contacted:
        queryForm.contacted !== undefined && queryForm.contacted !== null
          ? String(queryForm.contacted)
          : undefined,
    },
  })
}

onBeforeMount(() => {
  initQueryParams()
  fetchData()
  nextTick(() => {
    skipAutoQuery = false
  })
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

const startRemark = (row: any) => {
  row.editingOrderCheckRemark = true
  ignoreRowSelect.value = true
  row._orderCheckRemark = `${row.orderCheckRemark}`
  nextTick(() => {
    remarkRef.value.focus()
  })
}
const handleUpdateOrderCheckRemark = (row: {
  orderCheckRemark: string
  editingOrderCheckRemark: boolean
  _updatingOrderCheckRemark: boolean
  id: string
  _orderCheckRemark: string
}) => {
  row._updatingOrderCheckRemark = true
  updateOrderCheckRemark([row.id], `${row._orderCheckRemark}`)
    .then(() => {
      row.orderCheckRemark = `${row._orderCheckRemark}`
      row.editingOrderCheckRemark = false
      row._updatingOrderCheckRemark = false

      setTimeout(() => {
        ignoreRowSelect.value = false
      }, 300)
    })
    .catch((error) => {
      console.error(error)
      row.editingOrderCheckRemark = false
      row._updatingOrderCheckRemark = false

      setTimeout(() => {
        ignoreRowSelect.value = false
      }, 300)
    })
}

const handleRowClick = (row: any, column: any, event: any) => {
  // 如果点击的是选择列（checkbox），或者点击的是 input/button/a/svg 等，则不在这里处理
  if (column && column.type === 'selection') return
  if (
    event &&
    event.target &&
    event.target.classList &&
    event.target.classList.contains('el-link__inner')
  )
    return // el-link
  if (event.target.nodeName === 'TEXTAREA') {
    // textarea focus
    return
  }
  if (ignoreRowSelect.value) {
    return
  }
  const index = selectRows.value.findIndex((item: any) => item.id === row.id)
  // console.log('handleRowClick', row, index, selectRows.value)
  // if (index === -1) {
  //   // 未选中，添加到选中行
  //   selectRows.value.push(row)
  //   tableRef.value.toggleRowSelection && tableRef.value.toggleRowSelection(row, true)
  // } else {
  //   // 已选中，移除
  //   selectRows.value.splice(index, 1)
  // }
  tableRef.value.toggleRowSelection && tableRef.value.toggleRowSelection(row, index === -1)
}
</script>

<style scoped>
.text-left {
  width: 100%;
  text-align: left !important;
}
.compact {
  height: auto !important;
  margin: 0 !important;
  line-height: 15px !important;
  text-align: center;
}
.product-name {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  overflow-wrap: break-word;
  white-space: normal;
}
.text-info {
  color: #505256;
}
.text-primary {
  /* color: #4e88f3; */
  color: #009900;
}
.text-warning {
  color: #009900;
}
.text-danger {
  /* color: #fd4e4e; */
  color: #ff0000;
}
.text-success {
  /* color: #2c7e00; */
  color: #009900;
}
.text-received {
  /* color: #2db7f5; */
  color: #0066ff;
}

/* .text-copy {
   color: #000000;
} */
.pointer-cursor {
  cursor: pointer;
}
.bold {
  font-weight: 400;
}
.contact-fade-enter-active,
.contact-fade-leave-active {
  transition: opacity 0.2s ease;
}
.contact-fade-enter-from,
.contact-fade-leave-to {
  opacity: 0;
}
</style>
