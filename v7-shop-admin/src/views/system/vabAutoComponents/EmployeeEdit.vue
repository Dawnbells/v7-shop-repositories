<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="姓&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;名" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="性&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;别" prop="gender">
        <el-radio-group v-model="form.gender">
          <el-radio value="MALE">男</el-radio>
          <el-radio value="FEMALE">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="电话号码" prop="telephone">
        <el-input v-model.trim="form.telephone" clearable />
      </el-form-item>
      <el-form-item label="密&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;码" prop="password">
        <el-input v-model.trim="form.password" clearable>
          <template #append>
            <vab-icon icon="loop-left-line" @click="handleGeneratePassword" />
          </template>
        </el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/employee'
import { patternPhone } from '/@/utils/patterns'

defineOptions({
  name: 'EmployeeEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const form = reactive<any>({
  name: '',
  gender: 'MALE',
  telephone: '',
  password: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入姓名' }],
  gender: [{ required: true, trigger: 'blur', message: '请选择性别' }],
  telephone: [
    { required: true, trigger: 'blur', message: '请输入手机号' },
    { type: 'string', required: true, pattern: patternPhone, message: '请输入正确的手机号' },
  ],
  password: [{ required: true, trigger: 'blur', message: '请输入密码' }],
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
      const { msg }: any = await doEdit(form)
      await $baseMessage(msg, 'success', 'hey')
      dialogFormVisible.value = false
    }
  })
}
const handleGeneratePassword = () => {
  const source = `abcdefghizklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789~!@#$%`
  let s = ''
  for (let i = 0; i < 16; i++) {
    s += source.charAt(Math.ceil(Math.random() * 1000) % source.length)
  }
  form.password = s
}
</script>
