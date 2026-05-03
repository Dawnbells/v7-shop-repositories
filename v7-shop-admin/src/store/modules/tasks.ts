import { defineStore } from 'pinia'
import {
  cancelTask as apiCancelTask,
  fetchUnacknowledged,
  acknowledgeTask as apiAcknowledgeTask,
  acknowledgeAllCompleted as apiAcknowledgeAllCompleted,
  retryTask as apiRetryTask,
} from '/@/api/taskManagement'

export interface TaskItem {
  taskId: string
  taskType: string
  name: string
  label: string
  state: 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progress: number
  message: string
  createdAt: number
  acknowledged: boolean
  hasDownload: boolean
  inBatchMode: boolean
  parameters?: Record<string, string>
}

const POLL_FAST = 5_000
const POLL_SLOW = 60_000

function mapBackendTask(t: any): TaskItem {
  const taskId = String(t.id ?? t.taskId)
  const name = t.name || ''
  return {
    taskId,
    taskType: t.taskType,
    name,
    label: name || taskTypeLabelMap[t.taskType] || t.taskType,
    state: t.state,
    progress: t.progress ?? 0,
    message: t.message ?? '',
    createdAt: t.createTime ? new Date(t.createTime).getTime() : Date.now(),
    acknowledged: Boolean(t.acknowledged),
    hasDownload: Boolean(t.hasDownload),
    inBatchMode: Boolean(t.inBatchMode),
  }
}

const isFinishedState = (state: string) =>
  state === 'COMPLETED' || state === 'FAILED' || state === 'CANCELLED'

const taskTypeLabelMap: Record<string, string> = {
  ORDER_DOWNLOAD: '订单下载',
  ORDER_UPLOAD: '订单上传',
  THIRD_PARTY_ORDER_SYNC: '第三方订单同步',
  PRODUCT_AI_TRANSLATE: 'AI账号翻译',
}

export const useTasksStore = defineStore('tasks', {
  state: () => ({
    tasks: [] as TaskItem[],
    globalPollTimer: null as ReturnType<typeof setTimeout> | null,
    loaded: false,
    drawerOpen: false,
  }),

  getters: {
    pollInterval: (state) => (state.drawerOpen ? POLL_FAST : POLL_SLOW),

    activeTasks: (state) =>
      state.tasks.filter((t) => t.state === 'PENDING' || t.state === 'PROCESSING'),

    completedTasks: (state) =>
      state.tasks.filter(
        (t) =>
          (t.state === 'COMPLETED' || t.state === 'FAILED' || t.state === 'CANCELLED') &&
          !t.acknowledged
      ),

    activeCount(): number {
      return this.activeTasks.length
    },

    completedCount(): number {
      return this.completedTasks.length
    },

    hasUnreadCompleted(): boolean {
      return this.completedCount > 0
    },

    unacknowledgedTasks: (state) => state.tasks.filter((t) => !t.acknowledged),
  },

  actions: {
    setDrawerOpen(open: boolean) {
      this.drawerOpen = open
      if (open) {
        this.startGlobalPolling()
      }
    },

    async loadFromBackend() {
      try {
        const res = await fetchUnacknowledged()
        const list = res?.data?.list ?? res?.data ?? res ?? []
        const backendTasks: TaskItem[] = (Array.isArray(list) ? list : []).map(mapBackendTask)

        for (const bt of backendTasks) {
          const existing = this.tasks.find((t) => t.taskId === bt.taskId)
          if (existing) {
            existing.state = bt.state
            existing.progress = bt.progress
            existing.message = bt.message
            existing.acknowledged = bt.acknowledged
            existing.hasDownload = bt.hasDownload
            existing.inBatchMode = bt.inBatchMode
            if (bt.name) {
              existing.name = bt.name
              existing.label = bt.name
            }
          } else {
            this.tasks.push(bt)
          }
        }

        const backendIds = new Set(backendTasks.map((t) => t.taskId))
        this.tasks = this.tasks.filter((t) => {
          if (backendIds.has(t.taskId)) return true
          return t.state === 'PENDING' || t.state === 'PROCESSING'
        })

        this.loaded = true
        this.ensurePolling()
      } catch (e) {
        console.error('[TasksStore] loadFromBackend failed', e)
      }
    },

    addTask(task: Omit<TaskItem, 'createdAt' | 'acknowledged' | 'hasDownload' | 'inBatchMode'>) {
      const existing = this.tasks.find((t) => t.taskId === task.taskId)
      if (existing) return

      const label = task.name || task.label
      this.tasks.unshift({
        ...task,
        label,
        createdAt: Date.now(),
        acknowledged: false,
        hasDownload: false,
        inBatchMode: false,
      })
      this.ensurePolling()
    },

    async acknowledgeTask(taskId: string) {
      try {
        await apiAcknowledgeTask(taskId)
        const task = this.tasks.find((t) => t.taskId === taskId)
        if (task) {
          task.acknowledged = true
        }
        this.tasks = this.tasks.filter((t) => !(t.taskId === taskId && t.acknowledged))
      } catch (e) {
        console.error(`[TasksStore] acknowledge task ${taskId} failed`, e)
      }
    },

    async acknowledgeAllCompleted() {
      try {
        await apiAcknowledgeAllCompleted()
        const toRemove = this.tasks.filter(
          (t) =>
            (t.state === 'COMPLETED' || t.state === 'FAILED' || t.state === 'CANCELLED') &&
            !t.acknowledged
        )
        toRemove.forEach((t) => {
          t.acknowledged = true
        })
        this.tasks = this.tasks.filter((t) => !t.acknowledged)
      } catch (e) {
        console.error('[TasksStore] acknowledgeAllCompleted failed', e)
      }
    },

    async cancelTask(taskId: string) {
      try {
        const res = await apiCancelTask(taskId)
        const data = res?.data ?? res
        const task = this.tasks.find((t) => t.taskId === taskId)
        if (task) {
          task.state = data.state
          task.progress = data.progress ?? task.progress
          task.message = data.message ?? '已取消'
        }
      } catch (e) {
        console.error(`[TasksStore] cancel task ${taskId} failed`, e)
      }
    },

    async retryTask(taskId: string) {
      try {
        const res = await apiRetryTask(taskId)
        const data = res?.data ?? res
        const oldTask = this.tasks.find((t) => t.taskId === taskId)
        if (oldTask) {
          oldTask.acknowledged = true
        }
        this.tasks = this.tasks.filter((t) => !(t.taskId === taskId && t.acknowledged))
        const newTaskId = String(data.id ?? data.taskId)
        if (newTaskId && !this.tasks.find((t) => t.taskId === newTaskId)) {
          this.tasks.unshift(mapBackendTask(data))
        }
        this.ensurePolling()
      } catch (e) {
        console.error(`[TasksStore] retry task ${taskId} failed`, e)
      }
    },

    isTranslatingProduct(productId: string, countryId: string, languageId: string): boolean {
      const translateTypes = new Set(['PRODUCT_AI_TRANSLATE'])
      return this.activeTasks.some((t) => {
        if (!translateTypes.has(t.taskType)) return false
        return (
          t.parameters?.productId === productId &&
          t.parameters?.countryId === countryId &&
          t.parameters?.languageId === languageId
        )
      })
    },

    ensurePolling() {
      if (this.activeTasks.length > 0 && !this.globalPollTimer) {
        this.startGlobalPolling()
      }
    },

    startGlobalPolling() {
      this.stopGlobalPolling()
      const poll = async () => {
        await this.pollAll()
        if (this.activeTasks.length > 0) {
          this.globalPollTimer = setTimeout(poll, this.pollInterval)
        } else {
          this.globalPollTimer = null
        }
      }
      this.globalPollTimer = setTimeout(poll, this.pollInterval)
    },

    stopGlobalPolling() {
      if (this.globalPollTimer) {
        clearTimeout(this.globalPollTimer)
        this.globalPollTimer = null
      }
    },

    async pollAll() {
      try {
        const previousStates = new Map(this.tasks.map((t) => [t.taskId, t.state]))

        const res = await fetchUnacknowledged()
        const list = res?.data?.list ?? res?.data ?? res ?? []
        const backendTasks: TaskItem[] = (Array.isArray(list) ? list : []).map(mapBackendTask)

        for (const bt of backendTasks) {
          const existing = this.tasks.find((t) => t.taskId === bt.taskId)
          if (existing) {
            existing.state = bt.state
            existing.progress = bt.progress
            existing.message = bt.message
            existing.hasDownload = bt.hasDownload
            existing.inBatchMode = bt.inBatchMode
            if (bt.name) {
              existing.name = bt.name
              existing.label = bt.name
            }
          } else {
            this.tasks.push(bt)
          }
        }

        const backendIds = new Set(backendTasks.map((t) => t.taskId))
        this.tasks = this.tasks.filter((t) => {
          if (backendIds.has(t.taskId)) return true
          return t.state === 'PENDING' || t.state === 'PROCESSING'
        })

        const newlyFinished = this.tasks.filter((t) => {
          const prev = previousStates.get(t.taskId)
          return isFinishedState(t.state) && prev && !isFinishedState(prev)
        })

        if (newlyFinished.length > 0) {
          const { ElNotification } = await import('element-plus')
          const titleMap: Record<string, string> = {
            COMPLETED: '任务完成',
            FAILED: '任务失败',
            CANCELLED: '任务已取消',
          }
          const typeMap: Record<string, 'success' | 'error' | 'warning'> = {
            COMPLETED: 'success',
            FAILED: 'error',
            CANCELLED: 'warning',
          }
          for (const task of newlyFinished) {
            ElNotification({
              title: titleMap[task.state] || '任务更新',
              message: task.label + (task.message ? `: ${task.message}` : ''),
              type: typeMap[task.state] || 'info',
              duration: 5000,
            })
          }
        }
      } catch (e) {
        console.error('[TasksStore] pollAll failed', e)
      }
    },
  },
})
