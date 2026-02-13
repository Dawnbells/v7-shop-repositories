<script setup lang="ts">
/**
 * PageTabs - 页面 Tab 切换组件
 *
 * 提供页面/布局的 Tab 切换功能，暂不包含交互逻辑
 */

// Tab 类型
type TabType = "layout" | "page";

// Tab 项接口
interface PageTab {
  key: string;
  label: string;
  required: boolean;
  removable: boolean;
  type: TabType;
}

// 暂存静态数据，后续由 props 传入
const tabs = ref<PageTab[]>([
  { key: "layout-default", label: "默认布局", required: true, removable: false, type: "layout" },
  { key: "home", label: "首页", required: true, removable: false, type: "page" },
  { key: "product", label: "商品详情", required: true, removable: false, type: "page" },
  { key: "orderResult", label: "订单结果", required: true, removable: false, type: "page" },
]);

// 当前激活的 Tab（暂存）
const activeTabKey = ref("home");

// Tab 点击事件（暂不实现）
function handleTabClick(key: string) {
  activeTabKey.value = key;
  // TODO: 实现切换逻辑
}

// 删除 Tab（暂不实现）
function handleRemoveTab(key: string) {
  // TODO: 实现删除逻辑
  console.log("Remove tab:", key);
}
</script>

<template>
  <nav class="page-tabs">
    <!-- Tab 列表 -->
    <div v-for="tab in tabs" :key="tab.key" class="page-tab-wrapper">
      <button
        class="page-tab"
        :class="{
          active: activeTabKey === tab.key,
          'is-layout': tab.type === 'layout',
        }"
        @click="handleTabClick(tab.key)"
      >
        <!-- 布局图标 -->
        <span
          v-if="tab.type === 'layout'"
          class="i-carbon-template tab-icon"
        ></span>
        {{ tab.label }}
        <!-- 可删除标签的关闭按钮 -->
        <span
          v-if="tab.removable"
          class="tab-close"
          @click.stop="handleRemoveTab(tab.key)"
        >
          <span class="i-carbon-close"></span>
        </span>
      </button>

      <!-- 布局选择按钮 - 仅在当前激活的页面 Tab 显示（布局不显示） -->
      <button
        v-if="activeTabKey === tab.key && tab.type === 'page'"
        class="tab-layout-btn layout-select-btn"
      >
        <span class="i-carbon-template"></span>
        <span class="layout-name">默认</span>
      </button>
    </div>

    <!-- 添加页面/布局下拉按钮 -->
    <div class="add-page-dropdown">
      <button class="page-tab add-tab">
        <span class="i-carbon-add"></span>
        添加页面
        <span class="i-carbon-chevron-down dropdown-icon"></span>
      </button>
    </div>
  </nav>
</template>

<style scoped>
/* 页面 Tab 容器 */
.page-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 16px;
  height: 44px;
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
  overflow-x: auto;
}

/* Tab 按钮 */
.page-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 14px;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 6px 6px 0 0;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.page-tab:hover {
  color: #e2e8f0;
  background-color: #334155;
}

.page-tab.active {
  color: #3b82f6;
  background-color: #0f172a;
}

/* 布局 Tab 样式 */
.page-tab.is-layout {
  border-left: 2px solid #8b5cf6;
}

.page-tab.is-layout.active {
  color: #8b5cf6;
}

/* Tab 图标 */
.tab-icon {
  font-size: 14px;
  margin-right: 4px;
}

/* Tab 关闭按钮 */
.tab-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.page-tab:hover .tab-close {
  opacity: 1;
}

.tab-close:hover {
  background-color: #ef4444;
  color: white;
}

/* Tab 包装器 */
.page-tab-wrapper {
  display: flex;
  align-items: center;
  position: relative;
}

/* 布局选择按钮 */
.tab-layout-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  margin-left: 2px;
  font-size: 12px;
  color: #64748b;
  background: none;
  border: 1px solid #334155;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-layout-btn:hover {
  color: #8b5cf6;
  border-color: #8b5cf6;
  background-color: rgba(139, 92, 246, 0.1);
}

.tab-layout-btn .layout-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 添加 Tab */
.add-tab {
  color: #64748b;
}

.add-tab:hover {
  color: #3b82f6;
}

/* 下拉图标 */
.dropdown-icon {
  font-size: 12px;
  margin-left: 4px;
}

/* 添加页面下拉容器 */
.add-page-dropdown {
  position: relative;
}
</style>
