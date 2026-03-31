<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    title="AI 翻译"
    width="500px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="目标语言" prop="languageId">
        <el-select
          v-model="form.languageId"
          filterable
          placeholder="请选择目标语言"
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
      <el-button :loading="submitLoading" type="primary" @click="save">
        提交翻译
      </el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQueryLanguage } from '/@/api/language'
import { translateByAI } from '/@/api/product'
import { useTasksStore } from '/@/store/modules/tasks'

defineOptions({
  name: 'ProductTranslateDialog',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const tasksStore = useTasksStore()
const formRef = ref<any>(null)
const dialogFormVisible = ref<boolean>(false)
const submitLoading = ref<boolean>(false)
const languageLoading = ref<boolean>(false)
const languageOptions = ref<any[]>([])
const form = reactive<any>({
  productId: '',
  languageId: '',
})
const rules = reactive<any>({
  languageId: [{ required: true, trigger: 'change', message: '请选择目标语言' }],
})

let currentProductTitle = ''

const showEdit = async (spuRow: any, productRow: any) => {
  form.productId = String(productRow.id)
  form.languageId = ''
  currentProductTitle = productRow.title || `商品#${productRow.id}`
  dialogFormVisible.value = true

  const existingLanguageIds = new Set(
    (spuRow.productList || []).map((p: any) => p.language?.id)
  )

  languageLoading.value = true
  try {
    const { data } = await getRemoteQueryLanguage('')
    languageOptions.value = (data.list || data).map((lang: any) => ({
      ...lang,
      disabled:
        existingLanguageIds.has(lang.id) ||
        tasksStore.isTranslatingProduct(form.productId, String(lang.id)),
    }))
  } finally {
    languageLoading.value = false
  }
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  form.productId = ''
  form.languageId = ''
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        submitLoading.value = true
        const { data }: any = await translateByAI({
          productId: form.productId,
          languageId: String(form.languageId),
        })

        const selectedLang = languageOptions.value.find(
          (l: any) => l.id === form.languageId
        )
        const langLabel = selectedLang
          ? `${selectedLang.cname}(${selectedLang.name})`
          : form.languageId

        tasksStore.addTask({
          taskId: String(data.taskId),
          taskType: 'PRODUCT_AI_TRANSLATE',
          label: `AI翻译: ${currentProductTitle} → ${langLabel}`,
          state: data.state || 'PENDING',
          progress: data.progress ?? 0,
          message: data.message || '',
        })

        $baseMessage('翻译任务已提交，请在右上角任务栏查看进度', 'success', 'hey')
        dialogFormVisible.value = false
      } catch (e: any) {
        $baseMessage(e?.response?.data?.msg || e?.msg || '提交失败，请重试', 'error', 'hey')
      } finally {
        submitLoading.value = false
      }
    }
  })
}
</script>
