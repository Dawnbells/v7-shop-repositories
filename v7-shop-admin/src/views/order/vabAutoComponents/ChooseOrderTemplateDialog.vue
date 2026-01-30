<template>
  <el-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="isDownloadTemplate ? '选择下载模板' : '选择上传模板'"
    width="500px"
    @close="handleClose"
  >
    <div style="margin-bottom: 20px">
      <el-form label-width="100px">
        <el-form-item label="模板选择" style="width: 100%">
          <el-select
            v-model="selectedTemplateId"
            filterable
            :loading="templateLoading"
            placeholder="请选择模板"
            remote
            :remote-method="fetchTemplates"
          >
            <el-option
              v-for="item in templateOptions"
              :key="item.id"
              :label="item.templateName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <div>
      <el-form label-width="100px">
        <el-form-item label="字段列表" style="width: 100%">
          <el-select v-model="selectedColumnIds" disabled filterable multiple suffix-icon="">
            <el-option
              v-for="col in selectedColumns"
              :key="col.id"
              :label="col.headerName"
              :value="col.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { query } from '~/src/api/orderTemplate'

const emit = defineEmits<{
  (
    event: 'onConfirm',
    data: { templateId: string; isDownloadTemplate: boolean; downloadType: string | undefined }
  ): void
}>()

const dialogFormVisible = ref<boolean>(false)
const isDownloadTemplate = ref<boolean>(false)
const downloadType = ref<string | undefined>(undefined)
const templateOptions = ref<
  {
    id: string
    templateName: string
    columns: { id: number; headerName: string; fieldKey: string }[]
  }[]
>([])
const templateLoading = ref(false)
const selectedTemplateId = ref<string | undefined>(undefined)

const selectedColumns = computed(() => {
  if (selectedTemplateId.value) {
    return templateOptions.value.find((option) => option.id === selectedTemplateId.value)?.columns
  }
})
const selectedColumnIds = computed(() => {
  if (selectedTemplateId.value) {
    return templateOptions.value
      .find((option) => option.id === selectedTemplateId.value)
      ?.columns?.map((item) => item.id)
  }
})

const showEdit = (downloadTemplate: boolean, type: string | undefined) => {
  isDownloadTemplate.value = downloadTemplate
  dialogFormVisible.value = true
  downloadType.value = type
  fetchTemplates('').then(() => {
    // 从 localStorage 还原默认选中
    const saved = localStorage.getItem('selectedTemplate')
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        if (parsed && parsed.id) {
          selectedTemplateId.value = parsed.id
          if (!templateOptions.value.some((option) => option.id === parsed.id)) {
            templateOptions.value = [...templateOptions.value, parsed]
          }
        }
      } catch {
        // ignore parse error
      }
    }
    // 如果没有默认选择第一个
    if (!selectedTemplateId.value && templateOptions.value && templateOptions.value.length > 0) {
      selectedTemplateId.value = templateOptions.value[0].id
    }
  })
}

defineExpose({
  showEdit,
})

const fetchTemplates = async (keyword: string) => {
  templateLoading.value = true
  try {
    // 假设接口 /api/order-templates?type=download|upload
    const type = isDownloadTemplate.value ? 'download' : 'upload'
    const res = await query(type, keyword)
    templateOptions.value = res?.data?.list || []
  } finally {
    templateLoading.value = false
  }
}

const handleClose = () => {
  dialogFormVisible.value = false
  templateOptions.value = []
  isDownloadTemplate.value = true
  templateLoading.value = false
  selectedTemplateId.value = undefined
}

const handleConfirm = () => {
  if (!selectedTemplateId.value) {
    $baseMessage('请选择模板', 'error', 'hey')
    return
  }

  // 将选中的模板写入localStorage
  const selectedTemplate = templateOptions.value.find(
    (option) => option.id === selectedTemplateId.value
  )
  if (selectedTemplate) {
    localStorage.setItem('selectedTemplate', JSON.stringify(selectedTemplate))
  }
  emit('onConfirm', {
    templateId: selectedTemplateId.value!,
    isDownloadTemplate: isDownloadTemplate.value,
    downloadType: downloadType.value,
  })
  handleClose()
}
</script>
