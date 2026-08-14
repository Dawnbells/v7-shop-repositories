<template>
  <div class="ai-token-usage-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="70px" :model="queryForm" @submit.prevent>
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
      :data="taskList"
      :expand-row-keys="expandedKeys"
      :row-key="(row: TaskRow) => String(row.id)"
      size="small"
      stripe
      style="width: 100%"
      @expand-change="handleExpand"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail-table-wrapper">
            <el-table
              v-loading="row._detailLoading"
              border
              :data="row._details || []"
              size="small"
              stripe
              style="width: 100%"
            >
              <el-table-column align="center" label="ID" prop="id" width="125" />
              <el-table-column align="center" label="内容类型" width="80">
                <template #default="{ row: r }">
                  <el-tag size="small" :type="contentTypeTagType(r.contentType)">
                    {{ contentTypeLabel(r.contentType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column align="center" label="目标语言" prop="targetLanguage" width="90" />
              <el-table-column align="center" label="使用模型" prop="model" width="140" />
              <el-table-column align="right" label="耗时" width="80">
                <template #default="{ row: r }">
                  {{ r.elapsedMs != null ? r.elapsedMs + 'ms' : '-' }}
                </template>
              </el-table-column>
              <el-table-column v-if="isAdmin" align="center" label="缓存" width="60">
                <template #default="{ row: r }">
                  <el-tag size="small" :type="r.cacheHit ? 'success' : 'info'">
                    {{ r.cacheHit ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column align="right" label="Prompt Tokens" width="110">
                <template #default="{ row: r }">
                  {{ formatNumber(r.businessPromptTokens) }}
                </template>
              </el-table-column>
              <el-table-column align="right" label="Thinking Tokens" width="110">
                <template #default="{ row: r }">
                  {{ formatNumber(r.businessThinkingTokens) }}
                </template>
              </el-table-column>
              <el-table-column align="right" label="Completion Tokens" width="120">
                <template #default="{ row: r }">
                  {{ formatNumber(r.businessCompletionTokens) }}
                </template>
              </el-table-column>
              <el-table-column align="right" label="Credits" width="80">
                <template #default="{ row: r }">
                  <strong>{{ formatNumber(r.businessCredits) }}</strong>
                </template>
              </el-table-column>
              <el-table-column v-if="isAdmin" align="right" label="Actual Prompt" width="110">
                <template #default="{ row: r }">
                  {{ formatNumber(r.actualPromptTokens) }}
                </template>
              </el-table-column>
              <el-table-column v-if="isAdmin" align="right" label="Actual Thinking" width="110">
                <template #default="{ row: r }">
                  {{ formatNumber(r.actualThinkingTokens) }}
                </template>
              </el-table-column>
              <el-table-column v-if="isAdmin" align="right" label="Actual Completion" width="130">
                <template #default="{ row: r }">
                  {{ formatNumber(r.actualCompletionTokens) }}
                </template>
              </el-table-column>
              <el-table-column v-if="isAdmin" align="right" label="Actual Credits" width="110">
                <template #default="{ row: r }">
                  <strong>
                    {{ r.actualCost != null ? formatNumber(Math.ceil(r.actualCost * 1000)) : '-' }}
                  </strong>
                </template>
              </el-table-column>
              <el-table-column label="翻译内容" min-width="280">
                <template #default="{ row: r }">
                  <template v-if="r.contentType === 'IMAGE'">
                    <div class="image-preview-row">
                      <div class="image-cell">
                        <span class="image-label">原图</span>
                        <el-image
                          v-if="r.sourceImageUrl"
                          fit="contain"
                          hide-on-click-modal
                          preview-teleported
                          :preview-src-list="[r.sourceImageUrl]"
                          :src="r.sourceImageUrl"
                          style="width: 80px; height: 80px"
                        />
                        <span v-else class="placeholder-box">-</span>
                      </div>
                      <div class="image-cell">
                        <span class="image-label">译图</span>
                        <el-image
                          v-if="r.translatedImageUrl"
                          fit="contain"
                          hide-on-click-modal
                          preview-teleported
                          :preview-src-list="[r.translatedImageUrl]"
                          :src="r.translatedImageUrl"
                          style="width: 80px; height: 80px"
                        />
                        <span v-else-if="r.skipped" class="placeholder-box skipped">动图跳过</span>
                        <el-tooltip
                          v-else-if="r.policyFallbackReason"
                          :content="`内容政策限制，保留原图（${r.policyFallbackReason}）`"
                          placement="top"
                          :show-after="300"
                        >
                          <span class="placeholder-box policy">政策保留原图</span>
                        </el-tooltip>
                        <el-tooltip
                          v-else-if="r.failReason"
                          :content="r.failReason"
                          placement="top"
                          :show-after="300"
                        >
                          <span class="placeholder-box failed">{{ failLabel(r.failReason) }}</span>
                        </el-tooltip>
                        <span v-else class="placeholder-box translating">翻译中...</span>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    <div class="text-preview">
                      <div class="text-row">
                        <el-tag effect="plain" size="small" type="info">原文</el-tag>
                        <template v-if="r.sourceText">
                          <el-tooltip :content="r.sourceText" placement="top" :show-after="300">
                            <span class="text-ellipsis">{{ r.sourceText }}</span>
                          </el-tooltip>
                        </template>
                        <span v-else class="text-placeholder">-</span>
                      </div>
                      <div class="text-row">
                        <el-tag effect="plain" size="small" type="success">译文</el-tag>
                        <template v-if="r.translatedText">
                          <el-tooltip :content="r.translatedText" placement="top" :show-after="300">
                            <span class="text-ellipsis">{{ r.translatedText }}</span>
                          </el-tooltip>
                        </template>
                        <el-tooltip
                          v-else-if="r.policyFallbackReason"
                          :content="`内容政策限制，保留原文（${r.policyFallbackReason}）`"
                          placement="top"
                          :show-after="300"
                        >
                          <span class="text-placeholder policy">政策保留原文</span>
                        </el-tooltip>
                        <el-tooltip
                          v-else-if="r.failReason"
                          :content="r.failReason"
                          placement="top"
                          :show-after="300"
                        >
                          <span class="text-placeholder failed">{{ failLabel(r.failReason) }}</span>
                        </el-tooltip>
                        <span v-else-if="!r.skipped" class="text-placeholder translating">翻译中...</span>
                        <span v-else class="text-placeholder">-</span>
                      </div>
                    </div>
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
      <el-table-column align="center" label="任务ID" prop="id" width="125" />
      <el-table-column label="任务名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.name || '-' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="提交用户" prop="ownerName" width="100" show-overflow-tooltip />
      <el-table-column align="center" label="记录数" width="80">
        <template #default="{ row }">
          {{ row.recordCount != null ? row.recordCount : '-' }}
        </template>
      </el-table-column>
      <el-table-column align="right" label="Prompt Tokens" width="120">
        <template #default="{ row }">
          {{ formatNumber(row.totalPromptTokens) }}
        </template>
      </el-table-column>
      <el-table-column align="right" label="Thinking Tokens" width="130">
        <template #default="{ row }">
          {{ formatNumber(row.totalThinkingTokens) }}
        </template>
      </el-table-column>
      <el-table-column align="right" label="Completion Tokens" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.totalCompletionTokens) }}
        </template>
      </el-table-column>
      <el-table-column align="right" label="Credits" width="110">
        <template #default="{ row }">
          <strong>{{ formatNumber(row.totalBusinessCredits) }}</strong>
        </template>
      </el-table-column>
      <el-table-column align="center" label="已结算" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.billingSettled ? 'success' : 'warning'">
            {{ row.billingSettled ? '已结算' : '未结算' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="结算时间" width="120">
        <template #default="{ row }">
          {{ row.billingSettledAt || '-' }}
        </template>
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
import { listAiTranslateTasks } from '/@/api/taskManagement'
import { page as fetchRecordPage } from '/@/api/aiTokenUsageRecord'

defineOptions({
  name: 'AiTokenUsageRecord',
})

interface TaskRow {
  id: number
  name: string
  ownerName: string | null
  recordCount: number | null
  totalPromptTokens: number | null
  totalCompletionTokens: number | null
  totalThinkingTokens: number | null
  totalBusinessCredits: number | null
  billingSettled: boolean
  billingSettledAt: string | null
  _details?: any[]
  _detailLoading?: boolean
  _detailLoaded?: boolean
}

const taskList = ref<TaskRow[]>([])
const listLoading = ref(true)
const total = ref(0)
const isAdmin = ref(false)
// 当前已展开的任务行 ID 列表，用于查询/切页后保留展开状态并重新拉取详情
const expandedKeys = ref<string[]>([])

const queryForm = reactive({
  pageNo: 1,
  pageSize: 20,
})

const contentTypeLabel = (type: string) => {
  if (type === 'TEXT') return '文本'
  if (type === 'IMAGE') return '图片'
  if (type === 'HTML') return 'HTML'
  return type
}

const contentTypeTagType = (
  type: string
): 'success' | 'primary' | 'warning' | 'info' | 'danger' => {
  if (type === 'IMAGE') return 'warning'
  if (type === 'HTML') return 'danger'
  return 'info'
}

const formatNumber = (value: number | undefined | null): string => {
  if (value == null) return '-'
  return value.toLocaleString()
}

/** failReason 形如 "SOURCE_MEDIA_NOT_FOUND: ..."，格子里只放短标签，完整原因走 tooltip */
const FAIL_LABELS: Record<string, string> = {
  SOURCE_MEDIA_NOT_FOUND: '源图不存在',
  TURBOFLOW_TEXT_PERMANENT_FAILED: 'AI 拒绝处理',
  TURBOFLOW_QUEUE_INVARIANT: '任务类型异常',
}

const failLabel = (failReason: string | undefined | null): string => {
  if (!failReason) return '已失败'
  const code = failReason.split(':')[0]?.trim() ?? ''
  return FAIL_LABELS[code] ?? '已失败'
}

/**
 * 加载某行的子任务详情。封装为独立函数以便 fetchData 后批量刷新已展开行。
 */
const loadDetails = async (row: TaskRow, force = false) => {
  if (!force && row._detailLoaded) return
  row._detailLoading = true
  try {
    const { data } = await fetchRecordPage({
      taskId: row.id,
      pageNo: 1,
      pageSize: 100,
    })
    row._details = data?.list || []
    row._detailLoaded = true
    if (
      !isAdmin.value &&
      row._details &&
      row._details.length > 0 &&
      row._details[0].actualCost !== undefined
    ) {
      isAdmin.value = true
    }
  } catch (e) {
    console.error('[AiTokenUsageRecord] loadDetails failed', e)
    if (!row._details) row._details = []
    row._detailLoaded = false
  } finally {
    row._detailLoading = false
  }
}

const fetchData = async () => {
  listLoading.value = true
  try {
    const params: any = {
      pageNo: queryForm.pageNo,
      pageSize: queryForm.pageSize,
      taskTypes: ['PRODUCT_AI_TRANSLATE', 'PRODUCT_AI_REALTIME_TRANSLATE'],
    }
    const { data } = await listAiTranslateTasks(params)
    const previouslyExpanded = new Set(expandedKeys.value.map((k) => String(k)))
    const prevById = new Map(taskList.value.map((r) => [r.id, r]))
    taskList.value = (data.list || []).map((item: any) => {
      const prev = prevById.get(item.id) as TaskRow | undefined
      const wasExpanded = previouslyExpanded.has(String(item.id))
      return {
        ...item,
        _details:
          wasExpanded && prev && Array.isArray(prev._details) ? prev._details : wasExpanded ? [] : undefined,
        _detailLoading: wasExpanded,
        _detailLoaded: false,
      }
    })
    total.value = data.total || 0
    // 重新计算 expandedKeys：仅保留新数据中仍存在的行 ID
    const remainingExpanded: string[] = []
    const refreshRows: TaskRow[] = []
    for (const row of taskList.value) {
      if (previouslyExpanded.has(String(row.id))) {
        remainingExpanded.push(String(row.id))
        refreshRows.push(row)
      }
    }
    expandedKeys.value = remainingExpanded
    await Promise.all(refreshRows.map((row) => loadDetails(row, true)))
  } finally {
    listLoading.value = false
  }
}

const handleExpand = async (row: TaskRow, expandedRows: TaskRow[]) => {
  expandedKeys.value = expandedRows.map((r) => String(r.id))
  const isExpanding = expandedRows.some((r) => r.id === row.id)
  if (!isExpanding) return
  await loadDetails(row)
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
    min-width: 0;
  }

  .text-ellipsis {
    font-size: 13px;
    color: var(--el-text-color-regular);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  .text-placeholder {
    font-size: 13px;
    color: var(--el-text-color-placeholder);
    flex: 1;
    min-width: 0;

    &.translating {
      color: var(--el-color-primary);
      font-style: italic;
    }

    &.policy {
      color: var(--el-color-warning);
      cursor: help;
    }

    &.failed {
      color: var(--el-color-danger);
      cursor: help;
    }
  }

  .placeholder-box {
    display: flex;
    width: 80px;
    height: 80px;
    align-items: center;
    justify-content: center;
    border: 1px dashed var(--el-border-color);
    border-radius: 4px;
    color: var(--el-text-color-placeholder);
    font-size: 12px;
    background-color: var(--el-fill-color-lighter);

    &.translating {
      color: var(--el-color-primary);
      font-style: italic;
    }

    &.skipped {
      color: var(--el-color-warning);
      border-color: var(--el-color-warning-light-5);
    }

    // 政策回退：任务算成功完成，但保留了原件，用警示色和"翻译中"区分开
    &.policy {
      color: var(--el-color-warning);
      border-color: var(--el-color-warning-light-5);
      cursor: help;
    }

    // 永久失败：已终态，不该再显示"翻译中..."
    &.failed {
      color: var(--el-color-danger);
      border-color: var(--el-color-danger-light-5);
      cursor: help;
    }
  }

  .no-content {
    color: var(--el-text-color-placeholder);
  }
}
</style>
