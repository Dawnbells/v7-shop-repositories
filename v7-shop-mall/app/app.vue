<script setup lang="ts">
const { shopNotFound } = usePageContext()
</script>

<template>
  <!-- 域名不存在时显示店铺不存在 -->
  <div v-if="shopNotFound" class="shop-not-found">
    <div class="shop-not-found-content">
      <div class="shop-not-found-icon">🏪</div>
      <h1 class="shop-not-found-title">店铺不存在</h1>
      <p class="shop-not-found-desc">您访问的店铺不存在或尚未开通，请检查网址是否正确</p>
    </div>
  </div>

  <NuxtLayout v-else>
    <NuxtPage />
  </NuxtLayout>
</template>

<style scoped>
.shop-not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8fafc;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

.shop-not-found-content {
  text-align: center;
  padding: 40px 24px;
}

.shop-not-found-icon {
  font-size: 64px;
  margin-bottom: 24px;
}

.shop-not-found-title {
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 12px 0;
}

.shop-not-found-desc {
  font-size: 16px;
  color: #6b7280;
  margin: 0;
  line-height: 1.6;
}
</style>
