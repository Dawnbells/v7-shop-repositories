<script setup lang="ts">
/**
 * Header Block - 页头组件
 * 展示网站名称、Logo 和购物车图标
 */

interface Props {
  layout?: 'left' | 'center'
  showCart?: boolean
  height?: string
}

const props = withDefaults(defineProps<Props>(), {
  layout: 'left',
  showCart: true,
  height: '60px',
})

const { globalConfig } = usePageTheme()

const siteName = computed(() => globalConfig.value?.siteName || '')
const logo = computed(() => globalConfig.value?.logo || '')
const enableCart = computed(() => globalConfig.value?.enableCart ?? true)

const showCartIcon = computed(() => props.showCart && enableCart.value)

const headerStyle = computed(() => ({
  height: props.height,
}))
</script>

<template>
  <header class="block-header" :style="headerStyle">
    <!-- 左侧区域 -->
    <div class="header-left">
      <template v-if="layout === 'left'">
        <img v-if="logo" :src="logo" alt="Logo" class="header-logo" />
        <span v-if="siteName" class="header-site-name">{{ siteName }}</span>
      </template>
      <template v-else>
        <span v-if="siteName" class="header-site-name">{{ siteName }}</span>
      </template>
    </div>

    <!-- 中间区域 -->
    <div class="header-center">
      <template v-if="layout === 'center'">
        <img v-if="logo" :src="logo" alt="Logo" class="header-logo" />
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
  padding: 0 16px;
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
  height: 32px;
  width: auto;
  object-fit: contain;
}

.header-site-name {
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}

.header-cart {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: inherit;
  text-decoration: none;
  border-radius: 50%;
  transition: background-color 0.2s;
}

.header-cart:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

.header-cart i {
  font-size: 24px;
}
</style>
