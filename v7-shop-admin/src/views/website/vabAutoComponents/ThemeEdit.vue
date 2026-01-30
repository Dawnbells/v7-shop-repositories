<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="650px"
    @close="close"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="主题名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="主题模板" prop="templateName">
        <el-select
          v-model="form.templateName"
          clearable
          :disabled="isEdit"
          placeholder="请选择主题模板"
          style="width: 100%"
        >
          <el-option
            v-for="item in props.templateOptions"
            :key="(item as any).name"
            :label="(item as any).cname"
            :value="(item as any).name"
          >
            <span style="float: left">{{ (item as any).cname }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ (item as any).name }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="主题描述" prop="description">
        <el-input
          v-model.trim="form.description"
          :autosize="{ minRows: 3, maxRows: 6 }"
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
import { doEdit } from '/@/api/theme'

defineOptions({
  name: 'ThemeEdit',
})

const props = withDefaults(
  defineProps<{
    templateOptions?: any[]
  }>(),
  {
    templateOptions: () => [],
  }
)

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const saveLoading = ref<boolean>(false)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const isEdit = ref<boolean>(false)
const form = reactive<any>({
  name: '',
  templateName: '',
  description: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '名称不能为空' }],
  templateName: [{ required: true, trigger: 'change', message: '主题标识不能为空' }],
  description: [{ required: false, trigger: 'blur', message: '请输入描述' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    isEdit.value = false
    if (row) {
      isEdit.value = true
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
    name: '',
    templateName: '',
    description: '',
  })
  isEdit.value = false
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
