<script setup lang="ts">
import { useProductPage } from "~/composables/useProductPage";

const route = useRoute();
const productId = computed(() => route.params.id as string);

// 只传递需要的字段到客户端
const pageContext = usePageContext([
  "cloak.page",
  "cloak.isAdmin",
  "landingProductId",
  "themeConfig",
  "siteConfig",
  "variableValues",
  "languages",
]);

// 产品页面专用 composable
const {
  productInfo,
  productPending,
  themeConfig,
  globalStyle,
  globalStyleVars,
  hasTheme,
  siteConfig,
  variableValues,
  pageSchema,
  layoutSchema,
  useThemeRenderer,
  device,
  useSiteTitle,
} = useProductPage();

// 设置浏览器标签页标题
useSiteTitle(computed(() => productInfo.value?.title || "商品详情"));

// 预览设备
const previewDevice = ref(device);
</script>

<template>
  <div class="product-page" :style="globalStyleVars">
    <!-- 使用 TemplateRenderer 统一渲染 -->
    <TemplateRenderer
      :page="pageSchema"
      :layout="layoutSchema"
      :global-style="globalStyle"
      :site-config="siteConfig"
      :preview-device="previewDevice"
      :is-editor="false"
    >
      <!-- 无主题配置时的 fallback -->
      <template #fallback>
        <template v-if="productPending">
          <div class="product-loading">加载中...</div>
        </template>
        <template v-else-if="productInfo">
          <div class="default-product-page">
            <h1 class="product-default-title">{{ productInfo.title }}</h1>
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
            <div v-if="productInfo.introduction" class="product-description">
              <p>{{ productInfo.introduction }}</p>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="product-not-found">
            <p>商品不存在或已被删除</p>
          </div>
        </template>
      </template>
    </TemplateRenderer>
  </div>
</template>

<style scoped>
.product-page {
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

.product-default-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 1rem;
  color: #1f2937;
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
  margin-bottom: 1rem;
}

.product-image {
  width: 200px;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.product-description {
  margin-top: 1rem;
  padding: 1rem;
  background-color: #f8fafc;
  border-radius: 8px;
  color: #64748b;
}

.product-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: #9ca3af;
  font-size: 14px;
}

.product-not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: #9ca3af;
  font-size: 16px;
}

.product-basic {
  margin-top: 2rem;
}

.product-basic h2 {
  font-size: 1.5rem;
  margin-bottom: 1rem;
}

.no-product {
  padding: 4rem 2rem;
  text-align: center;
  color: #64748b;
}
</style>
