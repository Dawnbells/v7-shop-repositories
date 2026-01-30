<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model.trim="form.description" clearable />
      </el-form-item>
      <el-form-item label="上级部门" prop="parentId">
        <el-tree-select
          v-model="form.parentId"
          accordion
          check-strictly
          :data="list"
          node-key="id"
          :props="{ label: 'name' }"
          :render-after-expand="false"
          style="width: 350px"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import type { Department } from '/@/api/department'
import { doEdit } from '/@/api/department'

defineOptions({
  name: 'DepartmentEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const list = ref<any>([])
const defaultCheckedKeys = ref<any>([])
const title = ref<string>('')
const parent = ref<any>()
const dialogFormVisible = ref<boolean>(false)
const form = reactive<any>({
  id: undefined,
  parentId: undefined,
  name: '',
  description: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入部门名称' }],
  description: [{ required: true, trigger: 'blur', message: '请输入部门描述' }],
})

const showEdit = (row: any, parentId: number, departmentTree: Department[]) => {
  dialogFormVisible.value = true
  list.value = departmentTree
  nextTick(() => {
    if (!row) {
      title.value = '添加'
      if (parentId) {
        form.id = undefined
        form.parentId = parentId
      }
    } else {
      title.value = '编辑'
      Object.assign(form, row)
      form.parentId = parentId
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
    console.log(parent)
    if (valid) {
      const { msg }: any = await doEdit(form)
      await $baseMessage(msg, 'success', 'hey')
      dialogFormVisible.value = false
    }
  })
}
</script>
