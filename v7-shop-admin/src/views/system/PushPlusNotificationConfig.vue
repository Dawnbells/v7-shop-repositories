<template>
  <div class="pushplus-notification-container">
    <el-row :gutter="20">
      <el-col :lg="14" :md="18" :sm="24" :xs="24">
        <el-card v-loading="loading" shadow="never">
          <template #header>
            <div class="card-header">
              <span>微信通知配置</span>
              <el-tag v-if="tokenSet" type="success">已保存 Token</el-tag>
              <el-tag v-else type="info">未保存 Token</el-tag>
            </div>
          </template>

          <el-form label-position="top" :model="form" @submit.prevent>
            <el-form-item label="AI翻译任务通知">
              <el-switch v-model="form.open" />
            </el-form-item>

            <el-form-item label="服务器IP切换通知">
              <el-switch v-model="form.serverIpSwitchOpen" />
            </el-form-item>

            <el-form-item label="PushPlus Token">
              <el-input
                v-model="form.token"
                clearable
                placeholder="留空则保留旧 Token"
                show-password
                type="password"
              />
            </el-form-item>

            <el-form-item label="模板类型">
              <el-select v-model="form.template" placeholder="请选择模板类型" style="width: 220px">
                <el-option label="Markdown" value="markdown" />
                <el-option label="HTML" value="html" />
                <el-option label="纯文本" value="txt" />
                <el-option label="JSON" value="json" />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button :loading="saving" type="primary" @click="handleSave">保存配置</el-button>
              <el-button :loading="loading" @click="fetchConfig">刷新</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :lg="10" :md="18" :sm="24" :xs="24">
        <el-card shadow="never">
          <template #header>
            <span>测试发送</span>
          </template>

          <el-form label-position="top" :model="testForm" @submit.prevent>
            <el-form-item label="测试内容">
              <el-input
                v-model="testForm.content"
                :autosize="{ minRows: 6, maxRows: 10 }"
                placeholder="请输入测试通知内容"
                type="textarea"
              />
            </el-form-item>
            <el-form-item>
              <el-button :loading="testing" type="primary" @click="handleTestSend">发送测试</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" setup>
import {
  getPushPlusNotificationConfig,
  savePushPlusNotificationConfig,
  testPushPlusNotification,
  type PushPlusNotificationConfig,
  type PushPlusNotificationTestResult,
  type PushPlusTemplate,
} from '/@/api/pushPlusNotification'

defineOptions({
  name: 'PushPlusNotificationConfig',
})

const $baseMessage = inject<any>('$baseMessage')

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const tokenSet = ref(false)

const form = reactive({
  open: false,
  serverIpSwitchOpen: false,
  token: '',
  template: 'markdown' as PushPlusTemplate,
})

const testForm = reactive({
  content: 'AI翻译任务微信通知测试',
})

const fetchConfig = async () => {
  loading.value = true
  try {
    const { data } = await getPushPlusNotificationConfig()
    const config = (data || {}) as Partial<PushPlusNotificationConfig>
    form.open = Boolean(config.open)
    form.serverIpSwitchOpen = Boolean(config.serverIpSwitchOpen)
    form.template = (config.template || 'markdown') as PushPlusTemplate
    form.token = ''
    tokenSet.value = Boolean(config.tokenSet)
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    const token = form.token.trim()
    const { msg }: any = await savePushPlusNotificationConfig({
      open: form.open,
      serverIpSwitchOpen: form.serverIpSwitchOpen,
      template: form.template,
      ...(token ? { token } : {}),
    })
    form.token = ''
    await fetchConfig()
    $baseMessage(msg || '保存成功', 'success', 'hey')
  } finally {
    saving.value = false
  }
}

const handleTestSend = async () => {
  const content = testForm.content.trim()
  if (!content) {
    $baseMessage('请输入测试通知内容', 'warning', 'hey')
    return
  }
  testing.value = true
  try {
    const { data } = await testPushPlusNotification(content)
    const result = (data || {}) as PushPlusNotificationTestResult
    $baseMessage(
      result.message || (result.success ? '发送成功' : '发送失败'),
      result.success ? 'success' : 'error',
      'hey'
    )
  } finally {
    testing.value = false
  }
}

onBeforeMount(() => {
  fetchConfig()
})
</script>

<style lang="scss" scoped>
.pushplus-notification-container {
  padding: 0;

  .el-card {
    margin-bottom: 20px;
    border-radius: 8px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}
</style>
