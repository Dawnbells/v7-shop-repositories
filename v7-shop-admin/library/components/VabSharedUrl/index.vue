<template>
  <div style="display: flex; gap: 8px; align-items: center">
    <el-popover
      class="box-item"
      placement="bottom"
      title="共享链接"
      :visible="visible"
      width="500"
      @show="replaceUrl"
    >
      <template #reference>
        <div style="display: inline-block" @click="visible = true">
          <vab-icon icon="link" />
        </div>
      </template>
      <el-form label-width="80">
        <el-form-item label="共享链接">
          <el-input v-model="form.url" clearable placeholder="请粘贴链接" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="form.expireSeconds" placeholder="请选择有效期" style="width: 100%">
            <el-option
              v-for="item in options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div style="margin: 0; text-align: right">
        <el-button :loading="loading" type="danger" @click="handleClose">取消</el-button>
        <el-button :loading="loading" type="primary" @click="handleShared">确定</el-button>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { generateSharedUrl } from '~/src/api/spu'

const options = [
  {
    value: 60,
    label: '1分钟',
  },
  {
    value: 300,
    label: '5分钟',
  },
  {
    value: 1800,
    label: '30分钟',
  },
  {
    value: 3600,
    label: '1小时',
  },
  {
    value: 43200,
    label: '12小时',
  },
  {
    value: 86400,
    label: '1天',
  },
  {
    value: 259200,
    label: '3天',
  },
  {
    value: 604800,
    label: '7天',
  },
  {
    value: 2592000,
    label: '30天',
  },
  {
    value: 999999999,
    label: '永久',
  },
]
const visible = ref<boolean>(false)
const loading = ref<boolean>(false)
const form = reactive<any>({
  url: '',
  expireSeconds: 86400,
})

const handleShared = async () => {
  loading.value = true
  try {
    const res = await generateSharedUrl(form)
    if (res && res.data) {
      copyText2Clipboard(res.data)
      handleClose()
    }
  } finally {
    loading.value = false
  }
}
const handleClose = () => {
  form.url = ''
  form.expireSeconds = 86400
  visible.value = false
  loading.value = false
}

const replaceUrl = async () => {
  const text = await readClipboard()
  if (text && text.startsWith('https://')) {
    form.url = text.trim()
  }
}

const readClipboard = async () => {
  try {
    if (navigator.clipboard && globalThis.isSecureContext) {
      // navigator.clipboard API 可用
      return await navigator.clipboard.readText()
    } else {
      // 兼容不支持 navigator.clipboard 的环境
      const textarea = document.createElement('textarea')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      let result = ''
      try {
        result = document.execCommand('paste') ? textarea.value : ''
      } catch {
        result = ''
      }
      document.body.removeChild(textarea)
      return result
    }
  } catch {
    return ''
  }
}

const copyText2Clipboard = (text?: string) => {
  if (!text) return
  navigator.clipboard
    .writeText(text)
    .then(() => {
      $baseMessage('生成共享链接成功', 'success')
    })
    .catch(() => {
      $baseMessage('生成共享链接失败', 'danger')
    })
}
</script>

<style lang="css">
.box-item {
  width: auto;
}
</style>
