<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <el-form-item label="角色名称" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="角色描述" prop="description">
        <el-input v-model.trim="form.description" clearable />
      </el-form-item>
      <el-form-item label="角色类型" prop="systemUserType">
        <el-select v-model="form.systemUserType">
          <el-option label="管理员" value="COMPANY_ADMIN" />
          <el-option label="部门超级管理员" value="DEEP_DEPARTMENT_MANAGER" />
          <el-option label="部门管理员" value="DEPARTMENT_MANAGER" />
          <el-option label="员工" value="EMPLOYEE" />
        </el-select>
      </el-form-item>
      <el-form-item label="特殊权限">
        <el-checkbox v-model="form.isCrossDepartment" @change="onCrossDepartmentChange">
          跨部门管理
        </el-checkbox>
      </el-form-item>
      <el-form-item v-if="form.isCrossDepartment" label="管理部门">
        <el-tree-select
          v-model="form.manageDepartmentIds"
          :data="departmentTree"
          multiple
          show-checkbox
          collapse-tags
          clearable
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          :default-checked-keys="form.manageDepartmentIds"
          :default-expanded-keys="form.manageDepartmentIds"
          placeholder="请选择管理部门"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/role'
import { getTree } from '/@/api/department'

defineOptions({
  name: 'RoleEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const departmentTree = ref<any[]>([])
const form = reactive<any>({
  name: '',
  description: '',
  systemUserType: 'EMPLOYEE',
  isCrossDepartment: false,
  manageDepartmentIds: [],
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入角色名称' }],
  description: [{ required: true, trigger: 'blur', message: '请输入角色描述' }],
  systemUserType: [{ required: true, trigger: 'blur', message: '请选择角色类型' }],
})

const fetchDepartmentTree = async () => {
  const { data }: any = await getTree({ status: 'VALID' })
  departmentTree.value = (data?.list || []).map((item: any) => ({
    ...item,
    label: item.name,
    value: item.id,
  }))
}

const onCrossDepartmentChange = (val: boolean) => {
  if (val) {
    fetchDepartmentTree()
  } else {
    form.manageDepartmentIds = []
  }
}

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = '编辑'
      Object.assign(form, row)
      if (form.isCrossDepartment) {
        fetchDepartmentTree()
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
  Object.assign(form, {
    id: undefined,
    isCrossDepartment: false,
    manageDepartmentIds: [],
  })
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      const { msg }: any = await doEdit(form)
      await $baseMessage(msg, 'success', 'hey')
      dialogFormVisible.value = false
    }
  })
}
</script>
