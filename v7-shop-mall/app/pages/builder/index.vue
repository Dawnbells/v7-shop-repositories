<script setup lang="ts">
/**
 * Builder 页面 - 与 admin iframe 的 postMessage 交互
 *
 * 认证机制：
 * - 必须通过 admin iframe 打开并接收认证信息才能使用
 * - 直接访问会显示等待认证，超时后显示错误
 */

import { useIframeAuth } from '~/composables/useIframeAuth'

// iframe 认证
const { isReady: iframeReady, token, stopReadyRetry } = useIframeAuth()

// 认证超时时间（秒）
const AUTH_TIMEOUT = 10
const authTimeout = ref(false)
const authCountdown = ref(AUTH_TIMEOUT)

// 检查是否在 iframe 中
const isInIframe = computed(() => {
  if (import.meta.client) {
    return window.parent !== window
  }
  return false
})

// 是否已认证
const isAuthenticated = computed(() => !!token.value)

// 认证超时倒计时
let timeoutTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  // 如果在 iframe 中且未认证，启动倒计时
  if (isInIframe.value && !isAuthenticated.value) {
    timeoutTimer = setInterval(() => {
      authCountdown.value--
      if (authCountdown.value <= 0) {
        authTimeout.value = true
        if (timeoutTimer) {
          clearInterval(timeoutTimer)
          timeoutTimer = null
        }
        // 停止 BUILDER_READY 重试
        stopReadyRetry()
        // 通知父窗口关闭 dialog
        if (window.parent !== window) {
          window.parent.postMessage({ type: 'themeEditor', action: 'close' }, '*')
          console.log('[Builder] 认证超时，已通知父窗口关闭 dialog')
        }
      }
    }, 1000)
  }

  // 如果已认证，通知父窗口
  if (isAuthenticated.value) {
    notifyAuthenticated()
  }
})

// 认证成功后清除计时器
watch(isAuthenticated, (val) => {
  if (val) {
    if (timeoutTimer) {
      clearInterval(timeoutTimer)
      timeoutTimer = null
    }
    stopReadyRetry()
    notifyAuthenticated()
  }
})

onUnmounted(() => {
  if (timeoutTimer) {
    clearInterval(timeoutTimer)
    timeoutTimer = null
  }
  stopReadyRetry()
})

// 通知父窗口认证完成
function notifyAuthenticated() {
  if (import.meta.client && window.parent !== window) {
    window.parent.postMessage({ type: 'BUILDER_AUTHENTICATED' }, '*')
    console.log('[Builder] 已通知父窗口认证完成')
  }
}

definePageMeta({
  layout: false,
})
</script>

<template>
  <div class="builder-page">
    <!-- 未认证时显示等待或错误 -->
    <div v-if="!isAuthenticated" class="auth-container">
      <!-- 认证超时 -->
      <template v-if="authTimeout">
        <div class="auth-error">
          <div class="auth-icon error">
            <span class="i-carbon-warning-alt text-4xl text-red-500"></span>
          </div>
          <h2 class="auth-title">认证失败</h2>
          <p class="auth-desc">未收到认证信息，请从管理后台打开此页面</p>
          <p class="auth-hint">
            Builder 需要通过管理后台的入口打开
          </p>
        </div>
      </template>
      <!-- 等待认证 -->
      <template v-else>
        <div class="auth-waiting">
          <div class="auth-icon waiting">
            <div class="loading-spinner"></div>
          </div>
          <h2 class="auth-title">等待认证</h2>
          <p class="auth-desc">正在等待管理后台发送认证信息...</p>
          <p class="auth-countdown">{{ authCountdown }} 秒后超时</p>
        </div>
      </template>
    </div>

    <!-- 已认证显示编辑器 -->
    <BuilderThemeEditor v-else />
  </div>
</template>

<style>
html,
body {
  margin: 0;
  padding: 0;
  overflow: hidden;
  width: 100%;
  height: 100%;
}

#__nuxt {
  width: 100%;
  height: 100%;
}
</style>

<style scoped>
.builder-page {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: #1e293b;
}

.auth-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 20px;
}

.auth-waiting,
.auth-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 400px;
}

.auth-icon {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  margin-bottom: 24px;
}

.auth-icon.waiting {
  background-color: rgba(59, 130, 246, 0.1);
}

.auth-icon.error {
  background-color: rgba(239, 68, 68, 0.1);
}

.auth-title {
  margin: 0 0 12px;
  font-size: 24px;
  font-weight: 600;
  color: #f1f5f9;
}

.auth-desc {
  margin: 0 0 8px;
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.6;
}

.auth-hint {
  margin: 16px 0 0;
  padding: 12px 16px;
  font-size: 13px;
  color: #64748b;
  background-color: rgba(51, 65, 85, 0.5);
  border-radius: 8px;
  line-height: 1.5;
}

.auth-countdown {
  margin: 16px 0 0;
  font-size: 13px;
  color: #64748b;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #334155;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

</style>
