<script setup lang="ts">
/**
 * Reviews Block - 用户评价组件
 * 可配置的评论列表：评分汇总、星级、买家秀图片、已验证徽章
 * 评论数据由商家在搭建器中以 JSON 配置（兼容数组与 JSON 字符串两种形态）
 */

interface ReviewItem {
  name?: string
  rating?: number
  date?: string
  content?: string
  images?: string[]
  avatar?: string
  verified?: boolean
}

interface Props {
  showTitle?: boolean
  title?: string
  reviews?: ReviewItem[] | string
  showSummary?: boolean
  totalCount?: string
  showDates?: boolean
  showImages?: boolean
  verifiedText?: string
  layout?: 'list' | 'grid'
  maxVisible?: number
  loadMoreText?: string
}

const props = withDefaults(defineProps<Props>(), {
  showTitle: true,
  title: '用户评价',
  reviews: '',
  showSummary: true,
  totalCount: '',
  showDates: true,
  showImages: true,
  verifiedText: '已验证购买',
  layout: 'list',
  maxVisible: 0,
  loadMoreText: '查看更多评价',
})

const { buildImageUrl } = useImageUrl()

// ============ 评论数据解析（builder 的 JSON 编辑器存的是字符串） ============

function clampRating(value: unknown): number {
  const num = typeof value === 'number' ? value : parseFloat(String(value))
  if (Number.isNaN(num)) return 5
  return Math.min(5, Math.max(0, num))
}

const parsedReviews = computed<ReviewItem[]>(() => {
  const raw = props.reviews
  let list: unknown = raw

  if (typeof raw === 'string') {
    if (!raw.trim()) return []
    try {
      list = JSON.parse(raw)
    } catch {
      return []
    }
  }

  if (!Array.isArray(list)) return []

  return (list as ReviewItem[])
    .filter((item) => item && (item.name || item.content))
    .map((item) => ({
      ...item,
      rating: clampRating(item.rating ?? 5),
      images: Array.isArray(item.images) ? item.images : [],
    }))
})

// ============ 评分汇总 ============

const averageRating = computed(() => {
  const list = parsedReviews.value
  if (!list.length) return 0
  const sum = list.reduce((acc, item) => acc + (item.rating ?? 5), 0)
  return Math.round((sum / list.length) * 10) / 10
})

const reviewCount = computed(() => parsedReviews.value.length)

// 汇总显示的总数：手动配置优先（如 "1,238"），留空时取评论条数
const displayCount = computed(() => {
  const manual = props.totalCount?.trim()
  return manual || String(reviewCount.value)
})

// ============ 展开/收起 ============

const showAll = ref(false)

const visibleReviews = computed(() => {
  const list = parsedReviews.value
  if (props.maxVisible <= 0 || showAll.value) return list
  return list.slice(0, props.maxVisible)
})

const hasMore = computed(() => {
  return props.maxVisible > 0 && !showAll.value && parsedReviews.value.length > props.maxVisible
})

// ============ 展示辅助 ============

// 星级填充百分比（支持半星等任意小数）
function starFillPercent(rating: number): string {
  return `${(rating / 5) * 100}%`
}

// 头像首字母
function initial(name?: string): string {
  return (name || '?').trim().charAt(0).toUpperCase()
}

// 按名字哈希取稳定的头像底色
const AVATAR_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316']

function avatarColor(name?: string): string {
  const str = name || ''
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 31 + str.charCodeAt(i)) >>> 0
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length] as string
}

function imageUrl(path: string): string {
  return buildImageUrl(path)
}
</script>

<template>
  <div class="block-reviews">
    <!-- 标题 -->
    <h2 v-if="showTitle && title" class="reviews-title">{{ title }}</h2>

    <!-- 评分汇总 -->
    <div v-if="showSummary && reviewCount > 0" class="reviews-summary">
      <span class="summary-score">{{ averageRating }}</span>
      <span class="summary-stars">
        <span class="stars-bg">★★★★★</span>
        <span class="stars-fill" :style="{ width: starFillPercent(averageRating) }">★★★★★</span>
      </span>
      <span class="summary-count">({{ displayCount }})</span>
    </div>

    <!-- 评论列表 -->
    <div v-if="visibleReviews.length > 0" class="reviews-list" :class="`layout-${layout}`">
      <div v-for="(review, index) in visibleReviews" :key="index" class="review-card">
        <div class="review-header">
          <!-- 头像 -->
          <img
            v-if="review.avatar"
            class="review-avatar"
            :src="imageUrl(review.avatar)"
            :alt="review.name"
            loading="lazy"
          />
          <span
            v-else
            class="review-avatar review-avatar-initial"
            :style="{ backgroundColor: avatarColor(review.name) }"
          >
            {{ initial(review.name) }}
          </span>

          <div class="review-meta">
            <div class="review-name-row">
              <span class="review-name">{{ review.name }}</span>
              <span v-if="review.verified" class="review-verified">
                <i class="i-carbon-checkmark-filled" />
                {{ verifiedText }}
              </span>
            </div>
            <div class="review-rating-row">
              <span class="review-stars">
                <span class="stars-bg">★★★★★</span>
                <span class="stars-fill" :style="{ width: starFillPercent(review.rating ?? 5) }">★★★★★</span>
              </span>
              <span v-if="showDates && review.date" class="review-date">{{ review.date }}</span>
            </div>
          </div>
        </div>

        <p v-if="review.content" class="review-content">{{ review.content }}</p>

        <!-- 买家秀图片 -->
        <div v-if="showImages && review.images?.length" class="review-images">
          <img
            v-for="(img, imgIndex) in review.images"
            :key="imgIndex"
            class="review-image"
            :src="imageUrl(img)"
            :alt="`${review.name || 'review'}-${imgIndex + 1}`"
            loading="lazy"
          />
        </div>
      </div>
    </div>

    <!-- 空状态（仅搭建时可见，提醒配置数据） -->
    <div v-else class="reviews-empty">
      <i class="i-carbon-star" />
      <span>请在属性面板配置评论数据</span>
    </div>

    <!-- 查看更多 -->
    <button v-if="hasMore" type="button" class="reviews-more-btn" @click="showAll = true">
      {{ loadMoreText }}
    </button>
  </div>
</template>

<style scoped>
.block-reviews {
  display: flex;
  flex-direction: column;
  gap: var(--reviews-gap, 16px);
  width: 100%;
  max-width: var(--reviews-max-width, 960px);
  margin: 0 auto;
  padding: var(--reviews-padding, 16px);
  box-sizing: border-box;
}

.reviews-title {
  margin: 0;
  font-size: var(--reviews-title-size, 20px);
  font-weight: var(--reviews-title-weight, 700);
  color: var(--reviews-title-color, inherit);
  text-align: var(--reviews-title-align, left);
}

/* 评分汇总 */
.reviews-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-score {
  font-size: var(--reviews-summary-score-size, 28px);
  font-weight: 700;
  color: var(--reviews-star-color, #f59e0b);
  line-height: 1;
}

.summary-count {
  font-size: 14px;
  color: var(--reviews-secondary-color, #9ca3af);
}

/* 星级（背景灰星 + 按百分比裁剪的填充星） */
.summary-stars,
.review-stars {
  position: relative;
  display: inline-block;
  font-size: var(--reviews-star-size, 16px);
  line-height: 1;
  letter-spacing: 2px;
}

.stars-bg {
  color: var(--reviews-star-empty-color, #e5e7eb);
}

.stars-fill {
  position: absolute;
  top: 0;
  inset-inline-start: 0;
  overflow: hidden;
  white-space: nowrap;
  color: var(--reviews-star-color, #f59e0b);
}

/* 列表布局 */
.reviews-list {
  display: flex;
  flex-direction: column;
  gap: var(--reviews-card-gap, 12px);
}

.reviews-list.layout-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
}

@media (max-width: 640px) {
  .reviews-list.layout-grid {
    grid-template-columns: 1fr;
  }
}

/* 评论卡片 */
.review-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: var(--reviews-card-padding, 14px 16px);
  background: var(--reviews-card-bg, #fafafa);
  border: var(--reviews-card-border, 1px solid #f0f0f0);
  border-radius: var(--reviews-card-radius, 10px);
}

.review-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-avatar {
  width: var(--reviews-avatar-size, 38px);
  height: var(--reviews-avatar-size, 38px);
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.review-avatar-initial {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
}

.review-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.review-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.review-name {
  font-size: var(--reviews-name-size, 14px);
  font-weight: 600;
  color: var(--reviews-name-color, inherit);
}

.review-verified {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--reviews-verified-color, #10b981);
}

.review-rating-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-rating-row .review-stars {
  font-size: var(--reviews-card-star-size, 13px);
}

.review-date {
  font-size: 12px;
  color: var(--reviews-secondary-color, #9ca3af);
}

.review-content {
  margin: 0;
  font-size: var(--reviews-content-size, 14px);
  line-height: 1.6;
  color: var(--reviews-content-color, inherit);
  white-space: pre-line;
}

/* 买家秀 */
.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-image {
  width: var(--reviews-image-size, 88px);
  height: var(--reviews-image-size, 88px);
  border-radius: 8px;
  object-fit: cover;
}

/* 空状态 */
.reviews-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  color: #9ca3af;
  font-size: 14px;
  background: #fafafa;
  border: 1px dashed #e5e7eb;
  border-radius: 10px;
}

.reviews-empty i {
  font-size: 24px;
}

/* 查看更多 */
.reviews-more-btn {
  align-self: center;
  padding: 10px 24px;
  border: 1px solid var(--reviews-more-border, #d1d5db);
  border-radius: var(--reviews-more-radius, 999px);
  background: var(--reviews-more-bg, transparent);
  color: var(--reviews-more-color, inherit);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;
}

.reviews-more-btn:hover {
  background: var(--reviews-more-hover-bg, #f3f4f6);
}
</style>
