<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <el-form-item label="服务器名称" prop="name">
        <el-input v-model.trim="form.name" clearable placeholder="请输入服务器名称" />
      </el-form-item>
      <el-form-item label="CNAME记录" prop="cnameRecord">
        <el-input
          v-model.trim="form.cnameRecord"
          clearable
          placeholder="请输入CNAME域名，如 eu.dwd-cname.com"
        />
      </el-form-item>
      <el-form-item label="主IP地址" prop="primaryIp">
        <el-input v-model.trim="form.primaryIp" clearable placeholder="IPv4，可选，优先级最高" />
      </el-form-item>
      <el-form-item label="备用IP地址" prop="failoverIp">
        <el-input v-model.trim="form.failoverIp" clearable placeholder="IPv4，可选，第二优先级" />
      </el-form-item>
      <el-form-item label="兜底IP地址" prop="fallbackIp">
        <el-input v-model.trim="form.fallbackIp" clearable placeholder="IPv4，可选，最后兜底" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/serverManager'

defineOptions({
  name: 'ServerManagerEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  id: undefined,
  name: '',
  cnameRecord: '',
  primaryIp: '',
  failoverIp: '',
  fallbackIp: '',
})

const validateCnameRecord = (rule: any, value: any, callback: any) => {
  if (!value) {
    callback(new Error('CNAME记录不能为空'))
    return
  }
  const pattern = /^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\.[A-Za-z0-9-]{1,63})*\.[A-Za-z]{2,63}$/
  callback(pattern.test(value) ? undefined : new Error('绑定的CNAME域名不正确'))
}

const validateOptionalIpv4 = (rule: any, value: any, callback: any) => {
  if (!value) {
    callback()
    return
  }
  const pattern = /^(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)$/
  callback(pattern.test(value) ? undefined : new Error('只允许填写IPv4地址'))
}

const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '服务器名称不能为空' }],
  cnameRecord: [{ required: true, trigger: 'blur', validator: validateCnameRecord }],
  primaryIp: [{ trigger: 'blur', validator: validateOptionalIpv4 }],
  failoverIp: [{ trigger: 'blur', validator: validateOptionalIpv4 }],
  fallbackIp: [{ trigger: 'blur', validator: validateOptionalIpv4 }],
})

const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    name: '',
    cnameRecord: '',
    primaryIp: '',
    failoverIp: '',
    fallbackIp: '',
  })
}

const showEdit = (row?: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    resetForm()
    if (!row) {
      title.value = '添加'
    } else {
      title.value = '编辑'
      Object.assign(form, row)
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  resetForm()
  dialogFormVisible.value = false
  emit('fetch-data')
}

const save = () => {
  const ips = [form.primaryIp, form.failoverIp, form.fallbackIp].filter(Boolean)
  if (ips.length === 0) {
    $baseMessage('主IP、备用IP、兜底IP至少填写一个', 'warning', 'hey')
    return
  }
  if (new Set(ips).size !== ips.length) {
    $baseMessage('主IP、备用IP、兜底IP不能重复', 'warning', 'hey')
    return
  }
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
