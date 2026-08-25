<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    title="批量编辑中文品名"
    width="600px"
    @close="close"
  >
    <el-alert
      v-if="form.scope === 'OWNED_ALL'"
      class="form-alert"
      :closable="false"
      title="将处理本人名下全部未删除 SPU，不受当前查询条件和分页影响。"
      type="warning"
    />
    <el-alert
      v-if="form.operation === 'REMOVE' && form.emptyResultPolicy === 'KEEP_EMPTY'"
      class="form-alert"
      :closable="false"
      title="删减后没有剩余字段时将保留空值：有前缀会得到“前缀=”，无前缀会得到空字符串。"
      type="error"
    />

    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="作用范围" prop="scope">
        <el-radio-group v-model="form.scope">
          <el-radio value="SELECTED" :disabled="selectedSpuIds.length === 0">
            已勾选 SPU（{{ selectedSpuIds.length }}）
          </el-radio>
          <el-radio value="OWNED_ALL">本人名下全部 SPU</el-radio>
        </el-radio-group>
        <div v-if="selectedSpuIds.length === 0" class="form-tip">
          当前未勾选 SPU；如需按指定集合处理，请先关闭弹窗并勾选。
        </div>
      </el-form-item>

      <el-form-item label="操作" prop="operation">
        <el-radio-group v-model="form.operation">
          <el-radio value="ADD">增加字段</el-radio>
          <el-radio value="REMOVE">删减字段</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="原始中文名称" prop="originalMerchandise">
        <el-input
          v-model="form.originalMerchandise"
          clearable
          maxlength="512"
          placeholder="只处理与此名称完全一致的商品"
          show-word-limit
        />
      </el-form-item>

      <el-form-item :label="form.operation === 'ADD' ? '增加字段' : '删除字段'" prop="field">
        <el-input
          v-model="form.field"
          clearable
          maxlength="512"
          :placeholder="form.operation === 'ADD' ? '请输入要增加的字段' : '请输入要删减的字段'"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="分隔符" prop="delimiter">
        <el-input
          v-model="form.delimiter"
          maxlength="2"
          placeholder="/"
          style="width: 120px"
          @input="onDelimiterChange"
        />
        <span class="form-tip inline-tip">默认“/”，只能填写一个非空白且非“=”字符。</span>
      </el-form-item>

      <el-form-item v-if="form.operation === 'REMOVE'" label="删空处理" prop="emptyResultPolicy">
        <el-radio-group v-model="form.emptyResultPolicy">
          <el-radio value="SKIP">跳过并保留原值</el-radio>
          <el-radio value="KEEP_EMPTY">保留空结果</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button :loading="saveLoading" type="primary" @click="save">确定</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import {
  batchEditMerchandise,
  type BatchEditMerchandiseRequest,
  type BatchEditMerchandiseResult,
} from '/@/api/product'

defineOptions({
  name: 'BatchEditMerchandise',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const $baseConfirm = inject<any>('$baseConfirm')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const formRef = ref<any>(null)
const selectedSpuIds = ref<Array<string | number>>([])

const defaultForm = (): BatchEditMerchandiseRequest => ({
  scope: 'SELECTED',
  operation: 'ADD',
  originalMerchandise: '',
  field: '',
  delimiter: '/',
  emptyResultPolicy: 'SKIP',
})
const form = reactive<BatchEditMerchandiseRequest>(defaultForm())

const validateScope = (_rule: any, value: string, callback: (error?: Error) => void) => {
  if (value === 'SELECTED' && selectedSpuIds.value.length === 0) {
    callback(new Error('请先勾选至少一个 SPU，或选择本人名下全部 SPU'))
    return
  }
  callback()
}

const validateDelimiter = (_rule: any, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入分隔符'))
    return
  }
  const characters = Array.from(value)
  if (characters.length !== 1 || /\s/u.test(value) || value === '=') {
    callback(new Error('分隔符只能是一个非空白且非“=”字符'))
    return
  }
  callback()
}

const validateField = (_rule: any, value: string, callback: (error?: Error) => void) => {
  const field = value?.trim()
  if (!field) {
    callback(new Error('请输入字段'))
    return
  }
  if (form.delimiter && field.includes(form.delimiter)) {
    callback(new Error('字段不能包含当前分隔符'))
    return
  }
  callback()
}

const validateOriginalMerchandise = (
  _rule: any,
  value: string,
  callback: (error?: Error) => void
) => {
  if (!value?.trim()) {
    callback(new Error('请输入原始中文名称'))
    return
  }
  callback()
}

const rules = reactive<any>({
  scope: [{ validator: validateScope, trigger: 'change' }],
  operation: [{ required: true, trigger: 'change', message: '请选择操作类型' }],
  originalMerchandise: [{ validator: validateOriginalMerchandise, trigger: ['blur', 'change'] }],
  field: [{ validator: validateField, trigger: ['blur', 'change'] }],
  delimiter: [{ validator: validateDelimiter, trigger: ['blur', 'change'] }],
  emptyResultPolicy: [{ required: true, trigger: 'change', message: '请选择删空处理方式' }],
})

const showEdit = (selectedRows: any[] = []) => {
  selectedSpuIds.value = selectedRows.map((item: any) => item.id)
  Object.assign(form, defaultForm())
  dialogFormVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const onDelimiterChange = () => {
  formRef.value?.validateField(['delimiter', 'field'])
}

const buildResultMessage = (result: BatchEditMerchandiseResult) => {
  const details = [
    `目标 ${result.targetSpuCount} 个 SPU / ${result.targetProductCount} 个商品`,
    `原始名称匹配 ${result.matchedProductCount} 个`,
    `实际更新 ${result.updatedProductCount} 个`,
  ]
  if (result.originalMismatchCount > 0)
    details.push(`原始名称不匹配跳过 ${result.originalMismatchCount} 个`)
  if (result.alreadyExistsCount > 0) details.push(`已存在跳过 ${result.alreadyExistsCount} 个`)
  if (result.notFoundCount > 0) details.push(`未找到跳过 ${result.notFoundCount} 个`)
  if (result.emptySkippedCount > 0) details.push(`删空跳过 ${result.emptySkippedCount} 个`)
  if (result.emptiedProductCount > 0) details.push(`保留空结果 ${result.emptiedProductCount} 个`)
  return details.join('，')
}

const save = () => {
  if (!formRef.value) return
  formRef.value.validate((valid: boolean) => {
    if (!valid) return
    const scopeText =
      form.scope === 'SELECTED' ? `${selectedSpuIds.value.length} 个已勾选 SPU` : '本人名下全部 SPU'
    const operationText = form.operation === 'ADD' ? '增加' : '删减'
    const emptyWarning =
      form.operation === 'REMOVE' && form.emptyResultPolicy === 'KEEP_EMPTY'
        ? ' 删空后的中文品名将按配置保留为空。'
        : ''
    const confirmText = `确认对${scopeText}中，中文品名完全等于“${form.originalMerchandise.trim()}”的商品${operationText}字段“${form.field.trim()}”吗？分隔符为“${form.delimiter}”。${emptyWarning}`

    $baseConfirm(confirmText, null, async () => {
      try {
        saveLoading.value = true
        const payload: BatchEditMerchandiseRequest = {
          ...form,
          originalMerchandise: form.originalMerchandise.trim(),
          field: form.field.trim(),
          spuIds: form.scope === 'SELECTED' ? selectedSpuIds.value : undefined,
        }
        const { data }: any = await batchEditMerchandise(payload)
        $baseMessage(buildResultMessage(data), 'success', 'hey')
        dialogFormVisible.value = false
        emit('fetch-data')
      } finally {
        saveLoading.value = false
      }
    })
  })
}

const close = () => {
  selectedSpuIds.value = []
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

defineExpose({
  showEdit,
})
</script>

<style lang="scss" scoped>
.form-alert {
  margin-bottom: 16px;
}

.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.inline-tip {
  margin-left: 12px;
}
</style>
