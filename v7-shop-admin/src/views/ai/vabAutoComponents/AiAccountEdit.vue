<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body class="ai-account-edit-dialog" :title="title" width="960px" @close="close">
    <el-form ref="formRef" class="ai-account-form" label-width="96px" :model="form" :rules="rules">
      <section class="form-section">
        <div class="section-title">基础信息</div>
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
            <el-form-item label="服务商" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择服务商" style="width: 100%">
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
          <el-col :span="24">
            <el-form-item label="描述" prop="description">
              <el-input v-model.trim="form.description" clearable placeholder="账号用途说明" />
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section">
        <div class="section-title">接口配置</div>
        <el-row :gutter="16">
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
        <div class="section-title">使用设置</div>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="每日限额" prop="dailyLimit">
              <el-input-number v-model="form.dailyLimit" controls-position="right" :min="0" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" controls-position="right" :min="0" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="是否启用" prop="enabled">
              <div class="switch-line">
                <el-switch v-model="form.enabled" />
                <span>{{ form.enabled ? '启用' : '停用' }}</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section billing-section">
        <div class="section-title">计费配置</div>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="计费币种" prop="billingCurrency">
              <el-input v-model.trim="form.billingCurrency" clearable placeholder="USD" />
            </el-form-item>
          </el-col>
        </el-row>
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
              <el-form-item :prop="item.inputPrice">
                <el-input-number v-model="form[item.inputPrice]" :controls="false" :min="0" :precision="6" placeholder="0.000000" />
              </el-form-item>
            </div>
            <div class="billing-cell">
              <el-form-item :prop="item.inputUnit">
                <el-select v-model="form[item.inputUnit]" clearable placeholder="请选择单位">
                  <el-option v-for="unit in priceUnitOptions" :key="unit.value" :label="unit.label" :value="unit.value" />
                </el-select>
              </el-form-item>
            </div>
            <div class="billing-cell">
              <el-form-item :prop="item.outputPrice">
                <el-input-number v-model="form[item.outputPrice]" :controls="false" :min="0" :precision="6" placeholder="0.000000" />
              </el-form-item>
            </div>
            <div class="billing-cell">
              <el-form-item :prop="item.outputUnit">
                <el-select v-model="form[item.outputUnit]" clearable placeholder="请选择单位">
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
  enabled: true,
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

const hasAnyPrice = () => billingItems.some((item) => form[item.price] !== undefined && form[item.price] !== null)

const validateBaseUrl = (_rule: any, value: string, callback: any) => {
  if (form.apiChannel === 'SUB2API' && !value) {
    callback(new Error('Sub2API渠道必须填写Base URL'))
    return
  }
  callback()
}

const validateBillingCurrency = (_rule: any, value: string, callback: any) => {
  if (hasAnyPrice() && !value) {
    callback(new Error('填写价格时必须填写计费币种'))
    return
  }
  callback()
}

const createPriceUnitValidator = (priceField: string, unitField: string, label: string) => {
  return (_rule: any, _value: string, callback: any) => {
    const price = form[priceField]
    const unit = form[unitField]
    if ((price === undefined || price === null) && unit) {
      callback(new Error(`${label}已选择单位时必须填写价格`))
      return
    }
    if (price !== undefined && price !== null && !unit) {
      callback(new Error(`${label}已填写价格时必须选择单位`))
      return
    }
    callback()
  }
}

const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入账号名称' }],
  provider: [{ required: true, trigger: 'change', message: '请选择服务商' }],
  apiChannel: [{ required: true, trigger: 'change', message: '请选择API渠道' }],
  apiKey: [{ required: true, trigger: 'blur', message: '请输入API Key' }],
  baseUrl: [{ validator: validateBaseUrl, trigger: 'blur' }],
  model: [{ required: true, trigger: 'blur', message: '请输入模型' }],
  billingCurrency: [{ validator: validateBillingCurrency, trigger: 'blur' }],
})

billingItems.forEach((item) => {
  rules[item.price] = [{ validator: createPriceUnitValidator(item.price, item.unit, item.priceLabel), trigger: 'blur' }]
  rules[item.unit] = [{ validator: createPriceUnitValidator(item.price, item.unit, item.priceLabel), trigger: 'change' }]
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    Object.assign(form, defaultForm())
    if (row) {
      title.value = '编辑'
      Object.assign(form, row)
    } else {
      title.value = '添加'
    }
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
  padding: 4px 8px 0;

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-input-number .el-input__inner) {
    text-align: left;
  }
}

.form-section {
  padding: 6px 0 4px;

  & + & {
    margin-top: 10px;
    border-top: 1px solid var(--el-border-color-lighter);
    padding-top: 18px;
  }
}

.section-title {
  margin-bottom: 14px;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}

.switch-line {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 32px;
  color: var(--el-text-color-regular);
}

.billing-section {
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.billing-table {
  display: grid;
  gap: 12px;
  margin-top: 4px;
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

  :deep(.el-form-item__content) {
    display: block;
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
