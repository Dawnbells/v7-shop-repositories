<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="800px"
    @close="close"
    @opened="handleOpened"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item v-if="isEditAllStatus" label="订单状态">
        <el-select v-model="form.status" clearable placeholder="请选择订单状态">
          <el-option label="已发货" value="SHIPPED" />
          <el-option label="已签收" value="DELIVERED" />
          <el-option label="拒收" value="REJECTED" />
          <el-option label="丢件" value="LOST" />
          <el-option label="客户取消" value="CUSTOMER_CANCELLED" />
          <el-option label="无效单" value="INVALID" />
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="待审核" value="PENDING" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="isEditAllStatus" label="订单编号:" prop="ids">
        <div style="width: 100%">
          <div style="display: inline-block; width: 50%">
            <el-input
              v-model.trim="form.ids"
              :autosize="{ minRows: 10, maxRows: 10 }"
              clearable
              :formatter="
                (value: string) => {
                  return value.split(',').join('\n')
                }
              "
              :parser="(value: string) => value.split('\n')"
              type="textarea"
            />
          </div>
          <div style="display: inline-block; width: 49%; margin-left: 1%">
            <el-input
              v-model.trim="result"
              :autosize="{ minRows: 10, maxRows: 10 }"
              clearable
              disabled
              type="textarea"
            />
          </div>
        </div>
      </el-form-item>
      <el-form-item label="订单备注:" prop="remark">
        <el-input
          ref="inputRef"
          v-model="form.remark"
          :autosize="{ minRows: 2, maxRows: 4 }"
          clearable
          placeholder="无内容不更新备注"
          type="textarea"
        />
        <el-checkbox v-model="form.clearRemark">删除备注</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { updateOrderStatus } from '/@/api/orderManager'

defineOptions({
  name: 'OrderManagerEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const inputRef = ref<any>(null)
const title = ref<string>('')
const result = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const isEditAllStatus = ref<boolean>(false)
const isSelectedIds = ref<boolean>(false)
const form = reactive<any>({
  status: '',
  ids: [],
  clearRemark: false,
  remark: undefined,
})
const rules = reactive<any>({
  ids: [{ required: true, trigger: 'blur', message: '请输入订单编号列表' }],
  status: [{ required: true, trigger: 'blur', message: '请选择订单状态' }],
})

const showEdit = (status: string, ids: string[]) => {
  isEditAllStatus.value = status === 'ALL'
  isSelectedIds.value = ids && ids.length > 0
  form.status = status === 'ALL' ? 'SHIPPED' : status
  form.ids = ids || []
  result.value = ''
  dialogFormVisible.value = true
}

const handleOpened = () => {
  nextTick(() => {
    if (!isEditAllStatus.value) {
      inputRef.value.focus()
    }
  })
}
defineExpose({
  showEdit,
})

watch(
  form,
  () => {
    const size = `(${form.ids.filter((id: string) => id.trim() !== '').length})`
    title.value = `批量${borderStatus(form.status)}${size}`
  },
  { deep: true }
)

const borderStatus = (status: string) => {
  if (status === 'PENDING') {
    return '待审核'
  }
  if (status === 'CONFIRMED') {
    return '已确认'
  }
  if (status === 'INVALID') {
    return '无效单'
  }
  if (status === 'SHIPPED') {
    return '已发货'
  }
  if (status === 'DELIVERED') {
    return '已签收'
  }
  if (status === 'REJECTED') {
    return '拒收'
  }
  if (status === 'LOST') {
    return '丢件'
  }
  if (status === 'CUSTOMER_CANCELLED') {
    return '客户取消'
  }
  return '正常单'
}

const close = () => {
  formRef.value.clearValidate()
  formRef.value.resetFields()
  Object.assign(form, {
    ids: [],
    status: '',
    remark: undefined,
    clearRemark: false,
  })
  emit('fetch-data')
}

const save = () => {
  result.value = ''
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg, data }: any = await updateOrderStatus({
          ids: form.ids,
          status: form.status,
          remark: form.clearRemark
            ? ''
            : form.remark && form.remark.trim() !== ''
              ? form.remark.trim()
              : undefined,
        })
        if (data && data.total > 0) {
          result.value = data.list.join('\n')
        } else {
          await $baseMessage(msg, 'success', 'hey')
          dialogFormVisible.value = false
        }
      } finally {
        saveLoading.value = false
      }
    }
  })
}
</script>
