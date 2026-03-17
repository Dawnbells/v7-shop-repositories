<script setup lang="ts">
/**
 * SpecSelector Block - 规格选择组件
 * 按钮式规格选择，支持图片显示，仅在多规格商品下显示
 */

import type { ProductSpecification } from "~/composables/useProductPage";

interface Props {
  showImage?: boolean;
  imageSize?: string;
  buttonSize?: "small" | "medium" | "large";
}

const props = withDefaults(defineProps<Props>(), {
  showImage: true,
  imageSize: "40px",
  buttonSize: "medium",
});

const { productInfo, selectedSpec, selectSpec, formatPrice } = useProductPage();
const { buildImageUrl } = useImageUrl();

// 是否显示组件（仅多规格商品显示）
const shouldShow = computed(
  () =>
    productInfo.value?.isMultiSpecs &&
    productInfo.value?.specifications?.length > 0,
);

// 规格列表
const specifications = computed(
  () => (productInfo.value?.specifications || []) as ProductSpecification[],
);

// 按属性名分组规格
const groupedAttributes = computed(() => {
  const groups = new Map<string, Set<string>>();
  const attrToSpecs = new Map<string, ProductSpecification[]>();

  for (const spec of specifications.value) {
    for (const attr of spec.attributes) {
      const key = attr.name;
      if (!groups.has(key)) {
        groups.set(key, new Set());
      }
      groups.get(key)!.add(attr.value);

      const attrKey = `${attr.name}:${attr.value}`;
      if (!attrToSpecs.has(attrKey)) {
        attrToSpecs.set(attrKey, []);
      }
      attrToSpecs.get(attrKey)!.push(spec);
    }
  }

  return { groups, attrToSpecs };
});

// 获取属性对应的图片
function getAttributeImage(attrName: string, attrValue: string): string | null {
  const attrKey = `${attrName}:${attrValue}`;
  const specs = groupedAttributes.value.attrToSpecs.get(attrKey);
  if (!specs?.length) return null;

  // 优先使用属性图片
  for (const spec of specs) {
    const attr = spec.attributes.find(
      (a) => a.name === attrName && a.value === attrValue,
    );
    if (attr?.imagePath) {
      return buildImageUrl(attr.imagePath);
    }
  }

  // 其次使用规格图片
  const firstSpec = specs[0];
  if (firstSpec?.specImagePath) {
    return buildImageUrl(firstSpec.specImagePath);
  }

  return null;
}

// 检查属性值是否被选中
function isAttributeSelected(attrName: string, attrValue: string): boolean {
  if (!selectedSpec.value) return false;
  return selectedSpec.value.attributes.some(
    (a) => a.name === attrName && a.value === attrValue,
  );
}

// 选择属性值
function handleSelectAttribute(attrName: string, attrValue: string) {
  const attrKey = `${attrName}:${attrValue}`;
  const specs = groupedAttributes.value.attrToSpecs.get(attrKey);
  const firstSpec = specs?.[0];
  if (firstSpec) {
    selectSpec(firstSpec);
  }
}

// 按钮尺寸类
const buttonSizeClass = computed(() => `size-${props.buttonSize}`);
</script>

<template>
  <div v-if="shouldShow" class="block-spec-selector">
    <div
      v-for="[attrName, attrValues] in groupedAttributes.groups"
      :key="attrName"
      class="spec-group"
    >
      <div class="spec-group-label">{{ attrName }}</div>
      <div class="spec-group-options">
        <button
          v-for="attrValue in attrValues"
          :key="attrValue"
          class="spec-option"
          :class="[
            buttonSizeClass,
            { selected: isAttributeSelected(attrName, attrValue) },
          ]"
          @click="handleSelectAttribute(attrName, attrValue)"
        >
          <img
            v-if="showImage && getAttributeImage(attrName, attrValue)"
            class="spec-option-image"
            :src="getAttributeImage(attrName, attrValue)!"
            :alt="attrValue"
            :style="{ width: imageSize, height: imageSize }"
          />
          <span class="spec-option-text">{{ attrValue }}</span>
        </button>
      </div>
    </div>

    <!-- 选中规格的价格显示 -->
    <div v-if="selectedSpec" class="spec-price-info">
      <span class="spec-price">{{ formatPrice(selectedSpec.sellPrice) }}</span>
      <span
        v-if="
          selectedSpec.originPrice &&
          selectedSpec.originPrice > selectedSpec.sellPrice
        "
        class="spec-origin-price"
      >
        {{ formatPrice(selectedSpec.originPrice) }}
      </span>
      <span v-if="selectedSpec.stockQuantity > 0" class="spec-stock">
        库存: {{ selectedSpec.stockQuantity }}
      </span>
      <span v-else class="spec-stock out-of-stock">缺货</span>
    </div>
  </div>
</template>

<style scoped>
.block-spec-selector {
  container-type: inline-size;
  width: 100%;
  padding: var(--spec-padding, 16px 0);
}

.spec-group {
  margin-bottom: var(--spec-group-gap, 16px);
}

.spec-group:last-child {
  margin-bottom: 0;
}

.spec-group-label {
  font-size: var(--spec-label-size, 14px);
  font-weight: var(--spec-label-weight, 500);
  color: var(--spec-label-color, #374151);
  margin-bottom: var(--spec-label-gap, 8px);
}

.spec-group-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spec-option-gap, 8px);
}

.spec-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: var(--spec-option-padding, 8px 16px);
  border: 1px solid var(--spec-option-border-color, #e5e7eb);
  border-radius: var(--spec-option-radius, 6px);
  background-color: var(--spec-option-bg, #fff);
  color: var(--spec-option-color, #374151);
  font-size: var(--spec-option-font-size, 14px);
  cursor: pointer;
  transition: all 0.2s ease;
}

.spec-option:hover {
  border-color: var(--spec-option-hover-border, #9ca3af);
}

.spec-option.selected {
  border-color: var(
    --spec-option-selected-border,
    var(--primary-color, #3b82f6)
  );
  background-color: var(--spec-option-selected-bg, rgba(59, 130, 246, 0.05));
  color: var(--spec-option-selected-color, var(--primary-color, #3b82f6));
}

.spec-option-image {
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.spec-option-text {
  white-space: nowrap;
}

/* 按钮尺寸 */
.spec-option.size-small {
  padding: 4px 10px;
  font-size: 12px;
}

.spec-option.size-small .spec-option-image {
  width: 24px !important;
  height: 24px !important;
}

.spec-option.size-medium {
  padding: 8px 16px;
  font-size: 14px;
}

.spec-option.size-large {
  padding: 12px 20px;
  font-size: 16px;
}

.spec-option.size-large .spec-option-image {
  width: 48px !important;
  height: 48px !important;
}

/* 价格信息 */
.spec-price-info {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-top: var(--spec-price-gap, 16px);
  padding-top: var(--spec-price-gap, 16px);
  border-top: 1px solid var(--spec-divider-color, #e5e7eb);
}

.spec-price {
  font-size: var(--spec-price-size, 20px);
  font-weight: var(--spec-price-weight, 700);
  color: var(--spec-price-color, var(--primary-color, #3b82f6));
}

.spec-origin-price {
  font-size: var(--spec-origin-price-size, 14px);
  color: var(--spec-origin-price-color, #9ca3af);
  text-decoration: line-through;
}

.spec-stock {
  font-size: 13px;
  color: #6b7280;
  margin-left: auto;
}

.spec-stock.out-of-stock {
  color: #ef4444;
}

/* 响应式：移动端 */
@container (max-width: 480px) {
  .spec-option {
    padding: 6px 12px;
    font-size: 13px;
  }

  .spec-option-image {
    width: 28px !important;
    height: 28px !important;
  }

  .spec-price-info {
    flex-wrap: wrap;
  }

  .spec-stock {
    width: 100%;
    margin-left: 0;
    margin-top: 8px;
  }
}
</style>
