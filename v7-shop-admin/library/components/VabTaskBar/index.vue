<template>
  <div class="task-bar-trigger" @click="drawerVisible = true">
    <el-badge :value="tasksStore.activeCount || undefined" :hidden="!tasksStore.activeCount" type="primary">
      <vab-icon icon="task-line" style="cursor: pointer" />
    </el-badge>
    <span v-if="tasksStore.hasUnreadCompleted" class="task-bar-dot" />
  </div>

  <el-drawer
    v-model="drawerVisible"
    title="任务中心"
    direction="rtl"
    size="620px"
    :append-to-body="true"
  >
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 活动任务 Tab -->
      <el-tab-pane name="active">
        <template #label>
          <span>当前任务</span>
          <el-badge
            v-if="tasksStore.activeCount"
            :value="tasksStore.activeCount"
            type="primary"
            style="margin-left: 6px"
          />
        </template>

        <div v-if="tasksStore.completedCount" class="task-bar-clear">
          <el-button
            link
            type="primary"
            size="small"
            :loading="clearingAll"
            :disabled="clearingAll"
            @click="handleClearAll"
          >
            清除已完成 ({{ tasksStore.completedCount }})
          </el-button>
        </div>

        <el-scrollbar max-height="calc(100vh - 200px)">
          <div v-if="tasksStore.unacknowledgedTasks.length" class="task-bar-list">
            <div v-for="task in tasksStore.unacknowledgedTasks" :key="task.taskId" class="task-bar-item">
              <div class="task-bar-item-header">
                <span class="task-bar-item-label">{{ task.label }}</span>
                <div class="task-bar-item-header-right">
                  <el-tag :type="stateTagType(task.state)" size="small" effect="plain">
                    {{ stateLabel(task.state) }}
                  </el-tag>
                  <el-icon
                    v-if="isFinished(task.state)"
                    class="task-bar-item-close"
                    @click="tasksStore.acknowledgeTask(task.taskId)"
                  >
                    <Close />
                  </el-icon>
                </div>
              </div>
              <el-progress
                :percentage="task.progress"
                :status="progressStatus(task.state)"
                :stroke-width="6"
                :show-text="true"
              />
              <!-- 活动任务: 等待时间 + 消息 -->
              <div v-if="!isFinished(task.state)" class="task-bar-item-msg task-bar-item-msg--info">
                {{ elapsedText(task.createdAt) }}
                <template v-if="task.message && task.state === 'PROCESSING'"> · {{ task.message }}</template>
              </div>
              <!-- 已完成任务: 消息 -->
              <div v-else-if="task.message" class="task-bar-item-msg" :class="{ 'task-bar-item-msg--info': task.state === 'COMPLETED' }">
                {{ task.message }}
              </div>
              <div v-if="!isFinished(task.state)" class="task-bar-item-actions">
                <el-button
                  v-if="task.taskType === 'PRODUCT_AI_TRANSLATE' && task.inBatchMode && task.state === 'PROCESSING'"
                  link
                  type="warning"
                  size="small"
                  @click="handleSwitchDirect(task.taskId)"
                >
                  <el-icon style="margin-right: 2px"><Sort /></el-icon>
                  加速翻译
                </el-button>
                <el-button link type="danger" size="small" @click="handleCancel(task.taskId)">
                  取消任务
                </el-button>
              </div>
              <div v-if="isFinished(task.state) && task.hasDownload" class="task-bar-item-actions">
                <el-button link type="primary" size="small" @click="handleDownload(Number(task.taskId))">
                  下载文件
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无未读任务" :image-size="80" />
        </el-scrollbar>
      </el-tab-pane>

      <!-- 历史任务 Tab -->
      <el-tab-pane label="历史任务" name="history">
        <div class="history-filter">
          <el-radio-group v-model="historyFilter" size="small" @change="loadHistory(1)">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="COMPLETED">已完成</el-radio-button>
            <el-radio-button value="FAILED">失败</el-radio-button>
            <el-radio-button value="CANCELLED">已取消</el-radio-button>
          </el-radio-group>
        </div>
        <el-table
          v-loading="historyLoading"
          :data="historyList"
          size="small"
          stripe
          style="width: 100%"
        >
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ taskTypeLabel(row.taskType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="stateTagType(row.state)" size="small" effect="plain">
                {{ stateLabel(row.state) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="消息" prop="message" show-overflow-tooltip min-width="120" />
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.hasDownload"
                link
                type="primary"
                size="small"
                @click="handleDownload(row.taskId)"
              >
                下载
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="history-pagination">
          <el-pagination
            v-model:current-page="historyPage"
            :page-size="historyPageSize"
            :total="historyTotal"
            layout="total, prev, pager, next"
            small
            background
            @current-change="loadHistory"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script lang="ts" setup>
import { Close, Sort } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useTasksStore, type TaskItem } from '/@/store/modules/tasks'
import { listTasks, downloadTaskFile } from '/@/api/taskManagement'

defineOptions({
  name: 'VabTaskBar',
})

const tasksStore = useTasksStore()

const drawerVisible = ref(false)
const activeTab = ref('active')
const clearingAll = ref(false)

let refreshTimer: ReturnType<typeof setInterval> | null = null

watch(drawerVisible, (open) => {
  tasksStore.setDrawerOpen(open)
  if (open) {
    tasksStore.loadFromBackend()
    refreshTimer = setInterval(() => tasksStore.loadFromBackend(), 10_000)
  } else {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }
})

onMounted(() => {
  tasksStore.loadFromBackend()
  tickTimer = setInterval(() => {
    now.value = Date.now()
  }, 10_000)
})

onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})

// --- 活动任务: 计时 ---
const now = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | null = null

const elapsedText = (createdAt: number) => {
  const diff = Math.floor((now.value - createdAt) / 1000)
  if (diff < 60) return `已等待 ${diff} 秒`
  return `已等待 ${Math.floor(diff / 60)} 分钟`
}

const isFinished = (state: string) =>
  state === 'COMPLETED' || state === 'FAILED' || state === 'CANCELLED'

const handleCancel = async (taskId: string) => {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '取消任务', {
      confirmButtonText: '确定取消',
      cancelButtonText: '返回',
      type: 'warning',
    })
    await tasksStore.cancelTask(taskId)
  } catch {
    // noop
  }
}

const handleSwitchDirect = async (taskId: string) => {
  try {
    await ElMessageBox.confirm(
      '将取消当前批量翻译任务，改为逐条即时翻译。即时模式速度更快，但会消耗更多 Token。确定切换吗？',
      '加速翻译',
      {
        confirmButtonText: '确定切换',
        cancelButtonText: '继续等待',
        type: 'warning',
      }
    )
    await tasksStore.switchToDirectTranslate(taskId)
  } catch {
    // noop
  }
}

const handleClearAll = async () => {
  clearingAll.value = true
  try {
    await tasksStore.acknowledgeAllCompleted()
  } finally {
    clearingAll.value = false
  }
}

// --- 历史任务 ---
const historyFilter = ref('')
const historyLoading = ref(false)
const historyList = ref<any[]>([])
const historyPage = ref(1)
const historyPageSize = 10
const historyTotal = ref(0)

const loadHistory = async (page?: number) => {
  if (page) historyPage.value = page
  historyLoading.value = true
  try {
    const params: Record<string, any> = {
      page: historyPage.value - 1,
      size: historyPageSize,
    }
    if (historyFilter.value) params.state = historyFilter.value
    const res = await listTasks(params)
    const pageData = res?.data ?? res
    historyList.value = pageData?.list || []
    historyTotal.value = pageData?.total || 0
  } catch (e) {
    console.error('[TaskBar] loadHistory failed', e)
  } finally {
    historyLoading.value = false
  }
}

const onTabChange = (tab: string | number) => {
  if (tab === 'history' && historyList.value.length === 0) {
    loadHistory(1)
  }
}

const handleDownload = (taskId: number) => {
  downloadTaskFile(taskId)
}

// --- 公共方法 ---
const stateLabel = (state: TaskItem['state'] | string) => {
  const map: Record<string, string> = {
    PENDING: '等待中',
    PROCESSING: '进行中',
    RESOLVED: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[state] || state
}

const stateTagType = (state: TaskItem['state'] | string) => {
  const map: Record<string, string> = {
    PENDING: 'info',
    PROCESSING: '',
    RESOLVED: '',
    COMPLETED: 'success',
    FAILED: 'danger',
    CANCELLED: 'warning',
  }
  return map[state] || 'info'
}

const progressStatus = (state: TaskItem['state'] | string) => {
  if (state === 'COMPLETED') return 'success'
  if (state === 'FAILED') return 'exception'
  if (state === 'CANCELLED') return 'warning'
  return undefined
}

const taskTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    ORDER_DOWNLOAD: '订单下载',
    ORDER_UPLOAD: '订单上传',
    THIRD_PARTY_ORDER_SYNC: '订单同步',
    PRODUCT_AI_TRANSLATE: 'AI翻译',
  }
  return map[type] || type
}

const formatTime = (time: string) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<style lang="scss" scoped>
.task-bar-trigger {
  position: relative;
  display: inline-flex;
  cursor: pointer;
}

.task-bar-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  background-color: var(--el-color-danger);
  border-radius: 50%;
  pointer-events: none;
}

.task-bar-clear {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.task-bar-list {
  padding: 4px 0;
}

.task-bar-item {
  padding: 10px 0;

  & + & {
    border-top: 1px solid var(--el-border-color-lighter);
  }
}

.task-bar-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.task-bar-item-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.task-bar-item-close {
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-placeholder);
  transition: color 0.2s;

  &:hover {
    color: var(--el-color-danger);
  }
}

.task-bar-item-label {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-bar-item-msg {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger);

  &--info {
    color: var(--el-color-info);
  }
}

.task-bar-item-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.history-filter {
  margin-bottom: 12px;
}

.history-pagination {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
