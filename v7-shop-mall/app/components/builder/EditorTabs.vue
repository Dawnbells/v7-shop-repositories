<script setup lang="ts">
/**
 * EditorTabs - 页面 Tab 切换栏
 */

export interface TabItem {
  key: string
  label: string
  type: 'layout' | 'page'
  removable?: boolean
}

defineProps<{
  tabs: TabItem[]
  activeKey: string
  hasCheckout?: boolean
}>()

const emit = defineEmits<{
  'switch': [key: string]
  'remove': [key: string]
  'add': [type: 'checkout' | 'custom' | 'layout']
  'settings': [key: string]
}>()

const showAddMenu = ref(false)
const addBtnRef = ref<HTMLElement | null>(null)

function toggleAddMenu() {
  showAddMenu.value = !showAddMenu.value
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.add-dropdown')) {
    showAddMenu.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <nav class="editor-tabs">
    <div class="tabs-scroll">
      <div class="tabs-list">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{
            active: activeKey === tab.key,
            'is-layout': tab.type === 'layout'
          }"
          @click="emit('switch', tab.key)"
        >
          <span v-if="tab.type === 'layout'" class="i-carbon-template tab-icon"></span>
          <span class="tab-label">{{ tab.label }}</span>
          <button
            v-if="tab.type === 'page'"
            class="tab-settings"
            title="页面设置"
            @click.stop="emit('settings', tab.key)"
          >
            <span class="i-carbon-settings"></span>
          </button>
          <button
            v-if="tab.removable"
            class="tab-close"
            @click.stop="emit('remove', tab.key)"
          >
            <span class="i-carbon-close"></span>
          </button>
          <span v-if="activeKey === tab.key" class="tab-indicator"></span>
        </button>
      </div>
    </div>

    <!-- 添加页面按钮 -->
    <div class="add-dropdown">
      <button
        ref="addBtnRef"
        class="add-btn"
        @click="toggleAddMenu"
      >
        <span class="i-carbon-add"></span>
        <span class="add-text">添加</span>
        <span 
          class="i-carbon-chevron-down dropdown-arrow"
          :class="{ open: showAddMenu }"
        ></span>
      </button>

      <Transition name="dropdown">
        <div v-if="showAddMenu" class="dropdown-menu">
          <button 
            v-if="!hasCheckout"
            class="dropdown-item" 
            @click="emit('add', 'checkout'); showAddMenu = false"
          >
            <span class="i-carbon-shopping-cart"></span>
            收银台
          </button>
          <button class="dropdown-item" @click="emit('add', 'custom'); showAddMenu = false">
            <span class="i-carbon-document-add"></span>
            自定义页面
          </button>
          <div class="dropdown-divider"></div>
          <button class="dropdown-item" @click="emit('add', 'layout'); showAddMenu = false">
            <span class="i-carbon-template"></span>
            添加布局
          </button>
        </div>
      </Transition>
    </div>

    <!-- 左右渐变遮罩 -->
    <div class="fade-left"></div>
    <div class="fade-right"></div>
  </nav>
</template>

<style scoped>
.editor-tabs {
  position: relative;
  display: flex;
  align-items: center;
  height: 44px;
  padding: 0 16px;
  background: #1e293b;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
}

.tabs-scroll {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.tabs-scroll::-webkit-scrollbar {
  display: none;
}

.tabs-list {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-right: 16px;
}

.tab-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: transparent;
  border: none;
  border-radius: 8px 8px 0 0;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.tab-item:hover {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.5);
}

.tab-item.active {
  color: #3b82f6;
  background: #0f172a;
}

.tab-item.is-layout {
  border-left: 2px solid #8b5cf6;
  padding-left: 14px;
}

.tab-item.is-layout.active {
  color: #8b5cf6;
}

.tab-icon {
  font-size: 14px;
  opacity: 0.8;
}

.tab-label {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tab-settings {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-left: 4px;
  font-size: 11px;
  color: inherit;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  opacity: 0.5;
  transition: all 0.15s ease;
}

.tab-settings:hover {
  opacity: 1;
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.tab-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-left: 4px;
  font-size: 10px;
  color: inherit;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
}

.tab-item:hover .tab-close {
  opacity: 0.6;
}

.tab-close:hover {
  opacity: 1;
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60%;
  height: 2px;
  background: linear-gradient(90deg, transparent, #3b82f6, transparent);
  border-radius: 2px 2px 0 0;
}

.tab-item.is-layout.active .tab-indicator {
  background: linear-gradient(90deg, transparent, #8b5cf6, transparent);
}

/* 添加按钮 */
.add-dropdown {
  position: relative;
  flex-shrink: 0;
  margin-left: 8px;
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  background: transparent;
  border: 1px dashed rgba(71, 85, 105, 0.5);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-btn:hover {
  color: #3b82f6;
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(59, 130, 246, 0.05);
}

.add-text {
  font-size: 12px;
}

.dropdown-arrow {
  font-size: 12px;
  transition: transform 0.2s ease;
}

.dropdown-arrow.open {
  transform: rotate(180deg);
}

/* 下拉菜单 */
.dropdown-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  min-width: 160px;
  padding: 4px;
  background: #1e293b;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
  z-index: 100;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  font-size: 13px;
  color: #e2e8f0;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.dropdown-item:hover {
  background: rgba(51, 65, 85, 0.5);
}

.dropdown-item span:first-child {
  font-size: 16px;
  color: #94a3b8;
}

.dropdown-divider {
  height: 1px;
  margin: 4px 0;
  background: rgba(71, 85, 105, 0.5);
}

/* 渐变遮罩 */
.fade-left,
.fade-right {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 32px;
  pointer-events: none;
  z-index: 1;
}

.fade-left {
  left: 0;
  background: linear-gradient(90deg, #1e293b 0%, transparent 100%);
  opacity: 0;
}

.fade-right {
  right: 80px;
  background: linear-gradient(90deg, transparent 0%, #1e293b 100%);
}

/* 下拉动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
