<template>
  <div class="theme-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="49px" :model="queryForm" @submit.prevent>
          <el-form-item label="名称">
            <el-input v-model="queryForm.name" clearable placeholder="请输入名称" />
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
      <el-table-column align="center" label="名称" prop="name" />
      <el-table-column align="center" label="主题模板" prop="templateName" />
      <el-table-column align="center" label="描述" prop="description" />
      <el-table-column align="center" label="操作" width="220">
        <template #default="{ row }">
          <el-button class="option-button" text type="primary" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button class="option-button" text type="success" @click="handleConfig(row)">
            自定义配置
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
    <theme-edit ref="editRef" :template-options="templateOptions" @fetch-data="fetchData" />
    <theme-config-edit
      ref="configEditRef"
      :template-options="templateOptions"
      @fetch-data="fetchData"
    />
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { doDelete, getTemplates, page } from '/@/api/theme'

defineOptions({
  name: 'Theme',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const configEditRef = ref<any>(null)
const tableRef = ref<any>(null)
const fold = ref<boolean>(true)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const templateOptions = ref<any[]>([])
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
  name: '',
})

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

const handleConfig = (row: any) => {
  configEditRef.value.showEdit(row)
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

onActivated(() => {
  tableRef.value.doLayout()
})

const fetchTemplates = async () => {
  try {
    const { data } = await getTemplates()
    templateOptions.value = data.list || []
  } catch (error) {
    console.error('获取模板列表失败:', error)
  }
}

onBeforeMount(() => {
  fetchData()
  fetchTemplates()
})
</script>

<style>
.option-button {
  padding: 15px 8px;
}
</style>
