<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="AccessKey" prop="accessKey">
        <el-input v-model.trim="form.accessKey" :placeholder="accessKeyPlaceholder" clearable />
      </el-form-item>
      <el-form-item label="Secret" prop="accessKeySecret">
        <el-input v-model.trim="form.accessKeySecret" :placeholder="secretPlaceholder" clearable />
      </el-form-item>
      <el-form-item label="Endpoint" prop="endpoint">
        <el-input v-model.trim="form.endpoint" :placeholder="endpointPlaceholder" clearable />
      </el-form-item>
      <el-form-item label="云平台" prop="cloudPlatform">
        <el-select v-model="form.cloudPlatform" placeholder="请选择云平台" style="width: 100%">
          <el-option v-for="item in options" :key="item.name" :label="item.label" :value="item.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="用途描述" prop="description">
        <el-input v-model.trim="form.description" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="saveLoading" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/cloudPlatformAccount'

defineOptions({
  name: 'CloudPlatformAccountEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const options = ref<any>([
  {
    name: 'ALIYUN',
    label: '阿里云',
  },
  {
    name: 'TENCENT',
    label: '腾讯云',
  },
  {
    name: 'GODADDY',
    label: 'GoDaddy',
  },
  {
    name: 'NAMECHEAP',
    label: 'Namecheap',
  },
])
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  name: '',
  accessKey: '',
  accessKeySecret: '',
  endpoint: '',
  description: '',
  cloudPlatform: 'ALIYUN',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入名称' }],
  accessKey: [{ required: true, trigger: 'blur', message: '请输入 AccessKey' }],
  accessKeySecret: [{ required: true, trigger: 'blur', message: '请输入 AccessKey Secret' }],
  endpoint: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (form.cloudPlatform === 'GODADDY' || value) callback()
        else callback(new Error('请输入 Endpoint'))
      },
      trigger: 'blur',
    },
  ],
  cloudPlatform: [{ required: true, trigger: 'blur', message: '请选择云平台' }],
})

const accessKeyPlaceholder = computed(() => {
  if (form.cloudPlatform === 'GODADDY') return 'GoDaddy API Key'
  if (form.cloudPlatform === 'NAMECHEAP') return 'Namecheap ApiUser / UserName'
  return ''
})

const secretPlaceholder = computed(() => {
  if (form.cloudPlatform === 'GODADDY') return 'GoDaddy API Secret'
  if (form.cloudPlatform === 'NAMECHEAP') return 'Namecheap ApiKey'
  return ''
})

const endpointPlaceholder = computed(() => {
  if (form.cloudPlatform === 'GODADDY') return 'https://api.godaddy.com，可留空'
  if (form.cloudPlatform === 'NAMECHEAP') return 'Namecheap API 白名单 ClientIp'
  return ''
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (!row) {
      title.value = '添加'
      form.cloudPlatform = 'ALIYUN'
    } else {
      title.value = '编辑'
      Object.assign(form, row)
      if (!row.cloudPlatform) {
        form.cloudPlatform = 'ALIYUN'
      }
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
    cloudPlatform: 'ALIYUN',
  })
  emit('fetch-data')
}

const save = () => {
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
