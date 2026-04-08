<template>
  <div class="ai-token-usage-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="70px" :model="queryForm" @submit.prevent>
          <el-form-item label="任务ID">
            <el-input
              v-model.number="queryForm.taskId"
              clearable
              placeholder="请输入任务ID"
              style="width: 150px"
            />
          </el-form-item>
          <el-form-item label="调用模式">
            <el-select
              v-model="queryForm.invokeMode"
              clearable
              placeholder="全部"
              style="width: 130px"
            >
              <el-option label="即时翻译" value="STANDARD" />
              <el-option label="批量翻译" value="BATCH" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容类型">
            <el-select
              v-model="queryForm.contentType"
              clearable
              placeholder="全部"
              style="width: 130px"
            >
              <el-option label="文本" value="TEXT" />
              <el-option label="图片" value="IMAGE" />
              <el-option label="HTML" value="HTML" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="isAdmin" label="缓存命中">
            <el-select
              v-model="queryForm.cacheHit"
              clearable
              placeholder="全部"
              style="width: 100px"
            >
              <el-option label="是" :value="true" />
              <el-option label="否" :value="false" />
            </el-select>
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
    </vab-query-form>

    <el-table
      v-loading="listLoading"
      border
      :data="taskGroups"
      row-key="taskId"
      size="small"
      stripe
      @expand-change="handleExpand"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail-table-wrapper">
            <el-table border :data="row.records" size="small" stripe>
              <el-table-column align="center" label="ID" prop="id" width="120" />
              <el-table-column align="center" label="内容类型" width="80">
                <template #default="{ row: r }">
                  <el-tag size="small" :type="contentTypeTagType(r.contentType)">
                    {{ contentTypeLabel(r.contentType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column align="center" label="目标语言" prop="targetLanguage" width="90" />
              <el-table-column v-if="isAdmin" align="center" label="缓存" width="60">
                <template #default="{ row: r }">
                  <el-tag size="small" :type="r.cacheHit ? 'success' : 'info'">
                    {{ r.cacheHit ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column align="center" label="模型" prop="model" width="140" />
              <el-table-column
                align="right"
                label="业务Token"
                prop="businessTotalTokens"
                width="100"
              >
                <template #default="{ row: r }">
                  <strong>{{ formatNumber(r.businessTotalTokens) }}</strong>
                </template>
              </el-table-column>
              <el-table-column align="right" label="积分" prop="businessCredits" width="70">
                <template #default="{ row: r }">
                  {{ formatNumber(r.businessCredits) }}
                </template>
              </el-table-column>
              <el-table-column v-if="isAdmin" align="right" label="实际Token" width="100">
                <template #default="{ row: r }">
                  {{ formatNumber(r.actualTotalTokens) }}
                </template>
              </el-table-column>
              <el-table-column v-if="isAdmin" align="right" label="实际成本" width="100">
                <template #default="{ row: r }">${{ formatCost(r.actualCost) }}</template>
              </el-table-column>
              <el-table-column align="right" label="耗时" width="80">
                <template #default="{ row: r }">
                  {{ r.elapsedMs != null ? r.elapsedMs + 'ms' : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="翻译内容" min-width="280">
                <template #default="{ row: r }">
                  <template v-if="r.contentType === 'IMAGE'">
                    <div class="image-preview-row">
                      <div v-if="r.sourceImageUrl" class="image-cell">
                        <span class="image-label">原图</span>
                        <el-image
                          fit="contain"
                          :preview-src-list="[r.sourceImageUrl]"
                          :src="r.sourceImageUrl"
                          style="width: 80px; height: 80px"
                        />
                      </div>
                      <div v-if="r.translatedImageUrl" class="image-cell">
                        <span class="image-label">译图</span>
                        <el-image
                          fit="contain"
                          :preview-src-list="[r.translatedImageUrl]"
                          :src="r.translatedImageUrl"
                          style="width: 80px; height: 80px"
                        />
                      </div>
                      <span v-if="!r.sourceImageUrl && !r.translatedImageUrl" class="no-content">
                        -
                      </span>
                    </div>
                  </template>
                  <template v-else>
                    <div v-if="r.sourceText || r.translatedText" class="text-preview">
                      <div v-if="r.sourceText" class="text-row">
                        <el-tag effect="plain" size="small" type="info">原文</el-tag>
                        <el-tooltip :content="r.sourceText" placement="top" :show-after="300">
                          <span class="text-content">{{ truncate(r.sourceText, 80) }}</span>
                        </el-tooltip>
                      </div>
                      <div v-if="r.translatedText" class="text-row">
                        <el-tag effect="plain" size="small" type="success">译文</el-tag>
                        <el-tooltip :content="r.translatedText" placement="top" :show-after="300">
                          <span class="text-content">{{ truncate(r.translatedText, 80) }}</span>
                        </el-tooltip>
                      </div>
                    </div>
                    <span v-else class="no-content">-</span>
                  </template>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty class="vab-data-empty" description="暂无数据" />
              </template>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="任务ID" prop="taskId" width="120" />
      <el-table-column label="任务名称" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.taskName || '-' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="调用模式" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.invokeMode === 'BATCH' ? 'success' : 'primary'">
            {{ invokeModeLabel(row.invokeMode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="记录数" width="80">
        <template #default="{ row }">
          {{ row.records.length }}
        </template>
      </el-table-column>
      <el-table-column align="right" label="总积分" width="100">
        <template #default="{ row }">
          <strong>{{ formatNumber(row.totalBusinessCredits) }}</strong>
        </template>
      </el-table-column>
      <el-table-column v-if="isAdmin" align="right" label="实际成本" width="110">
        <template #default="{ row }">${{ formatCost(row.totalActualCost) }}</template>
      </el-table-column>
      <template #empty>
        <el-empty class="vab-data-empty" description="暂无数据" />
      </template>
    </el-table>

    <vab-pagination
      :current-page="queryForm.pageNo"
      :page-size="queryForm.pageSize"
      size="small"
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script lang="ts" setup>
import { Search } from '@element-plus/icons-vue'
import { page } from '/@/api/aiTokenUsageRecord'

defineOptions({
  name: 'AiTokenUsageRecord',
})

interface TokenRecord {
  id: string
  taskId: number
  taskName: string
  contentType: string
  targetLanguage: string
  cacheHit?: boolean
  model: string
  invokeMode: string
  businessPromptTokens: number
  businessCompletionTokens: number
  businessThinkingTokens: number
  businessTotalTokens: number
  businessCredits: number
  actualPromptTokens?: number
  actualCompletionTokens?: number
  actualThinkingTokens?: number
  actualTotalTokens?: number
  actualCost?: number
  businessCost?: number
  elapsedMs: number
  hasImageOutput: boolean
  sourceText?: string
  translatedText?: string
  sourceImageUrl?: string
  translatedImageUrl?: string
}

interface TaskGroup {
  taskId: number
  taskName: string
  invokeMode: string
  records: TokenRecord[]
  totalBusinessCredits: number
  totalActualCost: number
}

const list = ref<TokenRecord[]>([])
const listLoading = ref<boolean>(true)
const total = ref<number>(0)
const isAdmin = ref<boolean>(false)

const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
  taskId: undefined,
  invokeMode: undefined,
  contentType: undefined,
  cacheHit: undefined,
})

const taskGroups = computed<TaskGroup[]>(() => {
  const groupMap = new Map<number, TokenRecord[]>()
  for (const record of list.value) {
    const taskId = record.taskId
    if (!groupMap.has(taskId)) {
      groupMap.set(taskId, [])
    }
    groupMap.get(taskId)!.push(record)
  }

  const groups: TaskGroup[] = []
  for (const [taskId, records] of groupMap) {
    groups.push({
      taskId,
      taskName: records[0]?.taskName || '',
      invokeMode: records[0]?.invokeMode || '',
      records,
      totalBusinessCredits: records.reduce((sum, r) => sum + (r.businessCredits || 0), 0),
      totalActualCost: records.reduce((sum, r) => sum + (Number(r.actualCost) || 0), 0),
    })
  }
  return groups
})

const invokeModeLabel = (mode: string) => {
  if (mode === 'STANDARD') return '即时翻译'
  if (mode === 'BATCH') return '批量翻译'
  return mode
}

const contentTypeLabel = (type: string) => {
  if (type === 'TEXT') return '文本'
  if (type === 'IMAGE') return '图片'
  if (type === 'HTML') return 'HTML'
  return type
}

const contentTypeTagType = (type: string) => {
  if (type === 'IMAGE') return 'warning'
  if (type === 'HTML') return 'danger'
  return ''
}

const formatNumber = (value: number | undefined | null): string => {
  if (value == null) return '-'
  return value.toLocaleString()
}

const formatCost = (value: number | string | undefined | null): string => {
  if (value == null) return '-'
  return Number(value).toFixed(6)
}

const truncate = (text: string, maxLen: number) => {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}

const fetchData = async () => {
  listLoading.value = true
  try {
    const { data } = await page(queryForm)
    list.value = data.list
    total.value = data.total
    isAdmin.value = data.list.length > 0 && data.list[0].actualCost !== undefined
  } finally {
    listLoading.value = false
  }
}

const handleExpand = () => {}

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

onBeforeMount(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.ai-token-usage-container {
  .detail-table-wrapper {
    padding: 8px 16px;

    :deep(.el-table) {
      width: 100%;
    }
  }

  .image-preview-row {
    display: flex;
    gap: 16px;
    align-items: flex-start;
    padding: 4px 0;
  }

  .image-cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }

  .image-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .text-preview {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 4px 0;
  }

  .text-row {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .text-content {
    font-size: 13px;
    color: var(--el-text-color-regular);
    word-break: break-all;
  }

  .no-content {
    color: var(--el-text-color-placeholder);
  }
}
</style>
