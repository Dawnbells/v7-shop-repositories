<script setup lang="ts">
/**
 * FolderTreeNode - 递归文件夹树节点
 */

// 文件夹类型
interface FolderItem {
  id: string;
  compactId: string;
  name: string;
  sensitive?: boolean;
  children?: FolderItem[];
}

// Props
const props = defineProps<{
  folder: FolderItem;
  currentFolderId: string;
  depth: number;
  expandedFolders: Set<string>;
}>();

// Emits
const emit = defineEmits<{
  (e: 'select', folder: FolderItem): void;
  (e: 'toggle-expand', folderId: string): void;
}>();

// 计算是否展开
const isExpanded = computed(() => {
  return props.expandedFolders.has(props.folder.compactId || props.folder.id);
});

// 计算是否选中
const isActive = computed(() => {
  const folderId = props.folder.compactId || props.folder.id;
  return props.currentFolderId === folderId;
});

// 计算是否有子节点
const hasChildren = computed(() => {
  return props.folder.children && props.folder.children.length > 0;
});

// 点击选择文件夹
function handleSelect() {
  emit('select', props.folder);
}

// 切换展开状态
function handleToggleExpand(event: Event) {
  event.stopPropagation();
  emit('toggle-expand', props.folder.compactId || props.folder.id);
}
</script>

<template>
  <div>
    <!-- 当前节点 -->
    <div
      class="tree-node"
      :class="{ active: isActive }"
      :style="{ paddingLeft: `${depth * 16 + 12}px` }"
      @click="handleSelect"
    >
      <!-- 展开/收起箭头 -->
      <span
        v-if="hasChildren"
        class="expand-icon"
        @click="handleToggleExpand"
      >
        <span :class="isExpanded ? 'i-carbon-chevron-down' : 'i-carbon-chevron-right'"></span>
      </span>
      <span v-else class="expand-placeholder"></span>
      
      <!-- 文件夹图标 -->
      <span
        class="i-carbon-folder folder-icon"
        :style="{ color: folder.sensitive ? '#008077' : '#94a3b8' }"
      ></span>
      
      <!-- 文件夹名称 -->
      <span class="folder-name">{{ folder.name }}</span>
    </div>

    <!-- 子节点递归渲染 -->
    <template v-if="isExpanded && hasChildren">
      <FolderTreeNode
        v-for="child in folder.children"
        :key="child.id"
        :folder="child"
        :current-folder-id="currentFolderId"
        :depth="depth + 1"
        :expanded-folders="expandedFolders"
        @select="emit('select', $event)"
        @toggle-expand="emit('toggle-expand', $event)"
      />
    </template>
  </div>
</template>

<style scoped>
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #94a3b8;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}

.tree-node:hover {
  color: #e2e8f0;
  background-color: #1e293b;
}

.tree-node.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

.expand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #64748b;
  cursor: pointer;
  transition: color 0.15s;
}

.expand-icon:hover {
  color: #94a3b8;
}

.expand-placeholder {
  width: 16px;
  flex-shrink: 0;
}

.folder-icon {
  flex-shrink: 0;
  font-size: 14px;
}

.folder-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
