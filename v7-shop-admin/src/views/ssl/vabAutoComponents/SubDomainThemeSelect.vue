<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="子域名" prop="name">
        <el-input v-model.trim="form.name" clearable disabled />
      </el-form-item>

      <el-form-item label="选择主题" prop="themeId">
        <el-select
          v-model="form.themeId"
          clearable
          filterable
          :loading="selectLoading"
          placeholder="请选择主题"
          remote
          :remote-method="remoteQueryTheme"
          style="width: 100%"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span
              v-if="item.description"
              style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
            >
              {{ item.description }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { remoteQuery } from '/@/api/theme'
import { bindTheme } from '/@/api/subDomain'

defineOptions({
  name: 'SubDomainThemeSelect',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const options = ref<any[]>([])
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const selectLoading = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  id: '',
  name: '',
  themeId: '',
})
const rules = reactive<any>({
  themeId: [{ required: true, trigger: 'change', message: '请选择主题' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    title.value = '选择主题'
    form.id = row.id
    form.name = row.name
    form.themeId = row.theme?.id || ''
    // 如果已有主题，加载到选项中
    if (row.theme) {
      options.value = [row.theme]
    } else {
      options.value = []
    }
    // 初始加载所有主题
    remoteQueryTheme('')
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
    themeId: '',
  })
  options.value = []
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await bindTheme(form.id, form.themeId)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const remoteQueryTheme = async (query: string) => {
  selectLoading.value = true
  try {
    const { data } = await remoteQuery(query)
    options.value = data.list || []
  } catch (error) {
    console.error('查询主题失败:', error)
    options.value = []
  } finally {
    selectLoading.value = false
  }
}
</script>

