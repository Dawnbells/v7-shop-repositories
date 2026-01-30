<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="协议名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="语言列表" prop="languageIds">
        <el-select
          v-model="form.languageIds"
          filterable
          :loading="languageLoading"
          multiple
          remote
          :remote-method="remoteQueryLanguage"
          style="width: 100%"
          value-key="id"
          @change="handleLanguageIdsChange"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.cname" :value="item.id">
            <span style="float: left">{{ item.cname }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="默认语言" prop="defaultLanguageId">
        <el-select
          v-model="form.defaultLanguageId"
          clearable
          placeholder="请选择默认语言"
          style="width: 100%"
        >
          <el-option
            v-for="item in defaultLanguageOptions"
            :key="item.id"
            :label="item.cname"
            :value="item.id"
          >
            <span style="float: left">{{ item.cname }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import { getRemoteQueryLanguage } from '~/src/api/language'
import { doEdit } from '/@/api/protocol'

defineOptions({
  name: 'ProtocolEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')

const formRef = ref<FormInstance>()
const title = ref<string>('')
const options = ref<any[]>([])
const languageLoading = ref<boolean>(false)
const dialogFormVisible = ref<boolean>(false)
const form = reactive<any>({
  name: '',
  languageIds: [],
  defaultLanguageId: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入协议名称' }],
  languageIds: [{ required: true, trigger: 'blur', message: '请选择语言列表' }],
  defaultLanguageId: [
    { required: false, trigger: 'blur', message: '请选择默认语言,不选择则不绑定默认语言' },
  ],
})

const defaultLanguageOptions = computed(() => {
  return options.value.filter((item) => form.languageIds.includes(item.id))
})

const handleLanguageIdsChange = (val: string[]) => {
  if (form.defaultLanguageId && !val.includes(form.defaultLanguageId)) {
    form.defaultLanguageId = ''
  }
}

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = '编辑'
      Object.assign(form, row)
      form.languageIds = row.languages.map((language: any) => `${language.id}`)
      form.defaultLanguageId = row.defaultLanguageId ? `${row.defaultLanguageId}` : ''
      options.value = row.languages.map((lang: any) => ({
        ...lang,
        id: String(lang.id),
      }))
    } else {
      title.value = '添加'
      Object.assign(form, {
        name: '',
        languageIds: [],
        defaultLanguageId: '',
      })
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  emit('fetch-data')
}

const save = () => {
  formRef.value?.validate(async (valid: any) => {
    if (valid) {
      const { msg }: any = await doEdit(form)
      await $baseMessage(msg, 'success', 'hey')
      await close()
      dialogFormVisible.value = false
    }
  })
}

const remoteQueryLanguage = async (query: string) => {
  languageLoading.value = true
  try {
    const { data } = await getRemoteQueryLanguage(query)
    options.value = data.list
  } finally {
    languageLoading.value = false
  }
}
</script>
