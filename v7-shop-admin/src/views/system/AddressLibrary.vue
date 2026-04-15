<template>
  <div class="address-library-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline :model="queryForm" @submit.prevent>
          <el-form-item label="国家">
            <el-select
              v-model="queryForm.countryCode"
              clearable
              filterable
              placeholder="选择国家"
              style="width: 200px"
              @change="handleCountryChange"
            >
              <el-option
                v-for="item in countryOptions"
                :key="item.code"
                :label="`${item.name} (${item.code})`"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="搜索">
            <el-input
              v-model="queryForm.keyword"
              clearable
              :placeholder="activeTab === 'address' ? '省/市/区/邮编' : '邮编/提示'"
              style="width: 220px"
              @clear="queryData"
              @keyup.enter="queryData"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              :disabled="!queryForm.countryCode"
              :icon="Search"
              :loading="listLoading"
              type="primary"
              @click="queryData"
            >
              查询
            </el-button>
          </el-form-item>
        </el-form>
      </vab-query-form-top-panel>
      <vab-query-form-left-panel :span="24">
        <el-button :icon="Upload" type="success" @click="openImportDialog">
          导入地址库
        </el-button>
      </vab-query-form-left-panel>
    </vab-query-form>

    <el-radio-group v-model="activeTab" style="margin-bottom: 12px" @change="handleTabChange">
      <el-radio-button value="address">地址库</el-radio-button>
      <el-radio-button value="remote">偏远地区</el-radio-button>
    </el-radio-group>

    <el-table v-loading="listLoading" border :data="list">
      <el-table-column align="center" label="序号" type="index" width="60" />
      <template v-if="activeTab === 'address'">
        <el-table-column align="left" label="省份" min-width="120" prop="province" />
        <el-table-column align="left" label="城市" min-width="120" prop="city" />
        <el-table-column align="left" label="区县" min-width="120" prop="district" />
        <el-table-column align="center" label="邮编" prop="postal_code" width="120" />
        <el-table-column align="center" label="状态" prop="status" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'VALID' ? 'success' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
      <template v-else>
        <el-table-column align="center" label="邮编" min-width="150" prop="postal_code" />
        <el-table-column align="left" label="偏远提示" min-width="250" prop="tip" />
      </template>
      <template #empty>
        <el-empty
          class="vab-data-empty"
          :description="emptyDescription"
        />
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

    <!-- 导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      destroy-on-close
      title="导入国家地址库"
      width="560px"
    >
      <el-form label-width="90px">
        <el-form-item label="国家代码" required>
          <el-select
            v-model="importForm.countryCode"
            filterable
            placeholder="选择国家"
            style="width: 100%"
          >
            <el-option
              v-for="item in countryOptions"
              :key="item.code"
              :label="`${item.name} (${item.code})`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            accept=".xlsx"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :show-file-list="true"
          >
            <el-button :icon="Upload" type="primary">选择Excel文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="fileValidation.checked">
          <el-alert
            v-if="fileValidation.valid"
            :closable="false"
            show-icon
            :title="fileValidation.message"
            type="success"
          />
          <el-alert
            v-else
            :closable="false"
            show-icon
            :title="fileValidation.message"
            type="error"
          />
        </el-form-item>
        <el-form-item>
          <el-alert :closable="false" type="info">
            <template #title>
              <div>
                <div>导入说明：</div>
                <div>- 文件格式：Excel（.xlsx），第一行为中文表头，顺序不限</div>
                <div>- 地址模式：含省份/城市/区县任一列，将覆盖该国家全部地址数据</div>
                <div>- 纯偏远模式：仅含邮编 + 是否偏远/偏远提示，仅更新偏远数据</div>
                <div>- 表头中存在的列不允许为空（是否偏远、偏远提示除外）</div>
                <div>- 系统自动按省份+城市+区县+邮编去重</div>
                <div style="margin-top: 8px">
                  <el-link type="primary" :underline="true" @click="downloadTemplate">
                    <el-icon style="margin-right: 4px"><Download /></el-icon>
                    下载导入模板
                  </el-link>
                </div>
              </div>
            </template>
          </el-alert>
        </el-form-item>
        <el-form-item v-if="importProgress.active">
          <div style="width: 100%">
            <el-progress
              :percentage="importProgress.percent"
              :status="importProgress.status"
              :stroke-width="16"
              text-inside
            />
            <div style="margin-top: 6px; color: var(--el-text-color-secondary); font-size: 13px">
              {{ importProgress.message }}
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="importProgress.active" @click="importDialogVisible = false">
          {{ importProgress.finished ? '关闭' : '取消' }}
        </el-button>
        <el-button
          v-if="!importProgress.active"
          :disabled="!canImport"
          :loading="importLoading"
          type="primary"
          @click="handleImport"
        >
          确认导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { Download, Search, Upload } from '@element-plus/icons-vue'
import { getCountries, pageByCountry, remoteAreaPage } from '/@/api/address'
import { getRemoteQuery } from '/@/api/country'
import { status as getTaskStatus } from '/@/api/taskManagement'
import { useUserStore } from '/@/store/modules/user'
import axios from 'axios'
import { getEnv } from '/@/utils/env'

defineOptions({
  name: 'AddressLibrary',
})

const $baseMessage = inject<any>('$baseMessage')
const userStore = useUserStore()
const uploadRef = ref<any>(null)
const listLoading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryForm = reactive({
  countryCode: '',
  keyword: '',
  pageNo: 1,
  pageSize: 20,
})

const activeTab = ref<'address' | 'remote'>('address')
const countryOptions = ref<any[]>([])
const existingCountryCodes = ref<Set<string>>(new Set())

const importDialogVisible = ref(false)
const importLoading = ref(false)
const importForm = reactive({
  countryCode: '',
  file: null as File | null,
})

const fileValidation = reactive({
  checked: false,
  valid: false,
  message: '',
})

const importProgress = reactive({
  active: false,
  finished: false,
  percent: 0,
  message: '',
  status: '' as '' | 'success' | 'exception',
})
let pollTimer: ReturnType<typeof setInterval> | null = null

const emptyDescription = computed(() => {
  if (!queryForm.countryCode) return '请先选择国家'
  if (activeTab.value === 'address' && !hasAddressLibrary(queryForm.countryCode))
    return '该国家/地区暂无地址库'
  if (activeTab.value === 'remote') return '该国家/地区暂无偏远地区数据'
  return '暂无数据'
})

const canImport = computed(() => {
  return importForm.countryCode && importForm.file && fileValidation.valid
})

const hasAddressLibrary = (code: string) => existingCountryCodes.value.has(code)

const fetchExistingCountries = async () => {
  try {
    const { data } = await getCountries()
    const list: string[] = data?.list || data || []
    existingCountryCodes.value = new Set(Array.isArray(list) ? list : [])
  } catch {
    existingCountryCodes.value = new Set()
  }
}

const fetchCountryOptions = async () => {
  try {
    const { data } = await getRemoteQuery('')
    countryOptions.value = (data?.list || []).map((c: any) => ({
      code: c.code,
      name: c.name,
    }))
  } catch {
    countryOptions.value = []
  }
}

const fetchData = async () => {
  if (!queryForm.countryCode) return
  listLoading.value = true
  try {
    const pageFn = activeTab.value === 'remote' ? remoteAreaPage : pageByCountry
    const { data } = await pageFn(queryForm.countryCode, {
      pageNo: queryForm.pageNo,
      pageSize: queryForm.pageSize,
      keyword: queryForm.keyword,
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

const handleTabChange = () => {
  queryForm.pageNo = 1
  list.value = []
  total.value = 0
  if (queryForm.countryCode) {
    fetchData()
  }
}

const handleCountryChange = () => {
  queryForm.pageNo = 1
  list.value = []
  total.value = 0
  if (queryForm.countryCode) {
    fetchData()
  }
}

const queryData = () => {
  queryForm.pageNo = 1
  fetchData()
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

const openImportDialog = () => {
  importForm.countryCode = queryForm.countryCode || ''
  importForm.file = null
  fileValidation.checked = false
  fileValidation.valid = false
  fileValidation.message = ''
  importProgress.active = false
  importProgress.finished = false
  importProgress.percent = 0
  importProgress.message = ''
  importProgress.status = ''
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  importDialogVisible.value = true
}

const handleFileChange = (uploadFile: any) => {
  const rawFile = uploadFile.raw || uploadFile
  const name: string = rawFile.name || ''
  if (!name.toLowerCase().endsWith('.xlsx')) {
    fileValidation.checked = true
    fileValidation.valid = false
    fileValidation.message = '仅支持 .xlsx 格式的 Excel 文件'
    importForm.file = null
    return
  }
  importForm.file = rawFile
  fileValidation.checked = true
  fileValidation.valid = true
  fileValidation.message = `已选择文件：${name}`
}

const handleFileRemove = () => {
  importForm.file = null
  fileValidation.checked = false
  fileValidation.valid = false
  fileValidation.message = ''
}

const downloadTemplate = async () => {
  try {
    const baseURL = getEnv('VITE_API_BASE_URL', window.location.origin)
    const { token } = userStore
    const response = await axios.get(`${baseURL}/address/template`, {
      responseType: 'blob',
      headers: { Authorization: `Bearer ${token}` },
    })
    const url = URL.createObjectURL(response.data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'address_import_template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    $baseMessage('下载模板失败', 'error', 'hey')
  }
}

const handleImport = async () => {
  if (!importForm.countryCode) {
    $baseMessage('请选择国家', 'warning', 'hey')
    return
  }
  if (!importForm.file) {
    $baseMessage('请选择Excel文件', 'warning', 'hey')
    return
  }

  importLoading.value = true
  importProgress.active = true
  importProgress.finished = false
  importProgress.percent = 0
  importProgress.message = '正在上传文件...'
  importProgress.status = ''

  try {
    const formData = new FormData()
    formData.append('file', importForm.file)
    formData.append('countryCode', importForm.countryCode)

    const baseURL = getEnv('VITE_API_BASE_URL', window.location.origin)
    const { token } = userStore

    const { data } = await axios.post(`${baseURL}/address/import`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        Authorization: `Bearer ${token}`,
      },
    })

    const taskId = data?.taskId || data?.data?.taskId
    if (taskId) {
      importProgress.message = '任务已提交，正在处理...'
      startPolling(String(taskId))
    } else {
      importProgress.active = false
      $baseMessage(data?.msg || '导入成功', 'success', 'hey')
      importDialogVisible.value = false
      await onImportDone()
    }
  } catch (err: any) {
    importProgress.active = false
    const msg = err?.response?.data?.msg || err?.response?.data?.message || err?.message || '导入失败'
    $baseMessage(msg, 'error', 'hey')
  } finally {
    importLoading.value = false
  }
}

const startPolling = (taskId: string) => {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const { data } = await getTaskStatus(taskId)
      const task = data?.data || data
      const state: string = task?.state || ''
      const progress: number = task?.progress ?? 0
      const message: string = task?.message || ''

      importProgress.percent = progress
      importProgress.message = message

      if (state === 'COMPLETED') {
        importProgress.percent = 100
        importProgress.status = 'success'
        importProgress.finished = true
        stopPolling()
        $baseMessage(message || '导入成功', 'success', 'hey')
        await onImportDone()
      } else if (state === 'FAILED') {
        importProgress.status = 'exception'
        importProgress.finished = true
        stopPolling()
        $baseMessage(message || '导入失败', 'error', 'hey')
      } else if (state === 'CANCELLED') {
        importProgress.status = 'exception'
        importProgress.message = '任务已取消'
        importProgress.finished = true
        stopPolling()
      }
    } catch {
      // 网络错误不停止轮询
    }
  }, 1000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  importProgress.active = false
}

const onImportDone = async () => {
  await fetchExistingCountries()
  if (importForm.countryCode) {
    queryForm.countryCode = importForm.countryCode
    queryForm.pageNo = 1
    await fetchData()
  }
}

onBeforeMount(async () => {
  await Promise.all([fetchExistingCountries(), fetchCountryOptions()])
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>
