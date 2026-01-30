<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :before-close="beforeClose"
    :title="title"
    width="80%"
  >
    <vab-query-form>
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
          border
          :data="list"
          :expand-row-keys="expandRowKeys"
          row-key="id"
          style="min-height: calc(var(--el-container-height) - 250px)"
          @expand-change="handleExpandChange"
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
                  <template #default="{ row, $index }">
                    <el-button
                      text
                      type="danger"
                      @click="handleUnbindArticle(props.row, row, $index)"
                    >
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
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button :loading="isSaving" type="primary" @click="save">保存</el-button>
    </template>
    <protocol-group-edit ref="editRef" @on-edit-data="onEditProtocolGroupData" />
    <website-protocol-article-choose
      ref="articleChooseRef"
      @on-edit-data="onAddProtocolArticleData"
    />
  </vab-dialog>
</template>

<script lang="ts" setup>
import { Delete, Plus } from '@element-plus/icons-vue'
import type { TableInstance, TabsPaneContext } from 'element-plus'
import { editProtocolTranslation } from '~/src/api/protocol'

defineOptions({
  name: 'ProtocolTranslationEdit',
})
const editRef = ref<any>(null)
const dialogFormVisible = ref<boolean>(false)
const isDirty = ref<boolean>(false)
const isSaving = ref<boolean>(false)
const articleChooseRef = ref<any>(null)
const tableRef = ref<Record<string, TableInstance>>({})
const title = ref<string>('')
const list = ref<any>([])
const selectRows = ref<any>([])
const languageList = ref<any>([])
const expandRowKeys = ref<any>([])
const activeLanguage = ref<string>('')
const protocol = ref<any>({})
const currentTranslation = ref<any>({})

const handleExpandChange = (row: any, expandedRows: any) => {
  console.log('handleExpandChange', row, expandedRows)
  if (expandedRows.findIndex((item: any) => item.id === row.id) >= 0) {
    expandRowKeys.value = [row.id]
  } else {
    expandRowKeys.value = []
  }
}

const handleClick = (tab: TabsPaneContext) => {
  const languageId = tab.props.name
  console.log(languageId, activeLanguage.value, protocol.value)
  if (isDirty.value) {
    $baseConfirm(
      '内容已变动，是否保存更改？',
      null,
      async () => {
        console.log('保存')
        save()
        const translation = protocol.value.translations.find(
          (translation: any) => translation.language.id === languageId
        )
        currentTranslation.value = translation
        list.value = translation.articleGroupList || []
        isDirty.value = false
      },
      async () => {
        console.log('丢弃')
        const translation = protocol.value.translations.find(
          (translation: any) => translation.language.id === languageId
        )
        currentTranslation.value = translation
        list.value = translation.articleGroupList || []
        isDirty.value = false
      },
      '保存',
      '丢弃'
    )
  } else {
    const translation = protocol.value.translations.find(
      (translation: any) => translation.language.id === languageId
    )
    currentTranslation.value = translation
    list.value = translation.articleGroupList || []
    isDirty.value = false
  }
}
const showEdit = (row: any) => {
  protocol.value = row
  title.value = '编辑协议分组'
  dialogFormVisible.value = true
  languageList.value = row.languages.map((lang: any) => ({
    ...lang,
    id: String(lang.id),
  }))
  activeLanguage.value = languageList.value[0].id
  const translation = protocol.value.translations.find(
    (translation: any) => translation.language.id === activeLanguage.value
  )
  currentTranslation.value = translation
  list.value = translation.articleGroupList || []
  isDirty.value = false
  console.log('showEdit', activeLanguage, list.value, currentTranslation.value, protocol.value)
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

const onEditProtocolGroupData = (data: any) => {
  data.articleList = data.articleList || []
  isDirty.value = true
  console.log('onEditProtocolGroupData', data, list.value)
  if (data.id) {
    const index = list.value.findIndex((obj: any) => obj.id === data.id) // 查找 id 为 a 的对象索引
    if (index !== -1) {
      list.value[index] = { ...list.value[index], ...data } // 更新对象的信息
    }
  } else {
    data.id = (Math.random() * 900000000 + 100000000).toFixed(0)
    list.value = [...list.value, data]
  }
}

const onAddProtocolArticleData = (data: any) => {
  console.log('onAddProtocolArticleData', data, list.value)
  isDirty.value = true
  list.value.forEach((element: any) => {
    if (element.id === data.protocolGroupId) {
      element.articleList = [...element.articleList, data.article]
    }
  })
}

const handleUnbindArticle = (row: any, article: any, index: number) => {
  console.log('unbind article', row, article)
  if (row.id && article.id) {
    $baseConfirm('您确定要删除当前项吗', null, async () => {
      isDirty.value = true
      row.articleList.splice(index, 1)
    })
  }
}

const handleDelete = (row: any) => {
  if (row.id) {
    $baseConfirm('您确定要删除当前项吗', null, async () => {
      isDirty.value = true
      list.value = list.value.filter((item: { id: any }) => item.id !== row.id)
    })
  } else {
    if (selectRows.value.length > 0) {
      const ids = new Set(selectRows.value.map((item: { id: any }) => item.id))
      $baseConfirm('您确定要删除选中项吗', null, async () => {
        isDirty.value = true
        list.value = list.value.filter((item: { id: any }) => !ids.has(item.id))
      })
    } else {
      $baseMessage('您未选中任何行', 'warning', 'hey')
    }
  }
}

const save = () => {
  isSaving.value = true
  editProtocolTranslation({
    id: currentTranslation.value.id,
    articleGroupList: list.value.map((group: any) => {
      return {
        id: group.id < 10000000000 ? undefined : group.id,
        name: group.name,
        sort: group.sort,
        languageId: group.languageId,
        articleList: group.articleList.map((article: { id: string }) => article.id),
      }
    }),
  })
    .then(async (res: any) => {
      // 搜索protocol中的当前translation，更新articleGroupList
      if (protocol.value) {
        const translation = protocol.value.translations.find(
          (t: any) => t.id === currentTranslation.value.id
        )
        console.log('change translation', protocol.value, currentTranslation.value, translation)
        if (translation) {
          translation.articleGroupList = [...list.value]
        }
      }
      isDirty.value = false
      isSaving.value = false
      await $baseMessage(res.msg, 'success', 'hey')
    })
    .catch(() => {
      isSaving.value = false
    })
}

onActivated(() => {
  if (activeLanguage.value) {
    tableRef.value[activeLanguage.value]?.doLayout()
  }
  console.log('onActivated')
})

defineExpose({
  showEdit,
})

const beforeClose = (done: () => void) => {
  if (isDirty.value) {
    $baseConfirm(
      '内容已变动，是否保存更改？',
      null,
      async () => {
        save()
        done()
      },
      async () => {
        done()
      },
      '保存',
      '丢弃'
    )
  } else {
    done()
  }
}
</script>
