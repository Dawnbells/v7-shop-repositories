<template>
  <div class="country-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="49px" :model="queryForm" @submit.prevent>
          <el-form-item label="标题">
            <el-input v-model="queryForm.title" clearable placeholder="请输入标题" />
          </el-form-item>
          <el-form-item v-show="!fold" label="标题">
            <el-input v-model="queryForm.title" clearable placeholder="请输入标题" />
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
            <el-button class="hidden-xs-only" text type="primary" @click="handleFold">
              <span v-if="fold">展开</span>
              <span v-else>合并</span>
              <vab-icon
                class="vab-dropdown"
                :class="{ 'vab-dropdown-active': fold }"
                icon="arrow-up-s-line"
              />
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
      @selection-change="setSelectRows"
    >
      <el-table-column type="selection" width="38" />
      <el-table-column align="center" label="国家名称" prop="name" />
      <el-table-column align="center" label="国家代码" prop="code" />
      <el-table-column align="center" label="归属大陆" prop="continentCode">
        <template #default="{ row }">
          <span>
            {{ continentMap[row.continentCode] }}
          </span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="国家语言">
        <template #default="{ row }">
          <el-space wrap>
            <el-tooltip
              v-for="language in row.languages"
              :key="language.id"
              class="box-item"
              :content="language.name + '(' + language.code + ')'"
              effect="dark"
              placement="top"
            >
              <el-tag type="primary">{{ language.cname }}</el-tag>
            </el-tooltip>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="国家货币">
        <el-table-column align="center" label="货币名称" prop="currency.name" />
        <el-table-column align="center" label="货币代码" prop="currency.code" />
        <el-table-column align="center" label="货币符号" prop="currency.symbol" />
        <el-table-column align="center" label="汇率" prop="currency.exchangeRate" />
      </el-table-column>
      <el-table-column align="center" label="配置" type="expand" width="80">
        <template #default="{ row }">
          <div class="expand-content">
            <el-descriptions :column="3" border size="small" title="收货配置">
              <el-descriptions-item label="服务器">
                {{ row.frontServer?.name || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="电话前缀">
                {{ row.phonePrefix || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="电话规则">
                <el-tooltip v-if="row.phoneRule" :content="row.phoneRule" placement="top">
                  <el-tag size="small">{{ row.phoneRule?.substring(0, 20) }}{{ row.phoneRule?.length > 20 ? '...' : '' }}</el-tag>
                </el-tooltip>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="必填电话">
                <el-tag :type="row.requiredPhone ? 'success' : 'info'" size="small">
                  {{ row.requiredPhone ? '是' : '否' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="必填邮箱">
                <el-tag :type="row.requiredEmail ? 'success' : 'info'" size="small">
                  {{ row.requiredEmail ? '是' : '否' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="使用全名">
                <el-tag :type="row.useFullName ? 'success' : 'info'" size="small">
                  {{ row.useFullName ? '是' : '否' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="地址字段" :span="2">
                <el-space v-if="row.addressFields" wrap size="small">
                  <el-tag v-for="field in row.addressFields?.split(',')" :key="field" size="small" type="info">
                    {{ field }}
                  </el-tag>
                </el-space>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="地址规则">
                <el-tooltip v-if="row.addressRule" :content="row.addressRule" placement="top">
                  <el-tag size="small">{{ row.addressRule?.substring(0, 20) }}{{ row.addressRule?.length > 20 ? '...' : '' }}</el-tag>
                </el-tooltip>
                <span v-else>-</span>
              </el-descriptions-item>
            </el-descriptions>
            <el-descriptions :column="1" border size="small" style="margin-top: 12px" title="其他配置">
              <el-descriptions-item label="底部版权信息">
                {{ row.footerCopyrightInfo || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="状态" prop="status">
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
      <el-table-column align="center" label="操作" width="150">
        <template #default="{ row }">
          <el-button text type="primary" @click="handleEdit(row)">编辑</el-button>
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
    <country-edit ref="editRef" @fetch-data="fetchData" />
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { doDelete, page, switchValidity } from '/@/api/country'

defineOptions({
  name: 'Country',
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

// 生态城map（大陆映射表）
const continentMap: Record<string, string> = {
  AF: '非洲',
  AN: '南极洲',
  AS: '亚洲',
  EU: '欧洲',
  NA: '北美洲',
  OC: '大洋洲',
  SA: '南美洲',
}

const fetchData = async () => {
  listLoading.value = true
  const { data } = await page(queryForm)
  list.value = data.list
  total.value = data.total
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

const handleFold = () => {
  fold.value = !fold.value
}

const setSelectRows = (value: string) => {
  selectRows.value = value
}

const handleAdd = () => {
  editRef.value.showEdit()
}

const handleEdit = (row = {}) => {
  editRef.value.showEdit(row)
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
const handleSwitchValidity = (
  newVal: boolean | string | number,
  row: { id: number; status: string; statusLoading: boolean }
) => {
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
  tableRef.value.doLayout()
})

onBeforeMount(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.country-container {
  .expand-content {
    padding: 16px 24px;
    background-color: var(--el-fill-color-lighter);

    :deep(.el-descriptions__title) {
      font-size: 14px;
      font-weight: 500;
    }
  }
}
</style>
