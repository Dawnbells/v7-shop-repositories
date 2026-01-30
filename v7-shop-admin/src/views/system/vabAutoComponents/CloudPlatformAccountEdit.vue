<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="AccessKey" prop="accessKey">
        <el-input v-model.trim="form.accessKey" clearable />
      </el-form-item>
      <el-form-item label="Secret" prop="accessKeySecret">
        <el-input v-model.trim="form.accessKeySecret" clearable />
      </el-form-item>
      <el-form-item label="Endpoint" prop="endpoint">
        <el-input v-model.trim="form.endpoint" clearable />
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
      <el-button type="primary" @click="save">保存</el-button>
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
    label: '狗爹',
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
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入名称' }],
  accessKey: [{ required: true, trigger: 'blur', message: '请输入AccessKey' }],
  accessKeySecret: [{ required: true, trigger: 'blur', message: '请输入AccessKey Secret' }],
  endpoint: [{ required: true, trigger: 'blur', message: '请输入访问端点' }],
  cloudPlatform: [{ required: true, trigger: 'blur', message: '请选择云平台' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (!row) title.value = '添加'
    else {
      title.value = '编辑'
      Object.assign(form, row)
      console.log(row)
      if (!row.cloudPlatform) {
        row.cloudPlatform = 'ALIYUN'
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
