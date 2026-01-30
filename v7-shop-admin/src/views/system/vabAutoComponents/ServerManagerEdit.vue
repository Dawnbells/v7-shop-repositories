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
        <el-input v-model.trim="form.primaryIp" clearable placeholder="请输入主IP地址" />
      </el-form-item>
      <el-form-item label="故障转移IP地址" prop="failoverIp">
        <el-input v-model.trim="form.failoverIp" clearable placeholder="请输入故障转移IP地址（可选）" />
      </el-form-item>
      <el-form-item label="健康检查地址" prop="healthCheckUrl">
        <el-input
          v-model.trim="form.healthCheckUrl"
          clearable
          placeholder="请输入健康检查地址，如 https://domain.com/health"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
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
const loading = ref<boolean>(false)
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  id: undefined,
  name: '',
  cnameRecord: '',
  primaryIp: '',
  failoverIp: '',
  healthCheckUrl: '',
})

// CNAME记录的正则验证
const validateCnameRecord = (rule: any, value: any, callback: any) => {
  if (!value) {
    callback(new Error('CNAME记录不能为空'))
    return
  }
  const pattern = /^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\.[A-Za-z0-9-]{1,63})*\.[A-Za-z]{2,6}$/
  if (pattern.test(value)) {
    callback()
  } else {
    callback(new Error('绑定的CNAME域名不正确'))
  }
}

const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '服务器名称不能为空' }],
  cnameRecord: [{ required: true, trigger: 'blur', validator: validateCnameRecord }],
  primaryIp: [{ required: true, trigger: 'blur', message: '主IP地址不能为空' }],
  failoverIp: [{ required: false, trigger: 'blur' }],
  healthCheckUrl: [{ required: true, trigger: 'blur', message: '健康检查地址不能为空' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (!row) {
      title.value = '添加'
      Object.assign(form, {
        id: undefined,
        name: '',
        cnameRecord: '',
        primaryIp: '',
        failoverIp: '',
        healthCheckUrl: '',
      })
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
  Object.assign(form, {
    id: undefined,
    name: '',
    cnameRecord: '',
    primaryIp: '',
    failoverIp: '',
    healthCheckUrl: '',
  })
  dialogFormVisible.value = false
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
