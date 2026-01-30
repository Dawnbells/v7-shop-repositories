<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="商品名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="商品分类" prop="productCategoryId">
        <el-select
          v-model="form.productCategoryId"
          filterable
          :loading="categoryLoading"
          remote
          :remote-method="remoteQueryCategory"
          style="width: 100%"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="商品说明" prop="description">
        <el-input
          v-model="form.description"
          :autosize="{ minRows: 2, maxRows: 4 }"
          clearable
          type="textarea"
        />
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
  productCategoryId: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入商品名称' }],
  productCategoryId: [{ required: true, trigger: 'blur', message: '请选择商品分类' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = '编辑'
      Object.assign(form, row)
      if (row.productCategory) {
        options.value = [row.productCategory]
        form.productCategoryId = row.productCategory.id
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
  options.value = []
  Object.assign(form, {
    id: undefined,
    productCategoryId: '',
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
