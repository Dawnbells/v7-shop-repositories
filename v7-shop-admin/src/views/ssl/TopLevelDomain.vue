<template>
  <div class="top-level-domain-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="49px" :model="queryForm" @submit.prevent>
          <el-form-item label="域名">
            <el-input v-model="queryForm.title" clearable placeholder="请输入域名" />
          </el-form-item>
          <el-form-item>
            <el-button
              :icon="Search"
              :loading="listLoading"
              native-type="submit"
              type="primary"
              @click="queryData"
            >
              查询
            </el-button>
          </el-form-item>
        </el-form>
      </vab-query-form-top-panel>
      <vab-query-form-left-panel :span="24">
        <el-button :icon="Plus" type="primary" @click="handleAdd">添加</el-button>
        <el-button :icon="Delete" type="danger" @click="handleDelete">删除</el-button>
      </vab-query-form-left-panel>
    </vab-query-form>

    <el-table
      ref="tableRef"
      v-loading="listLoading"
      border
      :data="list"
      :expand-row-keys="expandedRowKeys"
      :row-key="getRowKey"
      @expand-change="handleExpandChange"
      @selection-change="setSelectRows"
      @sort-change="handleSortChange"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <sub-domain :top-level-domain-id="Number(row.id)" />
        </template>
      </el-table-column>
      <el-table-column type="selection" width="38" />
      <el-table-column align="center" label="域名" prop="name" />
      <el-table-column align="center" label="归属人" prop="ownerName" />
      <el-table-column align="center" label="部门" prop="departmentName">
        <template #default="{ row }">
          <el-tag v-if="row.departmentName" :type="'primary'">{{ row.departmentName }}</el-tag>
          <el-tag v-else :type="'info'">未分配</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="用途" prop="type">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'COMPANY'" type="primary">公司</el-tag>
          <el-tag v-if="row.type === 'WEBSITE'" type="primary">网站</el-tag>
          <el-tag v-if="row.type === 'RELAY'" type="primary">中继</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="平台" prop="remark">
        <template #default="{ row }">
          <el-tag v-if="row.cloudPlatformAccount" type="primary">
            {{ row.cloudPlatformAccount.name }}
          </el-tag>
          <el-tooltip
            v-else
            class="box-item"
            content="未设置云平台不支持进行证书自动管理"
            effect="dark"
            placement="top"
          >
            <el-tag type="warning">未设置</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column align="center" label="SSL证书" prop="sslExpiryDate" sortable="custom">
        <template #default="{ row }">
          <el-tooltip v-if="showCertbotInfo(row)" placement="top">
            <template #content>
              <span v-html="formatCertInfo(row)"></span>
            </template>
            <el-tag
              style="cursor: pointer"
              :type="certbotInfoType(row)"
              @click="handleCertificate(row)"
            >
              {{ certbotInfoStatus(row) }}
            </el-tag>
          </el-tooltip>
          <el-tag
            v-else
            style="cursor: pointer"
            :type="certbotInfoType(row)"
            @click="handleCertificate(row)"
          >
            {{ certbotInfoStatus(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="到期时间" prop="expiryDate" sortable="custom">
        <template #default="{ row }">
          <el-tag :type="expiryDateType(row.expiryDate)">
            {{ expiryDateFormat(row.expiryDate) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="备注" prop="remark" />
      <el-table-column align="center" label="操作" width="450">
        <template #default="{ row }">
          <el-button class="option-button" text type="primary" @click="handleAddSubDomain(row)">
            新增
          </el-button>
          <el-dropdown @command="(command) => handleAnalyzer(command, row)">
            <el-button class="option-button" text type="primary">
              解析
              <el-icon class="el-icon--right">
                <arrow-down />
              </el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="nuxt_mall">可编辑主题方案</el-dropdown-item>
                <el-dropdown-item command="vike">新模板方案</el-dropdown-item>
                <el-dropdown-item command="thymeleaf">旧模板方案</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-tooltip v-if="row.protocolName" :content="row.protocolName" placement="top">
            <el-button class="option-button" text type="success" @click="handleBindProtocol(row)">
              协议
            </el-button>
          </el-tooltip>
          <el-button
            v-else
            class="option-button"
            text
            type="primary"
            @click="handleBindProtocol(row)"
          >
            协议
          </el-button>
          <el-tooltip
            v-if="getPixelNames(row).length > 0"
            :content="getPixelNames(row).join(', ')"
            placement="top"
          >
            <el-button class="option-button" text type="success" @click="handleBindPixel(row)">
              像素
            </el-button>
          </el-tooltip>
          <el-button v-else class="option-button" text type="primary" @click="handleBindPixel(row)">
            像素
          </el-button>
          <el-button class="option-button" text type="primary" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button class="option-button" text type="primary" @click="handleTransfer(row)">
            转移
          </el-button>
          <el-button class="option-button" text type="danger" @click="handleDelete(row)">
            删除
          </el-button>
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
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
    <top-level-domain-edit ref="editRef" @close="resumePolling" @fetch-data="fetchData" />
    <sub-domain-edit ref="subEditRef" @close="resumePolling" @fetch-data="fetchData" />
    <top-level-domain-transfer ref="transferRef" @close="resumePolling" @fetch-data="fetchData" />
    <certificate-edit ref="certificateEditRef" @close="resumePolling" @fetch-data="fetchData" />
    <bind-domain-protocol-edit
      ref="bindDomainProtocolRef"
      @close="resumePolling"
      @fetch-data="fetchData"
    />
    <bind-domain-pixel-edit
      ref="bindDomainPixelRef"
      @close="resumePolling"
      @fetch-data="fetchData"
    />
  </div>
</template>

<script lang="ts" setup>
import { ArrowDown, Delete, Plus, Search } from '@element-plus/icons-vue'
import { doDelete, nginxConfig, page } from '/@/api/topLevelDomain'
import {
  certbotInfoStatus,
  certbotInfoType,
  expiryDateFormat,
  expiryDateType,
  showCertbotInfo,
} from '/@/utils/datetime'

defineOptions({
  name: 'TopLevelDomain',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const bindDomainProtocolRef = ref<any>(null)
const bindDomainPixelRef = ref<any>(null)
const transferRef = ref<any>(null)
const subEditRef = ref<any>(null)
const certificateEditRef = ref<any>(null)
const tableRef = ref<any>(null)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const expandedRowKeys = ref<Array<number | string>>([])
const sslPollingTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
  sortBy: 'id desc',
})
const getRowKey = (row: { id: number | string }) => row.id
const dialogOpen = ref(false)

const clearSslPollingTimer = () => {
  if (sslPollingTimer.value) {
    clearTimeout(sslPollingTimer.value)
    sslPollingTimer.value = null
  }
}

const pausePolling = () => {
  dialogOpen.value = true
  clearSslPollingTimer()
}

const resumePolling = () => {
  dialogOpen.value = false
  checkAnyInSslRequesting()
}

provide('pausePolling', pausePolling)
provide('resumePolling', resumePolling)
const formatCertInfo = (row: any) => {
  return `${row.sslCertificate?.result?.replace(/\n/g, '<br />') || ''}<br /> ErrorMsg: <br />${
    row.sslCertificate?.errorMsg?.replace(/\n/g, '<br />') || ''
  }<br /> errLog: <br />${
    row.sslCertificate?.errLog?.replace(/\n/g, '<br />') || ''
  }<br /><br />  sslPushMsg: <br />${
    row.sslCertificate?.sslPushMsg?.replace(/\n/g, '<br />') || ''
  }`
}
const fetchData = async () => {
  listLoading.value = true
  const { data } = await page(queryForm)
  list.value = data.list
  total.value = data.total
  expandedRowKeys.value = expandedRowKeys.value.filter((key) =>
    list.value.some((item: { id: number | string }) => item.id === key)
  )
  checkAnyInSslRequesting()
  listLoading.value = false
}

const checkAnyInSslRequesting = async () => {
  clearSslPollingTimer()
  if (dialogOpen.value) return
  const anyInQueue = list.value.some((item: any) => item.certificateRequestStatus === 'QUEUE')
  const anyInRequesting = list.value.some(
    (item: any) => item.certificateRequestStatus === 'REQUESTING'
  )
  if (anyInQueue || anyInRequesting) {
    const { data } = await page(queryForm)
    list.value = data.list
    total.value = data.total
    expandedRowKeys.value = expandedRowKeys.value.filter((key) =>
      list.value.some((item: { id: number | string }) => item.id === key)
    )
    if (!dialogOpen.value) {
      sslPollingTimer.value = setTimeout(checkAnyInSslRequesting, 5000)
    }
  }
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

const handleSortChange = ({ prop, order }: { prop: string; order: string | null }) => {
  if (!order) {
    queryForm.sortBy = 'id desc'
  } else {
    const field = prop === 'sslExpiryDate' ? 'certificateExpiryDate' : 'expiryDate'
    const dir = order === 'descending' ? 'desc' : 'asc'
    queryForm.sortBy = `${field} ${dir}`
  }
  queryForm.pageNo = 1
  fetchData()
}

const setSelectRows = (value: string) => {
  selectRows.value = value
}

const handleExpandChange = (
  row: { id: number | string },
  expandedRows: Array<{ id: number | string }>
) => {
  expandedRowKeys.value = expandedRows.map((item) => item.id)
}

const handleAdd = () => {
  pausePolling()
  editRef.value.showEdit()
}

const handleEdit = (row = {}) => {
  pausePolling()
  editRef.value.showEdit(row)
}

const handleTransfer = (row = {}) => {
  pausePolling()
  transferRef.value.showEdit(row)
}

const handleDelete = (row: any) => {
  if (row.id) {
    $baseConfirm('您确定要删除当前项吗', null, async () => {
      const { msg }: any = await doDelete({ ids: row.id })
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  } else {
    if (selectRows.value.length > 0) {
      const ids = selectRows.value.map((item: { id: any }) => item.id).join(',')
      $baseConfirm('您确定要删除选中项吗', null, async () => {
        const { msg }: any = await doDelete({ ids })
        $baseMessage(msg, 'success', 'hey')
        await fetchData()
      })
    } else {
      $baseMessage('您未选中任何行', 'warning', 'hey')
    }
  }
}

const handleCertificate = (row = {}) => {
  pausePolling()
  certificateEditRef.value.showEdit(row)
}

const handleAddSubDomain = (row = {}) => {
  pausePolling()
  subEditRef.value.showEdit(row)
}

const handleBindProtocol = (row: any) => {
  pausePolling()
  bindDomainProtocolRef.value.showEdit(row)
}

const getPixelNames = (row: any): string[] => {
  if (row.pixelNames && Array.isArray(row.pixelNames) && row.pixelNames.length > 0) {
    return row.pixelNames
  }
  if (row.pixels && Array.isArray(row.pixels) && row.pixels.length > 0) {
    return row.pixels.map((pixel: any) => pixel.pixelName || pixel.name || '').filter(Boolean)
  }
  return []
}

const handleBindPixel = (row: any) => {
  pausePolling()
  bindDomainPixelRef.value.showEdit(row, 'topLevel')
}

const handleAnalyzer = async (command: string, row: any) => {
  try {
    const { msg }: any = await nginxConfig(row.id, command as 'vike' | 'thymeleaf' | 'nuxt_mall')
    await $baseMessage(msg, 'success', 'hey')
  } catch (error) {
    console.error('解析失败:', error)
  }
}

onActivated(() => {
  tableRef.value.doLayout()
})

onBeforeMount(() => {
  fetchData()
})

onBeforeUnmount(() => {
  clearSslPollingTimer()
})
</script>

<style>
.option-button {
  padding: 15px 8px;
}

/* 展开行子域名表格自适应高度 */
.top-level-domain-container .el-table__expanded-cell {
  padding: 12px 20px;
}

.top-level-domain-container .el-table__expanded-cell .el-table {
  height: auto !important;
  max-height: none !important;
}

.top-level-domain-container .el-table__expanded-cell .el-table__body-wrapper {
  height: auto !important;
  max-height: none !important;
  overflow: visible !important;
}

.top-level-domain-container .el-table__expanded-cell .el-scrollbar__wrap {
  height: auto !important;
  max-height: none !important;
  overflow: visible !important;
}

.top-level-domain-container .el-table__expanded-cell .el-scrollbar__view {
  height: auto !important;
}

.top-level-domain-container .el-table__expanded-cell .el-scrollbar {
  height: auto !important;
}
</style>
