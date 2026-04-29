<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    title="AI 翻译"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item prop="aiAccountId">
        <template #label>
          <span class="label-with-help">
            AI账号
            <el-tooltip placement="right" :disabled="!selectedAiAccount" :show-after="150">
              <template #content>
                <div v-if="selectedAiAccount" class="ai-account-detail">
                  <div>账号名称：{{ selectedAiAccount.name || '-' }}</div>
                  <div>服务商：{{ providerLabel(selectedAiAccount.provider) }}</div>
                  <div>渠道：{{ apiChannelLabel(selectedAiAccount.apiChannel) }}</div>
                  <div>接口模式：{{ invokeModeLabel(selectedAiAccount.invokeMode) }}</div>
                  <div>模型：{{ selectedAiAccount.model || '-' }}</div>
                  <div>Base URL：{{ selectedAiAccount.baseUrl || '-' }}</div>
                  <div>API Key：{{ maskApiKey(selectedAiAccount.apiKey) }}</div>
                  <div>计费币种：{{ selectedAiAccount.billingCurrency || 'USD' }}</div>
                  <div>每日限额：{{ selectedAiAccount.dailyLimit ?? '-' }}</div>
                  <div>优先级：{{ selectedAiAccount.priority ?? '-' }}</div>
                  <div>文本输入：{{ formatPrice(selectedAiAccount.textInputPrice, selectedAiAccount.textInputPriceUnit) }}</div>
                  <div>文本输出：{{ formatPrice(selectedAiAccount.textOutputPrice, selectedAiAccount.textOutputPriceUnit) }}</div>
                  <div>图片输入：{{ formatPrice(selectedAiAccount.imageInputPrice, selectedAiAccount.imageInputPriceUnit) }}</div>
                  <div>图片输出：{{ formatPrice(selectedAiAccount.imageOutputPrice, selectedAiAccount.imageOutputPriceUnit) }}</div>
                  <div>视频输入：{{ formatPrice(selectedAiAccount.videoInputPrice, selectedAiAccount.videoInputPriceUnit) }}</div>
                  <div>视频输出：{{ formatPrice(selectedAiAccount.videoOutputPrice, selectedAiAccount.videoOutputPriceUnit) }}</div>
                  <div>描述：{{ selectedAiAccount.description || '-' }}</div>
                </div>
              </template>
              <el-icon class="help-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
        </template>
        <el-select
          v-model="form.aiAccountId"
          filterable
          placeholder="请选择AI账号"
          :loading="aiAccountLoading"
          style="width: 100%"
        >
          <el-option
            v-for="item in aiAccountOptions"
            :key="item.id"
            :label="aiAccountOptionLabel(item)"
            :value="String(item.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标国家" prop="countryId">
        <el-select
          v-model="form.countryId"
          filterable
          placeholder="请选择目标国家"
          :loading="countryLoading"
          style="width: 100%"
          @change="onCountryChange"
        >
          <el-option
            v-for="item in countryOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标语言" prop="languageId">
        <el-select
          v-model="form.languageId"
          :disabled="!form.countryId"
          filterable
          placeholder="请先选择目标国家"
          :loading="languageLoading"
          style="width: 100%"
        >
          <el-option
            v-for="item in languageOptions"
            :key="item.id"
            :disabled="item.disabled"
            :label="item.cname + ' (' + item.name + ')'"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button :loading="batchLoading" type="primary" @click="save('batch')">
        批量翻译
      </el-button>
      <el-button :loading="directLoading" type="success" @click="save('direct')">
        即时翻译
      </el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { QuestionFilled } from '@element-plus/icons-vue'
import { page as pageAiAccount } from '/@/api/aiAccount'
import { getRemoteQuery as getRemoteQueryCountry } from '/@/api/country'
import { translateByAI, translateByAIDirect } from '/@/api/product'
import { useTasksStore } from '/@/store/modules/tasks'

defineOptions({
  name: 'ProductTranslateDialog',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const tasksStore = useTasksStore()
const formRef = ref<any>(null)
const dialogFormVisible = ref<boolean>(false)
const batchLoading = ref<boolean>(false)
const directLoading = ref<boolean>(false)
const aiAccountLoading = ref<boolean>(false)
const countryLoading = ref<boolean>(false)
const languageLoading = ref<boolean>(false)
const aiAccountOptions = ref<any[]>([])
const countryOptions = ref<any[]>([])
const languageOptions = ref<any[]>([])
const form = reactive<any>({
  aiAccountId: '',
  productId: '',
  countryId: '',
  languageId: '',
})
const rules = reactive<any>({
  aiAccountId: [{ required: true, trigger: 'change', message: '请选择AI账号' }],
  countryId: [{ required: true, trigger: 'change', message: '请选择目标国家' }],
  languageId: [{ required: true, trigger: 'change', message: '请选择目标语言' }],
})

let currentProductTitle = ''
let existingLanguageCountryPairs = new Set<string>()

const selectedAiAccount = computed(() => aiAccountOptions.value.find((item: any) => String(item.id) === String(form.aiAccountId)))

const providerLabel = (provider?: string) => {
  if (provider === 'GEMINI') return 'Gemini'
  if (provider === 'OPENAI') return 'OpenAI'
  return provider || '-'
}

const apiChannelLabel = (apiChannel?: string) => {
  if (apiChannel === 'OFFICIAL') return '官方'
  if (apiChannel === 'SUB2API') return 'Sub2API'
  return apiChannel || '-'
}

const invokeModeLabel = (invokeMode?: string) => {
  if (invokeMode === 'BATCH') return '批量接口'
  if (invokeMode === 'STANDARD') return '标准接口'
  return invokeMode || '标准接口'
}

const priceUnitLabel = (unit?: string) => {
  const map: Record<string, string> = {
    PER_1M_TOKENS: '/百万Tokens',
    PER_1K_TOKENS: '/千Tokens',
    PER_IMAGE: '/张',
    PER_1K_IMAGES: '/千张',
    PER_VIDEO: '/个视频',
    PER_MINUTE: '/分钟',
    PER_SECOND: '/秒',
  }
  return unit ? map[unit] || unit : ''
}

const formatPrice = (price?: number | string | null, unit?: string) => {
  if (price === undefined || price === null || price === '') return '-'
  return `${price}${priceUnitLabel(unit)}`
}

const maskApiKey = (apiKey?: string) => {
  if (!apiKey) return '-'
  if (apiKey.length <= 8) return '已配置'
  return `${apiKey.slice(0, 4)}****${apiKey.slice(-4)}`
}

const aiAccountOptionLabel = (item: any) => {
  return `${item.name || '未命名'} / ${providerLabel(item.provider)} / ${item.model || '-'}`
}

const loadAiAccounts = async () => {
  aiAccountLoading.value = true
  try {
    const { data }: any = await pageAiAccount({
      pageNo: 1,
      pageSize: 100,
      status: 'VALID',
      sortBy: 'priority asc,id asc',
    })
    aiAccountOptions.value = data.list || []
  } finally {
    aiAccountLoading.value = false
  }
}

const onCountryChange = () => {
  form.languageId = ''
  loadLanguagesForCountry(form.countryId)
}

const loadLanguagesForCountry = (countryId: string) => {
  if (!countryId) {
    languageOptions.value = []
    return
  }
  languageLoading.value = true
  const selected = countryOptions.value.find((c: any) => c.id === countryId)
  const langs = selected?.languages ?? []
  languageOptions.value = langs.map((lang: any) => ({
    ...lang,
    disabled:
      existingLanguageCountryPairs.has(`${countryId}:${lang.id}`) ||
      tasksStore.isTranslatingProduct(form.productId, String(countryId), String(lang.id)),
  }))
  languageLoading.value = false
}

const showEdit = async (spuRow: any, productRow: any) => {
  form.aiAccountId = ''
  form.productId = String(productRow.id)
  form.countryId = ''
  form.languageId = ''
  currentProductTitle = productRow.title || `商品#${productRow.id}`
  dialogFormVisible.value = true

  existingLanguageCountryPairs = new Set(
    (spuRow.productList || []).map(
      (p: any) => `${p.country?.id}:${p.language?.id}`
    )
  )

  await loadAiAccounts()

  countryLoading.value = true
  try {
    const { data } = await getRemoteQueryCountry('')
    countryOptions.value = data.list || data
  } finally {
    countryLoading.value = false
  }
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  form.aiAccountId = ''
  form.productId = ''
  form.countryId = ''
  form.languageId = ''
  languageOptions.value = []
  emit('fetch-data')
}

const save = (mode: 'batch' | 'direct') => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      const isDirect = mode === 'direct'
      const loading = isDirect ? directLoading : batchLoading
      try {
        loading.value = true
        const apiFn = isDirect ? translateByAIDirect : translateByAI
        const taskType = isDirect ? 'PRODUCT_AI_TRANSLATE_DIRECT' : 'PRODUCT_AI_TRANSLATE'

        const { data }: any = await apiFn({
          aiAccountId: String(form.aiAccountId),
          productId: form.productId,
          countryId: String(form.countryId),
          languageId: String(form.languageId),
        })

        const selectedLang = languageOptions.value.find(
          (l: any) => l.id === form.languageId
        )
        const langLabel = selectedLang
          ? `${selectedLang.cname}(${selectedLang.name})`
          : form.languageId

        const modeLabel = isDirect ? '即时翻译' : 'AI翻译'

        tasksStore.addTask({
          taskId: String(data.id ?? data.taskId),
          taskType,
          name: data.name || '',
          label: data.name || `${modeLabel}: ${currentProductTitle} → ${langLabel}`,
          state: data.state || 'PENDING',
          progress: data.progress ?? 0,
          message: data.message || '',
          parameters: {
            aiAccountId: String(form.aiAccountId),
            productId: form.productId,
            countryId: String(form.countryId),
            languageId: String(form.languageId),
          },
        })

        $baseMessage('翻译任务已提交，请在右上角任务栏查看进度', 'success', 'hey')
        dialogFormVisible.value = false
      } catch {
        // Error already displayed by global interceptor
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  color: var(--el-text-color-secondary);
  cursor: help;
  font-size: 15px;
}

.ai-account-detail {
  display: grid;
  gap: 4px;
  max-width: 420px;
  line-height: 1.5;
}
</style>
