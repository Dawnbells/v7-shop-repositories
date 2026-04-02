<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    title="AI 翻译"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="目标国家" prop="countryId">
        <el-select
          v-model="form.countryId"
          filterable
          placeholder="请选择目标国家"
          :loading="countryLoading"
          style="width: 100%"
          @change="onCountryChange"
        >
          <el-option
            v-for="item in countryOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标语言" prop="languageId">
        <el-select
          v-model="form.languageId"
          :disabled="!form.countryId"
          filterable
          placeholder="请先选择目标国家"
          :loading="languageLoading"
          style="width: 100%"
        >
          <el-option
            v-for="item in languageOptions"
            :key="item.id"
            :disabled="item.disabled"
            :label="item.cname + ' (' + item.name + ')'"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button :loading="batchLoading" type="primary" @click="save('batch')">
        批量翻译
      </el-button>
      <el-button :loading="directLoading" type="success" @click="save('direct')">
        即时翻译
      </el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQuery as getRemoteQueryCountry } from '/@/api/country'
import { translateByAI, translateByAIDirect } from '/@/api/product'
import { useTasksStore } from '/@/store/modules/tasks'

defineOptions({
  name: 'ProductTranslateDialog',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const tasksStore = useTasksStore()
const formRef = ref<any>(null)
const dialogFormVisible = ref<boolean>(false)
const batchLoading = ref<boolean>(false)
const directLoading = ref<boolean>(false)
const countryLoading = ref<boolean>(false)
const languageLoading = ref<boolean>(false)
const countryOptions = ref<any[]>([])
const languageOptions = ref<any[]>([])
const form = reactive<any>({
  productId: '',
  countryId: '',
  languageId: '',
})
const rules = reactive<any>({
  countryId: [{ required: true, trigger: 'change', message: '请选择目标国家' }],
  languageId: [{ required: true, trigger: 'change', message: '请选择目标语言' }],
})

let currentProductTitle = ''
let existingLanguageCountryPairs = new Set<string>()

const onCountryChange = () => {
  form.languageId = ''
  loadLanguagesForCountry(form.countryId)
}

const loadLanguagesForCountry = (countryId: string) => {
  if (!countryId) {
    languageOptions.value = []
    return
  }
  languageLoading.value = true
  const selected = countryOptions.value.find((c: any) => c.id === countryId)
  const langs = selected?.languages ?? []
  languageOptions.value = langs.map((lang: any) => ({
    ...lang,
    disabled:
      existingLanguageCountryPairs.has(`${countryId}:${lang.id}`) ||
      tasksStore.isTranslatingProduct(form.productId, String(countryId), String(lang.id)),
  }))
  languageLoading.value = false
}

const showEdit = async (spuRow: any, productRow: any) => {
  form.productId = String(productRow.id)
  form.countryId = ''
  form.languageId = ''
  currentProductTitle = productRow.title || `商品#${productRow.id}`
  dialogFormVisible.value = true

  existingLanguageCountryPairs = new Set(
    (spuRow.productList || []).map(
      (p: any) => `${p.country?.id}:${p.language?.id}`
    )
  )

  countryLoading.value = true
  try {
    const { data } = await getRemoteQueryCountry('')
    countryOptions.value = data.list || data
  } finally {
    countryLoading.value = false
  }
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  form.productId = ''
  form.countryId = ''
  form.languageId = ''
  languageOptions.value = []
  emit('fetch-data')
}

const save = (mode: 'batch' | 'direct') => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      const isDirect = mode === 'direct'
      const loading = isDirect ? directLoading : batchLoading
      try {
        loading.value = true
        const apiFn = isDirect ? translateByAIDirect : translateByAI
        const taskType = isDirect ? 'PRODUCT_AI_TRANSLATE_DIRECT' : 'PRODUCT_AI_TRANSLATE'

        const { data }: any = await apiFn({
          productId: form.productId,
          countryId: String(form.countryId),
          languageId: String(form.languageId),
        })

        const selectedLang = languageOptions.value.find(
          (l: any) => l.id === form.languageId
        )
        const langLabel = selectedLang
          ? `${selectedLang.cname}(${selectedLang.name})`
          : form.languageId

        const modeLabel = isDirect ? '即时翻译' : 'AI翻译'

        tasksStore.addTask({
          taskId: String(data.taskId),
          taskType,
          name: data.name || '',
          label: data.name || `${modeLabel}: ${currentProductTitle} → ${langLabel}`,
          state: data.state || 'PENDING',
          progress: data.progress ?? 0,
          message: data.message || '',
          parameters: {
            productId: form.productId,
            countryId: String(form.countryId),
            languageId: String(form.languageId),
          },
        })

        $baseMessage('翻译任务已提交，请在右上角任务栏查看进度', 'success', 'hey')
        dialogFormVisible.value = false
      } catch (e: any) {
        $baseMessage(e?.response?.data?.msg || e?.msg || '提交失败，请重试', 'error', 'hey')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>
