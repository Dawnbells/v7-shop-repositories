<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="600px" @close="close">
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <el-form-item label="店铺名称" prop="nickName">
        <el-input v-model.trim="form.nickName" clearable />
      </el-form-item>
      <el-form-item label="HANDLE" prop="handle">
        <el-input
          v-model.trim="form.handle"
          clearable
          :placeholder="isShopify ? 'xxx.myshopify.com 中的 xxx' : ''"
        />
      </el-form-item>
      <el-form-item v-if="!isShopify" label="访问令牌" prop="token">
        <el-input v-model.trim="form.token" clearable />
      </el-form-item>
      <el-form-item v-if="isShopify" label="Client ID" prop="clientId">
        <el-input v-model.trim="form.clientId" clearable />
      </el-form-item>
      <el-form-item v-if="isShopify" label="Client Secret" prop="clientSecret">
        <el-input
          v-model.trim="form.clientSecret"
          clearable
          :placeholder="form.id ? '留空表示不修改' : ''"
          show-password
        />
      </el-form-item>
      <el-form-item label="商城类型" prop="websiteType">
        <el-select v-model="form.websiteType" :disabled="!!form.id" placeholder="请选择店铺类型">
          <el-option label="SHOPLINE" value="SHOPLINE" />
          <el-option label="SHOPIFY" value="SHOPIFY" />
        </el-select>
      </el-form-item>
      <el-form-item label="币种模式" prop="currencyMode">
        <el-select v-model="form.currencyMode" placeholder="请选择币种模式">
          <el-option label="店铺结算币种" value="SHOP_MONEY" />
          <el-option label="订单展示币种" value="PRESENTMENT_MONEY" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit, doEditShopify } from '../../../api/ThirdPartyWebsite'

defineOptions({
  name: 'ThirdPartyWebsiteEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  nickName: '',
  handle: '',
  token: '',
  clientId: '',
  clientSecret: '',
  websiteType: 'SHOPLINE',
  currencyMode: 'SHOP_MONEY',
})
const isShopify = computed<boolean>(() => form.websiteType === 'SHOPIFY')
const rules = reactive<any>({
  nickName: [{ required: true, trigger: 'blur', message: '请输入店铺名称' }],
  handle: [{ required: true, trigger: 'blur', message: '请输入店铺唯一标识' }],
  token: [{ required: true, trigger: 'blur', message: '请输入访问令牌' }],
  clientId: [{ required: true, trigger: 'blur', message: '请输入Client ID' }],
  clientSecret: [
    {
      trigger: 'blur',
      validator: (_rule: any, value: string, callback: any) => {
        // 编辑时留空表示不修改
        if (!value && !form.id) callback(new Error('请输入Client Secret'))
        else callback()
      },
    },
  ],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (!row) title.value = '添加'
    else {
      title.value = '编辑'
      Object.assign(form, row)
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
    token: '',
    clientId: '',
    clientSecret: '',
  })
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = isShopify.value ? await doEditShopify(form) : await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}
</script>
