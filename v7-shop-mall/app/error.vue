<script setup lang="ts">
import { computed } from 'vue'
import { SafePageType } from './types/safe-page'

const props = defineProps<{
  error: {
    statusCode: number
    message: string
    data?: {
      type?: SafePageType
      trackingId?: string
    }
  }
}>()

const config = computed(() => {
  const type = props.error.data?.type

  switch (type) {
    case SafePageType.SHOP_CLOSED:
      return {
        emoji: '🔒',
        title: 'Shop Closed',
        message: 'We apologize, but this shop is currently closed.',
        submessage: 'Thank you for visiting. We will resume service soon.',
      }
    case SafePageType.SHOP_NOT_FOUND:
      return {
        emoji: '🏪',
        title: 'Shop Not Found',
        message: 'Sorry, the shop you are looking for does not exist or has not been activated.',
        submessage: 'Please check the URL and try again.',
      }
    case SafePageType.PRODUCT_NOT_FOUND:
      return {
        emoji: '📦',
        title: 'Product Not Found',
        message: 'Sorry, the product you are looking for does not exist.',
        submessage: 'Please check the link or browse other products.',
      }
    default:
      return {
        emoji: 'ℹ️',
        title: 'Page Not Available',
        message: props.error.message || 'Sorry, this page is not available.',
        submessage: 'Please try again later.',
      }
  }
})

const trackingId = computed(() => props.error.data?.trackingId)
</script>

<template>
  <div class="safe-page">
    <div class="safe-page-content">
      <div class="safe-page-icon">{{ config.emoji }}</div>
      <h1 class="safe-page-title">{{ config.title }}</h1>
      <p class="safe-page-desc">{{ config.message }}</p>
      <p v-if="config.submessage" class="safe-page-subdesc">{{ config.submessage }}</p>
    </div>
    <div v-if="trackingId" style="display: none" :data-pd="trackingId" />
  </div>
</template>

<style scoped>
.safe-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8fafc;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

.safe-page-content {
  text-align: center;
  padding: 40px 24px;
}

.safe-page-icon {
  font-size: 64px;
  margin-bottom: 24px;
}

.safe-page-title {
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 12px 0;
}

.safe-page-desc {
  font-size: 16px;
  color: #6b7280;
  margin: 0;
  line-height: 1.6;
}

.safe-page-subdesc {
  font-size: 14px;
  color: #9ca3af;
  margin: 12px 0 0 0;
  line-height: 1.5;
}
</style>
