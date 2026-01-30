<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="SPU编码" prop="code">
        <el-select
          v-model="form.spuIds"
          filterable
          :loading="remoteLoading"
          remote
          :remote-method="remoteQuery"
          style="width: 100%"
          @change="onSelect"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.code }}</span>
            <span
              style="
                float: right;
                margin-left: 20px;
                font-size: 13px;
                color: var(--el-text-color-secondary);
              "
            >
              {{ item.name }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="SPU名称" prop="name">
        <el-input v-model.trim="form.name" clearable disabled />
      </el-form-item>

      <el-form-item label="商品分类" prop="productCategory.name">
        <el-input v-model.trim="form.productCategory.name" clearable disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doBind, getRemoteQuery } from '/@/api/websiteProduct'

defineOptions({
  name: 'WebsiteProductEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const remoteLoading = ref<boolean>(false)
const options = ref<any>([])
const form = reactive<any>({
  id: '',
  name: '',
  spuIds: [],
  productCategory: {
    name: '',
  },
})
const rules = reactive<any>({
  title: [{ required: true, trigger: 'blur', message: '请输入标题' }],
  author: [{ required: true, trigger: 'blur', message: '请输入作者' }],
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
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await doBind(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const remoteQuery = async (query: string) => {
  remoteLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    options.value = data.list
  } finally {
    remoteLoading.value = false
  }
}

const onSelect = (id: any) => {
  console.log(id)
  const spus = options.value.filter((c: any) => c.id === id)
  if (spus && spus.length > 0) {
    const spu = spus[0]
    Object.assign(form, spu)
  }
}
</script>
