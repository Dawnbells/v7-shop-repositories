<template>
  <el-badge type="danger" :value="badge">
    <el-popover placement="bottom" :width="340">
      <template #reference>
        <vab-icon icon="notification-2-line" />
      </template>
      <el-tabs v-model="activeName">
        <el-tab-pane :label="translate('通知')" name="notice">
          <div class="notice-list">
            <el-scrollbar>
              <ul v-if="badge">
                <li v-for="item in notices" :key="item.id">
                  <div class="notice-item" @click="handleViewNotice(item)">
                    <div class="notice-header">
                      <span class="notice-title">{{ item.title }}</span>
                      <el-icon
                        class="notice-dismiss"
                        @click.stop="handleReadNotice(item)"
                      >
                        <Close />
                      </el-icon>
                    </div>
                    <div v-if="item.content" class="notice-content">{{ item.content }}</div>
                    <div class="notice-time">{{ formatTime(item.createTime) }}</div>
                  </div>
                </li>
              </ul>
              <el-empty v-else description="暂无通知" />
            </el-scrollbar>
          </div>
        </el-tab-pane>
      </el-tabs>
      <div class="notice-clear" @click="handleClearNotice">
        <el-button text>
          <vab-icon icon="close-circle-line" />
          <span>{{ translate('清空消息') }}</span>
        </el-button>
      </div>
    </el-popover>
  </el-badge>

  <el-dialog
    v-model="detailVisible"
    :title="currentNotice?.title"
    width="480px"
    append-to-body
    destroy-on-close
  >
    <div class="notice-detail">
      <div class="notice-detail-content">{{ currentNotice?.content }}</div>
      <div class="notice-detail-time">{{ formatTime(currentNotice?.createTime) }}</div>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
      <el-button type="primary" @click="handleReadFromDetail">标记已读</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { Close } from '@element-plus/icons-vue'
import { getList, getUnreadCount, markAsRead, markAllAsRead } from '/@/api/notice'
import { translate } from '/@/i18n'
import { useSettingsStore } from '/@/store/modules/settings'

defineOptions({
  name: 'VabNotice',
})

const POLL_INTERVAL = 60 * 1000

const settingsStore = useSettingsStore()
const { theme } = storeToRefs(settingsStore)
const activeName = ref<string>('notice')
const notices = ref<Array<any>>([])
const badge = ref<any>(undefined)
const detailVisible = ref(false)
const currentNotice = ref<any>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const fetchData = async () => {
  const { data } = await getList()
  notices.value = data.list || []
  badge.value = data.total === 0 ? undefined : data.total
}

const pollUnreadCount = async () => {
  try {
    const { data } = await getUnreadCount()
    const count = data.count || 0
    const prevBadge = badge.value
    badge.value = count === 0 ? undefined : count
    if (count > 0 && (prevBadge === undefined || count > prevBadge)) {
      await fetchData()
    }
  } catch {
    // 轮询失败时静默处理
  }
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(pollUnreadCount, POLL_INTERVAL)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const formatTime = (time: string) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

const handleViewNotice = (item: any) => {
  currentNotice.value = item
  detailVisible.value = true
}

const removeNoticeFromList = (id: string | number) => {
  notices.value = notices.value.filter((n: any) => n.id !== id)
  badge.value = notices.value.length === 0 ? undefined : notices.value.length
}

const handleReadNotice = async (item: any) => {
  if (item.id) {
    await markAsRead(item.id)
    removeNoticeFromList(item.id)
  }
}

const handleReadFromDetail = async () => {
  if (currentNotice.value?.id) {
    await markAsRead(currentNotice.value.id)
    removeNoticeFromList(currentNotice.value.id)
  }
  detailVisible.value = false
  currentNotice.value = null
}

const handleClearNotice = async () => {
  await markAllAsRead()
  badge.value = undefined
  notices.value = []
  $baseMessage('清空消息成功', 'success', 'hey')
}

onBeforeMount(() => {
  if (theme.value.showNotice) {
    fetchData()
    startPolling()
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style lang="scss" scoped>
:deep() {
  .el-tabs__active-bar {
    min-width: 28px;
  }
}

.notice-list {
  height: 315px;

  ul {
    padding: 0;
    margin: 0;
    list-style: none;

    li {
      padding: 10px 12px;
      cursor: pointer;
      border-bottom: 1px solid var(--el-border-color-lighter);

      &:last-child {
        border-bottom: none;
      }

      &:hover {
        background-color: var(--el-color-primary-light-9);
        border-radius: var(--el-border-radius-base);
      }

      .notice-item {
        .notice-header {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          gap: 8px;
        }

        .notice-title {
          font-size: 14px;
          font-weight: 500;
          color: var(--el-text-color-primary);
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .notice-dismiss {
          flex-shrink: 0;
          font-size: 14px;
          color: var(--el-text-color-placeholder);
          cursor: pointer;
          padding: 2px;
          border-radius: 50%;
          transition: all 0.2s;

          &:hover {
            color: var(--el-color-danger);
            background-color: var(--el-color-danger-light-9);
          }
        }

        .notice-content {
          margin-top: 4px;
          font-size: 12px;
          color: var(--el-text-color-regular);
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .notice-time {
          margin-top: 4px;
          font-size: 12px;
          color: var(--el-text-color-placeholder);
        }
      }
    }
  }
}

.notice-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 0 0 0;
  font-size: var(--el-font-size-base);
  text-align: center;
  cursor: pointer;
  border-top: 1px solid var(--el-border-color);
}

.notice-detail {
  .notice-detail-content {
    font-size: 14px;
    line-height: 1.8;
    color: var(--el-text-color-primary);
    white-space: pre-wrap;
    word-break: break-word;
  }

  .notice-detail-time {
    margin-top: 16px;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    text-align: right;
  }
}
</style>
