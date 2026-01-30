<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="分组名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input
          v-model.trim="form.sort"
          clearable
          placeholder="请输入排序, 数字大的排前面，一样的按添加顺序"
          type="number"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import type { FormInstance } from 'element-plus'

defineOptions({
  name: 'ProtocolGroupEdit',
})

const emit = defineEmits(['on-edit-data'])

const formRef = ref<FormInstance>()
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const language = ref<string | undefined>(undefined)
const form = reactive<any>({
  name: '',
  sort: '',
  id: undefined,
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入分组名称' }],
  sort: [{ required: true, trigger: 'blur', message: '请输入排序' }],
})

const showEdit = (row: any, lang: string) => {
  dialogFormVisible.value = true
  language.value = lang
  console.log(row, lang)
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
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  form.name = ''
  form.sort = ''
  form.id = undefined
  language.value = undefined
}

const save = () => {
  formRef.value?.validate(async (valid: any) => {
    if (valid) {
      emit('on-edit-data', { ...form, languageId: language.value })
      await close()
      dialogFormVisible.value = false
    }
  })
}
</script>
