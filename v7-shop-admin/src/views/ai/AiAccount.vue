<template>
  <div class="ai-account-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="70px" :model="queryForm" @submit.prevent>
          <el-form-item label="账号名称">
            <el-input v-model="queryForm.name" clearable placeholder="请输入账号名称" />
          </el-form-item>
          <el-form-item v-show="!fold" label="服务商">
            <el-select v-model="queryForm.provider" clearable placeholder="全部" style="width: 130px">
              <el-option label="Gemini" value="GEMINI" />
              <el-option label="OpenAI" value="OPENAI" />
            </el-select>
          </el-form-item>
          <el-form-item v-show="!fold" label="API渠道">
            <el-select v-model="queryForm.apiChannel" clearable placeholder="全部" style="width: 130px">
              <el-option label="官方" value="OFFICIAL" />
              <el-option label="Sub2API" value="SUB2API" />
            </el-select>
          </el-form-item>
          <el-form-item v-show="!fold" label="状态">
            <el-select v-model="queryForm.status" clearable placeholder="全部" style="width: 120px">
              <el-option label="有效" value="VALID" />
              <el-option label="无效" value="INVALID" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button :icon="Search" :loading="listLoading" native-type="submit" type="primary" @click="queryData">查询</el-button>
            <el-button class="hidden-xs-only" text type="primary" @click="handleFold">
              <span v-if="fold">展开</span>
              <span v-else>合并</span>
              <vab-icon class="vab-dropdown" :class="{ 'vab-dropdown-active': fold }" icon="arrow-up-s-line" />
            </el-button>
          </el-form-item>
        </el-form>
      </vab-query-form-top-panel>
      <vab-query-form-left-panel :span="24">
        <el-button :icon="Plus" type="primary" @click="handleAdd">添加</el-button>
        <el-button :icon="Delete" type="danger" @click="handleDelete">删除</el-button>
      </vab-query-form-left-panel>
    </vab-query-form>

    <el-table ref="tableRef" v-loading="listLoading" border :data="list" @selection-change="setSelectRows">
      <el-table-column type="selection" width="38" />
      <el-table-column align="center" label="名称" min-width="140" prop="name" show-overflow-tooltip />
      <el-table-column align="center" label="服务商" width="100">
        <template #default="{ row }">
          <el-tag>{{ providerLabel(row.provider) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="渠道" width="100">
        <template #default="{ row }">
          <el-tag :type="row.apiChannel === 'SUB2API' ? 'success' : 'primary'">
            {{ apiChannelLabel(row.apiChannel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="接口模式" width="100">
        <template #default="{ row }">
          <span v-if="row.provider === 'GEMINI'">{{ invokeModeLabel(row.invokeMode) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="模型" min-width="150" prop="model" show-overflow-tooltip />
      <el-table-column align="center" label="Base URL" min-width="180" prop="baseUrl" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.baseUrl || '-' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="币种" width="80">
        <template #default="{ row }">
          {{ row.billingCurrency || '-' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="文本输入" width="120">
        <template #default="{ row }">
          {{ formatPrice(row.textInputPrice, row.textInputPriceUnit) }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="文本输出" width="120">
        <template #default="{ row }">
          {{ formatPrice(row.textOutputPrice, row.textOutputPriceUnit) }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="图片输入" width="120">
        <template #default="{ row }">
          {{ formatPrice(row.imageInputPrice, row.imageInputPriceUnit) }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="图片输出" width="120">
        <template #default="{ row }">
          {{ formatPrice(row.imageOutputPrice, row.imageOutputPriceUnit) }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="视频输入" width="120">
        <template #default="{ row }">
          {{ formatPrice(row.videoInputPrice, row.videoInputPriceUnit) }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="视频输出" width="120">
        <template #default="{ row }">
          {{ formatPrice(row.videoOutputPrice, row.videoOutputPriceUnit) }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="每日限额" width="90">
        <template #default="{ row }">
          {{ row.dailyLimit == null ? '-' : row.dailyLimit }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="优先级" prop="priority" width="80" />
      <el-table-column align="center" label="状态" prop="status" width="90">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            active-value="VALID"
            inactive-value="INVALID"
            :loading="row.statusLoading"
            @change="($event) => handleSwitchValidity($event, row)"
          />
        </template>
      </el-table-column>
      <el-table-column align="center" label="操作" width="210">
        <template #default="{ row }">
          <el-button text type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button text type="primary" @click="handleCopy(row)">复制</el-button>
          <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
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
    <ai-account-edit ref="editRef" @fetch-data="fetchData" />
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { doDelete, page, switchValidity } from '/@/api/aiAccount'

defineOptions({
  name: 'AiAccount',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const tableRef = ref<any>(null)
const fold = ref<boolean>(true)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
})

const providerLabel = (provider?: string) => {
  if (provider === 'GEMINI') return 'Gemini'
  if (provider === 'OPENAI') return 'OpenAI'
  return provider || '-'
}

const apiChannelLabel = (apiChannel?: string) => {
  if (apiChannel === 'OFFICIAL') return '官方'
  if (apiChannel === 'SUB2API') return 'Sub2API'
  return apiChannel || '-'
}

const invokeModeLabel = (invokeMode?: string) => {
  if (invokeMode === 'BATCH') return '批量接口'
  if (invokeMode === 'STANDARD') return '标准接口'
  return invokeMode || '标准接口'
}

const priceUnitLabel = (unit?: string) => {
  const map: Record<string, string> = {
    PER_1M_TOKENS: '/百万Tokens',
    PER_1K_TOKENS: '/千Tokens',
    PER_IMAGE: '/张',
    PER_1K_IMAGES: '/千张',
    PER_VIDEO: '/个视频',
    PER_MINUTE: '/分钟',
    PER_SECOND: '/秒',
  }
  return unit ? map[unit] || unit : ''
}

const formatPrice = (price?: number | string | null, unit?: string) => {
  if (price === undefined || price === null || price === '') return '-'
  return `${price}${priceUnitLabel(unit)}`
}

const fetchData = async () => {
  listLoading.value = true
  try {
    const { data } = await page(queryForm)
    list.value = data.list
    total.value = data.total
  } finally {
    listLoading.value = false
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

const handleFold = () => {
  fold.value = !fold.value
}

const setSelectRows = (value: any[]) => {
  selectRows.value = value
}

const handleAdd = () => {
  editRef.value.showEdit()
}

const handleEdit = (row = {}) => {
  editRef.value.showEdit(row)
}

const handleCopy = (row: any) => {
  editRef.value.showEdit(row, true)
}

const handleDelete = (row: any) => {
  if (row.id) {
    $baseConfirm('您确定要删除当前项吗', null, async () => {
      const { msg }: any = await doDelete({ ids: row.id })
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  } else if (selectRows.value.length > 0) {
    const ids = selectRows.value.map((item: { id: any }) => item.id).join()
    $baseConfirm('您确定要删除选中项吗', null, async () => {
      const { msg }: any = await doDelete({ ids })
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  } else {
    $baseMessage('您未选中任何行', 'warning', 'hey')
  }
}

const handleSwitchValidity = (newVal: boolean | string | number, row: { id: number; status: string; statusLoading: boolean }) => {
  row.statusLoading = true
  switchValidity({ id: row.id, status: row.status })
    .then(() => {
      row.statusLoading = false
    })
    .catch(() => {
      row.statusLoading = false
      row.status = newVal == 'VALID' ? 'INVALID' : 'VALID'
    })
}

onActivated(() => {
  tableRef.value?.doLayout()
})

onBeforeMount(() => {
  fetchData()
})
</script>
