<template>
  <div>
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form label-width="100px" :model="queryForm" @submit.prevent>
          <el-row class="row-bg" justify="start">
            <el-form-item label="" label-width="35px">
              <el-input
                v-model="queryForm.keywords"
                clearable
                placeholder="请输入查询关键字"
                style="width: 515px"
              >
                <template #prepend>
                  <div class="search-type-select-wrap">
                    <el-select
                      v-model="queryForm.searchType"
                      class="search-type-select"
                      @change="onSearchTypeManualChange"
                    >
                      <el-option label="订单编号" value="ORDER_ID" />
                      <el-option label="中文品名" value="MERCHANDISE" />
                      <el-option label="手机号码" value="TELEPHONE" />
                      <el-option label="客户姓名" value="NAME" />
                      <el-option label="产品标题" value="PRODUCT_TITLE" />
                      <el-option label="远程IP" value="REMOTE_IP" />
                      <el-option label="客户地址" value="ADDRESS" />
                      <el-option label="下单域名" value="DOMAIN" />
                      <el-option label="重单查询" value="REPEAT" />
                    </el-select>
                    <el-tooltip :content="inferTooltip" placement="top">
                      <button
                        class="infer-toggle-button"
                        :style="{
                          color: autoInferSearchType
                            ? 'var(--el-color-primary)'
                            : 'var(--el-text-color-disabled)',
                        }"
                        type="button"
                        @click.stop="onToggleAutoInfer"
                      >
                        <el-icon>
                          <MagicStick />
                        </el-icon>
                      </button>
                    </el-tooltip>
                  </div>
                </template>
                <template #append>
                  <el-space>
                    <el-button
                      :loading="listLoading"
                      native-type="submit"
                      type="primary"
                      @click="queryData"
                    >
                      搜索
                    </el-button>
                  </el-space>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="下单时间">
              <el-date-picker
                v-model="queryForm.dateRange"
                :default-time="isAudit ? auditDefaultDataRange : defaultDataRange"
                :default-value="isAudit ? auditDefaultDataRange : defaultDataRange"
                end-placeholder="结束时间"
                start-placeholder="起始时间"
                type="datetimerange"
              />
            </el-form-item>
            <!-- <el-button class="hidden-xs-only" text type="primary" @click="handleFold">
              <span v-if="fold">展开</span>
              <span v-else>合并</span>
              <vab-icon
                class="vab-dropdown"
                :class="{ 'vab-dropdown-active': fold }"
                icon="arrow-up-s-line"
              />
            </el-button> -->
          </el-row>
          <el-row class="row-bg" justify="start">
            <el-form-item label="订单状态">
              <el-select v-model="queryForm.orderStatus" clearable placeholder="请选择订单状态">
                <el-option label="待审核" value="PENDING" />
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="已发货" value="SHIPPED" />
                <el-option label="已签收" value="DELIVERED" />
                <el-option label="拒收" value="REJECTED" />
                <el-option label="丢件" value="LOST" />
                <el-option label="客户取消" value="CUSTOMER_CANCELLED" />
                <el-option label="无效单" value="INVALID" />
              </el-select>
            </el-form-item>
            <el-form-item label="订单类型">
              <el-select v-model="queryForm.botOrderStatus" clearable placeholder="请选择订单类型">
                <el-option label="只重单" value="DUPLICATE" />
                <el-option label="只提示" value="WARNING" />
                <el-option label="重单提示" value="DUPLICATE_WARNING" />
                <el-option label="正常" value="NORMAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="重单类型">
              <el-select v-model="queryForm.repeatType" clearable placeholder="请选择重单类型">
                <el-option label="浏览器IP" value="IP" />
                <el-option label="用户手机" value="PHONE" />
                <el-option label="用户姓名" value="NAME" />
                <el-option label="用户设备" value="DEVICE" />
                <el-option label="真实IP" value="REAL_IP" />
              </el-select>
            </el-form-item>
            <el-form-item label="归属国家" prop="languageId">
              <el-select
                v-model="queryForm.countryId"
                clearable
                filterable
                :loading="countryLoading"
                remote
                :remote-method="remoteQueryCountry"
              >
                <el-option
                  v-for="item in countryOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                >
                  <span style="float: left">{{ item.name }}</span>
                  <span
                    style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
                  >
                    {{ item.code }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="订单归属">
              <el-select
                v-model="queryForm.belongEmployeeIds"
                clearable
                collapse-tags
                filterable
                :loading="belongUserIdLoading"
                multiple
                remote
                :remote-method="remoteQueryBelongUserId"
              >
                <el-option
                  v-for="item in belongUserIdOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                >
                  <span style="float: left">{{ item.name }}</span>
                  <span
                    style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
                  >
                    {{ item.telephone }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="归属部门">
              <el-tree-select
                v-model="queryForm.belongDepartmentIds"
                clearable
                collapse-tags
                :data="allDepartmentTree"
                :default-checked-keys="queryForm.belongDepartmentIds"
                :default-expanded-keys="queryForm.belongDepartmentIds"
                multiple
                node-key="id"
                :props="defaultProps"
                show-checkbox
              />
            </el-form-item>
            <el-form-item label="订单平台">
              <el-select v-model="queryForm.platform" clearable placeholder="请选择订单平台">
                <el-option label="新系统" value="V7_SHOP" />
                <el-option label="小宇宙" value="XYZ" />
                <el-option label="SHOPLINE" value="SHOPLINE" />
              </el-select>
            </el-form-item>
            <el-form-item label="建联状态">
              <el-select v-model="queryForm.contacted" clearable placeholder="请选择建联状态">
                <el-option label="已建联" :value="true" />
                <el-option label="未建联" :value="false" />
              </el-select>
            </el-form-item>
          </el-row>
        </el-form>
      </vab-query-form-top-panel>
      <vab-query-form-left-panel
        :span="24"
        style="display: flex; gap: 12px; align-items: center; margin-left: 35px"
      >
        <el-button
          v-if="isAudit"
          :icon="CircleCheck"
          :loading="updatingOrderStatus"
          type="primary"
          @click="handleBatchChangeOrderStatus('ALL')"
        >
          批量修改状态
        </el-button>
        <el-button
          v-if="isAudit"
          :icon="CircleCheck"
          :loading="updatingOrderStatus"
          type="primary"
          @click="handleBatchChangeOrderStatus('CONFIRMED')"
        >
          批量已确认
        </el-button>
        <el-button
          v-if="isAudit"
          :icon="Delete"
          :loading="updatingOrderStatus"
          type="danger"
          @click="handleBatchChangeOrderStatus('INVALID')"
        >
          批量无效单
        </el-button>
        <el-button
          v-if="isContact"
          :icon="CircleCheck"
          :loading="updatingOrderStatus"
          type="success"
          @click="handleBatchContactStatus(true)"
        >
          批量已建联
        </el-button>
        <el-button
          v-if="isContact"
          :icon="Delete"
          :loading="updatingOrderStatus"
          type="danger"
          @click="handleBatchContactStatus(false)"
        >
          批量未建联
        </el-button>
        <el-button
          v-if="isContact"
          :icon="EditPen"
          type="warning"
          @click="handleBatchContactRemark"
        >
          批量备注
        </el-button>
        <el-button
          v-if="!isContact"
          :icon="Download"
          :loading="taskDownloading"
          type="primary"
          @click="handleDownload('all')"
        >
          下载全部
        </el-button>
        <el-button
          v-if="!isContact"
          :icon="Download"
          :loading="taskDownloading"
          type="danger"
          @click="handleDownload('selected')"
        >
          下载选中
        </el-button>
        <el-upload
          v-if="isAudit"
          :action="uploadUrl"
          :before-upload="beforeUpload"
          class="upload-order"
          :headers="{ Authorization: calcTokenHeader() }"
          :on-error="handleUploadError"
          :on-progress="handleProgress"
          :on-success="handleUploadSuccess"
          :show-file-list="false"
        >
          <el-button :icon="Upload" :loading="progressVisible" type="success">上传订单</el-button>
        </el-upload>
      </vab-query-form-left-panel>
    </vab-query-form>

    <el-dialog
      v-model="progressVisible"
      center
      style="min-height: 150px; max-height: 650px; margin-bottom: 10px"
      title="处理进度"
      width="30%"
    >
      <el-progress :format="percentageFormat" :percentage="uploadPercentage" />
      <div class="error-message-scroll">
        {{ uploadErrorMessage || '' }}
      </div>
      <div style="height: 50px"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  CircleCheck,
  Delete,
  Download,
  EditPen,
  MagicStick,
  Upload,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { type Department, getTree as getAllDepartmentTree } from '~/src/api/department'
import { status } from '~/src/api/taskManagement'
import { useUserStore } from '~/src/store/modules/user'
import { getRemoteQuery } from '/@/api/country'
import { getRemoteQuery as getRemoteQueryEmployee } from '/@/api/employee'
import { getEnv } from '/@/utils/env'
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
const defaultDataRange: [Date, Date] = [today, tomorrow]
const auditDefaultDataRange: [Date, Date] = [auditYesterday, auditToday]

const countryLoading = ref<boolean>(false)
const progressVisible = ref<boolean>(false)
const uploadPercentage = ref<number>(0)
const percentageFormat = (percentage: number) => `${percentage}%`
// const fold = ref<boolean>(false)
const queryForm = defineModel<any>()

const SEARCH_TYPE_LABELS: Record<string, string> = {
  ORDER_ID: '订单编号',
  MERCHANDISE: '中文品名',
  TELEPHONE: '手机号码',
  NAME: '客户姓名',
  PRODUCT_TITLE: '产品标题',
  REMOTE_IP: '远程IP',
  ADDRESS: '客户地址',
  DOMAIN: '下单域名',
  REPEAT: '重单查询',
}

// 仅在 autoInferSearchType=true 时根据 keyword 自动写入 queryForm.searchType
const autoInferSearchType = ref(true)

const inferSearchType = (raw?: string): string => {
  const s = (raw ?? '').trim()
  if (!s) return 'ORDER_ID'
  if (/[\u4e00-\u9fa5]/.test(s)) return 'MERCHANDISE'
  if (s.includes('.')) {
    if (/[a-zA-Z]/.test(s)) return 'DOMAIN'
    if (/^[\d.]+$/.test(s)) return 'REMOTE_IP'
  }
  if (/^\d+$/.test(s)) {
    return s.length >= 5 && s.length <= 12 ? 'TELEPHONE' : 'ORDER_ID'
  }
  if (/[a-zA-Z]/.test(s)) {
    const spaceCount = (s.match(/ /g) || []).length
    return spaceCount === 1 ? 'NAME' : 'PRODUCT_TITLE'
  }
  return 'ORDER_ID'
}

const inferTooltip = computed(() => {
  const label = SEARCH_TYPE_LABELS[queryForm.value?.searchType] ?? '订单编号'
  return autoInferSearchType.value
    ? `智能推断中：${label}（手动选择类型后会暂停推断）`
    : `已手动锁定：${label}，点击恢复智能推断`
})

const onSearchTypeManualChange = () => {
  // el-select @change 仅在用户交互时触发，程序赋值不会触发；因此可作为“手动锁定”的判定点
  autoInferSearchType.value = false
}

const onToggleAutoInfer = () => {
  autoInferSearchType.value = true
  if (queryForm.value) {
    queryForm.value.searchType = inferSearchType(queryForm.value.keywords)
  }
}

watch(
  () => queryForm.value?.keywords,
  (kw) => {
    if (!kw || !String(kw).trim()) {
      autoInferSearchType.value = true
      if (queryForm.value) queryForm.value.searchType = 'ORDER_ID'
      return
    }
    if (!autoInferSearchType.value) return
    if (queryForm.value) queryForm.value.searchType = inferSearchType(kw)
  }
)

const uploadUrl = `${getEnv('VITE_API_BASE_URL', window.location.origin)}/orders/upload`
const belongUserIdLoading = ref<boolean>(false)
const belongUserIdOptions = ref<any[]>([])
const allDepartmentTree = ref<Department[]>([])
const uploadErrorMessage = ref<string>('')
const countryOptions = ref<any>([])

const defaultProps = {
  label: 'name',
  children: 'children',
}
const props = defineProps<{
  listLoading: boolean
  isAudit: boolean
  isContact: boolean
  taskDownloading: boolean
  updatingOrderStatus: boolean
}>()

const emit = defineEmits<{
  (event: 'onSearch'): void
  (event: 'onReset'): void
  (event: 'onDownload', type: string): void
  (event: 'onBatchChangeOrderStatus', status: string): void
  (event: 'onBatchChangeOrderRemark'): void
  (event: 'onBatchContactStatus', contacted: boolean): void
  (event: 'onBatchContactRemark'): void
}>()

const fetchAllDepartments = () => {
  const params: any = { status: 'VALID' }
  if (props.isContact) {
    params.isPrivateDomain = true
  }
  getAllDepartmentTree(params).then((data) => {
    allDepartmentTree.value = data.data.list.map((item: any) => ({
      ...item,
      label: item.name,
      value: item.id,
    }))
  })
}

const handleBatchChangeOrderStatus = (status: string) => {
  emit('onBatchChangeOrderStatus', status)
}

const handleBatchContactStatus = (contacted: boolean) => {
  emit('onBatchContactStatus', contacted)
}

const handleBatchContactRemark = () => {
  emit('onBatchContactRemark')
}

const calcTokenHeader = () => {
  const userStore = useUserStore()
  const { token } = userStore
  return `Bearer ${token}`
}

const queryData = () => {
  emit('onSearch')
}

// const handleFold = () => {
//   fold.value = !fold.value
// }

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const queryBelongToSearchAsync = (queryString: string, callback: (suggestions: any) => void) => {
  console.log(queryString)
}

const handleDownload = (type: any) => {
  emit('onDownload', type)
}
const handleProgress = (event: any) => {
  console.log('upload progress', event)
  uploadPercentage.value = event.percent
  progressVisible.value = true
  uploadErrorMessage.value = ''
}
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const handleUploadSuccess = (response: any, uploadFile: any, uploadFiles: any) => {
  if (response.code === '0') {
    waitingForUpload(response.data)
    // queryData() // Refresh data after successful upload
  } else {
    ElMessage.error(response.msg || '上传失败')
  }
}

const waitingForUpload = async (taskId: any) => {
  const checkDownloadStatus = async (taskId: string) => {
    try {
      const response = await status(taskId)
      uploadErrorMessage.value = response.data.message
      uploadPercentage.value = response.data.progress

      if (response.data.state === 'COMPLETED') {
        // Download completed, get file URL and trigger download
        queryData()
        return true
      } else if (response.data.state === 'FAILED') {
        $baseMessage('上传失败，请重试', 'error', 'hey', 0)
        return true
      }
      return false
    } catch (error) {
      console.error('Check download status error:', error)
      $baseMessage('检查上传状态出错', 'error', 'hey', 0)
      return true
    }
  }

  const poll = async (taskId: string) => {
    while (true) {
      const finished = await checkDownloadStatus(taskId)
      if (finished) {
        // progressVisible.value = false
        break
      }
      await new Promise((resolve) => setTimeout(resolve, 500)) // Poll every 2 seconds
    }
  }

  await poll(taskId)
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const handleUploadError = (error: any, uploadFile: any, uploadFiles: any) => {
  console.error('upload error', error)
  ElMessage.error('上传失败')
}

const beforeUpload = (file: any) => {
  console.log('beforeUpload', file)
  return true
}
const remoteQueryBelongUserId = async (query: string) => {
  belongUserIdLoading.value = true
  try {
    const { data } = await getRemoteQueryEmployee(query)
    belongUserIdOptions.value = data.list
  } finally {
    belongUserIdLoading.value = false
  }
}
const remoteQueryCountry = async (query: string) => {
  countryLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    countryOptions.value = data.list
  } finally {
    countryLoading.value = false
  }
}

onActivated(() => {
  if (queryForm.value.belongEmployeeIds && queryForm.value.belongEmployeeIds.length > 0) {
    remoteQueryBelongUserId(queryForm.value.belongEmployeeIds[0])
  }
  if (queryForm.value.countryId) {
    remoteQueryCountry(queryForm.value.countryId)
  }
  fetchAllDepartments()
})
</script>

<style scoped>
/* 让 prepend 里的查询类型 select 与外层 input 边框融合，避免视觉上出现双重边框 */
.search-type-select-wrap {
  position: relative;
  display: inline-flex;
  width: 90px;
}
.search-type-select {
  width: 100%;
}
.search-type-select :deep(.el-select__wrapper),
.search-type-select :deep(.el-select__wrapper.is-hovering),
.search-type-select :deep(.el-select__wrapper.is-focused) {
  padding-left: 0;
  background-color: transparent;
  box-shadow: none !important;
}
.search-type-select :deep(.el-select__selected-item) {
  margin-left: 34px;
}
.search-type-select :deep(.el-select__input-wrapper) {
  margin-left: 34px;
}

/* 智能推断图标区域：覆盖在 select 左侧内部，高度与下拉框一致，整块可点击 */
.infer-toggle-button {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  padding: 0;
  margin: 0 0 0 -20px;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--el-border-radius-base) 0 0 var(--el-border-radius-base);
  transition: background-color 0.15s;
}
.infer-toggle-button .el-icon {
  font-size: 16px;
}

.form-item-wrap {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  width: 100%;
}
.upload-order {
  display: inline-flex;
  align-items: center;
  padding: 0;
  margin: -10px 0 0 0;
  vertical-align: middle;
}

.custom-tree-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
  font-size: 14px;
}
.error-message-scroll {
  max-height: 400px;
  padding: 4px;
  margin-top: 10px;
  overflow-y: auto;
  white-space: pre-line;
  border: 1px solid #eee;
}
.input-with-select .el-input-group__prepend {
  background-color: var(--el-fill-color-blank);
}
</style>
