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
      <el-table-column align="center" label="SPU名称" prop="name" show-overflow-tooltip />
      <el-table-column align="center" label="归属人" prop="belong" />
      <el-table-column align="center" label="商品分类" prop="productCategory.name" />
      <el-table-column align="center" label="描述" prop="description" />
      <el-table-column label="商品" type="expand" width="80">
        <template #default="props">
          <el-table border :data="props.row.productList">
            <el-table-column align="center" label="商品标题" prop="title" show-overflow-tooltip />
            <el-table-column align="center" label="商品售价" prop="sellPrice" />
            <el-table-column align="center" label="国家名称" prop="country.name" />
            <el-table-column align="center" label="语言名称" prop="language.cname" />

            <el-table-column align="center" label="操作" width="300">
              <template #default="{ row }">
                <el-button text type="primary" @click="handleAddProduct(props.row, row)">
                  编辑
                </el-button>
                <el-button text type="primary" @click="handleProductTranslate(props.row, row)">
                  复制
                </el-button>
                <el-button text type="primary" @click="handleAITranslate(props.row, row)">
                  翻译
                </el-button>
                <el-button text type="primary" @click="handleProductAITranslate(props.row, row)">
                  AI翻译
                </el-button>
                <el-button text type="danger" @click="handleDeleteProduct(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column align="center" label="是否公开" prop="open" width="150">
        <template #default="{ row }">
          <el-switch
            v-model="row.open"
            :active-value="true"
            :inactive-value="false"
            :loading="row.statusLoading"
            @change="($event) => handleSwitchOpen($event, row)"
          />
        </template>
      </el-table-column>
      <el-table-column align="center" label="操作" width="300">
        <template #default="{ row }">
          <el-button text type="primary" @click="handleAddProduct(row, null)">新增</el-button>
          <el-button
            v-show="false"
            text
            type="primary"
            @click="handleEditCurrencyExchangeRate(row)"
          >
            汇率
          </el-button>
          <el-button text type="primary" @click="handleShare(row)">分享</el-button>
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
    <spu-edit ref="editRef" @fetch-data="fetchData" />
    <product-edit ref="productEditRef" @fetch-data="fetchData" />
    <spu-currency-exchange-rate-edit ref="currencyExchangeRateEditRef" @fetch-data="fetchData" />
    <spu-share-edit ref="spuShareEditRef" @fetch-data="fetchData" />
    <product-translate-dialog ref="productTranslateRef" @fetch-data="fetchData" />
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { doDelete as doDeleteProduct } from '/@/api/product'
import { doDelete, page, switchOpen } from '/@/api/spu'

defineOptions({
  name: 'Spu',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const currencyExchangeRateEditRef = ref<any>(null)
const productEditRef = ref<any>(null)
const tableRef = ref<any>(null)
const spuShareEditRef = ref<any>(null)
const productTranslateRef = ref<any>(null)
const fold = ref<boolean>(true)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
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

const handleAddProduct = (row: any, product: any) => {
  productEditRef.value.showEdit(product, row)
}

const handleProductTranslate = (row: any, product: any) => {
  const newProduct = { ...product }
  newProduct.id = undefined
  newProduct.language = undefined
  productEditRef.value.showEdit(newProduct, row)
}

const handleEditCurrencyExchangeRate = (row: any) => {
  currencyExchangeRateEditRef.value.showEdit(row)
}

const handleShare = (row: any) => {
  spuShareEditRef.value.showEdit(row)
}

const handleAITranslate = (spuRow: any, productRow: any) => {
  productTranslateRef.value.showEdit(spuRow, productRow)
}

const handleProductAITranslate = (spuRow: any, productRow: any) => {
  productTranslateRef.value.showEdit(spuRow, productRow, { ai: true })
}

const handleDelete = (row: any) => {
  if (row.id) {
    $baseConfirm('删除此SPU将会移除其下所有商品。确定继续吗？', null, async () => {
      const { msg }: any = await doDelete({ ids: row.id })
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  } else {
    if (selectRows.value.length > 0) {
      const ids = selectRows.value.map((item: { id: any }) => item.id).join(',')
      $baseConfirm('删除此SPU将会移除其下所有商品。确定继续吗？', null, async () => {
        const { msg }: any = await doDelete({ ids })
        $baseMessage(msg, 'success', 'hey')
        await fetchData()
      })
    } else {
      $baseMessage('您未选中任何行', 'warning', 'hey')
    }
  }
}

const handleDeleteProduct = (row: any) => {
  if (row && row.id) {
    $baseConfirm('您确定要删除当前项吗', null, async () => {
      const { msg }: any = await doDeleteProduct({ ids: row.id })
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  }
}
const handleSwitchOpen = (
  newVal: boolean | string | number,
  row: { id: number; open: boolean; statusLoading: boolean }
) => {
  row.statusLoading = true
  switchOpen({ id: row.id, open: row.open })
    .then(() => {
      row.statusLoading = false
    })
    .catch(() => {
      row.statusLoading = false
      row.open = newVal === true
    })
}
onActivated(() => {
  tableRef.value.doLayout()
})

onBeforeMount(() => {
  fetchData()
})
</script>
