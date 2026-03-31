<template>
  <el-badge v-if="tasksStore.hasTasks" type="primary" :value="tasksStore.activeCount || undefined">
    <el-popover placement="bottom" :width="360" trigger="hover">
      <template #reference>
        <vab-icon icon="task-line" style="cursor: pointer" />
      </template>
      <div class="task-bar-header">
        <span class="task-bar-title">任务中心</span>
        <el-button
          v-if="completedTasks.length"
          link
          type="primary"
          size="small"
          @click="tasksStore.clearCompleted()"
        >
          清除已完成
        </el-button>
      </div>
      <el-scrollbar max-height="320px">
        <div v-if="tasksStore.tasks.length" class="task-bar-list">
          <div v-for="task in tasksStore.tasks" :key="task.taskId" class="task-bar-item">
            <div class="task-bar-item-header">
              <span class="task-bar-item-label">{{ task.label }}</span>
              <el-tag
                :type="stateTagType(task.state)"
                size="small"
                effect="plain"
              >
                {{ stateLabel(task.state) }}
              </el-tag>
            </div>
            <el-progress
              :percentage="task.progress"
              :status="progressStatus(task.state)"
              :stroke-width="6"
              :show-text="true"
            />
            <div v-if="task.message && (task.state === 'FAILED')" class="task-bar-item-msg">
              {{ task.message }}
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无任务" :image-size="60" />
      </el-scrollbar>
    </el-popover>
  </el-badge>
</template>

<script lang="ts" setup>
import { useTasksStore, type TaskItem } from '/@/store/modules/tasks'

defineOptions({
  name: 'VabTaskBar',
})

const tasksStore = useTasksStore()

const completedTasks = computed(() =>
  tasksStore.tasks.filter((t) => t.state === 'COMPLETED' || t.state === 'FAILED')
)

const stateLabel = (state: TaskItem['state']) => {
  const map: Record<string, string> = {
    PENDING: '等待中',
    PROCESSING: '进行中',
    RESOLVED: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return map[state] || state
}

const stateTagType = (state: TaskItem['state']) => {
  const map: Record<string, string> = {
    PENDING: 'info',
    PROCESSING: '',
    RESOLVED: '',
    COMPLETED: 'success',
    FAILED: 'danger',
  }
  return map[state] || 'info'
}

const progressStatus = (state: TaskItem['state']) => {
  if (state === 'COMPLETED') return 'success'
  if (state === 'FAILED') return 'exception'
  return undefined
}
</script>

<style lang="scss" scoped>
.task-bar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--el-border-color);
}

.task-bar-title {
  font-size: 14px;
  font-weight: 600;
}

.task-bar-list {
  padding: 4px 0;
}

.task-bar-item {
  padding: 8px 0;

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

.task-bar-item-label {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-bar-item-msg {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
