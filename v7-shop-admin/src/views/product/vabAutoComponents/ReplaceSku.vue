<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    title="替换 SKU"
    width="560px"
    @close="close"
  >
    <el-alert v-if="showEmptyAlert" :closable="false" :title="emptyAlertTitle" :type="emptyAlertType" />
    <el-form
      v-else
      ref="formRef"
      v-loading="formLoading"
      label-width="90px"
      :model="form"
      :rules="rules"
    >
      <!-- 行模式：源 SKU 固定为表格所在行 -->
      <el-form-item v-if="mode === 'row'" label="源 SKU">
        <span>{{ sourceSku?.skuCode }} / {{ sourceSku?.name }}</span>
        <el-tag class="ml8" size="small" :type="sourceSku?.isVirtual ? 'warning' : 'info'">
          {{ sourceSku?.isVirtual ? '虚拟' : '真实' }}
        </el-tag>
      </el-form-item>
      <!-- SPU 模式：源 SKU 从选中 SPU 的共同引用（交集）中选择 -->
      <el-form-item v-else label="源 SKU" prop="sourceSkuId">
        <el-select
          v-model="form.sourceSkuId"
          filterable
          placeholder="选择要被替换的 SKU（所选SPU共同引用）"
          style="width: 100%"
          @change="onSelectSource"
        >
          <el-option
            v-for="item in sourceOptions"
            :key="item.id"
            :label="`${item.skuCode} / ${item.name}`"
            :value="item.id"
          >
            <span class="sku-code">{{ item.skuCode }}</span>
            <span class="sku-item-name">{{ item.name }}</span>
            <el-tag class="ml8" size="small" :type="item.isVirtual ? 'warning' : 'info'">
              {{ item.isVirtual ? '虚拟' : '真实' }}
            </el-tag>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="市场" prop="countryIds">
        <el-select
          v-model="form.countryIds"
          :disabled="mode === 'spu' && !form.sourceSkuId"
          filterable
          multiple
          :placeholder="marketPlaceholder"
          style="width: 100%"
        >
          <el-option
            v-for="m in markets"
            :key="m.countryId"
            :label="`${m.countryName}（${m.productCount} 件）`"
            :value="m.countryId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标 SKU" prop="targetSkuId">
        <el-select
          v-model="form.targetSkuId"
          filterable
          :loading="skuLoading"
          placeholder="搜索并选择目标 SKU"
          remote
          :remote-method="remoteQuerySku"
          style="width: 100%"
        >
          <el-option
            v-for="item in skuOptions"
            :key="item.id"
            :label="`${item.skuCode} / ${item.name}`"
            :value="item.id"
          >
            <span class="sku-code">{{ item.skuCode }}</span>
            <span class="sku-item-name">{{ item.name }}</span>
            <el-tag class="ml8" size="small" :type="item.isVirtual ? 'warning' : 'info'">
              {{ item.isVirtual ? '虚拟' : '真实' }}
            </el-tag>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="影响预览">
        <span>
          将影响
          <b>{{ affectedCount }}</b>
          个商品
          <template v-if="mode === 'spu'">（限 {{ spuIds.length }} 个选中SPU）</template>
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button
        :disabled="showEmptyAlert || markets.length === 0"
        :loading="saveLoading"
        type="primary"
        @click="save"
      >
        替换
      </el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import {
  getReplaceTargetQuery,
  replaceDistribution,
  replaceSku,
  replaceSourceQuery,
} from '/@/api/sku'

defineOptions({
  name: 'ReplaceSku',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const $baseConfirm = inject<any>('$baseConfirm')

const formRef = ref<any>(null)
const dialogFormVisible = ref<boolean>(false)
const mode = ref<'row' | 'spu'>('row')
const spuIds = ref<number[]>([])
const distributionLoading = ref<boolean>(false)
const sourceLoading = ref<boolean>(false)
const skuLoading = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
// 加载失败态：区分"请求失败"与"业务上确实为空"，避免空态/占位冒充业务结论
const sourceLoadFailed = ref<boolean>(false)
const distributionLoadFailed = ref<boolean>(false)
const sourceSku = ref<any>(null)
const markets = ref<any[]>([])
const sourceOptions = ref<any[]>([])
const skuOptions = ref<any[]>([])
const form = reactive<any>({
  sourceSkuId: undefined,
  countryIds: [],
  targetSkuId: undefined,
})
const rules = reactive<any>({
  sourceSkuId: [{ required: true, trigger: 'change', message: '请选择源 SKU' }],
  countryIds: [
    { required: true, type: 'array', min: 1, trigger: 'change', message: '请至少选择一个市场' },
  ],
  targetSkuId: [{ required: true, trigger: 'change', message: '请选择目标 SKU' }],
})

const formLoading = computed(() => distributionLoading.value || sourceLoading.value)

const showEmptyAlert = computed(() =>
  mode.value === 'row'
    ? !distributionLoading.value && markets.value.length === 0
    : !sourceLoading.value && sourceOptions.value.length === 0
)

const emptyAlertTitle = computed(() => {
  if (mode.value === 'row') {
    return distributionLoadFailed.value
      ? '市场分布加载失败，请关闭后重试'
      : '该 SKU 未在你管理范围内的任何商品中使用，无可替换的市场'
  }
  return sourceLoadFailed.value
    ? '源 SKU 候选加载失败，请关闭后重试'
    : '所选 SPU 没有共同引用的 SKU，请调整勾选'
})

const emptyAlertType = computed(() =>
  (mode.value === 'row' ? distributionLoadFailed.value : sourceLoadFailed.value) ? 'error' : 'info'
)

const marketPlaceholder = computed(() => {
  if (mode.value === 'spu' && !form.sourceSkuId) return '请先选择源 SKU'
  if (mode.value === 'spu' && form.sourceSkuId && distributionLoadFailed.value)
    return '市场分布加载失败，请重新选择源 SKU'
  if (
    mode.value === 'spu' &&
    form.sourceSkuId &&
    !distributionLoading.value &&
    markets.value.length === 0
  )
    return '该 SKU 在所选 SPU 下无有效市场'
  return '选择市场(可多选)'
})

const affectedCount = computed(() =>
  markets.value
    .filter((m: any) => form.countryIds.includes(m.countryId))
    .reduce((sum: number, m: any) => sum + (m.productCount || 0), 0)
)

// 请求时序守卫：快速换源/请求在途时关闭重开，过期响应一律丢弃，防止旧分布覆盖新状态
let requestSeq = 0
// 目标搜索用独立计数器：与 requestSeq 共用会导致打字搜索误杀在途的分布/候选请求
let targetQuerySeq = 0

const loadDistribution = async () => {
  const seq = ++requestSeq
  distributionLoading.value = true
  distributionLoadFailed.value = false
  try {
    const { data } = await replaceDistribution({
      sourceSkuId: sourceSku.value.id,
      spuIds: mode.value === 'spu' ? spuIds.value : undefined,
    })
    if (seq !== requestSeq) return
    markets.value = data.list || []
  } catch {
    // 全局拦截器已提示错误，这里仅标记失败态，避免空态/占位冒充业务结论
    if (seq === requestSeq) distributionLoadFailed.value = true
  } finally {
    if (seq === requestSeq) distributionLoading.value = false
  }
}

const remoteQuerySku = async (query: string) => {
  const seq = ++targetQuerySeq
  skuLoading.value = true
  try {
    // 按管理范围搜索（与后端目标校验同口径），避免选到提交时会被拒的 SKU
    const { data } = await getReplaceTargetQuery(query)
    if (seq !== targetQuerySeq) return
    // 目标不能等于源 SKU
    skuOptions.value = (data.list || []).filter((s: any) => s.id !== sourceSku.value?.id)
  } catch {
    // 全局拦截器已提示错误
  } finally {
    if (seq === targetQuerySeq) skuLoading.value = false
  }
}

const resetForm = () => {
  targetQuerySeq++ // 作废在途的目标搜索
  sourceLoadFailed.value = false
  distributionLoadFailed.value = false
  sourceSku.value = null
  markets.value = []
  sourceOptions.value = []
  skuOptions.value = []
  Object.assign(form, { sourceSkuId: undefined, countryIds: [], targetSkuId: undefined })
}

// 行模式：源 SKU = 表格所在行
const showEdit = async (row: any) => {
  dialogFormVisible.value = true
  mode.value = 'row'
  // 先置 loading，避免首帧渲染闪现"无可替换市场"空态；
  // 同时复位另一模式的 loading（其在途请求已被 seq 守卫作废，不会再自行复位）
  distributionLoading.value = true
  sourceLoading.value = false
  requestSeq++ // 同步作废上一会话的在途请求
  resetForm()
  sourceSku.value = row
  await nextTick()
  formRef.value?.clearValidate()
  await loadDistribution()
}

// SPU 模式：多选 SPU 批量替换，源 SKU 从交集候选中选择
const showSpuEdit = async (ids: number[]) => {
  dialogFormVisible.value = true
  mode.value = 'spu'
  // 先置 loading，避免首帧闪现"无共同引用SKU"空态；
  // 同时复位另一模式的 loading（其在途请求已被 seq 守卫作废，不会再自行复位）
  sourceLoading.value = true
  distributionLoading.value = false
  const seq = ++requestSeq // 同步作废上一会话的在途请求
  resetForm()
  spuIds.value = ids
  await nextTick()
  formRef.value?.clearValidate()
  try {
    const { data } = await replaceSourceQuery({ spuIds: ids })
    if (seq !== requestSeq) return
    sourceOptions.value = data.list || []
  } catch {
    // 全局拦截器已提示错误，这里仅标记失败态，避免空态冒充"无共同引用SKU"的业务结论
    if (seq === requestSeq) sourceLoadFailed.value = true
  } finally {
    if (seq === requestSeq) sourceLoading.value = false
  }
}

const onSelectSource = (id: any) => {
  sourceSku.value = sourceOptions.value.find((s: any) => s.id === id) || null
  form.countryIds = []
  markets.value = []
  // 源变更后目标下拉的"≠源"过滤基准也变了，清掉旧候选并作废在途搜索
  targetQuerySeq++
  skuOptions.value = []
  form.targetSkuId = undefined
  // 程序化重置会触发 el-select 的 change 校验，清掉误报的必填红字
  nextTick(() => formRef.value?.clearValidate(['countryIds', 'targetSkuId']))
  if (sourceSku.value) loadDistribution()
}

defineExpose({
  showEdit,
  showSpuEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  Object.assign(form, { sourceSkuId: undefined, countryIds: [], targetSkuId: undefined })
}

const save = () => {
  if (!formRef.value) return
  formRef.value.validate((valid: any) => {
    if (!valid) return
    const confirmText =
      mode.value === 'spu'
        ? `将影响 ${affectedCount.value} 个商品（限 ${spuIds.value.length} 个选中SPU），确认将该 SKU 替换为所选目标 SKU？此操作不可撤销。`
        : `将影响 ${affectedCount.value} 个商品，确认将该 SKU 替换为所选目标 SKU？此操作不可撤销。`
    $baseConfirm(confirmText, null, async () => {
      try {
        saveLoading.value = true
        const { data }: any = await replaceSku({
          sourceSkuId: sourceSku.value.id,
          targetSkuId: form.targetSkuId,
          countryIds: form.countryIds,
          spuIds: mode.value === 'spu' ? spuIds.value : undefined,
        })
        $baseMessage(`已替换 ${data.affectedProductCount} 个商品`, 'success', 'hey')
        dialogFormVisible.value = false
        emit('fetch-data')
      } finally {
        saveLoading.value = false
      }
    })
  })
}
</script>

<style lang="scss" scoped>
.ml8 {
  margin-left: 8px;
}
.sku-item-name {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
}
</style>
