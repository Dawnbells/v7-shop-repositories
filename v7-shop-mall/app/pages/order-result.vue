<script setup lang="ts">
/**
 * 订单结果页
 *
 * SSR 完整渲染：
 * - 主题数据由中间件加载，通过 usePageTheme 获取
 * - 绑定解析和组件渲染在服务端完成
 * - 浏览器收到完整渲染的 HTML
 */

// 获取主题相关数据
const { cssVariables, getPageSchema, getLayoutSchema, siteConfig } = usePageTheme();

// 页面配置
const pageSchema = computed(() => getPageSchema("order-result"));
const layoutSchema = computed(() => {
  const layoutId = pageSchema.value?.layoutId;
  return layoutId ? getLayoutSchema(layoutId) : undefined;
});
const hasTheme = computed(() => !!pageSchema.value);

// 设置浏览器标签页标题
useHead({
  title: computed(() =>
    siteConfig.value?.globalConfig?.siteName
      ? `订单结果 - ${siteConfig.value.globalConfig.siteName}`
      : "订单结果"
  ),
});

// 提供编辑器状态（非编辑器模式）
provide("isInEditor", ref(false));

// 提供页面数据供 NodeRenderer 绑定解析使用
provide(
  "pageData",
  computed(() => ({
    // 订单结果数据后续添加
  }))
);
</script>

<template>
  <div class="order-result-page" :style="cssVariables">
    <!-- 有主题配置时使用 PageRenderer -->
    <RendererPageRenderer
      v-if="hasTheme && pageSchema"
      :page="pageSchema"
      :layout="layoutSchema"
    />

    <!-- 无主题配置时的 fallback -->
    <template v-else>
      <div class="default-order-result">
        <div class="result-container">
          <div class="result-icon">
            <i class="i-carbon-checkmark-filled" />
          </div>
          <h1 class="result-title">订单提交成功</h1>
          <p class="result-desc">感谢您的购买，我们将尽快处理您的订单。</p>
          <NuxtLink to="/" class="back-home-btn">返回首页</NuxtLink>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-result-page {
  min-height: 100vh;
  background-color: var(--background-color, #f8fafc);
  color: var(--text-color, #1e293b);
  font-family: var(
    --font-family,
    "Inter",
    -apple-system,
    BlinkMacSystemFont,
    sans-serif
  );
}

.default-order-result {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 24px;
}

.result-container {
  text-align: center;
  max-width: 480px;
}

.result-icon {
  font-size: 72px;
  color: #22c55e;
  margin-bottom: 24px;
}

.result-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-color, #1f2937);
  margin: 0 0 12px 0;
}

.result-desc {
  font-size: 16px;
  color: var(--text-secondary-color, #6b7280);
  margin: 0 0 32px 0;
  line-height: 1.6;
}

.back-home-btn {
  display: inline-block;
  padding: 12px 32px;
  background-color: var(--primary-color, #3b82f6);
  color: #ffffff;
  text-decoration: none;
  border-radius: 8px;
  font-weight: 500;
  font-size: 15px;
  transition: background-color 0.2s;
}

.back-home-btn:hover {
  background-color: var(--primary-color-dark, #2563eb);
}
</style>
