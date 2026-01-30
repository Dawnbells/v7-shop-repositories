<template>
  <div class="department-container auto-height-container">
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
        <el-button :icon="Plus" type="primary" @click="handleAdd(undefined)">添加</el-button>
        <el-button :icon="Delete" type="danger" @click="handleDelete">删除</el-button>
      </vab-query-form-left-panel>
    </vab-query-form>

    <el-table
      ref="tableRef"
      v-loading="listLoading"
      border
      :data="list"
      row-key="id"
      @selection-change="setSelectRows"
    >
      <el-table-column type="selection" width="38" />
      <el-table-column align="left" label="名称" prop="name" />
      <el-table-column align="center" label="描述" prop="description" />
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
      <el-table-column align="center" label="操作" width="320">
        <template #default="{ row }">
          <el-button text type="primary" @click="handleAdd(row)">添加</el-button>
          <el-button text type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button text type="success" @click="handleConfig(row)">邮件配置</el-button>
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
    <department-edit ref="editRef" @fetch-data="fetchData" />
    <department-config-dialog ref="configDialogRef" />
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { doDelete, getTree, switchValidity } from '/@/api/department'
import DepartmentConfigDialog from '/@/views/system/vabAutoComponents/DepartmentConfigDialog.vue'

defineOptions({
  name: 'Department',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const configDialogRef = ref<InstanceType<typeof DepartmentConfigDialog> | null>(null)
const tableRef = ref<any>(null)
const fold = ref<boolean>(true)
const list = ref<any>([])
const allValidList = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
})

const fetchData = async () => {
  listLoading.value = true
  const { data } = await getTree()
  const validTree = await getTree({
    status: 'VALID',
  })
  list.value = data.list
  total.value = data.total
  allValidList.value = validTree.data.list
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

const handleAdd = (row: { id: number } | undefined) => {
  editRef.value.showEdit(null, row ? row.id : null, allValidList.value)
}

const handleEdit = (row: { parentId: number | null } = { parentId: null }) => {
  editRef.value.showEdit(row, row.parentId, allValidList.value)
}

const handleConfig = (row: any) => {
  configDialogRef.value?.show(row)
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
}
const handleSwitchValidity = (
  newVal: boolean | string | number,
  row: { id: number; status: string; statusLoading: boolean }
) => {
  row.statusLoading = true
  switchValidity({ id: row.id, status: row.status })
    .then(() => {
      row.statusLoading = false
      fetchData()
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
