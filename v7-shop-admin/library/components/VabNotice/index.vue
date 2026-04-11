<template>
  <el-badge type="danger" :value="badge">
    <el-popover placement="bottom" :width="305">
      <template #reference>
        <vab-icon icon="notification-2-line" />
      </template>
      <el-tabs v-model="activeName">
        <el-tab-pane :label="translate('通知')" name="notice">
          <div class="notice-list">
            <el-scrollbar>
              <ul v-if="badge">
                <li v-for="item in notices" :key="item.id" @click="handleReadNotice(item)">
                  <div class="notice-item">
                    <div class="notice-title">{{ item.title }}</div>
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
</template>

<script lang="ts" setup>
import { getList, markAsRead, markAllAsRead } from '/@/api/notice'
import { translate } from '/@/i18n'
import { useSettingsStore } from '/@/store/modules/settings'

defineOptions({
  name: 'VabNotice',
})

const settingsStore = useSettingsStore()
const { theme } = storeToRefs(settingsStore)
const activeName = ref<string>('notice')
const notices = ref<Array<any>>([])
const badge = ref<any>(undefined)

const fetchData = async () => {
  const { data } = await getList()
  notices.value = data.list || []
  badge.value = data.total === 0 ? undefined : data.total
}

const formatTime = (time: string) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

const handleReadNotice = async (item: any) => {
  if (item.id) {
    await markAsRead(item.id)
    notices.value = notices.value.filter((n: any) => n.id !== item.id)
    badge.value = notices.value.length === 0 ? undefined : notices.value.length
  }
}

const handleClearNotice = async () => {
  await markAllAsRead()
  badge.value = undefined
  notices.value = []
  $baseMessage('清空消息成功', 'success', 'hey')
}

onBeforeMount(() => {
  if (theme.value.showNotice) fetchData()
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
        .notice-title {
          font-size: 14px;
          font-weight: 500;
          color: var(--el-text-color-primary);
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
</style>
