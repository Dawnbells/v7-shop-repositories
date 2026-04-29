<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body class="ai-account-edit-dialog" :title="title" width="960px" @close="close">
    <el-form ref="formRef" class="ai-account-form" label-width="96px" :model="form" :rules="rules">
      <section class="form-section">
        <div class="section-title">基础信息</div>
        <el-row class="form-grid" :gutter="16">
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
            <el-form-item label="服务商" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择服务商" style="width: 100%" @change="handleProviderChange">
                <el-option label="Gemini" value="GEMINI" />
                <el-option label="OpenAI" value="OPENAI" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="API渠道" prop="apiChannel">
              <el-select v-model="form.apiChannel" placeholder="请选择API渠道" style="width: 100%">
                <el-option label="官方" value="OFFICIAL" />
                <el-option label="Sub2API" value="SUB2API" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.provider === 'GEMINI'" :span="12">
            <el-form-item label="接口模式" prop="invokeMode">
              <el-radio-group v-model="form.invokeMode">
                <el-radio-button label="STANDARD">标准接口</el-radio-button>
                <el-radio-button label="BATCH">批量接口</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="描述" prop="description">
              <el-input v-model.trim="form.description" clearable placeholder="账号用途说明" />
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section">
        <div class="section-title">接口配置</div>
        <el-row class="form-grid" :gutter="16">
          <el-col :span="24">
            <el-form-item label="API Key" prop="apiKey">
              <el-input v-model.trim="form.apiKey" clearable placeholder="官方或Sub2API提供的API Key" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Base URL" prop="baseUrl">
              <el-input v-model.trim="form.baseUrl" clearable :placeholder="baseUrlPlaceholder" />
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section">
        <div class="section-title">流控配置</div>
        <el-row class="form-grid" :gutter="16">
          <el-col :span="12">
            <el-form-item label="每日调用上限" prop="dailyLimit">
              <el-input-number
                v-model="form.dailyLimit"
                controls-position="right"
                :min="0"
                :precision="0"
                placeholder="不填则不限制"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" controls-position="right" :min="0" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section billing-section">
        <div class="section-title">计费配置</div>
        <div class="billing-table">
          <div class="billing-header">
            <div />
            <div>输入价格</div>
            <div>输入单位</div>
            <div>输出价格</div>
            <div>输出单位</div>
          </div>
          <div v-for="item in billingRows" :key="item.name" class="billing-row">
            <div class="billing-name">{{ item.name }}</div>
            <div class="billing-cell">
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
            <div class="billing-cell">
              <el-form-item label-width="0" :prop="item.inputUnit">
                <el-select v-model="form[item.inputUnit]" clearable placeholder="请选择单位" @change="validateBillingPair(item.inputPrice, item.inputUnit)">
                  <el-option v-for="unit in priceUnitOptions" :key="unit.value" :label="unit.label" :value="unit.value" />
                </el-select>
              </el-form-item>
            </div>
            <div class="billing-cell">
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
            <div class="billing-cell">
              <el-form-item label-width="0" :prop="item.outputUnit">
                <el-select v-model="form[item.outputUnit]" clearable placeholder="请选择单位" @change="validateBillingPair(item.outputPrice, item.outputUnit)">
                  <el-option v-for="unit in priceUnitOptions" :key="unit.value" :label="unit.label" :value="unit.value" />
                </el-select>
              </el-form-item>
            </div>
          </div>
        </div>
      </section>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="save">保存</el-button>
      </div>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
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
  provider: 'GEMINI',
  apiChannel: 'OFFICIAL',
  invokeMode: 'STANDARD',
  apiKey: '',
  baseUrl: '',
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
  dailyLimit: undefined,
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
    inputPrice: 'textInputPrice',
    inputUnit: 'textInputPriceUnit',
    outputPrice: 'textOutputPrice',
    outputUnit: 'textOutputPriceUnit',
  },
  {
    name: '图片',
    inputPrice: 'imageInputPrice',
    inputUnit: 'imageInputPriceUnit',
    outputPrice: 'imageOutputPrice',
    outputUnit: 'imageOutputPriceUnit',
  },
  {
    name: '视频',
    inputPrice: 'videoInputPrice',
    inputUnit: 'videoInputPriceUnit',
    outputPrice: 'videoOutputPrice',
    outputUnit: 'videoOutputPriceUnit',
  },
]

const baseUrlPlaceholder = computed(() => {
  if (form.apiChannel === 'SUB2API') return '请输入Sub2API接口地址，例如 https://api.example.com'
  return '官方渠道可留空；代理接口可填写'
})

const validateBaseUrl = (_rule: any, value: string, callback: any) => {
  if (form.apiChannel === 'SUB2API' && !value) {
    callback(new Error('Sub2API渠道必须填写Base URL'))
    return
  }
  callback()
}

const validateInvokeMode = (_rule: any, value: string, callback: any) => {
  if (form.provider === 'GEMINI' && !value) {
    callback(new Error('请选择Gemini接口模式'))
    return
  }
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
  provider: [{ required: true, trigger: 'change', message: '请选择服务商' }],
  apiChannel: [{ required: true, trigger: 'change', message: '请选择API渠道' }],
  invokeMode: [{ validator: validateInvokeMode, trigger: 'change' }],
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
  if (form.provider !== 'GEMINI') {
    form.invokeMode = 'STANDARD'
  } else {
    form.invokeMode = form.invokeMode || 'STANDARD'
  }
  formRef.value?.clearValidate('invokeMode')
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
      if (form.provider !== 'GEMINI') {
        form.invokeMode = 'STANDARD'
      }
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
  padding: 12px 10px 2px;

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  :deep(.el-input-number .el-input__inner) {
    text-align: left;
  }
}

.form-section {
  padding: 10px 0 8px;

  & + & {
    margin-top: 14px;
    border-top: 1px solid var(--el-border-color-lighter);
    padding-top: 22px;
  }
}

.form-grid {
  row-gap: 2px;
}

.section-title {
  margin-bottom: 18px;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}

.billing-section {
  padding-bottom: 2px;
}

.billing-table {
  display: grid;
  gap: 12px;
  margin-top: 0;
}

.billing-header {
  display: grid;
  grid-template-columns: 96px minmax(150px, 180px) minmax(190px, 1fr) minmax(150px, 180px) minmax(190px, 1fr);
  gap: 18px;
  padding: 0 20px 0 20px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 22px;
}

.billing-row {
  display: grid;
  grid-template-columns: 96px minmax(150px, 180px) minmax(190px, 1fr) minmax(150px, 180px) minmax(190px, 1fr);
  align-items: start;
  gap: 18px;
  min-height: 74px;
  padding: 16px 20px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.billing-name {
  padding-top: 8px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.billing-cell {
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
    padding-top: 6px;
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
  gap: 8px;
}
</style>
