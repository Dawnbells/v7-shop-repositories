<script setup lang="ts">
/**
 * 主题编辑器页面 - CSR 渲染
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

import type { ThemeSchema, CustomVariable } from "~/types/builder";
import { useIframeAuth } from "~/composables";

// iframe 认证（初始化 postMessage 监听）
const { 
  isReady: iframeReady, 
  query: iframeQuery, 
  token, 
  stopReadyRetry,
  mode,
  templateId,
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
          window.parent.postMessage({ 
            type: 'themeEditor', 
            action: 'authFailed',
            message: '认证超时，请重试'
          }, '*');
        }
      }
    }, 1000);
  }
  
  // 如果已认证，直接加载主题
  if (isAuthenticated.value) {
    loadThemeFromServer();
  }
});

// 认证成功后清除计时器并加载主题
watch(isAuthenticated, (val) => {
  if (val) {
    if (timeoutTimer) {
      clearInterval(timeoutTimer);
      timeoutTimer = null;
    }
    // 停止 BUILDER_READY 重试
    stopReadyRetry();
    // 认证成功后加载主题
    loadThemeFromServer();
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

// ==================== 查询参数 ====================

// 从 iframe postMessage 获取查询参数
// LANDING 模式
const subDomainId = computed(() => iframeQuery.value?.subDomainId);
const spuId = computed(() => iframeQuery.value?.spuId);
const landingType = computed(() => iframeQuery.value?.landingType || "LAND");

// 获取显示名称
const displayContextName = computed(() => {
  if (contextName.value) {
    return contextName.value;
  }
  if (isLandingMode.value && iframeQuery.value?.subDomainName) {
    const landingTypeLabel = {
      'LAND': '落地页',
      'CLOAK': '风险页',
      'BLACKLISTED': '黑名单页'
    }[iframeQuery.value.landingType || 'LAND'] || '落地页';
    return `${iframeQuery.value.subDomainName} - ${landingTypeLabel} - ${iframeQuery.value.spuName || 'SPU'}`;
  }
  return '主题编辑器';
});

definePageMeta({
  layout: false, // 编辑器使用自定义布局
});

// ==================== 主题状态 ====================

const { theme, initTheme, loadFullData, clearTheme, hasUnsavedChanges } = useThemeSchema();

// API 响应类型
interface LoadApiResponse {
  success: boolean;
  data: {
    themeConfig: ThemeSchema | null;
    variableSchema: CustomVariable[];
    siteConfig: Record<string, any>;
    variableValues: Record<string, any>;
  } | null;
  message?: string;
}

// 加载状态
const isLoading = ref(true);
const loadError = ref<string | null>(null);

// 加载主题配置
async function loadThemeFromServer() {
  isLoading.value = true;
  loadError.value = null;

  // 先清除现有状态，确保从数据库重新加载
  clearTheme();

  // 根据模式选择不同的加载逻辑
  if (isTemplateMode.value) {
    await loadTemplateTheme();
  } else {
    await loadLandingTheme();
  }

  isLoading.value = false;

  // 通知父窗口认证和加载完成
  if (import.meta.client && window.parent !== window) {
    window.parent.postMessage({ type: 'BUILDER_AUTHENTICATED' }, '*');
    console.log("[Builder] 已通知父窗口认证完成");
  }
}

// 加载模板主题 (TEMPLATE 模式)
async function loadTemplateTheme() {
  const tplId = templateId.value;
  
  if (tplId) {
    console.log("[Builder] 加载模板主题，参数:", { templateId: tplId });

    try {
      const response = await $fetch<LoadApiResponse>("/api/builder/template/load", {
        query: { templateId: tplId },
      });

      if (response.success && response.data?.themeConfig) {
        loadFullData({
          themeConfig: response.data.themeConfig,
          variableSchema: response.data.variableSchema || [],
          siteConfig: response.data.siteConfig || {},
          variableValues: response.data.variableValues || {},
        });
        console.log("[Builder] 已加载模板主题配置");
      } else {
        initTheme(contextName.value || "主题模板");
        console.log("[Builder] 初始化新模板主题");
      }
    } catch (error: any) {
      console.error("[Builder] 加载模板主题失败:", error);
      initTheme(contextName.value || "主题模板");
      loadError.value = "加载模板配置失败，已创建新主题";
    }
  } else {
    console.warn("[Builder] TEMPLATE 模式缺少 templateId");
    initTheme("新模板");
  }
}

// 加载落地页主题 (LANDING 模式)
async function loadLandingTheme() {
  if (subDomainId.value && spuId.value) {
    console.log("[Builder] 加载落地页主题，参数:", {
      subDomainId: subDomainId.value,
      spuId: spuId.value,
      landingType: landingType.value,
    });

    try {
      const response = await $fetch<LoadApiResponse>("/api/builder/load", {
        query: {
          subDomainId: subDomainId.value,
          spuId: spuId.value,
          landingType: landingType.value,
        },
      });

      if (response.success && response.data?.themeConfig) {
        loadFullData({
          themeConfig: response.data.themeConfig,
          variableSchema: response.data.variableSchema || [],
          siteConfig: response.data.siteConfig || {},
          variableValues: response.data.variableValues || {},
        });
        console.log("[Builder] 已加载落地页主题配置");
      } else {
        initTheme("商品落地页主题");
        console.log("[Builder] 初始化新落地页主题");
      }
    } catch (error: any) {
      console.error("[Builder] 加载主题失败:", error);
      initTheme("商品落地页主题");
      loadError.value = "加载主题配置失败，已创建新主题";
    }
  } else {
    console.warn("[Builder] LANDING 模式参数不完整，请提供 subDomainId 和 spuId");
    initTheme("新主题");
  }
}

// 暴露刷新方法供外部调用（如工具栏刷新按钮）
defineExpose({
  reload: loadThemeFromServer,
});

// 离开页面提示
onBeforeRouteLeave((to, from, next) => {
  if (hasUnsavedChanges.value) {
    const answer = window.confirm("有未保存的更改，确定要离开吗？");
    if (!answer) {
      next(false);
      return;
    }
  }
  next();
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
          <p class="auth-hint">主题编辑器需要通过管理后台的"站点配置 → 落地页配置 → 主题"入口打开</p>
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
      <!-- 加载错误提示 -->
      <Transition name="fade">
        <div v-if="loadError" class="load-error-toast">
          <span class="i-carbon-warning mr-2"></span>
          {{ loadError }}
          <button class="close-toast" @click="loadError = null">
            <span class="i-carbon-close"></span>
          </button>
        </div>
      </Transition>

      <ThemeEditor v-if="theme && !isLoading" />

      <!-- 加载中 -->
      <div v-else class="loading-container">
        <div class="loading-spinner"></div>
        <p class="loading-text">加载主题配置中...</p>
      </div>
    </template>
  </div>
</template>

<style>
/* 全局样式重置 - 仅在编辑器页面生效 */
html, body {
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
 * 不受任何父元素或全局样式影响
 */
.builder-page {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: #1e293b;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #334155;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-text {
  margin-top: 16px;
  color: #94a3b8;
  font-size: 14px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 错误提示 */
.load-error-toast {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background-color: rgba(239, 68, 68, 0.9);
  color: white;
  border-radius: 8px;
  font-size: 14px;
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.close-toast {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 12px;
  padding: 4px;
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  opacity: 0.8;
  transition: opacity 0.2s;
}

.close-toast:hover {
  opacity: 1;
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-10px);
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
</style>
