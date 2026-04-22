<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="域名记录" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="归属域名" prop="parentName">
        <el-input v-model.trim="form.parentName" clearable disabled />
      </el-form-item>
      <el-form-item prop="countryId">
        <template #label>
          <span>国家</span>
          <el-tooltip content="国家设定后不可修改" placement="top">
            <el-icon style="margin-left: 4px; cursor: help; color: var(--el-color-warning)">
              <Warning />
            </el-icon>
          </el-tooltip>
        </template>
        <el-select
          v-model="form.countryId"
          :disabled="isEdit"
          filterable
          :loading="countryLoading"
          remote
          :remote-method="remoteQueryCountry"
          style="width: 100%"
          @change="onSelectCountry"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="语言" prop="languages">
        <el-input v-model.trim="languages" clearable disabled />
      </el-form-item>
      <el-form-item label="货币" prop="currency">
        <el-input v-model.trim="currency" clearable disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { Warning } from '@element-plus/icons-vue'
import { getRemoteQuery } from '/@/api/country'
import { doEdit } from '/@/api/subDomain'

defineOptions({
  name: 'SubDomainEdit',
})

const emit = defineEmits(['fetch-data', 'close'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const options = ref<any>([])
const title = ref<string>('')
const currency = ref<string>('')
const languages = ref<string>('')
const countryLoading = ref<boolean>(false)
const isEdit = ref<boolean>(false)
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const domain = ref<any>(null)
const form = reactive<any>({
  name: '',
  parentName: '',
  parentDomainId: undefined,
  countryId: undefined,
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '二级域名记录不能为空' }],
  countryId: [{ required: true, trigger: 'blur', message: '请选择国家' }],
})

const showEdit = (parent: any, row?: any) => {
  dialogFormVisible.value = true
  remoteQueryCountry('')
  nextTick(() => {
    isEdit.value = false
    if (row && row.id) {
      // 编辑模式
      isEdit.value = true
      title.value = '编辑'
      domain.value = parent
      Object.assign(form, row)
      form.parentName = parent.name
      form.parentDomainId = parent.id
      if (row.country) {
        form.countryId = row.country.id
        currency.value = row.currency?.name || ''
        languages.value = row.languages?.map((language: any) => language.name).join(',') || ''
      }
    } else {
      // 新增模式
      title.value = '新增'
    domain.value = parent
    form.name = ''
    form.parentName = parent.name
    form.parentDomainId = parent.id
      form.countryId = undefined
      currency.value = ''
      languages.value = ''
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
    countryId: undefined,
  })
  currency.value = ''
  languages.value = ''
  isEdit.value = false
  emit('close')
}

const remoteQueryCountry = async (query: string) => {
  countryLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    options.value = data.list
  } finally {
    countryLoading.value = false
  }
}

const onSelectCountry = (id: any) => {
  const countries = options.value.filter((c: any) => c.id === id)
  if (countries && countries.length > 0) {
    const country = countries[0]
    currency.value = country.currency.name
    languages.value = country.languages.map((language: any) => language.name).join(',')
  }
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        emit('fetch-data', form.parentDomainId)
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}
</script>
