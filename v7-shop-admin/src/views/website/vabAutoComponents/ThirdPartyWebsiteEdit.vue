<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="600px" @close="close">
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <el-form-item label="店铺名称" prop="nickName">
        <el-input v-model.trim="form.nickName" clearable />
      </el-form-item>
      <el-form-item label="HANDLE" prop="handle">
        <el-input v-model.trim="form.handle" clearable />
      </el-form-item>
      <el-form-item label="访问令牌" prop="token">
        <el-input v-model.trim="form.token" clearable />
      </el-form-item>
      <el-form-item label="App Key" prop="appKey">
        <el-input v-model.trim="form.appKey" clearable />
      </el-form-item>
      <el-form-item label="APP Secret" prop="appSecret">
        <el-input v-model.trim="form.appSecret" clearable />
      </el-form-item>
      <el-form-item label="商城类型" prop="websiteType">
        <el-select v-model="form.websiteType" placeholder="请选择店铺类型">
          <el-option label="SHOPLINE" value="SHOPLINE" />
        </el-select>
      </el-form-item>
      <el-form-item label="认证方式" prop="authType">
        <el-select v-model="form.authType" placeholder="请选择认证方式">
          <el-option label="BASIC_AUTH" value="BASIC_AUTH" />
          <el-option label="OAUTH2" value="OAUTH2" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '../../../api/ThirdPartyWebsite'

defineOptions({
  name: 'ThirdPartyWebsiteEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  nickName: '',
  handle: '',
  token: '',
  appKey: '',
  appSecret: '',
  websiteType: 'SHOPLINE',
  authType: 'BASIC_AUTH',
})
const rules = reactive<any>({
  nickName: [{ required: true, trigger: 'blur', message: '请输入店铺名称' }],
  handle: [{ required: true, trigger: 'blur', message: '请输入店铺唯一标识' }],
  appKey: [{ required: true, trigger: 'blur', message: '请输入店铺App Key' }],
  appSecret: [{ required: true, trigger: 'blur', message: '请输入店铺App Secret' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (!row) title.value = '添加'
    else {
      title.value = '编辑'
      Object.assign(form, row)
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
