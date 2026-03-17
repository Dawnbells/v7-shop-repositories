<script setup lang="ts">
/**
 * 产品详情页
 *
 * SSR 完整渲染：
 * - 主题数据由中间件加载，通过 usePageTheme 获取
 * - 产品数据通过 useProductPage 获取（从 pageContext）
 * - 绑定解析和组件渲染在服务端完成
 * - 浏览器收到完整渲染的 HTML
 */

// 获取主题相关数据
const { cssVariables, getPageSchema, getLayoutSchema } = usePageTheme();

// 获取产品数据
const { productInfo, formatPrice } = useProductPage();

// 页面配置
const pageSchema = computed(() => getPageSchema("product-detail"));
const layoutSchema = computed(() => {
  const layoutId = pageSchema.value?.layoutId;
  return layoutId ? getLayoutSchema(layoutId) : undefined;
});
const hasTheme = computed(() => !!pageSchema.value);

console.log(JSON.stringify(pageSchema.value, null, 2), "pageSchema");

// 设置浏览器标签页标题
useHead({
  title: computed(() => productInfo.value?.title || "产品详情"),
});

// 提供编辑器状态（非编辑器模式）
provide("isInEditor", ref(false));

// 提供页面数据供 NodeRenderer 绑定解析使用
provide(
  "pageData",
  computed(() => ({
    product: productInfo.value,
  })),
);

// 当前选中的图片索引
const currentImageIndex = ref(0);

// 当前选中的图片
const currentImage = computed(() => {
  const images = productInfo.value?.images || [];
  return images[currentImageIndex.value]?.relativePath || "";
});

// 切换图片
function selectImage(index: number) {
  currentImageIndex.value = index;
}
</script>

<template>
  <div class="product-page" :style="cssVariables">
    <!-- 有主题配置时使用 PageRenderer -->
    <RendererPageRenderer
      v-if="hasTheme && pageSchema"
      :page="pageSchema"
      :layout="layoutSchema"
    />

    <!-- 无主题配置时的 fallback -->
    <template v-else>
      <!-- 产品内容 -->
      <div v-if="productInfo" class="default-product-page">
        <div class="product-gallery">
          <!-- 主图 -->
          <div class="main-image">
            <img
              v-if="currentImage"
              :src="currentImage"
              :alt="productInfo.title"
            />
            <div v-else class="no-image">暂无图片</div>
          </div>

          <!-- 缩略图列表 -->
          <div v-if="productInfo.images?.length > 1" class="thumbnail-list">
            <div
              v-for="(image, index) in productInfo.images"
              :key="image.id"
              class="thumbnail"
              :class="{ active: index === currentImageIndex }"
              @click="selectImage(index)"
            >
              <img :src="image.relativePath" :alt="image.name" />
            </div>
          </div>
        </div>

        <div class="product-info">
          <h1 class="product-title">{{ productInfo.title }}</h1>

          <div class="product-price">
            <span class="sell-price">{{
              formatPrice(productInfo.sellPrice)
            }}</span>
            <span v-if="productInfo.originPrice" class="origin-price">
              {{ formatPrice(productInfo.originPrice) }}
            </span>
          </div>

          <p v-if="productInfo.summary" class="product-summary">
            {{ productInfo.summary }}
          </p>

          <div v-if="productInfo.introduction" class="product-introduction">
            <h3>产品介绍</h3>
            <div v-html="productInfo.introduction"></div>
          </div>

          <!-- 规格选择（多规格产品） -->
          <div
            v-if="
              productInfo.isMultiSpecs && productInfo.specifications?.length
            "
            class="product-specs"
          >
            <h3>规格选择</h3>
            <div class="spec-list">
              <div
                v-for="spec in productInfo.specifications"
                :key="spec.id"
                class="spec-item"
              >
                <span class="spec-attrs">
                  <template v-for="(attr, i) in spec.attributes" :key="i">
                    {{ attr.name }}: {{ attr.value }}
                    <template v-if="i < spec.attributes.length - 1"
                      >,
                    </template>
                  </template>
                </span>
                <span class="spec-price">{{
                  formatPrice(spec.sellPrice)
                }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 产品不存在 -->
      <div v-else class="product-not-found">
        <p>产品不存在或已下架</p>
      </div>
    </template>
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
}

@media (max-width: 768px) {
  .default-product-page {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}

.product-gallery {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.main-image {
  aspect-ratio: 1;
  background: #f1f5f9;
  border-radius: 12px;
  overflow: hidden;
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 14px;
}

.thumbnail-list {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

.thumbnail {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}

.thumbnail.active {
  border-color: var(--primary-color, #3b82f6);
}

.thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.product-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  line-height: 1.3;
}

.product-price {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.sell-price {
  font-size: 28px;
  font-weight: 700;
  color: var(--primary-color, #3b82f6);
}

.origin-price {
  font-size: 16px;
  color: #9ca3af;
  text-decoration: line-through;
}

.product-summary {
  font-size: 15px;
  color: #6b7280;
  line-height: 1.7;
  margin: 0;
}

.product-introduction {
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

.product-introduction h3 {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
  margin: 0 0 12px 0;
}

.product-specs {
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

.product-specs h3 {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
  margin: 0 0 12px 0;
}

.spec-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.spec-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.spec-attrs {
  font-size: 14px;
  color: #4b5563;
}

.spec-price {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color, #3b82f6);
}

.product-not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: #9ca3af;
  font-size: 16px;
  text-align: center;
  padding: 24px;
}
</style>
