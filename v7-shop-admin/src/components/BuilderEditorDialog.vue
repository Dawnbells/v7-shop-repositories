<template>
  <vab-dialog
    v-model="dialogVisible"
    append-to-body
    class="theme-editor-dialog"
    fullscreen
    :show-close="false"
    @close="handleClose"
  >
    <!-- Loading 状态 -->
    <div v-if="loading" class="theme-editor-loading">
      <el-icon class="loading-icon"><Loading /></el-icon>
      <span>加载主题编辑器...</span>
    </div>
    <iframe
      v-show="!loading"
      v-if="dialogVisible && builderUrl"
      ref="iframeRef"
      :src="builderUrl"
      class="theme-editor-iframe"
      @load="handleIframeLoad"
    />
  </vab-dialog>
</template>

<script lang="ts" setup>
import { Loading } from '@element-plus/icons-vue'
import { getEnv } from '/@/utils/env'
import { getToken } from '/@/utils/token'

defineOptions({
  name: 'BuilderEditorDialog',
})

// Props
const props = withDefaults(
  defineProps<{
    visible: boolean
    // LANDING 模式参数
    subDomainId?: string | number
    spuId?: string | number
    landingType?: 'LAND' | 'CLOAK' | 'BLACKLISTED'
    subDomainName?: string
    spuName?: string
    // TEMPLATE 模式参数
    templateId?: string | number
    contextName?: string
  }>(),
  {
    subDomainId: undefined,
    spuId: undefined,
    landingType: 'LAND',
    subDomainName: '',
    spuName: '',
    templateId: undefined,
    contextName: '',
  }
)

// Emits
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'close'): void
  (e: 'save'): void
  (e: 'auth-failed', message: string): void
}>()

const $baseMessage = inject<any>('$baseMessage')

// 状态
const loading = ref(true)
const iframeRef = ref<HTMLIFrameElement | null>(null)
let authTimeoutTimer: ReturnType<typeof setTimeout> | null = null

// 计算弹窗可见性（v-model 支持）
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

// 计算模式：有 templateId 为 TEMPLATE 模式，否则为 LANDING 模式
const isTemplateMode = computed(() => !!props.templateId)

// 获取 builder URL
const builderUrl = computed(() => {
  return getEnv('VITE_NUXT_BUILDER_URL', 'http://localhost:3000/builder')
})

// 发送认证信息给 builder iframe
const sendAuthToBuilder = () => {
  const iframe = iframeRef.value || document.querySelector('.theme-editor-iframe') as HTMLIFrameElement
  if (!iframe?.contentWindow) return

  const token = getToken()
  const payload: any = {
    token: token,
    imageBaseUrl: getEnv('VITE_IMAGE_BASE_URL', ''),
    apiBaseUrl: getEnv('VITE_API_BASE_URL', window.location.origin),
  }

  if (isTemplateMode.value) {
    // TEMPLATE 模式
    payload.mode = 'TEMPLATE'
    payload.templateId = props.templateId
    payload.contextName = props.contextName || '主题模板'
  } else {
    // LANDING 模式（站点配置进入落地页主题编辑）
    payload.mode = 'LANDING'
    payload.query = {
      subDomainId: String(props.subDomainId),
      spuId: String(props.spuId),
      landingType: props.landingType,
      subDomainName: props.subDomainName,
      spuName: props.spuName,
    }
  }

  iframe.contentWindow.postMessage(
    {
      type: 'BUILDER_INIT',
      payload,
    },
    '*'
  )
  console.log(`[Admin] 已发送认证信息给 builder (${isTemplateMode.value ? 'TEMPLATE' : 'LANDING'} 模式)`)
}

// iframe 加载完成
const handleIframeLoad = () => {
  setTimeout(() => {
    sendAuthToBuilder()
  }, 100)
}

// 处理 postMessage 消息
const handleMessage = (event: MessageEvent) => {
  // BUILDER_READY - builder 已准备好接收认证信息
  if (event.data?.type === 'BUILDER_READY') {
    console.log('[Admin] 收到 BUILDER_READY，发送认证信息')
    sendAuthToBuilder()
    return
  }

  // BUILDER_AUTHENTICATED - 认证成功，关闭 loading
  if (event.data?.type === 'BUILDER_AUTHENTICATED') {
    console.log('[Admin] 收到 BUILDER_AUTHENTICATED，关闭 loading')
    loading.value = false
    if (authTimeoutTimer) {
      clearTimeout(authTimeoutTimer)
      authTimeoutTimer = null
    }
    return
  }

  // 处理保存/关闭/认证失败
  if (event.data?.type === 'themeEditor') {
    if (event.data.action === 'close') {
      dialogVisible.value = false
      emit('close')
    }
    if (event.data.action === 'save') {
      dialogVisible.value = false
      emit('save')
    }
    if (event.data.action === 'authFailed') {
      dialogVisible.value = false
      emit('auth-failed', event.data.message || '认证失败，请重试')
      $baseMessage?.(event.data.message || '认证失败，请重试', 'error', 'hey')
    }
  }
}

// 关闭弹窗
const handleClose = () => {
  emit('close')
}

// 监听弹窗打开/关闭
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      loading.value = true
      window.addEventListener('message', handleMessage)
      authTimeoutTimer = setTimeout(() => {
        if (loading.value) {
          console.log('[Admin] 认证超时 30 秒，自动关闭 dialog')
          dialogVisible.value = false
          emit('auth-failed', '主题编辑器加载超时，请重试')
          $baseMessage?.('主题编辑器加载超时，请重试', 'error', 'hey')
        }
      }, 30000)
    } else {
      window.removeEventListener('message', handleMessage)
      if (authTimeoutTimer) {
        clearTimeout(authTimeoutTimer)
        authTimeoutTimer = null
      }
    }
  }
)

// 组件卸载时移除监听
onUnmounted(() => {
  window.removeEventListener('message', handleMessage)
  if (authTimeoutTimer) {
    clearTimeout(authTimeoutTimer)
    authTimeoutTimer = null
  }
})
</script>

<style lang="scss" scoped>
.theme-editor-iframe {
  width: 100% !important;
  height: calc(100vh - 3px) !important;
  border: none;
}

.theme-editor-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: #909399;
  font-size: 14px;
  gap: 12px;
  background-color: #1e293b;

  .loading-icon {
    font-size: 32px;
    color: #3b82f6;
    animation: rotate 1s linear infinite;
  }

  span {
    color: #94a3b8;
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>

<style lang="scss">
.theme-editor-dialog {
  .el-dialog__header {
    display: none !important;
  }
  .el-dialog__body {
    padding: 0 !important;
    margin: 0 !important;
    width: 100% !important;
    height: 100vh !important;
  }
  .el-dialog__footer {
    display: none !important;
  }
}
</style>
