<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="600px"
    @close="close"
  >
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <el-form-item v-if="!isEditingPublicApp" label="商城类型" prop="websiteType">
        <el-select v-model="form.websiteType" :disabled="!!form.id" placeholder="请选择店铺类型">
          <el-option label="SHOPLINE" value="SHOPLINE" />
          <el-option label="SHOPIFY" value="SHOPIFY" />
        </el-select>
      </el-form-item>
      <el-form-item label="店铺名称" prop="nickName">
        <el-input
          v-model.trim="form.nickName"
          clearable
          :placeholder="isConnectingPublicApp ? '可不填，默认使用 Shopify 店铺名' : ''"
        />
      </el-form-item>
      <!-- 公开应用不填店铺标识：店铺由商家在 Shopify 中选择（App Store 要求） -->
      <el-form-item v-if="!isPublicApp" label="HANDLE" prop="handle">
        <el-input v-model.trim="form.handle" clearable />
      </el-form-item>
      <el-form-item v-if="!isShopify" label="访问令牌" prop="token">
        <el-input v-model.trim="form.token" clearable />
      </el-form-item>
      <el-form-item v-if="isShopify && !isPublicApp" label="Client ID" prop="clientId">
        <el-input v-model.trim="form.clientId" clearable />
      </el-form-item>
      <el-form-item v-if="isShopify && !isPublicApp" label="Client Secret" prop="clientSecret">
        <el-input
          v-model.trim="form.clientSecret"
          clearable
          :placeholder="form.id ? '留空表示不修改' : ''"
          show-password
        />
      </el-form-item>
      <el-form-item label="币种模式" prop="currencyMode">
        <el-select v-model="form.currencyMode" placeholder="请选择币种模式">
          <el-option label="店铺结算币种" value="SHOP_MONEY" />
          <el-option label="订单展示币种" value="PRESENTMENT_MONEY" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-alert v-if="isConnectingPublicApp" :closable="false" show-icon type="info">
      {{
        `复制连接链接，在已登录 Shopify 的浏览器中打开并安装应用，店铺会自动连接到当前账号。链接 ${CONNECT_LINK_TTL_MINUTES} 分钟内有效，一个链接连接一个店铺。`
      }}
    </el-alert>
    <template #footer>
      <template v-if="isConnectingPublicApp">
        <el-button :disabled="copyLoading" :loading="saveLoading" @click="connect">
          在本浏览器连接
        </el-button>
        <el-button :disabled="saveLoading" :loading="copyLoading" type="primary" @click="copyLink">
          复制连接链接
        </el-button>
      </template>
      <el-button v-else :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit, doEditShopify, shopifyInstallUrl } from '../../../api/ThirdPartyWebsite'

defineOptions({
  name: 'ThirdPartyWebsiteEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const copyLoading = ref<boolean>(false)
// 表单默认值；关闭时整体还原，避免被 v-if 隐藏的字段残留上一次的值
const defaultForm = () => ({
  id: undefined,
  nickName: '',
  handle: '',
  token: '',
  clientId: '',
  clientSecret: '',
  websiteType: 'SHOPLINE',
  // Shopify 新增默认走公开应用
  accessMode: 'PUBLIC_APP',
  currencyMode: 'SHOP_MONEY',
})
const form = reactive<any>(defaultForm())
// 与后端 shopify.app.connect-intent-ttl-minutes 默认值一致
const CONNECT_LINK_TTL_MINUTES = 60
const isShopify = computed<boolean>(() => form.websiteType === 'SHOPIFY')
// 新增 Shopify 只走公开应用；凭证模式仅保留存量记录的编辑
const isPublicApp = computed<boolean>(() => isShopify.value && form.accessMode === 'PUBLIC_APP')
// 编辑公开应用接入的店铺：只允许改店铺名称与币种模式
const isEditingPublicApp = computed<boolean>(() => !!form.id && isPublicApp.value)
// 新增公开应用店铺：名称可空，绑定后默认取 Shopify 店铺名
const isConnectingPublicApp = computed<boolean>(() => !form.id && isPublicApp.value)
const rules = reactive<any>({
  nickName: [
    {
      trigger: 'blur',
      validator: (_rule: any, value: string, callback: any) => {
        if (!value && !isConnectingPublicApp.value) callback(new Error('请输入店铺名称'))
        else callback()
      },
    },
  ],
  handle: [{ required: true, trigger: 'blur', message: '请输入店铺唯一标识' }],
  token: [{ required: true, trigger: 'blur', message: '请输入访问令牌' }],
  clientId: [{ required: true, trigger: 'blur', message: '请输入Client ID' }],
  clientSecret: [
    {
      trigger: 'blur',
      validator: (_rule: any, value: string, callback: any) => {
        // 编辑时留空表示不修改
        if (!value && !form.id) callback(new Error('请输入Client Secret'))
        else callback()
      },
    },
  ],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (!row) title.value = '添加'
    else {
      title.value = '编辑'
      Object.assign(form, row)
      // 历史记录 accessMode 为空，按凭证模式处理
      if (!form.accessMode) form.accessMode = 'CREDENTIALS'
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value.clearValidate()
  formRef.value.resetFields()
  Object.assign(form, defaultForm())
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = isShopify.value ? await doEditShopify(form) : await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}

// 公开应用：校验表单后获取连接链接（不含店铺标识），校验不通过或请求失败时返回空
const fetchAuthorizeUrl = async (): Promise<string> => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return ''
  const { data }: any = await shopifyInstallUrl({
    nickName: form.nickName,
    currencyMode: form.currencyMode,
  }).catch(() => ({}))
  return data?.authorizeUrl || ''
}

// 本浏览器已登录 Shopify 时：新标签页打开连接链接，安装授权后自动绑定
const connect = async () => {
  saveLoading.value = true
  try {
    const authorizeUrl = await fetchAuthorizeUrl()
    if (!authorizeUrl) return
    const win = globalThis.open(authorizeUrl, '_blank')
    if (!win) {
      $baseMessage('新标签页被浏览器拦截，请允许弹出窗口或使用「复制链接」', 'warning', 'hey')
      return
    }
    win.opener = null
    $baseMessage('已在新标签页打开，安装并授权应用后刷新列表即可', 'success', 'hey')
    dialogFormVisible.value = false
  } finally {
    saveLoading.value = false
  }
}

// 复制连接链接，到已登录 Shopify 的浏览器（或发给店主）打开
const { copy } = useClipboard({ legacy: true })
const copyLink = async () => {
  copyLoading.value = true
  try {
    const authorizeUrl = await fetchAuthorizeUrl()
    if (!authorizeUrl) return
    await copy(authorizeUrl)
    $baseMessage('连接链接已复制，请在已登录 Shopify 的浏览器中打开', 'success', 'hey')
  } catch {
    $baseMessage('复制失败，请重试', 'error', 'hey')
  } finally {
    copyLoading.value = false
  }
}
</script>
