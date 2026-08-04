<template>
  <div v-loading="loading">
    <vab-card>
      <template #header>
        <div class="header">
          <div>
            <div class="title">{{ pageTitle }}</div>
            <div class="description">{{ companyScope ? '公司级邮件配置' : '部门级邮件配置' }}</div>
          </div>
          <div class="actions">
            <el-button :loading="loading" @click="load">刷新</el-button>
            <el-button :loading="saving" type="primary" @click="save">保存</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="companyScope && isUnified"
        :closable="false"
        show-icon
        title="公司统一邮件发送已开启"
        type="warning"
      >
        所有未禁用部门将统一使用公司的 SMTP 或 Amazon SES 配置；部门发送配置
        暂停生效，但部门及上级部门的订单创建模板仍按优先级使用。
      </el-alert>
      <el-alert
        v-else-if="!companyScope && companyUnified"
        :closable="false"
        show-icon
        title="公司当前统一发送邮件"
        type="warning"
      >
        本部门邮件发送配置已暂停生效并设为只读；部门状态和订单创建模板仍按规则生效。
      </el-alert>

      <el-tabs v-model="activeTab" class="email-tabs">
        <el-tab-pane label="邮箱设置" name="email">
          <el-form label-width="170px">
            <el-form-item v-if="companyScope" label="开启邮件通知">
              <el-switch v-model="form.email.open" />
            </el-form-item>
            <el-form-item v-if="companyScope" label="发送配置来源">
              <el-select v-model="form.email['smtp-mode']" style="width: 100%">
                <el-option label="部门逐级继承" value="DEPARTMENT_INHERITANCE" />
                <el-option label="公司统一发送" value="COMPANY_UNIFIED" />
              </el-select>
            </el-form-item>
            <el-form-item v-else label="部门邮件状态">
              <el-select v-model="form.email.state" style="width: 100%">
                <el-option label="继承上级或公司" value="INHERIT" />
                <el-option label="使用本部门发送配置" value="ENABLED" />
                <el-option label="明确禁用" value="DISABLED" />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">邮件发送配置</el-divider>
            <fieldset class="smtp-fields" :disabled="smtpDisabled">
              <el-form-item label="发送方式">
                <el-select v-model="form.email.provider" style="width: 100%">
                  <el-option label="通用 SMTP" value="SMTP" />
                  <el-option label="Amazon SES API" value="AMAZON_SES" />
                </el-select>
              </el-form-item>
              <template v-if="isAmazonSes">
                <el-alert :closable="false" show-icon type="info">
                  发件人或域名必须已在所选 SES 区域完成验证；沙盒账户的测试收件人也必须已验证。
                </el-alert>
                <el-form-item label="AWS 区域">
                  <el-input v-model="form.email.region" placeholder="例如 eu-central-1" />
                </el-form-item>
                <el-form-item label="Access Key ID">
                  <el-input v-model="form.email['access-key-id']" autocomplete="off" />
                </el-form-item>
                <el-form-item label="Secret Access Key">
                  <el-input
                    v-model="form.email['secret-access-key']"
                    autocomplete="new-password"
                    show-password
                    type="password"
                  />
                </el-form-item>
                <el-form-item label="已验证发件人地址">
                  <el-input v-model="form.email.from" placeholder="sender@example.com" />
                </el-form-item>
                <el-form-item label="Configuration Set">
                  <el-input v-model="form.email['configuration-set']" placeholder="可选" />
                </el-form-item>
              </template>
              <template v-else>
                <el-form-item label="SMTP 服务器地址">
                  <el-input v-model="form.email.host" />
                </el-form-item>
                <el-form-item label="端口">
                  <el-input-number v-model="form.email.port" :max="65535" :min="1" />
                </el-form-item>
                <el-form-item label="用户名">
                  <el-input v-model="form.email.username" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="form.email.password" show-password type="password" />
                </el-form-item>
                <el-form-item label="发件人地址">
                  <el-input v-model="form.email.from" />
                </el-form-item>
                <el-form-item label="使用 SSL/TLS">
                  <el-switch v-model="form.email.secure" />
                </el-form-item>
              </template>
            </fieldset>

            <template v-if="companyScope">
              <el-divider content-position="left">发送测试</el-divider>
              <el-form-item label="测试收件人">
                <div class="test-row">
                  <el-input v-model="testRecipient" placeholder="请输入实际接收测试邮件的邮箱" />
                  <el-button :loading="testing" type="success" @click="testSmtp">
                    发送测试邮件
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="测试状态">
                <el-tag :type="smtpTested ? 'success' : 'warning'">
                  {{ smtpTested ? '当前邮件发送配置已通过测试' : '待测试或配置已变更' }}
                </el-tag>
              </el-form-item>
            </template>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="订单创建模板" name="email-template" :disabled="templateDisabled">
          <div class="template-toolbar">
            <el-button :disabled="templateDisabled" type="primary" @click="openLanguageDialog">
              添加语言
            </el-button>
          </div>
          <el-empty
            v-if="languages.length === 0"
            description="未配置模板，将继续继承上级或公司模板"
          />
          <el-tabs v-else v-model="activeLanguage" tab-position="left" @tab-remove="removeLanguage">
            <el-tab-pane
              v-for="language in languages"
              :key="language.code"
              closable
              :label="language.cname || language.code"
              :name="language.code"
            >
              <el-form label-width="150px">
                <dynamic-form-field
                  v-for="(fieldSchema, key) in templateSchema"
                  :key="String(key)"
                  :field-key="String(key)"
                  :field-schema="fieldSchema"
                  :model-value="form['email-template'][language.code]?.[key]"
                  @update:model-value="updateTemplate(language.code, String(key), $event)"
                />
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>
      </el-tabs>
    </vab-card>

    <vab-dialog v-model="languageDialog" append-to-body title="添加语言" width="500px">
      <el-select
        v-model="selectedLanguage"
        filterable
        :loading="languageLoading"
        placeholder="搜索并选择语言"
        remote
        :remote-method="queryLanguages"
        style="width: 100%"
        value-key="id"
      >
        <el-option
          v-for="language in languageOptions"
          :key="language.id"
          :disabled="languages.some((item) => item.code === language.code)"
          :label="`${language.cname} (${language.code})`"
          :value="language"
        />
      </el-select>
      <template #footer>
        <el-button @click="languageDialog = false">取消</el-button>
        <el-button type="primary" @click="addLanguage">添加</el-button>
      </template>
    </vab-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { getConfigCenterSchema, getConfigCenterValue, saveConfigCenter } from '/@/api/configCenter'
import { getRemoteQueryLanguage } from '/@/api/language'
import request from '/@/utils/request'
import DynamicFormField from '/@/views/website/vabAutoComponents/DynamicFormField.vue'

defineOptions({ name: 'EmailConfigurationPage' })

const props = withDefaults(
  defineProps<{
    departmentId?: number
    pageTitle?: string
  }>(),
  { pageTitle: '邮件配置' }
)

const $baseMessage = inject<any>('$baseMessage')
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const activeTab = ref('email')
const testRecipient = ref('')
const savedMode = ref('DEPARTMENT_INHERITANCE')
const smtpTested = ref(false)
const initializing = ref(false)
const templateSchema = ref<Record<string, any>>({})
const languages = ref<any[]>([])
const activeLanguage = ref('')
const languageDialog = ref(false)
const selectedLanguage = ref<any>(null)
const languageLoading = ref(false)
const languageOptions = ref<any[]>([])
const companySmtpMode = ref('DEPARTMENT_INHERITANCE')

const form = reactive<any>({
  email: {
    open: false,
    state: 'INHERIT',
    'smtp-mode': 'DEPARTMENT_INHERITANCE',
    provider: 'SMTP',
    host: '',
    port: 587,
    username: '',
    password: '',
    from: '',
    secure: false,
    region: 'eu-central-1',
    'access-key-id': '',
    'secret-access-key': '',
    'configuration-set': '',
  },
  'email-template': {},
})

const companyScope = computed(() => props.departmentId === undefined)
const isUnified = computed(() => form.email['smtp-mode'] === 'COMPANY_UNIFIED')
const isAmazonSes = computed(() => form.email.provider === 'AMAZON_SES')
const companyUnified = computed(() => companySmtpMode.value === 'COMPANY_UNIFIED')
const departmentDisabled = computed(() => !companyScope.value && form.email.state === 'DISABLED')
const smtpDisabled = computed(
  () => !companyScope.value && (companyUnified.value || form.email.state !== 'ENABLED')
)
const templateDisabled = computed(() => departmentDisabled.value)

const unwrap = (response: any) => response?.data ?? response ?? {}
const clone = (value: any) => JSON.parse(JSON.stringify(value ?? {}))

const rebuildLanguages = () => {
  languages.value = Object.keys(form['email-template'] || {}).map((code) => {
    return languageOptions.value.find((item: any) => item.code === code) || { code, cname: code }
  })
  activeLanguage.value = languages.value[0]?.code || ''
}

const load = async () => {
  loading.value = true
  initializing.value = true
  try {
    const [schemaResponse, valueResponse, policyResponse]: any[] = await Promise.all([
      getConfigCenterSchema('email'),
      getConfigCenterValue('email', { departmentId: props.departmentId }),
      request({ url: '/config-center/email/policy', method: 'get' }),
    ])
    const schema = unwrap(schemaResponse)
    templateSchema.value = schema?.['email-template'] || {}
    const value = unwrap(valueResponse)
    const policy = unwrap(policyResponse)
    companySmtpMode.value = policy.smtpMode || 'DEPARTMENT_INHERITANCE'

    const emailDefaults =
      schema?.tabs?.find((tab: any) => tab.name === 'email')?.['default-values'] || {}
    form.email = {
      ...clone(emailDefaults),
      ...clone(value.email),
    }
    form.email.provider ||= 'SMTP'
    form.email.region ||= 'eu-central-1'
    if (companyScope.value) {
      form.email['smtp-mode'] ||= 'DEPARTMENT_INHERITANCE'
      savedMode.value = form.email['smtp-mode']
    } else {
      form.email.state ||=
        value.email?.open === true ? 'ENABLED' : value.email ? 'DISABLED' : 'INHERIT'
    }
    form['email-template'] = clone(value['email-template'] || {})
    smtpTested.value = Boolean(policy.companySmtpTested)
    await queryLanguages('')
    rebuildLanguages()
  } catch (error) {
    console.error(error)
    $baseMessage('加载邮件配置失败', 'error', 'hey')
  } finally {
    initializing.value = false
    loading.value = false
  }
}

const smtpSnapshot = computed(() =>
  JSON.stringify([
    form.email.provider,
    form.email.host,
    form.email.port,
    form.email.username,
    form.email.password,
    form.email.from,
    form.email.secure,
    form.email.region,
    form.email['access-key-id'],
    form.email['secret-access-key'],
    form.email['configuration-set'],
  ])
)

watch(smtpSnapshot, () => {
  if (initializing.value) return
  smtpTested.value = false
  delete form.email['smtp-test-signature']
})

watch(
  () => props.departmentId,
  () => load()
)

const testSmtp = async () => {
  if (!testRecipient.value) {
    $baseMessage('请输入测试收件人', 'warning', 'hey')
    return
  }
  testing.value = true
  try {
    const templates = Object.values(form['email-template'] || {}) as any[]
    const orderTemplate =
      templates.find(
        (template) => template?.default && template?.subject?.trim() && template?.content?.trim()
      ) || templates.find((template) => template?.subject?.trim() && template?.content?.trim())
    const response: any = await request({
      url: '/config-center/email/test',
      method: 'post',
      data: {
        recipient: testRecipient.value,
        emailConfig: clone(form.email),
        orderTemplate: clone(orderTemplate),
      },
    })
    const data = unwrap(response)
    form.email['smtp-test-signature'] = data.smtpTestSignature
    smtpTested.value = true
    $baseMessage('测试邮件发送成功', 'success', 'hey')
  } finally {
    testing.value = false
  }
}

const save = async () => {
  if (companyScope.value && isUnified.value) {
    if (!form.email.open) {
      $baseMessage('请先开启公司邮件通知', 'warning', 'hey')
      return
    }
    if (!smtpTested.value || !form.email['smtp-test-signature']) {
      $baseMessage('请先发送并成功接收测试邮件', 'warning', 'hey')
      return
    }
    if (savedMode.value !== 'COMPANY_UNIFIED') {
      await ElMessageBox.confirm(
        '开启后，所有未禁用部门将统一使用公司邮件发送配置，部门发送配置暂停生效。是否继续？',
        '确认开启公司统一发送',
        { confirmButtonText: '确认开启', cancelButtonText: '取消', type: 'warning' }
      )
    }
  }

  saving.value = true
  try {
    form.email.open = companyScope.value ? Boolean(form.email.open) : form.email.state === 'ENABLED'
    await saveConfigCenter({
      configName: 'email',
      departmentId: props.departmentId,
      configValue: clone(form),
    })
    savedMode.value = form.email['smtp-mode']
    $baseMessage('保存成功', 'success', 'hey')
    await load()
  } finally {
    saving.value = false
  }
}

const queryLanguages = async (query: string) => {
  languageLoading.value = true
  try {
    const response: any = await getRemoteQueryLanguage(query || '')
    languageOptions.value = response?.data?.list || []
  } finally {
    languageLoading.value = false
  }
}

const openLanguageDialog = async () => {
  await queryLanguages('')
  selectedLanguage.value = null
  languageDialog.value = true
}

const addLanguage = () => {
  if (!selectedLanguage.value) return
  const code = selectedLanguage.value.code
  if (!form['email-template'][code]) {
    form['email-template'][code] = {}
    Object.keys(templateSchema.value).forEach((key) => {
      form['email-template'][code][key] = templateSchema.value[key]?.type === 'boolean' ? false : ''
    })
  }
  languages.value.push(selectedLanguage.value)
  activeLanguage.value = code
  languageDialog.value = false
}

const removeLanguage = (code: string | number) => {
  const value = String(code)
  delete form['email-template'][value]
  languages.value = languages.value.filter((item) => item.code !== value)
  activeLanguage.value = languages.value[0]?.code || ''
}

const updateTemplate = (language: string, key: string, value: any) => {
  form['email-template'][language] ||= {}
  form['email-template'][language][key] = value
}

onBeforeMount(load)
</script>

<style scoped lang="scss">
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
}

.description {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions,
.test-row {
  display: flex;
  gap: 12px;
}

.email-tabs {
  margin-top: 16px;
}

.smtp-fields {
  min-width: 0;
  padding: 0;
  margin: 0;
  border: 0;
}

.smtp-fields:disabled {
  opacity: 0.6;
}

.test-row {
  width: 100%;
}

.template-toolbar {
  margin-bottom: 16px;
}
</style>
