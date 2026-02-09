<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * HeaderBar 组件元数据
 * 用于编辑器中的组件注册和属性配置
 * Logo 和站点名称从全局站点配置 (siteConfig) 获取
 */
export const meta: ComponentMeta = {
  type: "header-bar",
  name: "页头导航",
  icon: "i-carbon-application-web",
  category: "layout",
  description: "页头组件，Logo 关联全局配置，支持居中显示",
  propsSchema: [
    {
      key: "centerLogo",
      label: "Logo 居中",
      type: "switch",
      defaultValue: false,
    },
    {
      key: "showUser",
      label: "显示用户",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "showLocale",
      label: "显示语言切换",
      type: "switch",
      defaultValue: false,
    },
    {
      key: "logoHeight",
      label: "Logo 高度",
      type: "text",
      defaultValue: "32px",
      description: "Logo 图片高度，如 32px、2rem",
    },
    {
      key: "navItems",
      label: "导航菜单",
      type: "json",
      defaultValue: [],
      description: '格式: [{ "text": "首页", "url": "/" }]',
    },
  ],
  styleSchema: [
    {
      key: "backgroundColor",
      label: "背景色",
      type: "color",
      defaultValue: "",
    },
    {
      key: "color",
      label: "文字色",
      type: "color",
      defaultValue: "",
    },
    {
      key: "borderColor",
      label: "边框色",
      type: "color",
      defaultValue: "",
    },
    {
      key: "padding",
      label: "内边距",
      type: "size",
      defaultValue: "12px 16px",
      unit: "px",
    },
  ],
  supportEvents: ["click"],
  defaultProps: {
    centerLogo: false,
    navItems: [],
    showUser: true,
    showLocale: false,
    logoHeight: "32px",
  },
  defaultStyle: {
    base: {
      width: "100%",
      backgroundColor: { type: "global", key: "surfaceColor" },
      color: { type: "global", key: "textColor" },
      borderColor: { type: "global", key: "borderColor" },
      padding: "12px 16px",
    },
  },
  isContainer: false,
  layoutOnly: true,
};

export default {
  __meta: meta,
};
</script>

<script setup lang="ts">
import type { ResponsiveStyle } from "~/types/schema";
import type { DeviceType } from "~/types/builder";

/**
 * HeaderBar 页头组件
 * Logo 和站点名称从全局站点配置获取
 * 支持可选 Logo 居中显示
 * 响应式设计：手机端显示抽屉式导航菜单
 */

interface NavItem {
  text: string;
  url?: string;
  icon?: string;
}

interface Props {
  centerLogo?: boolean;
  navItems?: NavItem[];
  showUser?: boolean;
  showLocale?: boolean;
  logoHeight?: string;
  componentStyle?: ResponsiveStyle;
  previewDevice?: DeviceType;
}

const props = withDefaults(defineProps<Props>(), {
  centerLogo: false,
  navItems: () => [],
  showUser: true,
  showLocale: false,
  logoHeight: "32px",
});

// 合并基础样式和设备样式
const mergedStyle = computed(() => {
  if (!props.componentStyle) return {};
  const base = props.componentStyle.base || {};
  const device = props.previewDevice ? props.componentStyle[props.previewDevice] || {} : {};
  return { ...base, ...device };
});

// 注入站点配置
const siteConfig = inject<Ref<Record<string, any>>>("siteConfig", ref({}));

// 计算 Header 动态样式
const headerStyle = computed(() => {
  const style: Record<string, string> = {};
  
  if (mergedStyle.value.backgroundColor) {
    style.backgroundColor = mergedStyle.value.backgroundColor as string;
  }
  if (mergedStyle.value.borderColor) {
    style.borderBottomColor = mergedStyle.value.borderColor as string;
  }
  if (mergedStyle.value.color) {
    style.color = mergedStyle.value.color as string;
  }
  
  return style;
});

// 计算内容区动态样式
const contentStyle = computed(() => {
  const style: Record<string, string> = {};
  
  if (mergedStyle.value.padding) {
    style.padding = mergedStyle.value.padding as string;
  }
  
  return style;
});

// 从站点配置获取 Logo 和站点名称
const logo = computed(() => siteConfig.value?.logo || "");
const siteName = computed(() => siteConfig.value?.siteName || "商城");

// 从站点配置获取购物车启用状态
const enableCart = computed(() => siteConfig.value?.enableCart !== false);

// 是否同时有 Logo 和网站名称（需要特殊布局：名称靠左，Logo 居中）
const hasBothLogoAndName = computed(
  () => !!logo.value && !!siteConfig.value?.siteName
);

// 实际是否居中显示（同时有两者时强制居中 Logo）
const shouldCenterLogo = computed(
  () => hasBothLogoAndName.value || props.centerLogo
);

// 是否显示操作区
const showActions = computed(
  () => enableCart.value || props.showUser || props.showLocale
);

// 抽屉菜单状态
const isDrawerOpen = ref(false);

function handleNavClick(item: NavItem) {
  if (item.url) {
    navigateTo(item.url);
  }
  isDrawerOpen.value = false;
}

function toggleDrawer() {
  isDrawerOpen.value = !isDrawerOpen.value;
}

function closeDrawer() {
  isDrawerOpen.value = false;
}

// 监听 ESC 键关闭抽屉
onMounted(() => {
  const handleEsc = (e: KeyboardEvent) => {
    if (e.key === "Escape" && isDrawerOpen.value) {
      closeDrawer();
    }
  };
  window.addEventListener("keydown", handleEsc);
  onUnmounted(() => {
    window.removeEventListener("keydown", handleEsc);
  });
});
</script>

<template>
  <header class="header-bar" :style="headerStyle">
    <div class="header-content" :class="{ 'center-logo': shouldCenterLogo }" :style="contentStyle">
      <!-- 移动端菜单按钮 -->
      <button
        v-if="navItems.length > 0"
        class="menu-toggle"
        :class="{ 'is-open': isDrawerOpen }"
        aria-label="菜单"
        @click="toggleDrawer"
      >
        <span class="menu-icon">
          <span class="menu-line"></span>
          <span class="menu-line"></span>
          <span class="menu-line"></span>
        </span>
      </button>

      <!-- 左侧占位区 (居中模式，但非双模式) -->
      <div
        v-if="shouldCenterLogo && !hasBothLogoAndName"
        class="header-spacer"
      ></div>

      <!-- 左侧网站名称（同时有 Logo 和名称时显示） -->
      <div v-if="hasBothLogoAndName" class="header-site-name">
        <span class="site-name-text">{{ siteName }}</span>
      </div>

      <!-- Logo 区域 - 使用全局站点配置 -->
      <div class="header-logo" :class="{ 'logo-centered': shouldCenterLogo }">
        <AppImage
          v-if="logo"
          :src="logo"
          :alt="siteName"
          class="logo-image"
          :style="{ height: props.logoHeight }"
          :lazy="false"
        />
        <span v-else class="logo-text">{{ siteName }}</span>
      </div>

      <!-- 导航菜单 - 桌面端 (非居中模式才显示) -->
      <nav
        v-if="!shouldCenterLogo && navItems.length > 0"
        class="header-nav desktop-nav"
      >
        <a
          v-for="(item, index) in navItems"
          :key="index"
          class="nav-item"
          :href="item.url || '#'"
          @click.prevent="handleNavClick(item)"
        >
          <span v-if="item.icon" :class="item.icon" class="nav-icon"></span>
          <span class="nav-text">{{ item.text }}</span>
        </a>
      </nav>

      <!-- 右侧占位区 -->
      <div class="header-spacer"></div>

      <!-- 用户操作区 -->
      <div v-if="showActions" class="header-actions">
        <button v-if="showLocale" class="action-btn" title="语言切换">
          <span class="i-carbon-language"></span>
        </button>
        <button v-if="enableCart" class="action-btn" title="购物车">
          <span class="i-carbon-shopping-cart"></span>
        </button>
        <button v-if="showUser" class="action-btn" title="用户">
          <span class="i-carbon-user"></span>
        </button>
      </div>
    </div>

    <!-- 抽屉式导航菜单 - 移动端 -->
    <Teleport to="body">
      <Transition name="drawer">
        <div
          v-if="isDrawerOpen && navItems.length > 0"
          class="drawer-overlay"
          @click="closeDrawer"
        >
          <nav class="drawer-menu" @click.stop>
            <div class="drawer-header">
              <span class="drawer-title">导航菜单</span>
              <button class="drawer-close" @click="closeDrawer">
                <span class="i-carbon-close"></span>
              </button>
            </div>
            <div class="drawer-content">
              <a
                v-for="(item, index) in navItems"
                :key="index"
                class="drawer-item"
                :href="item.url || '#'"
                @click.prevent="handleNavClick(item)"
              >
                <span
                  v-if="item.icon"
                  :class="item.icon"
                  class="drawer-icon"
                ></span>
                <span class="drawer-text">{{ item.text }}</span>
                <span class="i-carbon-chevron-right drawer-arrow"></span>
              </a>
            </div>
          </nav>
        </div>
      </Transition>
    </Teleport>
  </header>
</template>

<style scoped>
/**
 * 响应式设计说明：
 * 使用 CSS Container Queries 实现基于容器宽度的响应式布局
 * 这样在编辑器画布中预览时，组件会根据画布宽度而非视口宽度响应
 */

.header-bar {
  width: 100%;
  background-color: var(--surface-color, #ffffff);
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  color: var(--text-color, #1e293b);
  /* 定义容器查询上下文 */
  container-type: inline-size;
  container-name: header;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 24px;
  min-width: 320px;
  max-width: 100%;
  padding: 12px 16px;
}

/* 文字色继承 - 当设置了自定义文字色时生效 */
.header-bar .site-name-text,
.header-bar .logo-text,
.header-bar .nav-item,
.header-bar .action-btn,
.header-bar .menu-line {
  color: inherit;
}

.header-bar .menu-line {
  background-color: currentColor;
}

/* 移动端菜单按钮 - 默认隐藏 */
.menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  background: none;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
}

.menu-icon {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 20px;
  height: 16px;
  position: relative;
}

.menu-line {
  display: block;
  width: 100%;
  height: 2px;
  background-color: var(--text-color, #1e293b);
  border-radius: 1px;
  transition: all 0.3s ease;
  position: absolute;
}

.menu-line:nth-child(1) {
  top: 0;
}

.menu-line:nth-child(2) {
  top: 50%;
  transform: translateY(-50%);
}

.menu-line:nth-child(3) {
  bottom: 0;
}

/* 菜单按钮打开状态动画 */
.menu-toggle.is-open .menu-line:nth-child(1) {
  top: 50%;
  transform: translateY(-50%) rotate(45deg);
}

.menu-toggle.is-open .menu-line:nth-child(2) {
  opacity: 0;
}

.menu-toggle.is-open .menu-line:nth-child(3) {
  bottom: 50%;
  transform: translateY(50%) rotate(-45deg);
}

/* 左侧网站名称（同时有 Logo 和名称时显示） */
.header-site-name {
  flex-shrink: 0;
}

.site-name-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color, #1e293b);
}

.header-logo {
  flex-shrink: 0;
}

/* Logo 居中时使用绝对定位 */
.header-content .header-logo.logo-centered {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.logo-image {
  height: 32px;
  width: auto;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--primary-color, #3b82f6);
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  font-size: 14px;
  color: var(--text-color, #1e293b);
  text-decoration: none;
  border-radius: 6px;
  transition: all 0.2s;
}

.nav-item:hover {
  color: var(--primary-color, #3b82f6);
  background-color: var(--background-color, #f8fafc);
}

.nav-icon {
  font-size: 16px;
}

/* 占位区 */
.header-spacer {
  flex: 1;
  min-width: 40px;
}

/* Logo 居中模式 - 容器需要相对定位 */
.header-content.center-logo {
  position: relative;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 20px;
  color: var(--text-color, #1e293b);
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  color: var(--primary-color, #3b82f6);
  background-color: var(--background-color, #f8fafc);
}

/* 抽屉菜单样式 */
.drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.drawer-menu {
  position: absolute;
  top: 0;
  left: 0;
  width: 280px;
  max-width: 80vw;
  height: 100%;
  background-color: var(--surface-color, #ffffff);
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color, #1e293b);
}

.drawer-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 20px;
  color: var(--text-color-secondary, #64748b);
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.drawer-close:hover {
  color: var(--text-color, #1e293b);
  background-color: var(--background-color, #f8fafc);
}

.drawer-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.drawer-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  font-size: 15px;
  color: var(--text-color, #1e293b);
  text-decoration: none;
  transition: all 0.2s;
}

.drawer-item:hover,
.drawer-item:active {
  background-color: var(--background-color, #f8fafc);
  color: var(--primary-color, #3b82f6);
}

.drawer-icon {
  font-size: 18px;
  color: var(--text-color-secondary, #64748b);
}

.drawer-item:hover .drawer-icon {
  color: var(--primary-color, #3b82f6);
}

.drawer-text {
  flex: 1;
}

.drawer-arrow {
  font-size: 16px;
  color: var(--text-color-secondary, #94a3b8);
}

/* 抽屉动画 */
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.3s ease;
}

.drawer-enter-active .drawer-menu,
.drawer-leave-active .drawer-menu {
  transition: transform 0.3s ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .drawer-menu,
.drawer-leave-to .drawer-menu {
  transform: translateX(-100%);
}

/* ============================================
 * 响应式样式 - 使用 Container Queries
 * 基于容器宽度而非视口宽度
 * ============================================ */

/* 平板样式 (容器宽度 <= 768px) */
@container header (max-width: 768px) {
  .header-content {
    gap: 16px;
    padding: 10px 12px;
  }

  .desktop-nav {
    display: none;
  }

  .menu-toggle {
    display: flex;
  }

  .action-btn {
    width: 36px;
    height: 36px;
    font-size: 18px;
  }
}

/* 手机样式 (容器宽度 <= 480px) */
@container header (max-width: 480px) {
  .header-content {
    gap: 12px;
    padding: 8px 12px;
  }

  .logo-text {
    font-size: 18px;
  }

  .logo-image {
    height: 28px;
  }

  .header-spacer {
    min-width: 32px;
  }

  .header-actions {
    gap: 4px;
  }

  .action-btn {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }

  .menu-toggle {
    width: 32px;
    height: 32px;
  }

  .menu-icon {
    width: 18px;
    height: 14px;
  }
}

/* ============================================
 * 回退：同时保留媒体查询用于实际页面渲染
 * 当组件不在容器查询上下文中时使用
 * ============================================ */

@media (max-width: 768px) {
  .header-content {
    gap: 16px;
    padding: 10px 12px;
  }

  .desktop-nav {
    display: none;
  }

  .menu-toggle {
    display: flex;
  }

  .action-btn {
    width: 36px;
    height: 36px;
    font-size: 18px;
  }
}

@media (max-width: 480px) {
  .header-content {
    gap: 12px;
    padding: 8px 12px;
  }

  .logo-text {
    font-size: 18px;
  }

  .logo-image {
    height: 28px;
  }

  .header-spacer {
    min-width: 32px;
  }

  .header-actions {
    gap: 4px;
  }

  .action-btn {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }

  .menu-toggle {
    width: 32px;
    height: 32px;
  }

  .menu-icon {
    width: 18px;
    height: 14px;
  }
}
</style>
