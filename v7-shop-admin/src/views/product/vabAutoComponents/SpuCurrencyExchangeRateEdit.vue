<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="统一汇率">
        <el-switch v-model="form.useStandardExchangeRate" />
      </el-form-item>
      <el-form-item label="货币汇率" prop="code">
        <el-input v-model.trim="form.code" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { remoteQuery } from '/@/api/productCategory'
import { doEdit } from '/@/api/spu'

defineOptions({
  name: 'SpuEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const categoryLoading = ref<boolean>(false)
const options = ref<any[]>([])
const form = reactive<any>({
  code: '',
  name: '',
  description: '',
})
const rules = reactive<any>({
  code: [{ required: true, trigger: 'blur', message: '请输入商品编码（SPU）' }],
  name: [{ required: true, trigger: 'blur', message: '请输入商品名称' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = '编辑'
      Object.assign(form, row)
      if (row.productCategory) {
        remoteQueryCategory(`${row.productCategory.id}`).then(() => {
          form.productCategoryId = row.productCategory.id
        })
      }
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
    productCategoryId: 0,
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

const remoteQueryCategory = async (query: string) => {
  categoryLoading.value = true
  try {
    const { data } = await remoteQuery(query)
    options.value = data.list
  } finally {
    categoryLoading.value = false
  }
}
</script>
