<template>
  <div
    class="explorer-container no-background-container auto-height-container context-menu-container"
  >
    <el-row :gutter="20">
      <el-col :lg="4" :md="24" :sm="24" :xl="4" :xs="24">
        <vab-card class="auto-height-card">
          <el-input
            v-model="filterText"
            placeholder="请输入查询条件"
            style="margin-top: 10px; margin-bottom: 10px"
          />
          <el-tree
            ref="treeRef"
            :data="treeData"
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            :highlight-current="true"
            :loading="treeLoading"
            :props="defaultProps"
            style="overflow-y: auto"
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <div style="width: 100%" @contextmenu="onContextMenu($event, data)">
                <el-icon :color="calcColor(data)">
                  <folder />
                </el-icon>
                <span
                  :style="{ color: data.sensitive ? '#008077' : '#a8abb2', 'margin-left': '3px' }"
                >
                  {{ data.name }}
                </span>
              </div>
            </template>
          </el-tree>
        </vab-card>
      </el-col>
      <el-col :lg="20" :md="24" :sm="24" :xl="20" :xs="24">
        <vab-card class="auto-height-card">
          <vab-query-form>
            <vab-query-form-top-panel>
              <el-form inline label-width="49px" :model="queryForm" @submit.prevent>
                <el-form-item label="标题">
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
                </el-form-item>
              </el-form>
            </vab-query-form-top-panel>
            <vab-query-form-left-panel :span="24">
              <el-upload
                ref="uploadRef"
                accept="image/png,image/jpeg,image/gif,image/webp"
                :before-upload="beforeUpload"
                :http-request="customUpload"
                multiple
                :show-file-list="false"
                style="margin: 0 10px 10px 0"
              >
                <el-button :icon="Upload" type="primary">本地上传</el-button>
              </el-upload>
              <el-button type="warning" @click="clearSelection">清空选中</el-button>
              <el-button :icon="Delete" type="danger" @click="handleDelete">删除</el-button>
            </vab-query-form-left-panel>
          </vab-query-form>
          <el-row v-loading="listLoading" class="explorer-image-box" :gutter="20">
            <el-col
              v-for="(item, index) in list"
              :key="index"
              :lg="6"
              :md="8"
              :sm="12"
              :xl="6"
              :xs="12"
            >
              <vab-card :body-style="{ padding: '0', position: 'relative' }">
                <!-- 按钮显示选中的顺序 -->
                <div
                  v-if="item.selectedIndex"
                  style="position: absolute; top: 10px; right: 10px; z-index: 100"
                >
                  <el-button circle size="small" type="success">{{ item.selectedIndex }}</el-button>
                </div>

                <!-- 图片点击事件 -->
                <el-image
                  fit="contain"
                  :src="getImageUrl(item)"
                  style="position: relative; cursor: pointer"
                  @click="toggleSelection(item)"
                />
              </vab-card>
            </el-col>

            <el-col v-if="list.length === 0" :span="24">
              <el-empty class="vab-data-empty" description="暂无数据" />
            </el-col>
          </el-row>

          <vab-pagination
            :current-page="queryForm.pageNo"
            :page-size="queryForm.pageSize"
            size="small"
            :total="total"
            @current-change="handleCurrentChange"
            @size-change="handleSizeChange"
          />
        </vab-card>
      </el-col>
    </el-row>
    <vab-context-menu v-model:show="show" :options="options">
      <vab-context-menu-item :click-close="false" label="新建" @click="addChildDirectory" />
      <vab-context-menu-item :click-close="false" label="重命名" @click="renameFolder" />
      <vab-context-menu-item :click-close="false" label="删除" @click="deleteDirectory" />
    </vab-context-menu>
  </div>
  <el-dialog
    v-model="dialogFormVisible"
    :close-on-click-modal="false"
    modal
    :title="title"
    width="500"
  >
    <el-form ref="formRef" :model="form" :rules="rules">
      <el-form-item label="文件夹名称">
        <el-input v-model="form.name" autocomplete="off" />
      </el-form-item>
      <el-form-item v-if="false" label="敏感文件夹">
        <el-switch v-model="form.isSensitive" :disabled="form.isDisabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogFormVisible = false">取消</el-button>
        <el-button type="primary" @click="mkdirFolder">确认</el-button>
      </div>
    </template>
  </el-dialog>
  <el-dialog
    v-model="uploadingDialogVisible"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :title="`上传进度 (${doneCount}/${tasks.length})${
      failCount ? ` · 失败 ${failCount}` : ''
    }`"
    width="500"
    @closed="handleClose"
  >
    <div ref="scrollContainer" style="max-height: 300px; overflow-y: auto">
      <div
        v-for="task in tasks"
        :key="task.uid"
        style="display: flex; flex-direction: column; margin-bottom: 15px"
      >
        <div
          style="
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 5px;
            gap: 8px;
          "
        >
          <span
            :title="task.name"
            style="
              flex: 1;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            "
          >
            {{ task.name }}
          </span>
          <el-button
            v-if="task.status === 'uploading' || task.status === 'pending'"
            link
            size="small"
            type="warning"
            @click="cancelTask(task)"
          >
            取消
          </el-button>
          <span
            v-else-if="task.status === 'fail'"
            style="color: var(--el-color-danger); font-size: 12px"
          >
            {{ task.error || '上传失败' }}
          </span>
          <span
            v-else-if="task.status === 'cancel'"
            style="color: var(--el-color-warning); font-size: 12px"
          >
            已取消
          </span>
        </div>
        <el-progress
          :percentage="task.percent"
          :status="
            task.status === 'fail' || task.status === 'cancel'
              ? 'exception'
              : task.status === 'success'
                ? 'success'
                : undefined
          "
        />
      </div>
    </div>
    <template #footer>
      <el-button v-if="isUploading" @click="handleClose">全部取消并关闭</el-button>
      <el-button :loading="isUploading" type="primary" @click="handleChoose">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { Delete, Folder, Search, Upload } from '@element-plus/icons-vue'
import type { UploadRawFile, UploadRequestOptions } from 'element-plus'
import { ElTree, ElUpload } from 'element-plus'
import { useDebounceFn } from '@vueuse/core'
import {
  deleteFolderApi,
  doDelete,
  folderTree,
  mkdirFolderApi,
  page,
  renameFolderApi,
  uploadFile,
} from '~/src/api/explorer'
import { VabContextMenu, VabContextMenuItem } from '/@/plugins/VabContextMenu'
import { useUserStore } from '/@/store/modules/user'

defineOptions({
  name: 'Explorer',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const userStore = useUserStore()
const list = ref<any>([])
const formRef = ref<any>(null)
const treeData = ref<any>([])
const listLoading = ref<boolean>(true)
const treeLoading = ref<boolean>(false)
const uploadingDialogVisible = ref<boolean>(false)
const dialogFormVisible = ref(false)
const show = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const renameOp = ref<boolean>(false)
const total = ref<any>(0)
const workspaceFolderId = ref<any>(undefined)
const title = ref<string>('新建文件夹')

// 上传任务模型：每个文件一条记录，状态完全独立、无隐藏耦合
type UploadTaskStatus = 'pending' | 'uploading' | 'success' | 'fail' | 'cancel'
interface UploadTask {
  uid: string | number
  name: string
  size: number
  percent: number
  status: UploadTaskStatus
  response?: any
  error?: string
  abort?: AbortController
}
const tasks = ref<UploadTask[]>([])
// 单文件大小上限（图片 20MB，按需调整）
const MAX_FILE_SIZE = 20 * 1024 * 1024
const root = ref<any>([
  {
    id: '10000',
    compactId: '10000',
    name: '所有文件',
    sensitive: false,
  },
  {
    id: '10001',
    compactId: '10001',
    name: '根目录',
    sensitive: false,
  },
])
const selectRows = ref<any>([])
const form = ref<any>({
  name: '',
  isSensitive: false,
  id: undefined,
})
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 50,
})
const filterText = ref<string>('')
const treeRef = ref<InstanceType<typeof ElTree>>()
const previewSrcList = ref<any>([])
const options = reactive<any>({ minWidth: 200, x: 500, y: 200 })
const context = ref<any>()
const currentNode = ref<string>('10000')
const uploadRef = ref<InstanceType<typeof ElUpload>>()
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '文件夹名称不能为空' }],
})
const scrollContainer = ref<HTMLElement>()

// 上传完成度派生量：均由 tasks 推导，避免任何手动计数错位
const isUploading = computed(() =>
  tasks.value.some((t) => t.status === 'uploading' || t.status === 'pending')
)
const doneCount = computed(
  () => tasks.value.filter((t) => t.status !== 'uploading' && t.status !== 'pending').length
)
const successCount = computed(() => tasks.value.filter((t) => t.status === 'success').length)
const failCount = computed(() => tasks.value.filter((t) => t.status === 'fail').length)

const onContextMenu = (e: MouseEvent, c: any) => {
  e.preventDefault()
  options.x = e.x
  options.y = e.y
  context.value = c
  show.value = true
}

const inputMkdirFolderInfo = async (parentFolder: any, rename: boolean) => {
  title.value = rename ? '重命名' : '新建文件夹'
  renameOp.value = rename
  form.value = {
    name: '',
    isSensitive: parentFolder == null ? null : parentFolder.sensitive,
    id: parentFolder == null ? null : parentFolder.id,
    isDisabled: parentFolder && parentFolder.sensitive,
  }
  dialogFormVisible.value = true
}

const calcColor = (data: any) => {
  return data.sensitive ? '#008077' : '#a8abb2'
}

const getImageUrl = (item: any): string => {
  if (!item) return ''
  const baseUrl = userStore.getImageBaseUrl
  if (item.relativePath && baseUrl && baseUrl.trim() !== '') {
    // 确保 baseUrl 不以 / 结尾，relativePath 不以 / 开头
    const cleanBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl
    const cleanRelativePath = item.relativePath.startsWith('/')
      ? item.relativePath
      : `/${item.relativePath}`
    return `${cleanBaseUrl}${cleanRelativePath}`
  }
  // 如果没有 relativePath 或 imageBaseUrl，回退到 absolutionPath
  return item.absolutionPath || ''
}

const addChildDirectory = () => {
  inputMkdirFolderInfo(context.value, false)
  show.value = false
}

const renameFolder = async () => {
  inputMkdirFolderInfo(context.value, true)
  show.value = false
}

const mkdirFolder = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        let res: any
        if (renameOp.value) {
          res = await renameFolderApi(form.value)
        } else {
          res = await mkdirFolderApi(form.value)
        }
        await $baseMessage(res.msg, 'success', 'hey')
        dialogFormVisible.value = false
        fetchTreeFolder()
      } finally {
        saveLoading.value = false
      }
    }
  })
}

// 使用防抖避免频繁过滤导致卡顿
const debouncedFilter = useDebounceFn((value: string) => {
  treeRef.value?.filter(value)
}, 300)

watch(filterText, (value) => {
  debouncedFilter(value)
})

const filterNode: any = (value: string, data: any) => {
  if (!value) return true
  return data.name.includes(value)
}

const handleNodeClick = (node: any) => {
  currentNode.value = node.compactId
  fetchData(node.compactId)
}

const defaultProps = {
  children: 'children',
  label: 'name',
}

const fetchTreeFolder = async () => {
  treeLoading.value = true
  const tree = await folderTree()
  treeData.value = [...root.value, ...tree.data.list]
  treeLoading.value = false
}

// 滚动条吸底，便于看到最新上传项
const scrollToBottom = async () => {
  await nextTick()
  if (scrollContainer.value) {
    scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
  }
}

// 计算当前应使用的目标文件夹 id（与原逻辑保持一致：未选或选了"所有文件"时落到 root）
const resolveTargetFolderId = (): string => {
  const id = workspaceFolderId.value
  if (!id || id === 'all' || id === '10000') return 'root'
  return String(id)
}

/**
 * 自定义上传函数：取代 el-upload 内置 XHR
 * - 每个文件单独走一次 axios 请求；状态、进度、错误、取消互不影响
 * - 复用全局拦截器（token 注入、业务码处理、错误 toast）
 * - 不再依赖任何"计数器齐平"判定
 */
const customUpload = async (options: UploadRequestOptions) => {
  const rawFile = options.file as UploadRawFile
  const abort = new AbortController()
  const uid = rawFile.uid ?? `${Date.now()}-${Math.random()}`

  // 注意 Vue 3 响应式陷阱：把原始对象 push 进 ref 数组后，数组里存的是 reactive proxy，
  // 必须通过 proxy 修改属性才会触发依赖更新；同时 abort 用 markRaw 排除响应式代理，
  // 否则 AbortController 方法被 Proxy 包裹后会抛 Illegal invocation。
  tasks.value.push({
    uid,
    name: rawFile.name,
    size: rawFile.size,
    percent: 0,
    status: 'pending',
    abort: markRaw(abort),
  })
  const task = tasks.value.find((t) => t.uid === uid)!
  uploadingDialogVisible.value = true
  scrollToBottom()

  try {
    task.status = 'uploading'
    const data = await uploadFile(resolveTargetFolderId(), rawFile, {
      signal: abort.signal,
      onProgress: (percent) => {
        task.percent = percent
      },
    })
    task.percent = 100
    task.status = 'success'
    task.response = data
    return data
  } catch (err: any) {
    if (abort.signal.aborted) {
      task.status = 'cancel'
      task.error = '已取消'
    } else {
      task.status = 'fail'
      task.error = err?.msg || err?.message || '上传失败'
    }
    return Promise.reject(err)
  } finally {
    if (!isUploading.value) {
      await handleAllUploadFinished()
    }
  }
}

// 所有上传任务都已结束（成功/失败/取消）后的统一收尾
const handleAllUploadFinished = async () => {
  await fetchData()
  clearSelection()
  // 仅对成功项做"自动选中新上传内容"
  tasks.value
    .filter((t) => t.status === 'success')
    .forEach((t) => {
      const resp = t.response
      const list0 = resp?.data?.list
      if (Array.isArray(list0) && list0.length > 0) {
        const newItem = list0[0]
        const matched = list.value.find((it: any) => it.id === newItem.id)
        if (matched) toggleSelection(matched)
      }
    })
}

const fetchData = async (id?: any) => {
  selectRows.value = []
  listLoading.value = true
  workspaceFolderId.value =
    typeof id === 'string' ? id : currentNode.value ? currentNode.value : 'all'
  queryForm.folderId = workspaceFolderId.value
  const { data } = await page(queryForm)
  list.value = data.list
  total.value = data.total
  previewSrcList.value = data.list.map((item: any) => getImageUrl(item))
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

// 切换选中状态
const toggleSelection = (item: any) => {
  if (item.selectedIndex) {
    // 如果图片已被选中，则取消选中并重新排列选中顺序
    const index = selectRows.value.indexOf(item)
    selectRows.value.splice(index, 1)
    item.selectedIndex = null

    // 更新剩余选中的图片的编号
    selectRows.value.forEach((img: any, i: any) => {
      img.selectedIndex = i + 1
    })
  } else {
    // 如果图片未被选中，则选中图片并设置选中的顺序编号
    selectRows.value.push(item)
    item.selectedIndex = selectRows.value.length
  }
}
// 清空选中状态
const clearSelection = () => {
  // 清空选中数组，并重置所有图片的 selectedIndex
  selectRows.value.forEach((item: any) => {
    if (item) {
      item.selectedIndex = null
    }
  })
  selectRows.value = []
}

const deleteDirectory = () => {
  if (context.value) {
    $baseConfirm(`您确定要删除文件夹【${context.value.name}】吗`, null, async () => {
      const { msg }: any = await deleteFolderApi({ ids: context.value.id })
      $baseMessage(msg, 'success', 'hey')
      await fetchTreeFolder()
      await fetchData()
    })
  } else {
    $baseMessage('您未选中文件夹', 'warning', 'hey')
  }
  show.value = false
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
const reset = () => {
  list.value = []
  fetchData()
}
const getSelectedFile = () => {
  const selected = selectRows.value.map((item: any) => {
    return {
      id: item.id,
      absolutionPath: item.absolutionPath,
      compactId: item.compactId,
      departmentName: item.departmentName,
      mediaType: item.mediaType,
      name: item.name,
      ownerName: item.ownerName,
      relativePath: item.relativePath,
    }
  })
  if (selected && selected.length > 0) {
    return selected
  } else {
    $baseMessage('您未选中任何资源', 'warning', 'hey')
    return []
  }
}

defineExpose({
  getSelectedFile,
  reset,
})

// 客户端校验：阻止超大文件 / 非允许类型在网络层浪费时间
const beforeUpload = (file: UploadRawFile) => {
  if (file.size > MAX_FILE_SIZE) {
    $baseMessage(
      `《${file.name}》超过 ${MAX_FILE_SIZE / 1024 / 1024}MB，已被忽略`,
      'warning',
      'hey'
    )
    return false
  }
  return true
}

// 主动取消单个上传任务（运营点"取消"按钮时调用）
const cancelTask = (task: UploadTask) => {
  if (task.status === 'uploading' || task.status === 'pending') {
    task.abort?.abort()
  }
}

const emit = defineEmits(['choose'])
const handleClose = () => {
  // 关闭对话框时，先取消尚未完成的任务，避免后台请求继续占用资源
  tasks.value.forEach((t) => {
    if (t.status === 'uploading' || t.status === 'pending') {
      t.abort?.abort()
    }
  })
  uploadRef.value?.clearFiles()
  tasks.value = []
  uploadingDialogVisible.value = false
}

const handleChoose = () => {
  handleClose()
  emit('choose', selectRows.value)
}

onBeforeMount(() => {
  fetchTreeFolder()
  fetchData()
})
</script>

<style lang="scss">
.mx-menu-ghost-host {
  z-index: 99999999 !important;
}
</style>

<style lang="scss" scoped>
.explorer-container {
  // 左侧文件夹树区域高度限制
  > :deep(.el-row) > .el-col:first-child {
    .el-card__body {
      display: flex;
      flex-direction: column;
      max-height: calc(var(--el-container-height) - 2px);

      > .el-input {
        flex-shrink: 0;
      }

      > .el-tree {
        flex: 1;
        overflow-y: auto;
        min-height: 0;
      }
    }
  }

  .explorer-image-box {
    flex: 1;
    overflow-y: auto;

    :deep() {
      .el-card__body {
        &:hover {
          .el-image {
            scale: 1.1;
          }
        }

        .el-image {
          width: 100%;
          height: 180px;
          scale: 1.05;
          transition: all ease-in-out 0.3s !important;
        }

        .el-checkbox {
          position: absolute;
          top: 5px;
          left: 10px;
        }
      }
    }
  }
}

.mx-context-menu {
  --mx-menu-backgroud: var(--el-color-white);
  --mx-menu-hover-backgroud: var(--el-color-primary-light-9);
  --mx-menu-active-backgroud: var(--el-color-primary-light-9);
  --mx-menu-open-backgroud: var(--el-color-primary-light-9);
  --mx-menu-open-hover-backgroud: var(--el-color-primary-light-9);
  --mx-menu-divider: var(--el-border-color);
  --mx-menu-text: var(--el-color-black);
  --mx-menu-hover-text: var(--el-color-primary);
  --mx-menu-active-text: var(--el-color-primary);
  --mx-menu-open-text: var(--el-color-primary);
  --mx-menu-open-hover-text: var(--el-color-primary);
  --mx-menu-disabled-text: var(--el-color-black);
  --mx-menu-icon-size: 16px;
  --mx-menu-shadow-color: rgba(0, 0, 0, 0.1);
  --mx-menu-backgroud-radius: var(--el-border-radius-base);
  --mx-menu-shortcut-backgroud: var(--el-color-white);
  --mx-menu-shortcut-backgroud-hover: var(--el-color-white);
  --mx-menu-shortcut-backgroud-active: var(--el-color-white);
  --mx-menu-shortcut-backgroud-open: var(--el-color-white);
  --mx-menu-shortcut-backgroud-disabled: var(--el-color-white);
  --mx-menu-shortcut-text: var(--el-color-black);
  --mx-menu-shortcut-text-hover: var(--el-color-primary);
  --mx-menu-shortcut-text-active: var(--el-color-primary);
  --mx-menu-shortcut-text-open: var(--el-color-primary);
  --mx-menu-shortcut-text-disabled: var(--el-color-black);
  --mx-menu-focus-color: var(--el-color-primary);
  --mx-menu-placeholder-width: 24px;

  &-item {
    height: 32px;
    line-height: 32px;

    .mx-right-arrow {
      width: 12px;
      height: 12px;
    }
  }
}
.pop_menu {
  z-index: 99999999 !important;
}
.item {
  margin-top: 10px;
  margin-right: 30px;
}
</style>
