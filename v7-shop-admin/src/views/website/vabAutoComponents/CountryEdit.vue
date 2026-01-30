<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="750px"
    @close="close"
  >
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <!-- 基本信息 -->
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="国家名称" prop="name">
            <el-input v-model.trim="form.name" clearable placeholder="请输入国家名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="国家代码" prop="code">
            <el-input v-model.trim="form.code" clearable placeholder="如: CN, US" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="归属大陆" prop="continentCode">
            <el-select v-model="form.continentCode" style="width: 100%">
              <el-option key="EU" label="欧洲" value="EU" />
              <el-option key="AS" label="亚洲" value="AS" />
              <el-option key="AF" label="非洲" value="AF" />
              <el-option key="NA" label="北美洲" value="NA" />
              <el-option key="SA" label="南美洲" value="SA" />
              <el-option key="OC" label="大洋洲" value="OC" />
              <el-option key="AN" label="南极洲" value="AN" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务器" prop="frontServerId">
            <el-select
              v-model="form.frontServerId"
              filterable
              :loading="frontServerLoading"
              placeholder="请选择服务器"
              remote
              :remote-method="remoteQueryFrontServer"
              style="width: 100%"
              value-key="id"
            >
              <el-option
                v-for="item in frontServerOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
                <span style="float: left">{{ item.name }}</span>
                <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
                  {{ item.cnameRecord }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="国家语言" prop="languageIds">
            <el-select
              v-model="form.languageIds"
              :disabled="isEdit"
              filterable
              :loading="languageLoading"
              multiple
              placeholder="请选择语言"
              remote
              :remote-method="remoteQueryLanguage"
              style="width: 100%"
              value-key="id"
            >
              <el-option v-for="item in options" :key="item.id" :label="item.cname" :value="item.id">
                <span style="float: left">{{ item.cname }}</span>
                <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
                  {{ item.code }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="国家货币" prop="currencyId">
            <el-select
              v-model="form.currencyId"
              :disabled="isEdit"
              filterable
              :loading="currencyLoading"
              placeholder="请选择货币"
              remote
              :remote-method="remoteQueryCurrency"
              style="width: 100%"
            >
              <el-option
                v-for="item in currencyOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
                <span style="float: left">{{ item.name }}({{ item.symbol }})</span>
                <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
                  {{ item.code }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 收货设置 -->
      <el-divider content-position="left">收货设置</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="电话前缀" prop="phonePrefix">
            <el-input v-model.trim="form.phonePrefix" clearable placeholder="如: +86, +1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="电话验证规则" prop="phoneRule">
            <el-input v-model.trim="form.phoneRule" clearable placeholder="正则表达式" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="必填电话" prop="requiredPhone">
            <el-switch v-model="form.requiredPhone" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="必填邮箱" prop="requiredEmail">
            <el-switch v-model="form.requiredEmail" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="使用全名" prop="useFullName">
            <el-switch v-model="form.useFullName" />
            <el-tooltip content="开启后只需输入一个名字，关闭则分开 First Name 和 Last Name" placement="top">
              <el-icon style="margin-left: 4px; cursor: help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="地址字段" prop="addressFields">
            <el-input
              v-model.trim="form.addressFields"
              clearable
              placeholder="如: province,city,district,postal_code"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="地址验证规则" prop="addressRule">
            <el-input v-model.trim="form.addressRule" clearable placeholder="正则表达式" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 其他设置 -->
      <el-divider content-position="left">其他设置</el-divider>
      <el-form-item label="底部版权信息" prop="footerCopyrightInfo">
        <el-input
          v-model.trim="form.footerCopyrightInfo"
          clearable
          placeholder="页面底部显示的版权信息"
          type="textarea"
          :rows="2"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="saveLoading" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { QuestionFilled } from '@element-plus/icons-vue'
import { doEdit } from '/@/api/country'
import { getRemoteQueryCurrency } from '/@/api/currency'
import { getRemoteQueryLanguage } from '/@/api/language'
import { getRemoteQueryFrontServer } from '/@/api/serverManager'

defineOptions({
  name: 'CountryEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const frontServerLoading = ref<boolean>(false)
const languageLoading = ref<boolean>(false)
const currencyLoading = ref<boolean>(false)
const isEdit = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const options = ref<any[]>([])
const currencyOptions = ref<any[]>([])
const frontServerOptions = ref<any[]>([])
const form = reactive<any>({
  name: '',
  code: '',
  continentCode: 'EU',
  // CountryMeta 字段
  phonePrefix: '',
  phoneRule: '',
  addressRule: '',
  addressFields: 'province,city,district,postal_code',
  useFullName: false,
  footerCopyrightInfo: '',
  requiredPhone: false,
  requiredEmail: false,
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入国家名称' }],
  code: [{ required: true, trigger: 'blur', message: '请输入国家代码' }],
  continentCode: [{ required: true, trigger: 'blur', message: '请选择归属大陆' }],
  frontServerId: [{ required: true, trigger: 'blur', message: '请选择绑定的服务器' }],
  languageIds: [{ required: true, trigger: 'blur', message: '请选择语言' }],
  currencyId: [{ required: true, trigger: 'blur', message: '请选择货币' }],
})

const showEdit = (row: any) => {
  console.log(row)
  dialogFormVisible.value = true
  remoteQueryCurrency('')
  nextTick(() => {
    isEdit.value = false
    if (row) {
      isEdit.value = true
      title.value = '编辑'
      Object.assign(form, row)
      options.value = [...row.languages]
      form.languageIds = row.languages.map((language: any) => language.id)
      frontServerOptions.value = [row.frontServer]
      form.frontServerId = row.frontServer.id
      currencyOptions.value = [row.currency]
      form.currencyId = row.currency.id
      // CountryMeta 字段赋值
      form.phonePrefix = row.phonePrefix || ''
      form.phoneRule = row.phoneRule || ''
      form.addressRule = row.addressRule || ''
      form.addressFields = row.addressFields || 'province,city,district,postal_code'
      form.useFullName = row.useFullName || false
      form.footerCopyrightInfo = row.footerCopyrightInfo || ''
      form.requiredPhone = row.requiredPhone || false
      form.requiredEmail = row.requiredEmail || false
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
  form.languageIds = undefined
  form.currencyId = undefined
  Object.assign(form, {
    id: undefined,
    // 重置 CountryMeta 字段
    phonePrefix: '',
    phoneRule: '',
    addressRule: '',
    addressFields: 'province,city,district,postal_code',
    useFullName: false,
    footerCopyrightInfo: '',
    requiredPhone: false,
    requiredEmail: false,
  })
  isEdit.value = false
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

const remoteQueryFrontServer = async (query: string) => {
  frontServerLoading.value = true
  try {
    const { data } = await getRemoteQueryFrontServer(query)
    frontServerOptions.value = data.list
  } finally {
    frontServerLoading.value = false
  }
}

const remoteQueryLanguage = async (query: string) => {
  languageLoading.value = true
  try {
    const { data } = await getRemoteQueryLanguage(query)
    options.value = data.list
  } finally {
    languageLoading.value = false
  }
}

const remoteQueryCurrency = async (query: string) => {
  currencyLoading.value = true
  try {
    const { data } = await getRemoteQueryCurrency(query)
    currencyOptions.value = data.list
  } finally {
    currencyLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.el-divider {
  margin: 24px 0 16px;

  &:first-child {
    margin-top: 0;
  }
}

.el-row {
  margin-bottom: 4px;
}

// 最后一个 form-item 不需要底部间距
.el-form-item:last-child {
  margin-bottom: 0;
}
</style>
