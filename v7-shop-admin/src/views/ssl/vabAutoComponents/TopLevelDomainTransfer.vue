<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="域名" prop="name">
        <el-input v-model.trim="form.name" clearable disabled />
      </el-form-item>

      <el-form-item label="转移用户" prop="transferUserId">
        <el-select
          v-model="form.transferUserId"
          filterable
          :loading="selectLoading"
          remote
          :remote-method="remoteQueryTransferUser"
          style="width: 100%"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.telephone }}
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
import { getRemoteQuery } from '/@/api/employee'
import { transfer } from '/@/api/topLevelDomain'

defineOptions({
  name: 'TopLevelDomainTransfer',
})

const emit = defineEmits(['fetch-data', 'close'])
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
  transferUserId: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入顶级域名' }],
  transferUserId: { required: true, trigger: 'blur', message: '请选择转移用户' },
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    title.value = '域名转移'
    form.id = row.id
    form.name = row.name
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
  emit('close')
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await transfer(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}
const remoteQueryTransferUser = async (query: string) => {
  selectLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    options.value = data.list
  } finally {
    selectLoading.value = false
  }
}
</script>
