<script setup lang="ts">
import { provideDataContext } from "~/composables/useDataContext";

const route = useRoute();
const productId = computed(() => route.params.id as string);

// 只传递需要的字段到客户端
const pageContext = usePageContext(["cloak.page", "cloak.isAdmin", "landingSpuId", "themeConfig", "siteConfig", "variableValues", "languages"]);

// 设备检测
const { device } = useDeviceDetect();

// 主题渲染
const {
  themeConfig,
  globalStyleVars,
  hasTheme,
  getPageSchema,
  getPageLayout,
  defaultLayout,
  productInfo,
  productPending,
  siteConfig,
  useSiteTitle,
} = useThemeRender();

// 设置浏览器标签页标题
useSiteTitle(computed(() => productInfo.value?.title || '商品详情'));

// 提供站点配置给子组件（如 HeaderBar）
provide('siteConfig', siteConfig);

// 商品页配置
const pageSchema = computed(() => getPageSchema("product"));

// 商品页使用的布局（如果页面没有指定布局，使用默认布局）
const layoutSchema = computed(() => {
  const pageLayout = getPageLayout("product");
  return pageLayout || defaultLayout.value;
});

// 提供数据上下文（用于组件内的数据绑定）
provideDataContext({
  product: productInfo.value ?? undefined,
});

// 是否使用主题渲染
const useThemeRenderer = computed(() => {
  return hasTheme.value && !!pageSchema.value;
});
</script>

<template>
  <div class="product-page" :style="globalStyleVars">
    <!-- 使用主题渲染器 -->
    <template v-if="useThemeRenderer">
      <!-- 有布局时使用 LayoutRenderer -->
      <LayoutRenderer
        v-if="layoutSchema && pageSchema"
        :layout="layoutSchema"
        :page="pageSchema"
        :global-style="themeConfig?.globalStyle"
        :preview-device="device"
        :is-editor="false"
      />

      <!-- 无布局时直接使用 PageRenderer -->
      <PageRenderer
        v-else-if="pageSchema"
        :schema="pageSchema"
        :global-style="themeConfig?.globalStyle"
        :preview-device="device"
        :is-editor="false"
      />
    </template>

    <!-- 降级：无主题配置时显示默认页面 -->
    <template v-else>
      <div class="default-product-page">
        <h1>Product {{ productId }}</h1>
        <div class="debug-info">
          <p>Cloak Page: {{ pageContext.cloak?.page }}</p>
          <p>Is Admin: {{ pageContext.cloak?.isAdmin }}</p>
          <p>Landing SPU ID: {{ pageContext.landingSpuId }}</p>
          <p v-if="productPending">Loading product info...</p>
          <details>
            <summary>Product Info</summary>
            <pre>{{ JSON.stringify(productInfo ?? {}, null, 2) }}</pre>
          </details>
        </div>

        <!-- 基础产品展示 -->
        <div v-if="productInfo" class="product-basic">
          <h2>{{ productInfo.title }}</h2>
          <p class="price">
            <span class="sell-price">{{ productInfo.sellPrice }}</span>
            <span v-if="productInfo.originPrice" class="origin-price">
              {{ productInfo.originPrice }}
            </span>
          </p>
          <div v-if="productInfo.images?.length" class="images">
            <img
              v-for="(img, idx) in productInfo.images.slice(0, 3)"
              :key="idx"
              :src="img.relativePath"
              :alt="img.name || productInfo.title"
              class="product-image"
            />
          </div>
        </div>

        <!-- 无产品信息 -->
        <div v-else-if="!productPending" class="no-product">
          <p>暂无产品信息</p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.product-page {
  min-height: 100vh;
  background-color: var(--background-color, #f8fafc);
  color: var(--text-color, #1e293b);
  font-family: var(--font-family, "Inter", -apple-system, BlinkMacSystemFont, sans-serif);
}

.default-product-page {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.debug-info {
  padding: 1rem;
  margin-bottom: 2rem;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
}

.debug-info pre {
  margin-top: 0.5rem;
  padding: 0.5rem;
  background-color: #1e293b;
  color: #e2e8f0;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
}

.product-basic {
  margin-top: 2rem;
}

.product-basic h2 {
  font-size: 1.5rem;
  margin-bottom: 1rem;
}

.price {
  font-size: 1.25rem;
  margin-bottom: 1rem;
}

.sell-price {
  color: #ef4444;
  font-weight: bold;
}

.origin-price {
  margin-left: 0.5rem;
  color: #94a3b8;
  text-decoration: line-through;
}

.images {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.product-image {
  width: 200px;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.no-product {
  padding: 4rem 2rem;
  text-align: center;
  color: #64748b;
}
</style>
