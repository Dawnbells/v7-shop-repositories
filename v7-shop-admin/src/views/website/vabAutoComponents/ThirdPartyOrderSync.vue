<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="600px"
    @close="close"
  >
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <el-form-item label="订单起始时间" prop="createAtMin">
        <el-date-picker v-model="form.createAtMin" placeholder="订单起始时间" type="datetime" />
      </el-form-item>
      <el-form-item label="订单结束时间" prop="createAtMax">
        <el-date-picker v-model="form.createAtMax" placeholder="订单结束时间" type="datetime" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="syncLoading" type="primary" @click="syncOrder">同步订单</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { countOrders, submitSyncOrders } from '../../../api/ThirdPartyWebsite'
import { useTasksStore } from '/@/store/modules/tasks'

defineOptions({
  name: 'ThirdPartyOrderSync',
})

const emit = defineEmits(['fetch-data'])
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const syncLoading = ref<boolean>(false)
const tasksStore = useTasksStore()
const form = reactive<any>({
  createAtMin: '2020-01-01 00:00:00',
  createAtMax: new Date(),
})
const rules = reactive<any>({})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    title.value = '订单同步'
    Object.assign(form, row)
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

const syncOrder = async () => {
  syncLoading.value = true
  try {
    let result = await countOrders(form)
    if (result && result.data) {
      await ElMessageBox.confirm(
        `该时间段总计订单数为 ${result.data.count}，是否立即同步到审单队列？`,
        '确认同步',
        {
          confirmButtonText: '立即同步',
          cancelButtonText: '取消',
        }
      )
      let taskResult = await submitSyncOrders(form)
      const taskId = taskResult?.data ?? taskResult
      if (taskId) {
        tasksStore.addTask({
          taskId: String(taskId),
          taskType: 'THIRD_PARTY_ORDER_SYNC',
          name: `${form.nickName || '商城'}订单同步`,
          label: '第三方订单同步',
          state: 'PENDING',
          progress: 0,
          message: '等待执行...',
        })
        tasksStore.setDrawerOpen(true)
        dialogFormVisible.value = false
      }
    }
  } catch (error) {
    console.error(error)
  } finally {
    syncLoading.value = false
  }
}
</script>
