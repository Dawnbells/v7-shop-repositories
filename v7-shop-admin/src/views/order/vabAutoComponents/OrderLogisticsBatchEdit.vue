<template>
  <vab-dialog v-model="dialogVisible" append-to-body :title="title" width="560px" @close="close">
    <el-alert
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
      title="留空表示不修改；勾选「清空」将删除选中订单原有的渠道/仓库"
      type="info"
    />
    <el-form label-width="60px" :model="form" @submit.prevent>
      <el-form-item label="渠道">
        <el-input
          v-model="form.deliveryChannel"
          clearable
          :disabled="form.clearDeliveryChannel"
          maxlength="255"
          :placeholder="form.clearDeliveryChannel ? '将清空渠道' : '留空表示不修改'"
          show-word-limit
        />
        <el-checkbox v-model="form.clearDeliveryChannel">清空渠道</el-checkbox>
      </el-form-item>
      <el-form-item label="仓库">
        <el-input
          v-model="form.storehouse"
          clearable
          :disabled="form.clearStorehouse"
          maxlength="255"
          :placeholder="form.clearStorehouse ? '将清空仓库' : '留空表示不修改'"
          show-word-limit
        />
        <el-checkbox v-model="form.clearStorehouse">清空仓库</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { updateOrderLogistics } from '/@/api/orderManager'

defineOptions({
  name: 'OrderLogisticsBatchEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const dialogVisible = ref(false)
const saving = ref(false)
const ids = ref<string[]>([])
const form = reactive({
  deliveryChannel: '',
  storehouse: '',
  clearDeliveryChannel: false,
  clearStorehouse: false,
})

const title = computed(() => `批量修改渠道/仓库(${ids.value.length})`)

// 勾选清空后输入框禁用，同时丢弃已输入的内容
watch(
  () => form.clearDeliveryChannel,
  (clear) => {
    if (clear) form.deliveryChannel = ''
  }
)
watch(
  () => form.clearStorehouse,
  (clear) => {
    if (clear) form.storehouse = ''
  }
)

const showEdit = (orderIds: string[]) => {
  ids.value = orderIds
  dialogVisible.value = true
}

defineExpose({
  showEdit,
})

const close = () => {
  Object.assign(form, {
    deliveryChannel: '',
    storehouse: '',
    clearDeliveryChannel: false,
    clearStorehouse: false,
  })
}

const save = async () => {
  if (saving.value) return
  const deliveryChannel = form.clearDeliveryChannel
    ? undefined
    : form.deliveryChannel.trim() || undefined
  const storehouse = form.clearStorehouse ? undefined : form.storehouse.trim() || undefined
  if (!deliveryChannel && !storehouse && !form.clearDeliveryChannel && !form.clearStorehouse) {
    $baseMessage('请至少填写或清空一项', 'warning', 'hey')
    return
  }
  saving.value = true
  try {
    await updateOrderLogistics({
      ids: ids.value,
      deliveryChannel,
      storehouse,
      clearDeliveryChannel: form.clearDeliveryChannel,
      clearStorehouse: form.clearStorehouse,
    })
    $baseMessage('渠道/仓库修改成功', 'success', 'hey')
    dialogVisible.value = false
    emit('fetch-data')
  } catch {
    // 接口错误由统一请求拦截器提示。
  } finally {
    saving.value = false
  }
}
</script>
