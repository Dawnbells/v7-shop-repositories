<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    title="替换 SKU"
    width="560px"
    @close="close"
  >
    <el-alert
      v-if="!distributionLoading && markets.length === 0"
      :closable="false"
      title="该 SKU 未在你管理范围内的任何商品中使用，无可替换的市场"
      type="info"
    />
    <el-form
      v-else
      ref="formRef"
      v-loading="distributionLoading"
      label-width="90px"
      :model="form"
      :rules="rules"
    >
      <el-form-item label="源 SKU">
        <span>{{ sourceSku?.skuCode }} / {{ sourceSku?.name }}</span>
        <el-tag
          class="ml8"
          size="small"
          :type="sourceSku?.isVirtual ? 'warning' : 'info'"
        >
          {{ sourceSku?.isVirtual ? '虚拟' : '真实' }}
        </el-tag>
      </el-form-item>
      <el-form-item label="市场" prop="countryIds">
        <el-select
          v-model="form.countryIds"
          filterable
          multiple
          placeholder="选择市场(可多选)"
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
            <el-tag
              class="ml8"
              size="small"
              :type="item.isVirtual ? 'warning' : 'info'"
            >
              {{ item.isVirtual ? '虚拟' : '真实' }}
            </el-tag>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="影响预览">
        <span>将影响 <b>{{ affectedCount }}</b> 个商品</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button
        :disabled="markets.length === 0"
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
import { getReplaceTargetQuery, replaceDistribution, replaceSku } from '/@/api/sku'

defineOptions({
  name: 'ReplaceSku',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const $baseConfirm = inject<any>('$baseConfirm')

const formRef = ref<any>(null)
const dialogFormVisible = ref<boolean>(false)
const distributionLoading = ref<boolean>(false)
const skuLoading = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const sourceSku = ref<any>(null)
const markets = ref<any[]>([])
const skuOptions = ref<any[]>([])
const form = reactive<any>({
  countryIds: [],
  targetSkuId: undefined,
})
const rules = reactive<any>({
  countryIds: [
    { required: true, type: 'array', min: 1, trigger: 'change', message: '请至少选择一个市场' },
  ],
  targetSkuId: [{ required: true, trigger: 'change', message: '请选择目标 SKU' }],
})

const affectedCount = computed(() =>
  markets.value
    .filter((m: any) => form.countryIds.includes(m.countryId))
    .reduce((sum: number, m: any) => sum + (m.productCount || 0), 0)
)

const loadDistribution = async () => {
  distributionLoading.value = true
  try {
    const { data } = await replaceDistribution({ sourceSkuId: sourceSku.value.id })
    markets.value = data.list || []
  } finally {
    distributionLoading.value = false
  }
}

const remoteQuerySku = async (query: string) => {
  skuLoading.value = true
  try {
    // 按管理范围搜索（与后端目标校验同口径），避免选到提交时会被拒的 SKU
    const { data } = await getReplaceTargetQuery(query)
    // 目标不能等于源 SKU
    skuOptions.value = (data.list || []).filter((s: any) => s.id !== sourceSku.value?.id)
  } finally {
    skuLoading.value = false
  }
}

const showEdit = async (row: any) => {
  dialogFormVisible.value = true
  // 先置 loading，避免首帧渲染闪现"无可替换市场"空态
  distributionLoading.value = true
  sourceSku.value = row
  markets.value = []
  skuOptions.value = []
  Object.assign(form, { countryIds: [], targetSkuId: undefined })
  await nextTick()
  formRef.value?.clearValidate()
  await loadDistribution()
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  Object.assign(form, { countryIds: [], targetSkuId: undefined })
}

const save = () => {
  formRef.value.validate((valid: any) => {
    if (!valid) return
    $baseConfirm(
      `将影响 ${affectedCount.value} 个商品，确认将该 SKU 替换为所选目标 SKU？此操作不可撤销。`,
      null,
      async () => {
        try {
          saveLoading.value = true
          const { data }: any = await replaceSku({
            sourceSkuId: sourceSku.value.id,
            targetSkuId: form.targetSkuId,
            countryIds: form.countryIds,
          })
          $baseMessage(`已替换 ${data.affectedProductCount} 个商品`, 'success', 'hey')
          dialogFormVisible.value = false
          emit('fetch-data')
        } finally {
          saveLoading.value = false
        }
      }
    )
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
