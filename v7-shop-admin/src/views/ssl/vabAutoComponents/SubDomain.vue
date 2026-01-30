<template>
  <div class="sub-domain-container">
    <el-table
      ref="tableRef"
      v-loading="listLoading"
      border
      :data="list"
      @selection-change="setSelectRows"
    >
      <el-table-column align="center" label="名称" prop="name" />
      <el-table-column align="center" label="国家/商城">
        <template #default="{ row }">
          <span v-if="row.country && row.country.name">{{ row.country.name }}</span>
          <el-link
            v-else-if="row.website && row.website.name"
            type="primary"
            @click="goWebsiteManager(row)"
          >
            {{ row.website.name }}
          </el-link>
          <span v-else>无</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="语言">
        <template #default="{ row }">
          <span v-if="row.country && row.country.languages && row.country.languages.length > 0">
            {{ row.country.languages.map((l: any) => l.cname).join(', ') }}
          </span>
          <span
            v-else-if="row.website && row.website.languages && row.website.languages.length > 0"
          >
            {{ row.website.languages.map((l: any) => l.cname).join(', ') }}
          </span>
          <span v-else>无</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="货币">
        <template #default="{ row }">
          <span v-if="row.country && row.country.currency && row.country.currency.name">
            {{ row.country.currency.name }}
          </span>
          <span v-else-if="row.website && row.website.currency && row.website.currency.name">
            {{ row.website.currency.name }}
          </span>
          <span v-else>无</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="跳转域名">
        <template #default="{ row }">
          <span v-if="row.redirectDomain && row.redirectDomain.name">
            {{ row.redirectDomain.name }}
          </span>
          <span v-else>无</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="主题">
        <template #default="{ row }">
          <span v-if="row.theme && row.theme.name">{{ row.theme.name }}</span>
          <span v-else>无</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="操作" width="360">
        <template #default="{ row }">
          <el-button class="option-button" text type="primary" @click="handleProduct(row)">
            站点配置
          </el-button>
          <el-button class="option-button" text type="warning" @click="handleTheme(row)">
            主题
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
      small
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
    <sub-domain-theme-select ref="themeSelectRef" @fetch-data="fetchData" />
    <bind-domain-pixel-edit ref="bindDomainPixelRef" @fetch-data="fetchData" />
    <bind-sub-domain-product ref="bindSubDomainProductRef" @fetch-data="fetchData" />
  </div>
</template>

<script lang="ts" setup>
import { getTicket } from '~/src/api/user'
import BindSubDomainProduct from './BindSubDomainProduct.vue'
import SubDomainThemeSelect from './SubDomainThemeSelect.vue'
import { doDelete, page } from '/@/api/subDomain'

defineOptions({
  name: 'SubDomain',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const tableRef = ref<any>(null)
const themeSelectRef = ref<any>(null)
const bindDomainPixelRef = ref<any>(null)
const bindSubDomainProductRef = ref<any>(null)
const fullDomain = ref<any>(null)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const props = defineProps({
  topLevelDomainId: {
    type: Number,
    default: 1,
  },
})
const queryForm = reactive<any>({
  parentId: props.topLevelDomainId,
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

const setSelectRows = (value: string) => {
  selectRows.value = value
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

const handleTheme = (row: any) => {
  themeSelectRef.value.showEdit(row)
}

const handleBindPixel = (row: any) => {
  bindDomainPixelRef.value.showEdit(row, 'sub')
}

const handleProduct = (row: any) => {
  bindSubDomainProductRef.value.showEdit(row)
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
const goWebsiteManager = async (row: { website: { id: string } }) => {
  const res = await getTicket()
  const url = `http://admin${row.website.id}.${fullDomain.value}/#/?ticket=${encodeURIComponent(res.data.ticket)}`
  window.open(url, '_blank')
}
onBeforeMount(() => {
  const fullHost = globalThis.location.host // 获取完整主机名，包括端口号
  const [domainPart, portPart] = fullHost.split(':') // 分离域名和端口

  const domainParts = domainPart.split('.') // 拆分域名
  const lastTwoParts = domainParts.slice(-2) // 取出最后两部分
  const primaryDomain = lastTwoParts.join('.') // 组合一级域名

  fullDomain.value = portPart ? `${primaryDomain}:${portPart}` : primaryDomain // 组合一级域名和端口
  fetchData()
})
</script>

<style scoped>
.sub-domain-container {
  width: 100%;
}
</style>
