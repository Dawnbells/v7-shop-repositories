<script setup lang="ts">
/**
 * 主题编辑器页面 - CSR 渲染
 *
 * 支持参数方式：
 * 1. URL 参数：/builder?subDomainId=xxx&spuId=xxx&landingType=LAND
 * 2. iframe postMessage：父窗口通过 postMessage 传递认证信息和参数
 * 
 * 当嵌入 admin iframe 时，通过 postMessage 接收：
 * - token：用于 API 鉴权
 * - imageBaseUrl：图片基础 URL
 * - apiBaseUrl：API 基础 URL
 * - query：subDomainId, spuId, landingType
 */

import type { ThemeSchema, CustomVariable } from "~/types/builder";
import { useIframeAuth } from "~/composables/useIframeAuth";

const route = useRoute();

// iframe 认证（初始化 postMessage 监听）
const { isReady: iframeReady, query: iframeQuery } = useIframeAuth();

// 获取查询参数（优先使用 URL 参数，其次使用 iframe 传递的参数）
const subDomainId = computed(() => {
  return (route.query.subDomainId as string) || iframeQuery.value?.subDomainId;
});
const spuId = computed(() => {
  return (route.query.spuId as string) || iframeQuery.value?.spuId;
});
const landingType = computed(() => {
  return (route.query.landingType as string) || iframeQuery.value?.landingType || "LAND";
});

definePageMeta({
  layout: false, // 编辑器使用自定义布局
});

// 主题状态
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

  if (subDomainId.value && spuId.value) {
    console.log("[Builder] 加载主题，参数:", {
      subDomainId: subDomainId.value,
      spuId: spuId.value,
      landingType: landingType.value,
    });

    try {
      // 从 API 加载主题配置（分离的 4 个字段）
      const response = await $fetch<LoadApiResponse>("/api/builder/load", {
        query: {
          subDomainId: subDomainId.value,
          spuId: spuId.value,
          landingType: landingType.value,
        },
      });

      if (response.success && response.data?.themeConfig) {
        // 加载已有主题配置（使用新的分离数据加载方法）
        loadFullData({
          themeConfig: response.data.themeConfig,
          variableSchema: response.data.variableSchema || [],
          siteConfig: response.data.siteConfig || {},
          variableValues: response.data.variableValues || {},
        });
        console.log("[Builder] 已加载主题配置（分离数据）");
      } else {
        // 没有已有配置，初始化新主题
        initTheme("商品落地页主题");
        console.log("[Builder] 初始化新主题");
      }
    } catch (error: any) {
      console.error("[Builder] 加载主题失败:", error);
      // 加载失败时也初始化新主题，让用户可以继续编辑
      initTheme("商品落地页主题");
      loadError.value = "加载主题配置失败，已创建新主题";
    }
  } else {
    // 参数不完整，初始化空主题
    console.warn("[Builder] 参数不完整，请提供 subDomainId 和 spuId");
    initTheme("新主题");
  }

  isLoading.value = false;
}

// 初始化时加载
onMounted(() => {
  loadThemeFromServer();
});

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
</style>
