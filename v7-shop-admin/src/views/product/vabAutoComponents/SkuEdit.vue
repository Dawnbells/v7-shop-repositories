<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="编码" prop="skuCode">
        <el-input v-model.trim="form.skuCode" clearable />
      </el-form-item>
      <el-form-item label="品名" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item v-if="isEdit" label="同步" prop="syncChangeOrder">
        <el-checkbox v-model:model-value="form.syncChangeOrder">是否同步已生成的订单</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/sku'

defineOptions({
  name: 'SkuEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const isEdit = ref<boolean>(false)
const form = reactive<any>({
  skuCode: '',
  name: '',
  syncChangeOrder: false,
})
const rules = reactive<any>({
  skuCode: [{ required: true, trigger: 'blur', message: '请输入SKU编码' }],
  name: [{ required: true, trigger: 'blur', message: '请输入品名' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  isEdit.value = false
  nextTick(() => {
    if (row) {
      title.value = '编辑'
      isEdit.value = true
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
