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
      <el-button :loading="saveLoading" type="primary" @click="save">
        {{ saveLoading ? '翻译中...' : '开始翻译' }}
      </el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQueryLanguage } from '/@/api/language'
import { translateByAI } from '/@/api/product'

defineOptions({
  name: 'ProductTranslateDialog',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const languageLoading = ref<boolean>(false)
const languageOptions = ref<any[]>([])
const form = reactive<any>({
  productId: '',
  languageId: '',
})
const rules = reactive<any>({
  languageId: [{ required: true, trigger: 'change', message: '请选择目标语言' }],
})

const showEdit = async (spuRow: any, productRow: any) => {
  form.productId = String(productRow.id)
  form.languageId = ''
  dialogFormVisible.value = true

  const existingLanguageIds = new Set(
    (spuRow.productList || []).map((p: any) => p.language?.id)
  )

  languageLoading.value = true
  try {
    const { data } = await getRemoteQueryLanguage('')
    languageOptions.value = (data.list || data).map((lang: any) => ({
      ...lang,
      disabled: existingLanguageIds.has(lang.id),
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
        saveLoading.value = true
        const { msg }: any = await translateByAI({
          productId: form.productId,
          languageId: String(form.languageId),
        })
        await $baseMessage(msg || '翻译完成', 'success', 'hey')
        dialogFormVisible.value = false
      } catch (e: any) {
        $baseMessage(e?.msg || '翻译失败，请重试', 'error', 'hey')
      } finally {
        saveLoading.value = false
      }
    }
  })
}
</script>
