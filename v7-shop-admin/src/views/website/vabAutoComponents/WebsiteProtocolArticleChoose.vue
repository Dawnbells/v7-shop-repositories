<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="协议标题" prop="id">
        <el-select
          v-model="form.id"
          filterable
          :loading="remoteLoading"
          remote
          :remote-method="remoteQuery"
          style="width: 100%"
          @change="onSelect"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="协议ID" prop="id">
        <el-input v-model.trim="form.id" clearable disabled />
      </el-form-item>

      <el-form-item label="协议语言" prop="language">
        <el-input v-model.trim="form.language" clearable disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQuery } from '/@/api/websiteProtocol'

defineOptions({
  name: 'WebsiteProductEdit',
})

const emit = defineEmits(['on-edit-data'])
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const remoteLoading = ref<boolean>(false)
const options = ref<any>([])
const languageId = ref<string | undefined>(undefined)
const protocolGroup = ref<any>({})
const form = reactive<any>({
  id: '',
  title: '',
  language: '',
})
const rules = reactive<any>({
  id: [{ required: true, trigger: 'blur', message: '请选择协议' }],
})

const showEdit = (row: any, lang: string | undefined) => {
  dialogFormVisible.value = true
  languageId.value = lang
  protocolGroup.value = row
  console.log(protocolGroup.value)
  nextTick(() => {
    title.value = '绑定协议'
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value.clearValidate()
  formRef.value.resetFields()
  protocolGroup.value = {}
  Object.assign(form, {
    id: undefined,
  })
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        emit('on-edit-data', { protocolGroupId: protocolGroup.value.id, article: { ...form } })
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
    const { data } = await getRemoteQuery(query, languageId.value)
    console.log('remote query >>', data.list)
    options.value = data.list
  } finally {
    remoteLoading.value = false
  }
}

const onSelect = (id: any) => {
  const items = options.value.filter((c: any) => c.id === id)
  if (items && items.length > 0) {
    const item = items[0]
    Object.assign(form, {
      ...item,
      language: item.language.cname,
    })
    console.log('onSelect', form)
  }
}
</script>
