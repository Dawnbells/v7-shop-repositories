<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="600px" @close="close">
    <el-form ref="formRef" label-width="15px" :model="form" :rules="rules">
      <el-form-item label="" prop="name">
        <el-tree
          ref="treeRef"
          accordion
          check-strictly
          :data="data"
          :default-checked-keys="defaultCheckedKeys"
          :default-expanded-keys="defaultExpendedKeys"
          node-key="id"
          :props="defaultProps"
          show-checkbox
          style="width: 100%"
          @check="onCheck"
        >
          <template #default="{ node, data }">
            <span class="custom-tree-node">
              <span>{{ node.label }}</span>
              <span>{{ data.description }}</span>
            </span>
          </template>
        </el-tree>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { dispatchDepartment } from '/@/api/employee'
defineOptions({
  name: 'DepartmentEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const treeRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const data = ref<any>()
const id = ref<any>()
const defaultCheckedKeys = ref<number[]>()
const defaultExpendedKeys = ref<number[]>()
const form = reactive<any>({
  id: 0,
  name: '',
  description: '',
})
const defaultProps = {
  label: 'name',
  children: 'children',
}
const rules = reactive<any>({})

const dispatch = (allDepartmentTree: any, row: any) => {
  dialogFormVisible.value = true
  id.value = row.id
  if (row.department) {
    defaultCheckedKeys.value = [row.department.id]
    defaultExpendedKeys.value = [row.department.id]
  }
  data.value = allDepartmentTree
  nextTick(() => {
    title.value = '分配部门'
    Object.assign(form, row)
  })
}

defineExpose({
  dispatch,
})

const close = () => {
  treeRef.value.setCheckedNodes([])
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
      const { msg }: any = await dispatchDepartment(id.value, treeRef.value.getCheckedKeys())
      await $baseMessage(msg, 'success', 'hey')
      dialogFormVisible.value = false
    }
  })
}

const onCheck = (checkedNodes: any, checkedNodesData: any) => {
  // Get the currently checked node(s)
  const checkedKeys = treeRef.value.getCheckedKeys()
  console.log(checkedNodes)

  // If more than one node is checked, keep only the latest checked node
  if (checkedKeys.length > 1) {
    // Set only this node as checked
    treeRef.value.setCheckedKeys([checkedNodes.id])
  }
}
</script>
<style>
.custom-tree-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
  font-size: 14px;
}
</style>
