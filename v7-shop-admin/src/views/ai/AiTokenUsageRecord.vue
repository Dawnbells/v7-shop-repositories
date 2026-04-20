<template>
  <div class="ai-token-usage-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="70px" :model="queryForm" @submit.prevent>
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
      row-key="id"
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
                      <div v-if="r.sourceImageUrl" class="image-cell">
                        <span class="image-label">原图</span>
                        <el-image
                          fit="contain"
                          hide-on-click-modal
                          preview-teleported
                          :preview-src-list="[r.sourceImageUrl]"
                          :src="r.sourceImageUrl"
                          style="width: 80px; height: 80px"
                        />
                      </div>
                      <div v-if="r.sourceImageUrl" class="image-cell">
                        <span class="image-label">译图</span>
                        <el-image
                          fit="contain"
                          hide-on-click-modal
                          preview-teleported
                          :preview-src-list="[r.translatedImageUrl || r.sourceImageUrl]"
                          :src="r.translatedImageUrl || r.sourceImageUrl"
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
                          <span class="text-ellipsis">{{ r.sourceText }}</span>
                        </el-tooltip>
                      </div>
                      <div v-if="r.translatedText" class="text-row">
                        <el-tag effect="plain" size="small" type="success">译文</el-tag>
                        <el-tooltip :content="r.translatedText" placement="top" :show-after="300">
                          <span class="text-ellipsis">{{ r.translatedText }}</span>
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
      <el-table-column align="center" label="任务ID" prop="id" width="125" />
      <el-table-column label="任务名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.name || '-' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="提交用户" prop="ownerName" width="100" show-overflow-tooltip />
      <el-table-column align="center" label="调用模式" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.invokeMode === 'BATCH' ? 'success' : 'primary'">
            {{ invokeModeLabel(row.invokeMode) }}
          </el-tag>
        </template>
      </el-table-column>
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
  invokeMode: string
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

const queryForm = reactive({
  pageNo: 1,
  pageSize: 20,
  invokeMode: undefined as string | undefined,
})

const invokeModeLabel = (mode: string) => {
  if (mode === 'STANDARD') return '即时翻译'
  if (mode === 'BATCH') return '批量翻译'
  return mode || '-'
}

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

const buildTaskTypes = () => {
  const mode = queryForm.invokeMode
  if (mode === 'BATCH') return ['PRODUCT_AI_TRANSLATE']
  if (mode === 'STANDARD') return ['PRODUCT_AI_TRANSLATE_DIRECT']
  return undefined
}

const fetchData = async () => {
  listLoading.value = true
  try {
    const params: any = {
      pageNo: queryForm.pageNo,
      pageSize: queryForm.pageSize,
    }
    const taskTypes = buildTaskTypes()
    if (taskTypes) {
      params.taskTypes = taskTypes
    }
    const { data } = await listAiTranslateTasks(params)
    taskList.value = (data.list || []).map((item: any) => ({
      ...item,
      _details: undefined,
      _detailLoading: false,
      _detailLoaded: false,
    }))
    total.value = data.total || 0
  } finally {
    listLoading.value = false
  }
}

const handleExpand = async (row: TaskRow, expandedRows: TaskRow[]) => {
  const isExpanding = expandedRows.some((r) => r.id === row.id)
  if (!isExpanding || row._detailLoaded) return

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
  } finally {
    row._detailLoading = false
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

  .no-content {
    color: var(--el-text-color-placeholder);
  }
}
</style>
