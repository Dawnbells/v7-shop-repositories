<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="域名" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model.trim="form.remark" clearable />
      </el-form-item>
      <el-form-item label="到期日期" prop="expiryDate">
        <el-date-picker
          v-model="form.expiryDate"
          placeholder="请选择域名到期日期"
          style="width: 100%"
          type="date"
        />
      </el-form-item>
      <el-form-item label="云平台账户" prop="cloudPlatformAccountId">
        <el-select
          v-model="form.cloudPlatformAccountId"
          filterable
          :loading="selectLoading"
          remote
          :remote-method="remoteQueryMethod"
          style="width: 100%"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="斗篷策略" prop="adPlatform">
        <el-select v-model="form.cloakStrategy">
          <el-option label="无" value="NONE" />
          <el-option label="默认" value="DEFAULT" />
          <el-option label="谷歌(常规)" value="GOOGLE_NORMAL" />
          <el-option label="谷歌(增强)" value="GOOGLE_ENHANCED" />
          <el-option label="谷歌(宽松)" value="GOOGLE_LENIENT" />
          <el-option label="谷歌(严格)" value="GOOGLE_STRICT" />
          <el-option label="安全模式" value="PHANTOM_ISOLATION" />
        </el-select>
      </el-form-item>
      <el-form-item label="域名用途" prop="type">
        <el-select v-model="form.type">
          <el-option label="网站域名" value="WEBSITE" />
          <el-option label="公司域名" value="COMPANY" />
          <el-option label="中继域名" value="RELAY" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQuery } from '/@/api/cloudPlatformAccount'
import { doEdit } from '/@/api/topLevelDomain'

defineOptions({
  name: 'TopLevelDomainEdit',
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
  name: '',
  remark: '',
})
const rules = reactive<any>({
  name: [
    { required: true, trigger: 'blur', message: '请输入顶级域名' },
    {
      pattern: /^[a-zA-Z0-9-]+(\.[a-zA-Z]{2,})$/,
      message: '请输入正确的顶级域名',
      trigger: 'blur',
    },
  ],
  type: { required: true, trigger: 'blur', message: '请选择用途' },
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  form.type = 'WEBSITE'
  nextTick(() => {
    console.log(row)
    if (row) {
      title.value = '编辑'
      options.value = [row.cloudPlatformAccount]
      Object.assign(form, row)
      form.cloudPlatformAccountId = row.cloudPlatformAccount.id
    } else {
      title.value = '添加'
    }
    console.log(form)
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
        console.log('edit')
        console.log(form)
        const { msg }: any = await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}
const remoteQueryMethod = async (query: string) => {
  selectLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    options.value = data.list
  } finally {
    selectLoading.value = false
  }
}
</script>
