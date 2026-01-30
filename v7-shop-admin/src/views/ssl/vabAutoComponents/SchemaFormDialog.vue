<template>
  <vab-dialog
    v-model="dialogVisible"
    append-to-body
    :title="title"
    width="600px"
    @close="handleClose"
  >
    <el-form ref="formRef" label-width="120px" :model="formData">
      <template v-for="(fieldSchema, key) in schema" :key="key">
        <dynamic-form-field
          :field-key="String(key)"
          :field-schema="fieldSchema"
          :model-value="formData[key]"
          @update:model-value="onFieldUpdate(String(key), $event)"
        />
      </template>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button :loading="loading" type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </vab-dialog>

  <file-chooser ref="fileChooserRef" :z-index="5000" />
</template>

<script lang="ts" setup>
import DynamicFormField from '/@/views/website/vabAutoComponents/DynamicFormField.vue'
import FileChooser from '/@/views/product/vabAutoComponents/FileChooser.vue'

defineOptions({
  name: 'SchemaFormDialog',
})

const props = withDefaults(
  defineProps<{
    title?: string
    schema?: Record<string, any>
    modelValue?: Record<string, any>
  }>(),
  {
    title: '编辑配置',
    schema: () => ({}),
    modelValue: () => ({}),
  }
)

const emit = defineEmits<{
  (e: 'confirm', data: Record<string, any>): void
  (e: 'cancel'): void
}>()

const $baseMessage = inject<any>('$baseMessage')

const dialogVisible = ref<boolean>(false)
const loading = ref<boolean>(false)
const formRef = ref<any>(null)
const formData = reactive<Record<string, any>>({})

// 文件选择器
const fileChooserRef = ref<any>(null)
provide('fileChooserRef', fileChooserRef)

// 初始化表单数据
const initFormData = () => {
  // 清空旧数据
  Object.keys(formData).forEach((k) => delete formData[k])

  // 从 modelValue 初始化
  if (props.modelValue) {
    Object.keys(props.modelValue).forEach((key) => {
      formData[key] = props.modelValue[key]
    })
  }

  // 确保 schema 中的所有字段都有初始值
  if (props.schema) {
    Object.keys(props.schema).forEach((key) => {
      if (formData[key] === undefined) {
        const fieldSchema = props.schema[key]
        // 根据类型设置默认值
        if (fieldSchema.type === 'boolean') {
          formData[key] = false
        } else if (fieldSchema.type === 'number') {
          formData[key] = 0
        } else if (fieldSchema.type === 'array') {
          formData[key] = []
        } else if (fieldSchema.type === 'object') {
          formData[key] = {}
        } else {
          formData[key] = ''
        }
      }
    })
  }
}

// 字段更新
const onFieldUpdate = (key: string, value: any) => {
  formData[key] = value
}

// 打开弹窗
const open = (initialData?: Record<string, any>) => {
  if (initialData) {
    Object.keys(formData).forEach((k) => delete formData[k])
    Object.keys(initialData).forEach((key) => {
      formData[key] = initialData[key]
    })
  } else {
    initFormData()
  }
  dialogVisible.value = true
}

// 关闭弹窗
const close = () => {
  dialogVisible.value = false
}

// 处理关闭
const handleClose = () => {
  emit('cancel')
}

// 处理取消
const handleCancel = () => {
  dialogVisible.value = false
  emit('cancel')
}

// 表单验证
const validateForm = async (): Promise<boolean> => {
  if (!formRef.value) return true

  try {
    await formRef.value.validate()
    return true
  } catch {
    return false
  }
}

// 处理确认
const handleConfirm = async () => {
  const valid = await validateForm()
  if (!valid) {
    $baseMessage?.('请检查表单填写是否正确', 'warning', 'hey')
    return
  }

  // 返回表单数据的深拷贝
  const data = JSON.parse(JSON.stringify(formData))
  emit('confirm', data)
  dialogVisible.value = false
}

// 暴露方法
defineExpose({
  open,
  close,
})

// 监听 schema 变化，重新初始化表单
watch(
  () => props.schema,
  () => {
    if (dialogVisible.value) {
      initFormData()
    }
  },
  { deep: true }
)
</script>
