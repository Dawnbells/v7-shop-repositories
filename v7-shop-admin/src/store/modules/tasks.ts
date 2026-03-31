import { defineStore } from 'pinia'
import { status as fetchTaskStatus } from '/@/api/taskManagement'

export interface TaskItem {
  taskId: string
  taskType: string
  label: string
  state: 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'COMPLETED' | 'FAILED'
  progress: number
  message: string
  createdAt: number
}

const POLL_INTERVAL = 2000

export const useTasksStore = defineStore('tasks', {
  state: () => ({
    tasks: [] as TaskItem[],
    pollingTimers: {} as Record<string, ReturnType<typeof setTimeout>>,
  }),

  getters: {
    activeTasks: (state) =>
      state.tasks.filter((t) => t.state === 'PENDING' || t.state === 'PROCESSING'),
    activeCount(): number {
      return this.activeTasks.length
    },
    hasTasks: (state) => state.tasks.length > 0,
  },

  actions: {
    addTask(task: Omit<TaskItem, 'createdAt'>) {
      const existing = this.tasks.find((t) => t.taskId === task.taskId)
      if (existing) return

      this.tasks.unshift({
        ...task,
        createdAt: Date.now(),
      })
      this.startPolling(task.taskId)
    },

    removeTask(taskId: string) {
      this.stopPolling(taskId)
      this.tasks = this.tasks.filter((t) => t.taskId !== taskId)
    },

    clearCompleted() {
      const completed = this.tasks.filter(
        (t) => t.state === 'COMPLETED' || t.state === 'FAILED'
      )
      completed.forEach((t) => this.removeTask(t.taskId))
    },

    isTranslatingProduct(productId: string, languageId: string): boolean {
      return this.activeTasks.some((t) => {
        if (t.taskType !== 'PRODUCT_AI_TRANSLATE') return false
        try {
          const params = JSON.parse(t.label)
          return params.productId === productId && params.languageId === languageId
        } catch {
          return false
        }
      })
    },

    async startPolling(taskId: string) {
      this.stopPolling(taskId)

      const poll = async () => {
        try {
          const { data } = await fetchTaskStatus(taskId)
          const task = this.tasks.find((t) => t.taskId === taskId)
          if (!task) return

          task.state = data.state
          task.progress = data.progress ?? task.progress
          task.message = data.message ?? task.message

          if (data.state === 'COMPLETED' || data.state === 'FAILED') {
            this.stopPolling(taskId)
            const { ElNotification } = await import('element-plus')
            ElNotification({
              title: data.state === 'COMPLETED' ? '任务完成' : '任务失败',
              message: task.label + (data.message ? `: ${data.message}` : ''),
              type: data.state === 'COMPLETED' ? 'success' : 'error',
              duration: 5000,
            })
            return
          }
        } catch (e) {
          console.error(`[TasksStore] poll task ${taskId} failed`, e)
        }
        this.pollingTimers[taskId] = setTimeout(poll, POLL_INTERVAL)
      }

      this.pollingTimers[taskId] = setTimeout(poll, POLL_INTERVAL)
    },

    stopPolling(taskId: string) {
      if (this.pollingTimers[taskId]) {
        clearTimeout(this.pollingTimers[taskId])
        delete this.pollingTimers[taskId]
      }
    },
  },
})
