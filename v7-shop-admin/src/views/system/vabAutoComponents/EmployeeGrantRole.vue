<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="650px" @close="close">
    <el-form ref="formRef" label-width="15px" :model="form">
      <el-form-item label="">
        <el-transfer v-model="roles" :data="toBeAssignedRoleList" :titles="['待分配角色列表', '已分配角色']" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { grantRole } from '/@/api/employee'
import type { Role } from '/@/api/role'

defineOptions({
  name: 'EmployeeGrantRole',
})

interface Option {
  key: number
  label: string
  disabled: boolean
}

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const toBeAssignedRoleList = ref<Option[]>([])
const roles = ref<number[]>([])
const id = ref<any>(0)
const form = ref<any>({})

const showGrant = (allRoleList: Role[], row: any) => {
  roles.value = [...row.roles.map((role: Role) => role.id)]
  id.value = row.id
  toBeAssignedRoleList.value = allRoleList.map((role) => {
    return {
      key: role.id,
      label: role.name,
      disabled: false,
    }
  })
  dialogFormVisible.value = true
  nextTick(() => {
    title.value = '角色分配'
  })
}

defineExpose({
  showGrant,
})

const close = () => {
  formRef.value.resetFields()
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      const { msg }: any = await grantRole({
        id: id.value,
        roles: roles.value,
      })
      await $baseMessage(msg, 'success', 'hey')
      dialogFormVisible.value = false
    }
  })
}
</script>
