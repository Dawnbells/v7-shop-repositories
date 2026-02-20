<script setup lang="ts">
/**
 * 变量类型选择器组件
 * 用于选择变量的数据类型
 */

import type { VariableType } from '~/types/data-context'

defineProps<{
  value: VariableType
}>()

const emit = defineEmits<{
  change: [type: VariableType]
}>()

const types: Array<{
  value: VariableType
  label: string
  icon: string
  description: string
}> = [
  {
    value: 'string',
    label: '文本',
    icon: 'i-carbon-text-font',
    description: '单行或多行文本内容',
  },
  {
    value: 'number',
    label: '数字',
    icon: 'i-carbon-hashtag',
    description: '整数或小数数值',
  },
  {
    value: 'boolean',
    label: '开关',
    icon: 'i-carbon-toggle-off',
    description: '是/否布尔值',
  },
  {
    value: 'color',
    label: '颜色',
    icon: 'i-carbon-color-palette',
    description: '颜色选择器',
  },
  {
    value: 'image',
    label: '图片',
    icon: 'i-carbon-image',
    description: '图片上传或URL',
  },
  {
    value: 'richtext',
    label: '富文本',
    icon: 'i-carbon-text-align-left',
    description: 'HTML富文本内容',
  },
  {
    value: 'enum',
    label: '枚举',
    icon: 'i-carbon-list-checked',
    description: '预定义选项列表',
  },
  {
    value: 'array',
    label: '数组',
    icon: 'i-carbon-list',
    description: '同类型数据列表',
  },
  {
    value: 'object',
    label: '对象',
    icon: 'i-carbon-json',
    description: '键值对结构数据',
  },
]
</script>

<template>
  <div class="type-selector">
    <button
      v-for="type in types"
      :key="type.value"
      class="type-option"
      :class="{ active: value === type.value }"
      @click="emit('change', type.value)"
    >
      <div class="type-icon">
        <span :class="type.icon"></span>
      </div>
      <div class="type-info">
        <span class="type-label">{{ type.label }}</span>
        <span class="type-desc">{{ type.description }}</span>
      </div>
      <div v-if="value === type.value" class="type-check">
        <span class="i-carbon-checkmark"></span>
      </div>
    </button>
  </div>
</template>

<style scoped>
.type-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.type-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 10px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  text-align: center;
}

.type-option:hover {
  background: rgba(15, 23, 42, 0.8);
  border-color: rgba(71, 85, 105, 0.6);
}

.type-option.active {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15) 0%, rgba(59, 130, 246, 0.05) 100%);
  border-color: rgba(59, 130, 246, 0.5);
}

.type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 20px;
  color: #64748b;
  background: rgba(51, 65, 85, 0.4);
  border-radius: 8px;
  transition: all 0.2s;
}

.type-option:hover .type-icon {
  color: #94a3b8;
}

.type-option.active .type-icon {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.2);
}

.type-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.type-label {
  font-size: 13px;
  font-weight: 500;
  color: #e2e8f0;
}

.type-desc {
  font-size: 11px;
  color: #64748b;
  line-height: 1.3;
}

.type-check {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 12px;
  color: #fff;
  background: #3b82f6;
  border-radius: 50%;
}

@media (max-width: 480px) {
  .type-selector {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
