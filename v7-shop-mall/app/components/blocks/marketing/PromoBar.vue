<script setup lang="ts">
/**
 * PromoBar Block - 限时促销条组件
 * 促销文案 + 折扣徽章 + 价格展示 + 可选倒计时
 * 价格支持自动读取当前商品（商品详情页），也可手动填写
 */

interface Props {
  text?: string
  showBadge?: boolean
  badgeText?: string
  showPrice?: boolean
  autoPrice?: boolean
  manualPrice?: string
  manualOriginPrice?: string
  originPriceLabel?: string
  countdownEnabled?: boolean
  countdownMode?: 'fixed' | 'cycle'
  countdownEndTime?: string
  countdownHours?: number
  expiredBehavior?: 'hide' | 'zero'
}

const props = withDefaults(defineProps<Props>(), {
  text: '限时特惠，售完即止',
  showBadge: true,
  badgeText: '',
  showPrice: true,
  autoPrice: true,
  manualPrice: '',
  manualOriginPrice: '',
  originPriceLabel: 'RRP:',
  countdownEnabled: false,
  countdownMode: 'cycle',
  countdownEndTime: '',
  countdownHours: 24,
  expiredBehavior: 'zero',
})

const { productInfo, selectedSpec, formatPrice } = useProductPage()

// ============ 价格 ============

// 自动价格：优先规格价，回退商品价（与 ActionButtons 口径一致）
const autoCurrentPrice = computed<number | null>(() => {
  if (!productInfo.value) return null
  return selectedSpec.value?.sellPrice ?? productInfo.value.sellPrice ?? null
})

const autoOriginPrice = computed<number | null>(() => {
  if (!productInfo.value) return null
  return selectedSpec.value?.originPrice ?? productInfo.value.originPrice ?? null
})

const displayPrice = computed<string>(() => {
  if (props.autoPrice && autoCurrentPrice.value != null) {
    return formatPrice(autoCurrentPrice.value)
  }
  return props.manualPrice
})

const displayOriginPrice = computed<string>(() => {
  if (props.autoPrice && autoOriginPrice.value != null) {
    return formatPrice(autoOriginPrice.value)
  }
  return props.manualOriginPrice
})

// 折扣徽章：未填写时按价格自动计算折扣百分比
const displayBadge = computed<string>(() => {
  if (props.badgeText) return props.badgeText
  const price = autoCurrentPrice.value
  const origin = autoOriginPrice.value
  if (props.autoPrice && price != null && origin != null && origin > price) {
    const pct = Math.round(((origin - price) / origin) * 100)
    if (pct > 0) return `${pct}% OFF`
  }
  return ''
})

// ============ 倒计时（SSR 安全：服务端渲染占位，客户端挂载后起跳） ============

const now = ref<number | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  now.value = Date.now()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

// 剩余毫秒数；null 表示客户端尚未就绪
const remainingMs = computed<number | null>(() => {
  if (!props.countdownEnabled || now.value == null) return null

  if (props.countdownMode === 'fixed') {
    if (!props.countdownEndTime) return null
    const end = new Date(props.countdownEndTime.replace(' ', 'T')).getTime()
    if (Number.isNaN(end)) return null
    return Math.max(0, end - now.value)
  }

  // cycle 模式：以自然时间为锚点循环重置
  const cycleMs = Math.max(1, props.countdownHours) * 3600 * 1000
  return cycleMs - (now.value % cycleMs)
})

const isExpired = computed(() => {
  return props.countdownMode === 'fixed' && remainingMs.value === 0
})

const showCountdown = computed(() => {
  if (!props.countdownEnabled) return false
  if (isExpired.value && props.expiredBehavior === 'hide') return false
  return true
})

interface CountdownSegments {
  days: string
  hours: string
  minutes: string
  seconds: string
  showDays: boolean
}

const segments = computed<CountdownSegments>(() => {
  const ms = remainingMs.value
  if (ms == null) {
    return { days: '--', hours: '--', minutes: '--', seconds: '--', showDays: false }
  }
  const totalSeconds = Math.floor(ms / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return {
    days: String(days),
    hours: pad(hours),
    minutes: pad(minutes),
    seconds: pad(seconds),
    showDays: days > 0,
  }
})
</script>

<template>
  <div class="block-promo-bar">
    <div class="promo-main">
      <span v-if="text" class="promo-text">{{ text }}</span>

      <span v-if="showPrice && (displayPrice || displayOriginPrice)" class="promo-price">
        <span v-if="displayOriginPrice" class="promo-origin-price">
          <span v-if="originPriceLabel" class="promo-origin-label">{{ originPriceLabel }}</span>
          {{ displayOriginPrice }}
        </span>
        <span v-if="displayPrice" class="promo-current-price">{{ displayPrice }}</span>
      </span>

      <span v-if="showBadge && displayBadge" class="promo-badge">{{ displayBadge }}</span>
    </div>

    <div v-if="showCountdown" class="promo-countdown">
      <template v-if="segments.showDays">
        <span class="countdown-digit">{{ segments.days }}</span>
        <span class="countdown-separator">:</span>
      </template>
      <span class="countdown-digit">{{ segments.hours }}</span>
      <span class="countdown-separator">:</span>
      <span class="countdown-digit">{{ segments.minutes }}</span>
      <span class="countdown-separator">:</span>
      <span class="countdown-digit">{{ segments.seconds }}</span>
    </div>
  </div>
</template>

<style scoped>
.block-promo-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: var(--promo-justify, center);
  gap: var(--promo-gap, 12px);
  width: 100%;
  box-sizing: border-box;
  padding: var(--promo-padding, 10px 16px);
  border-radius: var(--promo-radius, 0);
  background: var(--promo-bg, linear-gradient(90deg, #ef4444, #f97316));
  color: var(--promo-color, #ffffff);
}

.promo-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--promo-gap, 12px);
  min-width: 0;
}

.promo-text {
  font-size: var(--promo-text-size, 15px);
  font-weight: var(--promo-text-weight, 600);
  line-height: 1.4;
}

.promo-price {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
}

.promo-origin-price {
  font-size: var(--promo-origin-price-size, 13px);
  color: var(--promo-origin-price-color, rgba(255, 255, 255, 0.75));
  text-decoration: line-through;
}

.promo-origin-label {
  text-decoration: none;
  margin-inline-end: 2px;
}

.promo-current-price {
  font-size: var(--promo-price-size, 18px);
  font-weight: 700;
  color: var(--promo-price-color, #ffffff);
}

.promo-badge {
  padding: var(--promo-badge-padding, 2px 8px);
  border-radius: var(--promo-badge-radius, 4px);
  background: var(--promo-badge-bg, #ffffff);
  color: var(--promo-badge-color, #ef4444);
  font-size: var(--promo-badge-size, 13px);
  font-weight: 700;
  white-space: nowrap;
}

.promo-countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.countdown-digit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--promo-countdown-digit-width, 28px);
  padding: var(--promo-countdown-digit-padding, 2px 4px);
  border-radius: var(--promo-countdown-digit-radius, 4px);
  background: var(--promo-countdown-digit-bg, rgba(0, 0, 0, 0.25));
  color: var(--promo-countdown-digit-color, #ffffff);
  font-size: var(--promo-countdown-digit-size, 14px);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.countdown-separator {
  font-weight: 700;
  opacity: 0.8;
}
</style>
