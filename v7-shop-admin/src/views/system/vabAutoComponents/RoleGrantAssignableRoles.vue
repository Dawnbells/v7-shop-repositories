<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="650px" @close="close">
    <el-form ref="formRef" label-width="15px" :model="form">
      <el-form-item label="">
        <el-transfer v-model="selectedRoleIds" :data="transferData" :titles="['可选角色', '已分配角色']" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { grantAssignableRoles } from '/@/api/role'
import type { Role } from '/@/api/role'

defineOptions({
  name: 'RoleGrantAssignableRoles',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const selectedRoleIds = ref<number[]>([])
const transferData = ref<any[]>([])
const form = reactive<any>({})

const showGrant = (allRoles: Role[], row: any) => {
  dialogFormVisible.value = true
  Object.assign(form, row)
  selectedRoleIds.value = [...(row.assignableRoleIds || [])].map(Number)
  transferData.value = allRoles
    .filter((role) => role.id !== row.id)
    .map((role) => ({
      key: Number(role.id),
      label: role.name,
      disabled: false,
    }))
  nextTick(() => {
    title.value = '可分配角色 - ' + row.name
  })
}

defineExpose({
  showGrant,
})

const close = () => {
  Object.assign(form, { id: undefined })
  emit('fetch-data')
}

const save = async () => {
  try {
    saveLoading.value = true
    const { msg }: any = await grantAssignableRoles({
      id: form.id,
      assignableRoleIds: selectedRoleIds.value,
    })
    await $baseMessage(msg, 'success', 'hey')
    dialogFormVisible.value = false
  } finally {
    saveLoading.value = false
  }
}
</script>
