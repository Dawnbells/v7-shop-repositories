<script setup lang="ts">
/**
 * 主题编辑器页面 - iframe 认证入口
 *
 * 认证机制：
 * - 必须通过 admin iframe 打开并接收认证信息才能使用
 * - 直接访问会显示等待认证，超时后显示错误
 *
 * 当嵌入 admin iframe 时，通过 postMessage 接收：
 * - token：用于 API 鉴权
 * - imageBaseUrl：图片基础 URL
 * - apiBaseUrl：API 基础 URL
 * - mode：编辑模式 ('TEMPLATE' | 'LANDING')
 * - templateId：模板 ID（TEMPLATE 模式）
 * - contextName：上下文名称（显示用）
 * - query：subDomainId, spuId, landingType, subDomainName, spuName（LANDING 模式）
 */

import BuilderMain from "@/components/builder/BuilderMain.vue";
import { useIframeAuth } from "@/composables/base/useIframeAuth";

// iframe 认证（初始化 postMessage 监听）
const {
  isReady: iframeReady,
  token,
  stopReadyRetry,
  contextName,
  isTemplateMode,
  isLandingMode,
} = useIframeAuth();

// ==================== 认证检查 ====================

// 认证超时时间（秒）
const AUTH_TIMEOUT = 10;
const authTimeout = ref(false);
const authCountdown = ref(AUTH_TIMEOUT);

// 检查是否在 iframe 中
const isInIframe = computed(() => {
  if (import.meta.client) {
    return window.parent !== window;
  }
  return false;
});

// 是否已认证
const isAuthenticated = computed(() => {
  // 只有通过 postMessage 收到了 token 才算已认证
  return !!token.value;
});

// 认证超时倒计时
let timeoutTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  // 如果在 iframe 中且未认证，启动倒计时
  if (isInIframe.value && !isAuthenticated.value) {
    timeoutTimer = setInterval(() => {
      authCountdown.value--;
      if (authCountdown.value <= 0) {
        authTimeout.value = true;
        if (timeoutTimer) {
          clearInterval(timeoutTimer);
          timeoutTimer = null;
        }
        // 停止 BUILDER_READY 重试
        stopReadyRetry();
        // 通知父窗口认证失败
        if (window.parent !== window) {
          window.parent.postMessage(
            {
              type: "themeEditor",
              action: "authFailed",
              message: "认证超时，请重试",
            },
            "*"
          );
        }
      }
    }, 1000);
  }
});

// 认证成功后清除计时器
watch(isAuthenticated, (val) => {
  if (val) {
    if (timeoutTimer) {
      clearInterval(timeoutTimer);
      timeoutTimer = null;
    }
    // 停止 BUILDER_READY 重试
    stopReadyRetry();
    // 通知父窗口认证完成
    if (import.meta.client && window.parent !== window) {
      window.parent.postMessage({ type: "BUILDER_AUTHENTICATED" }, "*");
      console.log("[Builder] 已通知父窗口认证完成");
    }
  }
});

onUnmounted(() => {
  if (timeoutTimer) {
    clearInterval(timeoutTimer);
    timeoutTimer = null;
  }
  // 清理重试定时器
  stopReadyRetry();
});

definePageMeta({
  layout: false, // 编辑器使用自定义布局
});
</script>

<template>
  <div class="builder-page">
    <!-- 未认证时显示等待或错误 -->
    <div v-if="!isAuthenticated" class="auth-container">
      <!-- 认证超时 -->
      <template v-if="authTimeout">
        <div class="auth-error">
          <div class="auth-icon error">
            <span class="i-carbon-warning-alt"></span>
          </div>
          <h2 class="auth-title">认证失败</h2>
          <p class="auth-desc">未收到认证信息，请从管理后台打开此页面</p>
          <p class="auth-hint">
            主题编辑器需要通过管理后台的"站点配置 → 落地页配置 → 主题"入口打开
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
    <template v-else>
      <BuilderMain />
    </template>
  </div>
</template>

<style>
/* 全局样式重置 - 仅在编辑器页面生效 */
html,
body {
  margin: 0;
  padding: 0;
  overflow: hidden;
  width: 100%;
  height: 100%;
}

/* 移除 Nuxt 默认的根元素样式干扰 */
#__nuxt {
  width: 100%;
  height: 100%;
}
</style>

<style scoped>
/**
 * 编辑器页面容器
 * 使用 position: fixed + inset: 0 确保完全覆盖视口
 */
.builder-page {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: #0f172a;
  color: #e2e8f0;
}

/* 认证容器 */
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

.auth-icon.error span {
  font-size: 40px;
  color: #ef4444;
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

/* 加载动画 */
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
