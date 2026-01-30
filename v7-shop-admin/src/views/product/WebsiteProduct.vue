<template>
  <div class="spu-container auto-height-container">
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
      <el-table-column align="center" label="SPU编码" prop="code" />
      <el-table-column align="center" label="SPU名称" prop="name" />
      <el-table-column align="center" label="商品分类" prop="productCategory.name" />
      <el-table-column align="center" label="商品" type="expand" width="80">
        <template #default="props">
          <el-table border :data="props.row.productList">
            <el-table-column align="center" label="商品标题" prop="title" />
            <el-table-column align="center" label="商品售价" prop="sellPrice" />
            <el-table-column align="center" label="语言名称" prop="language.cname" />

            <!-- <el-table-column align="center" label="操作" width="250">
              <template #default="{ row }">
                <el-button text type="primary" @click="handleAddProduct(props.row, row)">编辑</el-button>
                <el-button text type="danger" @click="handleDeleteProduct(row)">删除</el-button>
              </template>
            </el-table-column> -->
          </el-table>
        </template>
      </el-table-column>
      <el-table-column align="center" label="状态" prop="status" width="80">
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
      <el-table-column align="center" label="操作">
        <template #default="{ row }">
          <el-button text type="primary" @click="handlePreview(row)">预览</el-button>
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
    <website-product-edit ref="editRef" @fetch-data="fetchData" />

    <el-dialog v-model="chooseDomainDialogVisible" center title="请选择预览使用的域名">
      <div style="text-align: center">
        <el-select
          v-model="previewDomain"
          filterable
          :loading="loading"
          placeholder="请输入域名"
          remote
          :remote-method="remoteDomains"
          style="width: 320px"
        >
          <el-option
            v-for="item in domainOptions"
            :key="item.id"
            :label="item.fullName"
            :value="item.fullName"
          />
        </el-select>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="chooseDomainDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handlePreviewLocation">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getTicket } from '~/src/api/user'
import { page as domainPage } from '/@/api/domain'
import { page, switchOpen } from '/@/api/spu'
import { doUnbind } from '/@/api/websiteProduct'

defineOptions({
  name: 'Spu',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const tableRef = ref<any>(null)
const fold = ref<boolean>(true)
const list = ref<any>([])
const domainOptions = ref<any>([])
const listLoading = ref<boolean>(true)
const loading = ref<boolean>(false)
const total = ref<any>(0)
const selectRows = ref<any>([])
const previewSpu = ref<{ id: string }>()
const previewDomain = ref<any>()
const chooseDomainDialogVisible = ref(false)
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
  onlyWebsite: true,
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

const remoteDomains = (query: string) => {
  domainPage({
    keyword: query,
    pageNo: 1,
    pageSize: 20,
  }).then((res: any) => {
    if (!res || res.code !== '0' || res.data.total <= 0) {
      domainOptions.value = []
      return
    }
    domainOptions.value = res.data.list
  })
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

const handlePreview = (row: any) => {
  previewSpu.value = row
  domainOptions.value = []
  chooseDomainDialogVisible.value = true
}

const handlePreviewLocation = async () => {
  if (!previewDomain.value) {
    ElMessage.error('请选择一个域名')
    return
  }
  if (!previewSpu.value) {
    ElMessage.error('请选择一个产品')
    return
  }
  chooseDomainDialogVisible.value = false
  const res = await getTicket()
  const url = `https://${previewDomain.value}/product/${previewSpu.value?.id}?ticket=${encodeURIComponent(res.data.ticket)}`
  window.open(url, '_blank')
}

const handleDelete = (row: any) => {
  if (row.id) {
    $baseConfirm('是否移除该SPU?', null, async () => {
      const { msg }: any = await doUnbind({ ids: row.id })
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  } else {
    if (selectRows.value.length > 0) {
      const ids = selectRows.value.map((item: { id: any }) => item.id).join(',')
      $baseConfirm('是否移除所有选中的SPU?', null, async () => {
        const { msg }: any = await doUnbind({ ids })
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
  switchOpen({ id: row.id, status: row.status })
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
