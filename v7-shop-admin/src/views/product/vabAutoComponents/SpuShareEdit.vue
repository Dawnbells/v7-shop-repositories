<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="分享目标" prop="targetUserId">
        <el-select
          v-model="form.targetUserId"
          filterable
          :loading="userLoading"
          remote
          :remote-method="remoteQueryUser"
          style="width: 100%"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.department && item.department.name }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { remoteQuerySpuSharedUser } from '/@/api/employee'
import { shareSpu } from '/@/api/spu'

defineOptions({
  name: 'SpuEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const userLoading = ref<boolean>(false)
const options = ref<any[]>([])
const form = reactive<any>({
  targetUserId: '',
})
const rules = reactive<any>({
  targetUserId: [{ required: true, trigger: 'blur', message: '请选择分享目标' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = 'SPU分享'
      Object.assign(form, row)
    } else {
      title.value = 'SPU分享'
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
    targetUserId: undefined,
  })
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await shareSpu(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const remoteQueryUser = async (query: string) => {
  userLoading.value = true
  try {
    const { data } = await remoteQuerySpuSharedUser(query, form.id)
    options.value = data.list
  } finally {
    userLoading.value = false
  }
}
</script>
