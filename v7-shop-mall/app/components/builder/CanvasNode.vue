<script setup lang="ts">
/**
 * CanvasNode - 递归渲染画布组件节点
 * 支持选中高亮、嵌套子组件
 */

import type { ComponentNode } from '~/types/component-meta'
import { useBlockRegistry } from '~/composables/useBlockRegistry'

interface Props {
  node: ComponentNode
  selectedId: string | null
  depth?: number
}

const props = withDefaults(defineProps<Props>(), {
  depth: 0,
})

const emit = defineEmits<{
  select: [nodeId: string]
}>()

const { getBlock, getBlockMeta } = useBlockRegistry()

// 获取组件
const blockComponent = computed(() => {
  return getBlock(props.node.type)
})

// 获取组件元数据
const blockMeta = computed(() => {
  return getBlockMeta(props.node.type)
})

// 是否为容器组件
const isContainer = computed(() => {
  return blockMeta.value?.isContainer ?? false
})

// 是否被选中
const isSelected = computed(() => {
  return props.selectedId === props.node.id
})

// 是否隐藏
const isHidden = computed(() => {
  return props.node.hidden ?? false
})

// 计算节点样式（使用 base 样式，后续可扩展响应式）
const nodeStyle = computed(() => {
  const style = props.node.style
  return style?.base || {}
})

// 点击选中节点
function onNodeClick(event: MouseEvent) {
  event.stopPropagation()
  emit('select', props.node.id)
}

// 显示名称
const displayName = computed(() => {
  return props.node.name || blockMeta.value?.name || props.node.type
})
</script>

<template>
  <div
    v-if="!isHidden"
    class="canvas-node"
    :class="{
      'is-selected': isSelected,
      'is-container': isContainer,
      'is-locked': node.locked,
    }"
    :data-node-id="node.id"
    :data-node-type="node.type"
    @click="onNodeClick"
  >
    <!-- 选中边框与操作栏 -->
    <div v-if="isSelected" class="node-selection-frame">
      <div class="node-label">
        <span v-if="blockMeta?.icon" :class="blockMeta.icon" class="node-icon" />
        <span class="node-name">{{ displayName }}</span>
      </div>
    </div>

    <!-- 动态渲染组件 -->
    <component
      :is="blockComponent"
      v-if="blockComponent"
      v-bind="node.props"
      :style="nodeStyle"
      class="node-content"
    >
      <!-- 容器组件渲染子节点 -->
      <template v-if="isContainer && node.children?.length">
        <BuilderCanvasNode
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :selected-id="selectedId"
          :depth="depth + 1"
          @select="emit('select', $event)"
        />
      </template>
    </component>

    <!-- 组件未找到的回退显示 -->
    <div v-else class="node-fallback">
      <span class="i-carbon-warning-alt" />
      <span>组件未找到: {{ node.type }}</span>
    </div>
  </div>
</template>

<style scoped>
.canvas-node {
  position: relative;
  cursor: pointer;
  transition: outline 0.15s ease;
}

.canvas-node:hover {
  outline: 1px dashed #94a3b8;
  outline-offset: 2px;
}

.canvas-node.is-selected {
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}

.canvas-node.is-locked {
  cursor: not-allowed;
  opacity: 0.7;
}

/* 选中框标签 */
.node-selection-frame {
  position: absolute;
  top: -24px;
  left: 0;
  z-index: 100;
  pointer-events: none;
}

.node-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #fff;
  background: #3b82f6;
  border-radius: 4px 4px 0 0;
  white-space: nowrap;
}

.node-icon {
  font-size: 12px;
}

.node-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 组件内容 */
.node-content {
  width: 100%;
}

/* 回退样式 */
.node-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  font-size: 14px;
  color: #ef4444;
  background: #fef2f2;
  border: 1px dashed #fca5a5;
  border-radius: 6px;
}

.node-fallback span[class^="i-"] {
  font-size: 18px;
}

/* 容器组件内边距 */
.canvas-node.is-container > .node-content {
  min-height: 60px;
  padding: 8px;
}
</style>
