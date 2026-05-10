<template>
  <el-dialog
    v-model="visible"
    append-to-body
    :before-close="handleBeforeClose"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    title="AI 翻译"
    width="560px"
    :z-index="4000"
  >
    <el-form label-width="80px" @submit.prevent>
      <el-form-item label="AI账号">
        <el-select
          v-model="aiAccountId"
          filterable
          :loading="accountLoading"
          placeholder="请选择AI账号"
          style="width: 100%"
          @change="onAccountChange"
        >
          <el-option
            v-for="item in accountOptions"
            :key="item.id"
            :label="`${item.name || '未命名'} / ${item.model || '-'}`"
            :value="String(item.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="翻译指令">
        <el-input
          v-model="userPrompt"
          :disabled="loading"
          maxlength="2000"
          placeholder="留空将使用系统默认翻译策略；填写则按你的指令翻译"
          :rows="3"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
      <el-form-item v-if="type === 'html'" label="翻译图片">
        <el-checkbox v-model="translateImages" :disabled="loading">
          同时翻译内嵌图片
        </el-checkbox>
      </el-form-item>
    </el-form>

    <div v-if="resultText !== null" class="result-area">
      <div class="result-label">翻译结果：</div>
      <div class="result-content" v-text="resultText" />
    </div>

    <div v-if="type === 'image' && resultImage" class="image-compare">
      <div class="image-compare-item">
        <div class="image-compare-label">原图</div>
        <el-image :src="sourceImage" style="max-width: 200px; max-height: 200px" />
      </div>
      <div class="image-compare-item">
        <div class="image-compare-label">翻译后</div>
        <el-image :src="resultImage.absolutionPath" style="max-width: 200px; max-height: 200px" />
      </div>
    </div>

    <template #footer>
      <el-button :disabled="loading" @click="visible = false">取消</el-button>
      <el-button
        v-if="!hasResult"
        :disabled="!aiAccountId"
        :loading="loading"
        type="primary"
        @click="startTranslate"
      >
        开始翻译
      </el-button>
      <el-button v-if="hasResult" type="success" @click="applyResult">
        应用
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { page as pageAiAccount } from '/@/api/aiAccount'
import {
  aiTranslateHtmlStream,
  aiTranslateImage,
  aiTranslateTextStream,
} from '/@/api/product'

defineOptions({ name: 'AiTranslatePromptDialog' })

const props = defineProps<{
  type: 'text' | 'html' | 'image'
  source: string | { id?: string; multimediaFileId?: string; absolutionPath?: string }
  languageId: string
}>()

const emit = defineEmits<{
  (e: 'apply', value: any): void
}>()

const STORAGE_KEY = 'v7.aiTranslate.lastAccountId'

const visible = ref(false)
const loading = ref(false)
const accountLoading = ref(false)
const accountOptions = ref<any[]>([])
const aiAccountId = ref('')
const userPrompt = ref('')
const translateImages = ref(false)
const resultText = ref<string | null>(null)
const resultImage = ref<any>(null)
let abortController: AbortController | null = null

const sourceImage = computed(() => {
  if (typeof props.source === 'object') return props.source?.absolutionPath || ''
  return ''
})

const hasResult = computed(() => {
  if (props.type === 'image') return !!resultImage.value
  return resultText.value !== null && !loading.value
})

const open = () => {
  visible.value = true
  resultText.value = null
  resultImage.value = null
  userPrompt.value = ''
  translateImages.value = false
  loading.value = false

  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) aiAccountId.value = saved

  loadAccounts()
}

const loadAccounts = async () => {
  accountLoading.value = true
  try {
    const { data }: any = await pageAiAccount({
      pageNo: 1,
      pageSize: 100,
      status: 'VALID',
      sortBy: 'priority asc,id asc',
    })
    accountOptions.value = data.list || []
    if (!aiAccountId.value && accountOptions.value.length > 0) {
      aiAccountId.value = String(accountOptions.value[0].id)
    }
  } finally {
    accountLoading.value = false
  }
}

const onAccountChange = (val: string) => {
  localStorage.setItem(STORAGE_KEY, val)
}

const handleBeforeClose = (done: () => void) => {
  if (loading.value) return
  done()
}

const startTranslate = () => {
  if (props.type === 'text') doTextStream()
  else if (props.type === 'html') doHtmlStream()
  else if (props.type === 'image') doImageTranslate()
}

const doTextStream = () => {
  loading.value = true
  resultText.value = ''
  abortController = aiTranslateTextStream(
    {
      text: typeof props.source === 'string' ? props.source : '',
      languageId: props.languageId,
      prompt: userPrompt.value || undefined,
      aiAccountId: aiAccountId.value,
    },
    {
      onChunk: (chunk) => {
        resultText.value = (resultText.value || '') + chunk
      },
      onDone: () => {
        loading.value = false
      },
      onError: (err) => {
        loading.value = false
        resultText.value = `翻译失败: ${err}`
      },
    }
  )
}

const doHtmlStream = () => {
  loading.value = true
  resultText.value = ''
  abortController = aiTranslateHtmlStream(
    {
      html: typeof props.source === 'string' ? props.source : '',
      languageId: props.languageId,
      prompt: userPrompt.value || undefined,
      aiAccountId: aiAccountId.value,
    },
    {
      onChunk: (chunk) => {
        resultText.value = (resultText.value || '') + chunk
      },
      onDone: () => {
        loading.value = false
      },
      onError: (err) => {
        loading.value = false
        resultText.value = `翻译失败: ${err}`
      },
    }
  )
}

const doImageTranslate = async () => {
  loading.value = true
  try {
    const src = props.source as { id?: string; multimediaFileId?: string }
    const fileId = src.multimediaFileId || src.id || ''
    const { data }: any = await aiTranslateImage({
      multimediaFileId: String(fileId),
      languageId: props.languageId,
      prompt: userPrompt.value || undefined,
      aiAccountId: aiAccountId.value,
    })
    resultImage.value = data
  } catch (err: any) {
    resultImage.value = null
  } finally {
    loading.value = false
  }
}

const applyResult = () => {
  if (props.type === 'image') {
    emit('apply', resultImage.value)
  } else {
    emit('apply', resultText.value)
  }
  visible.value = false
}

defineExpose({ open })

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<style lang="scss" scoped>
.result-area {
  margin-top: 12px;
  padding: 10px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  max-height: 300px;
  overflow: auto;

  .result-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 6px;
  }

  .result-content {
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 13px;
    line-height: 1.6;
  }
}

.image-compare {
  display: flex;
  gap: 20px;
  margin-top: 12px;
  justify-content: center;

  .image-compare-item {
    text-align: center;

    .image-compare-label {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-bottom: 6px;
    }
  }
}
</style>
