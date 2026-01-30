<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="名称" prop="name">
        <el-autocomplete
          v-model="form.name"
          :fetch-suggestions="querySearch"
          placeholder="请输入语言名称"
          popper-class="my-autocomplete"
          style="width: 100%"
          @select="handleSelect"
        >
          <template #default="{ item }">
            <div class="value">{{ item.cname }}</div>
            <span class="link">{{ item.code }}</span>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="中文名称" prop="cname">
        <el-input v-model.trim="form.cname" clearable placeholder="请输入语言中文名称" />
      </el-form-item>
      <el-form-item label="语言代码" prop="code">
        <el-input v-model.trim="form.code" clearable placeholder="请输入语言代码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/language'

defineOptions({
  name: 'LanguageEdit',
})

interface LanguageItem {
  name: string
  cname: string
  code: string
}
const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const isoLanguages = ref<LanguageItem[]>([])
const form = reactive<LanguageItem>({
  name: '',
  cname: '',
  code: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入名称' }],
  cname: [{ required: true, trigger: 'blur', message: '请输入中文名称' }],
  code: [{ required: true, trigger: 'blur', message: '请输入语言代码(ISO-639-1)' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  getIso639Languages()
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
const getIso639Languages = async () => {
  const { data } = await axios({
    url: 'static/json/languages.json',
    method: 'get',
  })
  isoLanguages.value = data
}
const createFilter = (queryString: string) => {
  return (restaurant: LanguageItem) => {
    return (
      restaurant.name.toLowerCase().indexOf(queryString.toLowerCase()) === 0 ||
      restaurant.cname.toLowerCase().indexOf(queryString.toLowerCase()) === 0 ||
      restaurant.code.toLowerCase().indexOf(queryString.toLowerCase()) === 0
    )
  }
}
const querySearch = (queryString: string, cb: any) => {
  const results = queryString ? isoLanguages.value.filter(createFilter(queryString)) : isoLanguages.value
  // call callback function to return suggestion objects
  cb(results)
}
const handleSelect = (item: any) => {
  form.name = item.name
  form.cname = item.cname
  form.code = item.code
}
</script>
