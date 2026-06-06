<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="700px"
    @close="close"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="像素名称" prop="pixelName">
        <el-input v-model.trim="form.pixelName" clearable placeholder="请输入像素名称" />
      </el-form-item>
      <el-form-item label="像素平台" prop="platform">
        <el-select v-model="form.platform" placeholder="请选择像素平台" style="width: 100%">
          <el-option label="META" value="META" />
          <el-option label="GOOGLE" value="GOOGLE" />
          <el-option label="TIKTOK" value="TIKTOK" />
          <el-option label="TABOOLA" value="TABOOLA" />
          <el-option label="BIGO" value="BIGO" />
          <el-option label="Google Tag Manager" value="GTM" />
          <el-option label="EMBED" value="EMBED" />
        </el-select>
      </el-form-item>
      <el-form-item label="像素ID" prop="pixelId">
        <el-input v-model.trim="form.pixelId" clearable :placeholder="pixelIdPlaceholder" />
      </el-form-item>
      <el-form-item v-if="form.platform === 'META'" label="AccessToken" prop="accessToken">
        <el-input v-model.trim="form.accessToken" clearable placeholder="请输入AccessToken" />
      </el-form-item>
      <el-form-item v-if="form.platform === 'BIGO'" label="Org ID" prop="accessToken">
        <el-input v-model.trim="form.accessToken" clearable placeholder="请输入 BIGO orgId，若像素ID已填完整URL可留空" />
      </el-form-item>
      <el-form-item v-if="form.platform === 'GOOGLE'" label="转化标签" prop="accessToken" required>
        <el-input v-model.trim="form.accessToken" clearable placeholder="请输入 Google Ads 转化标签" />
      </el-form-item>
      <el-form-item
        v-if="form.platform === 'META' || form.platform === 'GOOGLE' || form.platform === 'TIKTOK' || form.platform === 'TABOOLA' || form.platform === 'BIGO' || form.platform === 'GTM'"
        label="转化事件"
        prop="conversionEvent"
      >
        <el-select v-model="form.conversionEvent" placeholder="请选择转化事件" style="width: 100%">
          <el-option label="加购物车" value="ADD_TO_CART" />
          <el-option label="下单购买" value="PURCHASE" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.platform === 'EMBED'" label="嵌入代码" prop="embedCode">
        <el-input
          v-model="form.embedCode"
          type="textarea"
          :rows="8"
          placeholder="直接粘贴平台提供的 HTML 像素代码，支持 script / noscript 标签"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/pixelAccount'

defineOptions({
  name: 'PixelAccountEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  pixelName: '',
  pixelId: '',
  accessToken: '',
  conversionEvent: 'PURCHASE',
  platform: 'META',
  trackingType: 'GLOBAL',
  embedCode: '',
})
const validateAccessToken = (_rule: any, value: string, callback: any) => {
  if (form.platform === 'GOOGLE' && !value) {
    callback(new Error('请输入 Google Ads 转化标签'))
    return
  }
  callback()
}
const rules = reactive<any>({
  pixelName: [{ required: true, trigger: 'blur', message: '请输入像素名称' }],
  pixelId: [{ required: true, trigger: 'blur', message: '请输入像素ID' }],
  platform: [{ required: true, trigger: 'blur', message: '请选择像素平台' }],
  accessToken: [{ validator: validateAccessToken, trigger: 'blur' }],
  conversionEvent: [{ required: true, trigger: 'blur', message: '请选择转化事件' }],
})

const pixelIdPlaceholder = computed(() => {
  if (form.platform === 'TABOOLA') return '请输入 Taboola Account ID'
  if (form.platform === 'BIGO') return '请输入 BIGO accountId 或完整 resource/pixel URL'
  if (form.platform === 'GTM') return '请输入 GTM 容器 ID，如 GTM-XXXXXX'
  if (form.platform === 'EMBED') return '请输入嵌入像素标识'
  return '请输入像素ID'
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
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
  formRef.value.clearValidate()
  formRef.value.resetFields()
  Object.assign(form, {
    id: undefined,
  })
  emit('fetch-data')
}

const save = () => {
  if (form.platform === 'GOOGLE' && !form.accessToken) {
    $baseMessage('请输入 Google Ads 转化标签', 'warning', 'hey')
    return
  }
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}
</script>
