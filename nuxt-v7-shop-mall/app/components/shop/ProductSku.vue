<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * ProductSku 组件元数据
 * SKU规格选择器，支持多规格选择
 */
export const meta: ComponentMeta = {
  type: "product-sku",
  name: "SKU选择器",
  icon: "i-carbon-catalog",
  category: "business",
  description: "商品规格选择器，支持颜色、尺码等多规格选择",

  propsSchema: [
    {
      key: "skuData",
      label: "SKU数据(静态)",
      type: "json",
      defaultValue: [],
      description: "留空则自动绑定产品规格",
    },
    {
      key: "layout",
      label: "布局",
      type: "select",
      options: [
        { label: "纵向", value: "vertical" },
        { label: "横向", value: "horizontal" },
      ],
      defaultValue: "vertical",
    },
  ],

  styleSchema: [
    {
      key: "gap",
      label: "规格间距",
      type: "size",
      defaultValue: "16px",
      unit: "px",
    },
  ],

  supportEvents: ["change"],

  defaultProps: {
    skuData: [],
    layout: "vertical",
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
import { useDataContext } from "~/composables/useDataContext";
import type { ProductSpecification, SpecificationAttribute } from "~/types/page-context";

// SKU 值类型
interface SkuValue {
  id: string;
  name: string;
  image?: string;
}

// SKU 选项类型
interface SkuOption {
  id: string;
  name: string;
  values: SkuValue[];
}

interface Props {
  skuData?: SkuOption[];
  layout?: "vertical" | "horizontal";
}

const props = withDefaults(defineProps<Props>(), {
  skuData: () => [],
  layout: "vertical",
});

const emit = defineEmits<{
  (e: "change", selection: Record<string, string>): void;
}>();

// 数据上下文
const dataContext = useDataContext();

// 将后端规格数据转换为 SKU 选项格式
function convertSpecificationsToSku(specifications: ProductSpecification[]): SkuOption[] {
  if (!specifications || specifications.length === 0) return [];
  
  // 收集所有属性
  const attributeMap = new Map<string, Set<string>>();
  
  for (const spec of specifications) {
    for (const attr of spec.attributes || []) {
      if (!attributeMap.has(attr.name)) {
        attributeMap.set(attr.name, new Set());
      }
      attributeMap.get(attr.name)!.add(attr.value);
    }
  }
  
  // 转换为 SKU 选项
  const skuOptions: SkuOption[] = [];
  attributeMap.forEach((values, name) => {
    skuOptions.push({
      id: name,
      name: name,
      values: Array.from(values).map((value) => ({
        id: value,
        name: value,
      })),
    });
  });
  
  return skuOptions;
}

// 获取 SKU 数据 - 优先使用 props，否则从 dataContext 获取
const skuOptions = computed<SkuOption[]>(() => {
  if (props.skuData && props.skuData.length > 0) {
    return props.skuData;
  }
  // 从 dataContext.product.specifications 获取
  const specifications = dataContext.value.product?.specifications;
  if (specifications && specifications.length > 0) {
    return convertSpecificationsToSku(specifications);
  }
  return [];
});

// 当前选中的 SKU
const selection = ref<Record<string, string>>({});

// 选择 SKU 值
function selectSku(skuId: string, valueId: string) {
  selection.value = {
    ...selection.value,
    [skuId]: valueId,
  };
  emit("change", selection.value);
}

// 判断是否选中
function isSelected(skuId: string, valueId: string): boolean {
  return selection.value[skuId] === valueId;
}
</script>

<template>
  <div
    class="product-sku"
    :class="[`layout-${layout}`]"
  >
    <div v-for="sku in skuOptions" :key="sku.id" class="sku-group">
      <div class="sku-label">{{ sku.name }}</div>
      <div class="sku-values">
        <button
          v-for="value in sku.values"
          :key="value.id"
          class="sku-value-btn"
          :class="{ selected: isSelected(sku.id, value.id) }"
          @click="selectSku(sku.id, value.id)"
        >
          <img
            v-if="value.image"
            :src="value.image"
            :alt="value.name"
            class="sku-value-img"
          />
          <span class="sku-value-name">{{ value.name }}</span>
        </button>
      </div>
    </div>

    <!-- 无规格时显示 -->
    <div v-if="skuOptions.length === 0" class="sku-empty">
      暂无可选规格
    </div>
  </div>
</template>

<style scoped>
.product-sku {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.product-sku.layout-horizontal {
  flex-direction: row;
  flex-wrap: wrap;
}

.product-sku.layout-horizontal .sku-group {
  flex: 1;
  min-width: 200px;
}

.sku-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sku-label {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
}

.sku-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sku-value-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  color: #374151;
}

.sku-value-btn:hover {
  border-color: #9ca3af;
}

.sku-value-btn.selected {
  border-color: var(--color-primary, #3b82f6);
  background: #eff6ff;
  color: var(--color-primary, #3b82f6);
}

.sku-value-img {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  object-fit: cover;
}

.sku-value-name {
  white-space: nowrap;
}

.sku-empty {
  font-size: 14px;
  color: #9ca3af;
  text-align: center;
  padding: 16px;
}

/* 响应式 */
@media (max-width: 480px) {
  .sku-value-btn {
    padding: 6px 12px;
    font-size: 13px;
  }
}
</style>
