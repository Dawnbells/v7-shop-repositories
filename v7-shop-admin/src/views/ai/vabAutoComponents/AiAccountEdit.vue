<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body class="ai-account-edit-dialog" :title="title" width="980px" @close="close">
    <el-form ref="formRef" class="ai-account-form" label-width="96px" :model="form" :rules="rules">
      <div class="form-card">
        <div class="card-header">
          <el-icon class="card-icon"><Connection /></el-icon>
          <span>基础信息</span>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="账号名称" prop="name">
              <el-input v-model.trim="form.name" clearable placeholder="例如：Gemini官方翻译账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型" prop="model">
              <el-input v-model.trim="form.model" clearable placeholder="例如：gemini-2.5-pro" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账号类型" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择账号类型" style="width: 100%" @change="handleProviderChange">
                <el-option label="TurboFlow Gemini" value="TURBOFLOW_GEMINI" />
                <el-option label="Gemini 官方批量" value="GEMINI_OFFICIAL_BATCH" />
                <el-option label="Gemini 官方标准" value="GEMINI_OFFICIAL_STANDARD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="描述" prop="description">
              <el-input v-model.trim="form.description" clearable placeholder="账号用途说明" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="form-card">
        <div class="card-header">
          <el-icon class="card-icon"><Key /></el-icon>
          <span>接口配置</span>
        </div>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="API Key" prop="apiKey">
              <el-input v-model.trim="form.apiKey" clearable placeholder="官方或Sub2API提供的API Key" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Base URL" prop="baseUrl">
              <el-input v-model.trim="form.baseUrl" clearable :placeholder="baseUrlPlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="User-Agent" prop="userAgent">
              <el-input v-model.trim="form.userAgent" clearable placeholder="不填则使用默认请求头" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="form-card">
        <div class="card-header">
          <el-icon class="card-icon"><Odometer /></el-icon>
          <span>流控配置</span>
        </div>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="流控模式" prop="rateLimitMode">
              <el-select v-model="form.rateLimitMode" style="width: 100%">
                <el-option label="并发限制" value="CONCURRENCY" />
                <el-option label="RPD / RPM" value="RPD_RPM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.rateLimitMode === 'CONCURRENCY'" :span="8">
            <el-form-item label="最大并发" prop="maxConcurrency">
              <el-input-number v-model="form.maxConcurrency" controls-position="right" :min="1" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.rateLimitMode === 'RPD_RPM'" :span="8">
            <el-form-item label="每日请求数" prop="requestsPerDay">
              <el-input-number v-model="form.requestsPerDay" controls-position="right" :min="0" :precision="0" placeholder="RPD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.rateLimitMode === 'RPD_RPM'" :span="8">
            <el-form-item label="每分钟请求" prop="requestsPerMinute">
              <el-input-number v-model="form.requestsPerMinute" controls-position="right" :min="0" :precision="0" placeholder="RPM" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="每日上限" prop="dailyLimit">
              <el-input-number
                v-model="form.dailyLimit"
                controls-position="right"
                :min="0"
                :precision="0"
                placeholder="不限"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" controls-position="right" :min="0" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="form-card">
        <div class="card-header">
          <el-icon class="card-icon"><Coin /></el-icon>
          <span>计费配置</span>
          <el-tag class="currency-tag" size="small" type="info">USD</el-tag>
        </div>
        <div class="billing-grid">
          <div class="billing-grid-header">
            <div class="billing-label-col" />
            <div>输入价格</div>
            <div>输入单位</div>
            <div>输出价格</div>
            <div>输出单位</div>
          </div>
          <div v-for="item in billingRows" :key="item.name" class="billing-grid-row">
            <div class="billing-label-col">
              <span class="billing-dot" :class="item.color" />
              {{ item.name }}
            </div>
            <div class="billing-field">
              <el-form-item label-width="0" :prop="item.inputPrice">
                <el-input-number
                  v-model="form[item.inputPrice]"
                  :controls="false"
                  :min="0"
                  :precision="6"
                  placeholder="0.000000"
                  @change="validateBillingPair(item.inputPrice, item.inputUnit)"
                />
              </el-form-item>
            </div>
            <div class="billing-field">
              <el-form-item label-width="0" :prop="item.inputUnit">
                <el-select v-model="form[item.inputUnit]" clearable placeholder="选择单位" @change="validateBillingPair(item.inputPrice, item.inputUnit)">
                  <el-option v-for="unit in priceUnitOptions" :key="unit.value" :label="unit.label" :value="unit.value" />
                </el-select>
              </el-form-item>
            </div>
            <div class="billing-field">
              <el-form-item label-width="0" :prop="item.outputPrice">
                <el-input-number
                  v-model="form[item.outputPrice]"
                  :controls="false"
                  :min="0"
                  :precision="6"
                  placeholder="0.000000"
                  @change="validateBillingPair(item.outputPrice, item.outputUnit)"
                />
              </el-form-item>
            </div>
            <div class="billing-field">
              <el-form-item label-width="0" :prop="item.outputUnit">
                <el-select v-model="form[item.outputUnit]" clearable placeholder="选择单位" @change="validateBillingPair(item.outputPrice, item.outputUnit)">
                  <el-option v-for="unit in priceUnitOptions" :key="unit.value" :label="unit.label" :value="unit.value" />
                </el-select>
              </el-form-item>
            </div>
          </div>
        </div>
      </div>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogFormVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="save">保 存</el-button>
      </div>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { Coin, Connection, Key, Odometer } from '@element-plus/icons-vue'
import { doEdit } from '/@/api/aiAccount'

defineOptions({
  name: 'AiAccountEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)

const defaultForm = () => ({
  id: undefined,
  name: '',
  description: '',
  provider: 'GEMINI_OFFICIAL_STANDARD',
  apiKey: '',
  baseUrl: '',
  userAgent: '',
  model: '',
  textInputPrice: undefined,
  textInputPriceUnit: undefined,
  textOutputPrice: undefined,
  textOutputPriceUnit: undefined,
  imageInputPrice: undefined,
  imageInputPriceUnit: undefined,
  imageOutputPrice: undefined,
  imageOutputPriceUnit: undefined,
  videoInputPrice: undefined,
  videoInputPriceUnit: undefined,
  videoOutputPrice: undefined,
  videoOutputPriceUnit: undefined,
  billingCurrency: 'USD',
  rateLimitMode: 'CONCURRENCY',
  dailyLimit: undefined,
  requestsPerDay: undefined,
  requestsPerMinute: undefined,
  maxConcurrency: 1,
  priority: 100,
})

const form = reactive<any>(defaultForm())

const priceUnitOptions = [
  { label: '每百万Tokens', value: 'PER_1M_TOKENS' },
  { label: '每千Tokens', value: 'PER_1K_TOKENS' },
  { label: '每张图片', value: 'PER_IMAGE' },
  { label: '每千张图片', value: 'PER_1K_IMAGES' },
  { label: '每个视频', value: 'PER_VIDEO' },
  { label: '每分钟', value: 'PER_MINUTE' },
  { label: '每秒', value: 'PER_SECOND' },
]

const billingItems = [
  { price: 'textInputPrice', unit: 'textInputPriceUnit', priceLabel: '文本输入价格', unitLabel: '文本输入单位' },
  { price: 'textOutputPrice', unit: 'textOutputPriceUnit', priceLabel: '文本输出价格', unitLabel: '文本输出单位' },
  { price: 'imageInputPrice', unit: 'imageInputPriceUnit', priceLabel: '图片输入价格', unitLabel: '图片输入单位' },
  { price: 'imageOutputPrice', unit: 'imageOutputPriceUnit', priceLabel: '图片输出价格', unitLabel: '图片输出单位' },
  { price: 'videoInputPrice', unit: 'videoInputPriceUnit', priceLabel: '视频输入价格', unitLabel: '视频输入单位' },
  { price: 'videoOutputPrice', unit: 'videoOutputPriceUnit', priceLabel: '视频输出价格', unitLabel: '视频输出单位' },
]

const billingRows = [
  {
    name: '文本',
    color: 'dot-text',
    inputPrice: 'textInputPrice',
    inputUnit: 'textInputPriceUnit',
    outputPrice: 'textOutputPrice',
    outputUnit: 'textOutputPriceUnit',
  },
  {
    name: '图片',
    color: 'dot-image',
    inputPrice: 'imageInputPrice',
    inputUnit: 'imageInputPriceUnit',
    outputPrice: 'imageOutputPrice',
    outputUnit: 'imageOutputPriceUnit',
  },
  {
    name: '视频',
    color: 'dot-video',
    inputPrice: 'videoInputPrice',
    inputUnit: 'videoInputPriceUnit',
    outputPrice: 'videoOutputPrice',
    outputUnit: 'videoOutputPriceUnit',
  },
]

const baseUrlPlaceholder = computed(() => {
  if (form.provider === 'TURBOFLOW_GEMINI') return 'TurboFlow 插件不使用该字段，可留空'
  return 'Gemini 官方接口可留空'
})

const validateBaseUrl = (_rule: any, value: string, callback: any) => {
  callback()
}

const hasValue = (value: any) => value !== undefined && value !== null && value !== ''

const createBillingPriceValidator = (priceField: string, unitField: string, label: string) => {
  return (_rule: any, _value: string, callback: any) => {
    const price = form[priceField]
    const unit = form[unitField]
    if (!hasValue(price) && hasValue(unit)) {
      callback(new Error(`${label}不能为空`))
      return
    }
    callback()
  }
}

const createBillingUnitValidator = (priceField: string, unitField: string, label: string) => {
  return (_rule: any, _value: string, callback: any) => {
    const price = form[priceField]
    const unit = form[unitField]
    if (hasValue(price) && !hasValue(unit)) {
      callback(new Error(`${label}不能为空`))
      return
    }
    callback()
  }
}

const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入账号名称' }],
  provider: [{ required: true, trigger: 'change', message: '请选择账号类型' }],
  apiKey: [{ required: true, trigger: 'blur', message: '请输入API Key' }],
  baseUrl: [{ validator: validateBaseUrl, trigger: 'blur' }],
  model: [{ required: true, trigger: 'blur', message: '请输入模型' }],
})

billingItems.forEach((item) => {
  rules[item.price] = [{ validator: createBillingPriceValidator(item.price, item.unit, item.priceLabel), trigger: 'blur' }]
  rules[item.unit] = [{ validator: createBillingUnitValidator(item.price, item.unit, item.unitLabel), trigger: 'change' }]
})

const validateBillingPair = (priceField: string, unitField: string) => {
  nextTick(() => {
    formRef.value?.validateField(priceField, () => undefined)
    formRef.value?.validateField(unitField, () => undefined)
  })
}

const handleProviderChange = () => {
  formRef.value?.clearValidate('provider')
}

const showEdit = (row: any, copy = false) => {
  dialogFormVisible.value = true
  nextTick(() => {
    Object.assign(form, defaultForm())
    if (row) {
      title.value = copy ? '添加' : '编辑'
      const source = { ...row }
      if (copy) {
        delete source.id
        delete source.compactId
        delete source.status
      }
      Object.assign(form, source)
    } else {
      title.value = '添加'
    }
    handleProviderChange()
    form.billingCurrency = form.billingCurrency || 'USD'
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  Object.assign(form, defaultForm())
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (!valid) return
    try {
      saveLoading.value = true
      form.billingCurrency = 'USD'
      form.dailyLimit = form.dailyLimit === '' || form.dailyLimit === null ? undefined : form.dailyLimit
      const { msg }: any = await doEdit(form)
      await $baseMessage(msg, 'success', 'hey')
      dialogFormVisible.value = false
    } finally {
      saveLoading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.ai-account-form {
  padding: 4px 6px 0;

  :deep(.el-form-item) {
    margin-bottom: 24px;
  }

  :deep(.el-input-number .el-input__inner) {
    text-align: left;
  }
}

.form-card {
  padding: 22px 24px 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 10px;

  & + & {
    margin-top: 16px;
  }

  :deep(.el-row) {
    row-gap: 8px;
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.card-icon {
  font-size: 17px;
  color: var(--el-color-primary);
}

.currency-tag {
  margin-left: auto;
}

.billing-grid {
  display: grid;
  gap: 10px;
}

.billing-grid-header {
  display: grid;
  grid-template-columns: 72px 1fr 1fr 1fr 1fr;
  gap: 12px;
  padding: 0 14px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.billing-grid-row {
  display: grid;
  grid-template-columns: 72px 1fr 1fr 1fr 1fr;
  align-items: start;
  gap: 12px;
  padding: 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.04);
  }
}

.billing-label-col {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 8px;
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.billing-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &.dot-text {
    background: var(--el-color-primary);
  }

  &.dot-image {
    background: var(--el-color-success);
  }

  &.dot-video {
    background: var(--el-color-warning);
  }
}

.billing-field {
  min-width: 0;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    display: none;
    width: 0 !important;
  }

  :deep(.el-form-item__content) {
    display: block;
    margin-left: 0 !important;
    line-height: 1.4;
  }

  :deep(.el-form-item__error) {
    position: static;
    padding-top: 4px;
    line-height: 16px;
    white-space: normal;
  }

  :deep(.el-input-number),
  :deep(.el-select) {
    width: 100%;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
