<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="500px" @close="close">
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="货币名称" prop="name">
        <el-autocomplete
          v-model="form.name"
          :fetch-suggestions="querySearch"
          popper-class="my-autocomplete"
          style="width: 100%"
          @select="handleSelect"
        >
          <template #default="{ item }">
            <span style="float: left">{{ item.name }}({{ item.symbol }})</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="货币符号" prop="symbol">
        <el-input v-model.trim="form.symbol" clearable />
      </el-form-item>
      <el-form-item label="货币代码" prop="code">
        <el-input v-model.trim="form.code" clearable />
      </el-form-item>
      <el-form-item label="有效位数" prop="fractionDigits">
        <el-input v-model.trim="form.fractionDigits" clearable />
      </el-form-item>
      <el-form-item label="货币汇率" prop="exchangeRate">
        <el-input v-model.trim="form.exchangeRate" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/currency'

defineOptions({
  name: 'CurrencyEdit',
})

interface CurrencyItem {
  name: string
  code: string
  symbol: string
}
const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const isoCurrencies = ref<CurrencyItem[]>([])
const form = reactive<any>({
  name: '',
  symbol: '',
  code: '',
  exchangeRate: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入货币名称' }],
  symbol: [{ required: true, trigger: 'blur', message: '请输入货币符号' }],
  code: [{ required: true, trigger: 'blur', message: '请输入货币代码' }],
  exchangeRate: [{ required: true, trigger: 'blur', message: '请输入汇率' }],
  fractionDigits: [{ required: true, trigger: 'blur', message: '请输入有效小数位' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  getIso4217Currencies()
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

const getIso4217Currencies = async () => {
  const { data } = await axios({
    url: 'static/json/currency.json',
    method: 'get',
  })
  isoCurrencies.value = data
}
const createFilter = (queryString: string) => {
  return (restaurant: CurrencyItem) => {
    return (
      restaurant.name.toLowerCase().indexOf(queryString.toLowerCase()) === 0 ||
      restaurant.symbol.toLowerCase().indexOf(queryString.toLowerCase()) === 0 ||
      restaurant.code.toLowerCase().indexOf(queryString.toLowerCase()) === 0
    )
  }
}
const querySearch = (queryString: string, cb: any) => {
  const results = queryString ? isoCurrencies.value.filter(createFilter(queryString)) : isoCurrencies.value
  // call callback function to return suggestion objects
  cb(results)
}
const handleSelect = (item: any) => {
  form.name = item.name
  form.code = item.code
  form.symbol = item.symbol
}
</script>
