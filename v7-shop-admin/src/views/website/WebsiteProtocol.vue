<template>
  <div class="website-protocol-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline :model="queryForm" @submit.prevent>
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
        <el-button :icon="Plus" type="primary" @click="handleAdd">添加分组</el-button>
        <el-button :icon="Delete" type="danger" @click="handleDelete">删除</el-button>
      </vab-query-form-left-panel>
    </vab-query-form>

    <el-tabs
      v-model="activeLanguage"
      class="demo-tabs"
      tab-position="left"
      @tab-click="handleClick"
    >
      <el-tab-pane v-for="item in languageList" :key="item.id" :label="item.cname" :name="item.id">
        <el-table
          :key="item.id"
          :ref="`tableRef-${item.id}`"
          v-loading="listLoading"
          border
          :data="list"
          @selection-change="setSelectRows"
        >
          <el-table-column type="selection" width="38" />
          <el-table-column align="center" label="名字" prop="name" />
          <el-table-column align="center" label="排序" prop="sort" />
          <el-table-column label="协议" type="expand" width="80">
            <template #default="props">
              <el-table border :data="props.row.articleList">
                <el-table-column align="center" label="协议ID" prop="id" show-overflow-tooltip />
                <el-table-column align="center" label="协议标题" prop="title" />
                <el-table-column align="center" label="协议说明" prop="description" />
                <el-table-column align="center" label="操作" width="250">
                  <template #default="{ row }">
                    <el-button text type="danger" @click="handleUnbindArticle(props.row, row)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </el-table-column>
          <el-table-column align="center" label="操作" width="250">
            <template #default="{ row }">
              <el-button text type="primary" @click="handleAddProtocol(row)">添加协议</el-button>
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
          :total="total"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </el-tab-pane>
    </el-tabs>
    <website-protocol-edit ref="editRef" @fetch-data="fetchData" />
    <website-protocol-article-choose ref="articleChooseRef" @fetch-data="fetchData" />
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import type { TableInstance, TabsPaneContext } from 'element-plus'
import { useUserStore } from '~/src/store/modules/user'
import { doDelete, doUnbind, getList } from '../../api/websiteProtocol'

defineOptions({
  name: 'WebsiteProtocol',
})
const userStore = useUserStore()
const editRef = ref<any>(null)
const articleChooseRef = ref<any>(null)
const tableRef = ref<Record<string, TableInstance>>({})
const fold = ref<boolean>(true)
const list = ref<any>([])
const listLoading = ref<boolean>(true)
const total = ref<number>(0)
const selectRows = ref<any>([])
const website = computed(() => userStore.getWebsite)
const languageList = computed(() => website.value?.languages)
const activeLanguage = ref<string | undefined>(languageList.value?.[0]?.id || undefined)

const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
})

const handleClick = (tab: TabsPaneContext, event: Event) => {
  console.log(tab, event)
  fetchData()
}

const fetchData = async () => {
  listLoading.value = true
  const { data } = await getList({ ...queryForm, languageId: activeLanguage.value })
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
  editRef.value.showEdit(undefined, activeLanguage.value)
}

const handleEdit = (row = {}) => {
  editRef.value.showEdit(row, activeLanguage.value)
}

const handleAddProtocol = (row = {}) => {
  articleChooseRef.value.showEdit(row, activeLanguage.value)
}

const handleUnbindArticle = (row: any, article: any) => {
  console.log('unbind article', row, article)
  if (row.id && article.id) {
    $baseConfirm('您确定要删除当前项吗', null, async () => {
      const { msg }: any = await doUnbind(row.id, article.id)
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  }
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
  if (activeLanguage.value) {
    tableRef.value[activeLanguage.value]?.doLayout()
  }
  console.log('onActivated')
})

onBeforeMount(() => {
  fetchData()
})
</script>
