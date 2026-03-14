<script setup lang="ts">
/**
 * Header Block - 页头组件
 * 展示网站名称、Logo 和购物车图标
 * 
 * 样式属性（通过 styleSchema 配置，由渲染器注入到根元素 style）：
 * - height: 页头高度
 * - logoSize: Logo 大小（通过 CSS 变量 --header-logo-size 传递）
 * - siteNameSize: 网站名称字号（通过 CSS 变量 --header-site-name-size 传递）
 * - paddingTop/Bottom/Left/Right: 内边距
 */

interface Props {
  layout?: 'left' | 'center'
  showSiteName?: boolean
  showCart?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  layout: 'left',
  showSiteName: true,
  showCart: true,
})

const { globalConfig } = usePageTheme()
const { currentUrl: logoUrl, handleError: handleLogoError } = useImageWithFallback(
  computed(() => globalConfig.value?.logo)
)

const siteName = computed(() => globalConfig.value?.siteName || '')
const enableCart = computed(() => globalConfig.value?.enableCart ?? true)

const showCartIcon = computed(() => props.showCart && enableCart.value)
</script>

<template>
  <header class="block-header">
    <!-- 左侧区域 -->
    <div class="header-left">
      <template v-if="layout === 'left'">
        <img v-if="logoUrl" :src="logoUrl" alt="Logo" class="header-logo" @error="handleLogoError" />
        <span v-if="showSiteName && siteName" class="header-site-name">{{ siteName }}</span>
      </template>
      <template v-else>
        <span v-if="showSiteName && siteName" class="header-site-name">{{ siteName }}</span>
      </template>
    </div>

    <!-- 中间区域 -->
    <div class="header-center">
      <template v-if="layout === 'center'">
        <img v-if="logoUrl" :src="logoUrl" alt="Logo" class="header-logo" @error="handleLogoError" />
      </template>
    </div>

    <!-- 右侧区域 -->
    <div class="header-right">
      <a v-if="showCartIcon" href="/cart" class="header-cart">
        <i class="i-carbon-shopping-cart" />
      </a>
    </div>
  </header>
</template>

<style scoped>
.block-header {
  display: flex;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
  background-color: var(--surface-color, #ffffff);
  border-bottom-color: var(--border-color, #e2e8f0);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.header-logo {
  height: var(--header-logo-size, 32px);
  width: auto;
  object-fit: contain;
}

.header-site-name {
  font-size: var(--header-site-name-size, 18px);
  font-weight: 600;
  white-space: nowrap;
  color: var(--header-text-color, var(--text-color, #1e293b));
}

.header-cart {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--header-cart-color, var(--text-color, #1e293b));
  text-decoration: none;
  border-radius: 50%;
  transition: background-color 0.2s;
}

.header-cart:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

.header-cart i {
  font-size: var(--header-cart-size, 24px);
}

/* 移动端响应式 */
@media (max-width: 640px) {
  .header-site-name {
    display: none;
  }
  
  .header-logo {
    height: var(--header-logo-size-mobile, var(--header-logo-size, 28px));
  }
  
  .header-cart {
    width: 36px;
    height: 36px;
  }
  
  .header-cart i {
    font-size: var(--header-cart-size-mobile, var(--header-cart-size, 20px));
  }
}
</style>
