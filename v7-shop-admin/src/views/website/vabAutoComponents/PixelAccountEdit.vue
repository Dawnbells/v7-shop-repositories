<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="700px"
    @close="close"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="像素名称" prop="pixelName">
        <el-input v-model.trim="form.pixelName" clearable placeholder="请输入像素名称" />
      </el-form-item>
      <el-form-item label="像素平台" prop="platform">
        <el-select v-model="form.platform" placeholder="请选择像素平台" style="width: 100%">
          <el-option label="META" value="META" />
          <el-option label="GOOGLE" value="GOOGLE" />
          <el-option label="TIKTOK" value="TIKTOK" />
        </el-select>
      </el-form-item>
      <el-form-item label="像素ID" prop="pixelId">
        <el-input v-model.trim="form.pixelId" clearable placeholder="请输入像素ID" />
      </el-form-item>
      <el-form-item v-if="form.platform === 'META'" label="AccessToken" prop="accessToken">
        <el-input v-model.trim="form.accessToken" clearable placeholder="请输入AccessToken" />
      </el-form-item>
      <el-form-item
        v-if="form.platform === 'META' || form.platform === 'TIKTOK'"
        label="转化事件"
        prop="conversionEvent"
      >
        <el-select v-model="form.conversionEvent" placeholder="请选择转化事件" style="width: 100%">
          <el-option label="加购物车" value="ADD_TO_CART" />
          <el-option label="下单购买" value="PURCHASE" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.platform === 'GOOGLE'" label="转化标签" prop="accessToken" required>
        <el-input v-model.trim="form.accessToken" clearable placeholder="请输入转化ID" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/pixelAccount'

defineOptions({
  name: 'PixelAccountEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  pixelName: '',
  pixelId: '',
  accessToken: '',
  conversionEvent: 'PURCHASE',
  platform: 'META',
  trackingType: 'GLOBAL',
})
const rules = reactive<any>({
  pixelName: [{ required: true, trigger: 'blur', message: '请输入像素名称' }],
  pixelId: [{ required: true, trigger: 'blur', message: '请输入像素ID' }],
  platform: [{ required: true, trigger: 'blur', message: '请选择像素平台' }],
  conversionEvent: [{ required: true, trigger: 'blur', message: '请选择转化事件' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = '编辑'
      Object.assign(form, row)
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
  })
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}
</script>
