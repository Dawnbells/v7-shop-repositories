<template>
  <div class="theme-template-container auto-height-container">
    <!-- 顶部筛选区域 -->
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="80px" :model="queryForm" @submit.prevent>
          <el-form-item label="模板名称">
            <el-input
              v-model="queryForm.name"
              clearable
              placeholder="请输入模板名称"
              @keyup.enter="queryData"
            />
          </el-form-item>
          <el-form-item label="共享类型">
            <el-select
              v-model="queryForm.shareType"
              clearable
              placeholder="全部类型"
              style="width: 140px"
            >
              <el-option label="私有" value="PRIVATE" />
              <el-option label="部门共享" value="DEPARTMENT" />
              <el-option label="公司共享" value="COMPANY" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button :icon="Search" :loading="listLoading" type="primary" @click="queryData">
              查询
            </el-button>
          </el-form-item>
        </el-form>
      </vab-query-form-top-panel>
      <vab-query-form-left-panel :span="24">
        <el-button :icon="Plus" type="primary" @click="handleAdd">新建模板</el-button>
        <el-button :icon="DocumentCopy" type="success" @click="handleCopyFrom">
          从模板复制
        </el-button>
      </vab-query-form-left-panel>
    </vab-query-form>

    <!-- 卡片列表 -->
    <div v-loading="listLoading" class="template-card-list">
      <el-empty v-if="list.length === 0 && !listLoading" description="暂无主题模板" />
      <div v-else class="card-grid">
        <div v-for="item in list" :key="item.id" class="template-card">
          <div class="card-cover">
            <img
              v-if="item.coverImage"
              :alt="item.name"
              :src="item.coverImage"
              @error="handleImageError"
            />
            <div v-else class="no-cover">
              <el-icon :size="40"><Picture /></el-icon>
              <span>暂无封面</span>
            </div>
            <!-- 共享类型标签 -->
            <div class="share-badge">
              <el-tag
                v-if="item.shareType === 'COMPANY'"
                effect="dark"
                size="small"
                type="danger"
              >
                公司
              </el-tag>
              <el-tag
                v-else-if="item.shareType === 'DEPARTMENT'"
                effect="dark"
                size="small"
                type="warning"
              >
                部门
              </el-tag>
              <el-tag v-else effect="dark" size="small" type="info">私有</el-tag>
            </div>
          </div>
          <div class="card-content">
            <div class="card-title" :title="item.name">{{ item.name }}</div>
            <div class="card-desc" :title="item.description">
              {{ item.description || '暂无描述' }}
            </div>
            <div class="card-meta">
              <span v-if="item.ownerName" class="owner">
                <el-icon><User /></el-icon>
                {{ item.ownerName }}
              </span>
              <span v-if="item.sharedFromName" class="source" :title="`复制自: ${item.sharedFromName}`">
                <el-icon><Link /></el-icon>
                {{ item.sharedFromName }}
              </span>
            </div>
          </div>
          <div class="card-actions">
            <el-button size="small" text type="primary" @click="handleEdit(item)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button size="small" text type="success" @click="handleDesign(item)">
              <el-icon><Brush /></el-icon>
              设计
            </el-button>
            <el-button size="small" text type="warning" @click="handleCopy(item)">
              <el-icon><DocumentCopy /></el-icon>
              复制
            </el-button>
            <el-button size="small" text type="danger" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <vab-pagination
      :current-page="queryForm.pageNo"
      :page-size="queryForm.pageSize"
      size="small"
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />

    <!-- 编辑弹窗 -->
    <theme-template-edit ref="editRef" @fetch-data="fetchData" />

    <!-- 从模板复制弹窗 -->
    <el-dialog v-model="copyDialogVisible" title="从模板复制" width="500px">
      <el-form ref="copyFormRef" label-width="100px" :model="copyForm" :rules="copyRules">
        <el-form-item label="选择模板" prop="sourceId">
          <el-select
            v-model="copyForm.sourceId"
            filterable
            placeholder="搜索并选择模板"
            remote
            :remote-method="remoteSearchTemplates"
            style="width: 100%"
          >
            <el-option
              v-for="tpl in templateOptions"
              :key="tpl.id"
              :label="`${tpl.name} (${tpl.shareTypeName || '私有'})`"
              :value="Number(tpl.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新模板名称" prop="name">
          <el-input v-model="copyForm.name" placeholder="请输入新模板名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button :loading="copyLoading" type="primary" @click="handleCopyConfirm">
          确定复制
        </el-button>
      </template>
    </el-dialog>

    <!-- 主题设计器弹窗 -->
    <vab-dialog
      v-model="themeEditorDialogVisible"
      append-to-body
      class="theme-editor-dialog"
      fullscreen
      :show-close="false"
      @close="handleThemeEditorClose"
    >
      <div v-if="themeEditorLoading" class="theme-editor-loading">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>加载主题编辑器...</span>
      </div>
      <iframe
        v-show="!themeEditorLoading"
        v-if="themeEditorDialogVisible && themeEditorUrl"
        :src="themeEditorUrl"
        class="theme-editor-iframe"
        @load="handleIframeLoad"
      />
    </vab-dialog>
  </div>
</template>

<script lang="ts" setup>
import {
  Brush,
  Delete,
  DocumentCopy,
  Edit,
  Link,
  Loading,
  Picture,
  Plus,
  Search,
  User,
} from '@element-plus/icons-vue'
import { copyFromTemplate, doDelete, page, remoteQuery } from '/@/api/themeTemplate'
import { getToken } from '/@/utils/token'

defineOptions({
  name: 'ThemeTemplate',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')

const editRef = ref<any>(null)
const copyFormRef = ref<any>(null)
const list = ref<any[]>([])
const listLoading = ref<boolean>(true)
const total = ref<number>(0)

const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 12,
  name: '',
  shareType: '',
})

// 复制模板相关
const copyDialogVisible = ref<boolean>(false)
const copyLoading = ref<boolean>(false)
const templateOptions = ref<any[]>([])
const copyForm = reactive<any>({
  sourceId: null,
  name: '',
})
const copyRules = {
  sourceId: [{ required: true, message: '请选择模板', trigger: 'change' }],
  name: [{ required: true, message: '请输入新模板名称', trigger: 'blur' }],
}

// 主题编辑器相关
const themeEditorDialogVisible = ref<boolean>(false)
const themeEditorLoading = ref<boolean>(true)
const themeEditorUrl = ref<string>('')
const currentTemplate = ref<any>(null)

const fetchData = async () => {
  listLoading.value = true
  try {
    const { data } = await page({
      ...queryForm,
      shareType: queryForm.shareType || undefined,
    })
    list.value = data.list || []
    total.value = data.total || 0
  } catch (error) {
    console.error('获取主题模板列表失败:', error)
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

const handleAdd = () => {
  editRef.value.showEdit()
}

const handleEdit = (row: any) => {
  editRef.value.showEdit(row)
}

const handleDelete = (row: any) => {
  $baseConfirm('您确定要删除该主题模板吗？', null, async () => {
    try {
      const { msg }: any = await doDelete({ ids: [row.id] })
      $baseMessage(msg || '删除成功', 'success', 'hey')
      await fetchData()
    } catch (error) {
      console.error('删除失败:', error)
    }
  })
}

const handleCopy = (row: any) => {
  copyForm.sourceId = Number(row.id)
  copyForm.name = `${row.name} - 副本`
  copyDialogVisible.value = true
}

const handleCopyFrom = () => {
  copyForm.sourceId = null
  copyForm.name = ''
  remoteSearchTemplates('')
  copyDialogVisible.value = true
}

const remoteSearchTemplates = async (query: string) => {
  try {
    const { data } = await remoteQuery(query)
    templateOptions.value = data.list || []
  } catch (error) {
    console.error('搜索模板失败:', error)
  }
}

const handleCopyConfirm = async () => {
  try {
    await copyFormRef.value.validate()
    copyLoading.value = true
    const { msg }: any = await copyFromTemplate({
      sourceId: copyForm.sourceId,
      name: copyForm.name,
    })
    $baseMessage(msg || '复制成功', 'success', 'hey')
    copyDialogVisible.value = false
    await fetchData()
  } catch (error: any) {
    if (error !== false) {
      console.error('复制失败:', error)
    }
  } finally {
    copyLoading.value = false
  }
}

// 主题设计器相关
const handleDesign = (row: any) => {
  currentTemplate.value = row
  themeEditorLoading.value = true
  // 构建 Builder URL，使用 TEMPLATE 模式
  const builderBaseUrl = import.meta.env.VITE_BUILDER_URL || 'http://localhost:3000'
  themeEditorUrl.value = `${builderBaseUrl}/builder`
  themeEditorDialogVisible.value = true
  window.addEventListener('message', handleThemeEditorMessage)
}

const handleIframeLoad = () => {
  setTimeout(() => {
    sendAuthToBuilder()
  }, 100)
}

const sendAuthToBuilder = () => {
  const iframe = document.querySelector('.theme-editor-iframe') as HTMLIFrameElement
  if (iframe?.contentWindow) {
    const token = getToken()
    iframe.contentWindow.postMessage(
      {
        type: 'BUILDER_INIT',
        payload: {
          token: token,
          imageBaseUrl: import.meta.env.VITE_IMAGE_BASE_URL || '',
          apiBaseUrl: import.meta.env.VITE_API_BASE_URL || window.location.origin,
          mode: 'TEMPLATE',
          templateId: currentTemplate.value?.id,
          contextName: currentTemplate.value?.name || '主题模板',
        },
      },
      '*'
    )
    console.log('[Admin] 已发送认证信息给 builder (TEMPLATE 模式)')
  }
}

const handleThemeEditorMessage = (event: MessageEvent) => {
  if (event.data?.type === 'BUILDER_READY') {
    console.log('[Admin] 收到 BUILDER_READY，发送认证信息')
    sendAuthToBuilder()
    return
  }
  if (event.data?.type === 'BUILDER_AUTHENTICATED') {
    console.log('[Admin] 收到 BUILDER_AUTHENTICATED，关闭 loading')
    themeEditorLoading.value = false
    return
  }
  if (event.data?.type === 'themeEditor') {
    if (event.data.action === 'close') {
      themeEditorDialogVisible.value = false
      window.removeEventListener('message', handleThemeEditorMessage)
      fetchData()
    }
    if (event.data.action === 'authFailed') {
      themeEditorDialogVisible.value = false
      window.removeEventListener('message', handleThemeEditorMessage)
      $baseMessage(event.data.message || '认证失败，请重试', 'error', 'hey')
    }
  }
}

const handleThemeEditorClose = () => {
  window.removeEventListener('message', handleThemeEditorMessage)
  themeEditorDialogVisible.value = false
}

const handleImageError = (e: Event) => {
  const target = e.target as HTMLImageElement
  target.style.display = 'none'
}

onBeforeMount(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.theme-template-container {
  padding: 16px;

  .template-card-list {
    min-height: 400px;
    margin-bottom: 16px;
  }

  .card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 20px;
  }

  .template-card {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
    overflow: hidden;
    transition: all 0.3s;

    &:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      transform: translateY(-2px);
    }

    .card-cover {
      height: 160px;
      position: relative;
      background: var(--el-fill-color-light);
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .no-cover {
        width: 100%;
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        color: var(--el-text-color-placeholder);

        span {
          margin-top: 8px;
          font-size: 12px;
        }
      }

      .share-badge {
        position: absolute;
        top: 8px;
        right: 8px;
      }
    }

    .card-content {
      padding: 12px 16px;

      .card-title {
        font-size: 16px;
        font-weight: 600;
        color: var(--el-text-color-primary);
        margin-bottom: 8px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .card-desc {
        font-size: 13px;
        color: var(--el-text-color-secondary);
        margin-bottom: 10px;
        height: 40px;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
      }

      .card-meta {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        font-size: 12px;
        color: var(--el-text-color-regular);

        .owner,
        .source {
          display: flex;
          align-items: center;
          gap: 4px;
          max-width: 120px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }

    .card-actions {
      display: flex;
      justify-content: space-around;
      padding: 10px 8px;
      border-top: 1px solid var(--el-border-color-lighter);
      background: var(--el-fill-color-blank);

      .el-button {
        padding: 4px 8px;
      }
    }
  }
}

.theme-editor-iframe {
  width: 100% !important;
  height: calc(100vh - 3px) !important;
  border: none;
}

.theme-editor-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: #909399;
  font-size: 14px;
  gap: 12px;
  background-color: #1e293b;

  .loading-icon {
    font-size: 32px;
    color: #3b82f6;
    animation: rotate 1s linear infinite;
  }

  span {
    color: #94a3b8;
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
<style lang="scss">
.theme-editor-dialog {
  .el-dialog__header {
    display: none !important;
  }
  .el-dialog__body {
    padding: 0 !important;
    margin: 0 !important;
    width: 100% !important;
    height: 100vh !important;
  }
  .el-dialog__footer {
    display: none !important;
  }
}
</style>
