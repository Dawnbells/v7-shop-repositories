<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductReviews 组件元数据
 * 用户评价列表组件
 */
export const meta: ComponentMeta = {
  type: "product-reviews",
  name: "用户评价",
  icon: "i-carbon-star",
  category: "business",
  description: "展示商品用户评价列表",

  propsSchema: [
    {
      key: "reviews",
      label: "评价数据",
      type: "json",
      defaultValue: [],
      description: "评价列表数据",
    },
    {
      key: "title",
      label: "标题",
      type: "text",
      defaultValue: "用户评价",
    },
    {
      key: "showTitle",
      label: "显示标题",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "maxCount",
      label: "最大显示数",
      type: "number",
      defaultValue: 10,
    },
  ],

  styleSchema: [],

  supportEvents: ["click"],

  defaultProps: {
    reviews: [],
    title: "用户评价",
    showTitle: true,
    maxCount: 10,
  },

  defaultStyle: {
    base: {
      width: "100%",
    },
  },

  isContainer: false,
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
// 评价项类型
interface ReviewItem {
  user: string;
  avatar?: string;
  rating: number;
  content: string;
  date: string;
  images?: string[];
}

interface Props {
  reviews?: ReviewItem[];
  title?: string;
  showTitle?: boolean;
  maxCount?: number;
}

const props = withDefaults(defineProps<Props>(), {
  reviews: () => [],
  title: "用户评价",
  showTitle: true,
  maxCount: 10,
});

const emit = defineEmits<{
  (e: "click", event: MouseEvent): void;
}>();

// 显示的评价列表
const displayedReviews = computed(() => {
  return props.reviews.slice(0, props.maxCount);
});

// 渲染星级
function renderStars(rating: number): Array<"full" | "half" | "empty"> {
  const stars: Array<"full" | "half" | "empty"> = [];
  for (let i = 1; i <= 5; i++) {
    if (i <= rating) {
      stars.push("full");
    } else if (i - 0.5 <= rating) {
      stars.push("half");
    } else {
      stars.push("empty");
    }
  }
  return stars;
}
</script>

<template>
  <div class="product-reviews">
    <h3 v-if="showTitle && title" class="reviews-title">
      {{ title }}
      <span v-if="reviews.length" class="review-count">({{ reviews.length }})</span>
    </h3>

    <div v-if="displayedReviews.length" class="reviews-list">
      <div v-for="(review, index) in displayedReviews" :key="index" class="review-item">
        <div class="review-header">
          <div class="review-user">
            <div class="user-avatar">
              <img v-if="review.avatar" :src="review.avatar" :alt="review.user" />
              <span v-else class="i-carbon-user"></span>
            </div>
            <span class="user-name">{{ review.user }}</span>
          </div>
          <div class="review-rating">
            <span
              v-for="(star, i) in renderStars(review.rating)"
              :key="i"
              class="star"
              :class="star"
            >
              <span
                :class="
                  star === 'full'
                    ? 'i-carbon-star-filled'
                    : star === 'half'
                      ? 'i-carbon-star-half'
                      : 'i-carbon-star'
                "
              ></span>
            </span>
          </div>
        </div>
        <div class="review-content">{{ review.content }}</div>
        <div v-if="review.images?.length" class="review-images">
          <img
            v-for="(img, imgIndex) in review.images"
            :key="imgIndex"
            :src="img"
            alt="评价图片"
          />
        </div>
        <div class="review-date">{{ review.date }}</div>
      </div>
    </div>

    <div v-else class="reviews-empty">暂无用户评价</div>
  </div>
</template>

<style scoped>
.product-reviews {
  width: 100%;
}

.reviews-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 16px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.review-count {
  font-size: 14px;
  font-weight: 400;
  color: #9ca3af;
  margin-left: 4px;
}

.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.review-item {
  padding: 16px 0;
  border-bottom: 1px solid #f3f4f6;
}

.review-item:last-child {
  border-bottom: none;
}

.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.review-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  overflow: hidden;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
}

.review-rating {
  display: flex;
  gap: 2px;
}

.star {
  font-size: 14px;
}

.star.full {
  color: #fbbf24;
}

.star.half {
  color: #fbbf24;
}

.star.empty {
  color: #e5e7eb;
}

.review-content {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
  margin-bottom: 12px;
}

.review-images {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  overflow-x: auto;
}

.review-images img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  flex-shrink: 0;
}

.review-date {
  font-size: 12px;
  color: #9ca3af;
}

.reviews-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: #9ca3af;
  font-size: 14px;
}
</style>
