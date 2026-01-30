<script lang="ts">
import type { ComponentMeta } from "~/types/component-meta";

/**
 * FooterBar 组件元数据
 * 用于编辑器中的组件注册和属性配置
 */
export const meta: ComponentMeta = {
  type: "footer-bar",
  name: "页脚信息",
  icon: "i-carbon-bookmark",
  category: "layout",
  description: "页脚组件，支持品牌信息、链接分组、社交媒体、版权声明",
  propsSchema: [
    {
      key: "logoText",
      label: "品牌名称",
      type: "text",
      defaultValue: "商城",
      placeholder: "品牌名称",
    },
    {
      key: "description",
      label: "品牌描述",
      type: "textarea",
      defaultValue: "为您提供优质的购物体验",
      placeholder: "简短的品牌描述",
    },
    {
      key: "linkGroups",
      label: "链接分组",
      type: "json",
      defaultValue: [],
      description:
        '格式: [{ "title": "关于我们", "links": [{ "text": "公司介绍", "url": "/about" }] }]',
    },
    {
      key: "socialLinks",
      label: "社交媒体",
      type: "json",
      defaultValue: [],
      description:
        '格式: [{ "icon": "i-carbon-logo-wechat", "url": "#", "name": "微信" }]',
    },
    {
      key: "copyright",
      label: "版权信息",
      type: "text",
      defaultValue: "© 2024 商城. All rights reserved.",
      placeholder: "版权声明文字",
    },
    {
      key: "showBackToTop",
      label: "返回顶部按钮",
      type: "switch",
      defaultValue: true,
    },
    {
      key: "backToTopMode",
      label: "返回顶部模式",
      type: "select",
      defaultValue: "auto",
      options: [
        { label: "自动显示", value: "auto" },
        { label: "始终显示", value: "always" },
        { label: "不显示", value: "never" },
      ],
    },
  ],
  styleSchema: [
    {
      key: "backgroundColor",
      label: "背景色",
      type: "color",
      defaultValue: "#1e293b",
    },
    {
      key: "textColor",
      label: "文字颜色",
      type: "color",
      defaultValue: "#94a3b8",
    },
    {
      key: "linkColor",
      label: "链接颜色",
      type: "color",
      defaultValue: "#e2e8f0",
    },
    {
      key: "padding",
      label: "内边距",
      type: "size",
      defaultValue: "48px",
      unit: "px",
    },
  ],
  supportEvents: ["click"],
  defaultProps: {
    logoText: "商城",
    description: "为您提供优质的购物体验",
    linkGroups: [
      {
        title: "关于我们",
        links: [
          { text: "公司介绍", url: "/about" },
          { text: "联系我们", url: "/contact" },
        ],
      },
      {
        title: "帮助中心",
        links: [
          { text: "常见问题", url: "/faq" },
          { text: "退换货政策", url: "/return-policy" },
        ],
      },
    ],
    socialLinks: [
      { icon: "i-carbon-logo-wechat", url: "#", name: "微信" },
      { icon: "i-carbon-logo-twitter", url: "#", name: "Twitter" },
    ],
    copyright: "© 2024 商城. All rights reserved.",
    showBackToTop: true,
    backToTopMode: "auto",
  },
  defaultStyle: {
    base: {
      width: "100%",
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
/**
 * FooterBar 页脚组件
 * 支持品牌信息、链接分组、社交媒体链接、版权声明
 * 响应式设计：手机端折叠式链接分组
 */

interface FooterLink {
  text: string;
  url?: string;
}

interface LinkGroup {
  title: string;
  links: FooterLink[];
}

interface SocialLink {
  icon: string;
  url?: string;
  name?: string;
}

interface Props {
  logoText?: string;
  description?: string;
  linkGroups?: LinkGroup[];
  socialLinks?: SocialLink[];
  copyright?: string;
  showBackToTop?: boolean;
  backToTopMode?: "auto" | "always" | "never";
}

const props = withDefaults(defineProps<Props>(), {
  logoText: "商城",
  description: "为您提供优质的购物体验",
  linkGroups: () => [],
  socialLinks: () => [],
  copyright: "© 2024 商城. All rights reserved.",
  showBackToTop: true,
  backToTopMode: "auto",
});

// 展开的链接分组 (移动端折叠)
const expandedGroups = ref<Set<number>>(new Set());

// 返回顶部按钮可见性
const isBackToTopVisible = ref(false);

// 是否显示返回顶部按钮
const shouldShowBackToTop = computed(() => {
  if (!props.showBackToTop) return false;
  if (props.backToTopMode === "never") return false;
  if (props.backToTopMode === "always") return true;
  return isBackToTopVisible.value;
});

function toggleGroup(index: number) {
  if (expandedGroups.value.has(index)) {
    expandedGroups.value.delete(index);
  } else {
    expandedGroups.value.add(index);
  }
  // 触发响应式更新
  expandedGroups.value = new Set(expandedGroups.value);
}

function isGroupExpanded(index: number): boolean {
  return expandedGroups.value.has(index);
}

function handleLinkClick(link: FooterLink) {
  if (link.url) {
    navigateTo(link.url);
  }
}

function handleSocialClick(social: SocialLink) {
  if (social.url && social.url !== "#") {
    window.open(social.url, "_blank", "noopener,noreferrer");
  }
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: "smooth" });
}

// 监听滚动，控制返回顶部按钮显示
onMounted(() => {
  if (props.backToTopMode !== "auto") return;

  const handleScroll = () => {
    isBackToTopVisible.value = window.scrollY > 300;
  };

  window.addEventListener("scroll", handleScroll, { passive: true });
  handleScroll();

  onUnmounted(() => {
    window.removeEventListener("scroll", handleScroll);
  });
});
</script>

<template>
  <footer class="footer-bar">
    <div class="footer-content">
      <!-- 主要内容区 -->
      <div class="footer-main">
        <!-- 品牌信息 -->
        <div class="footer-brand">
          <div class="brand-logo">{{ logoText }}</div>
          <p v-if="description" class="brand-description">{{ description }}</p>
          <!-- 社交媒体链接 -->
          <div v-if="socialLinks.length > 0" class="social-links">
            <button
              v-for="(social, index) in socialLinks"
              :key="index"
              class="social-btn"
              :title="social.name || ''"
              @click="handleSocialClick(social)"
            >
              <span :class="social.icon"></span>
            </button>
          </div>
        </div>

        <!-- 链接分组 -->
        <div v-if="linkGroups.length > 0" class="footer-links">
          <div
            v-for="(group, groupIndex) in linkGroups"
            :key="groupIndex"
            class="link-group"
            :class="{ 'is-expanded': isGroupExpanded(groupIndex) }"
          >
            <!-- 分组标题 - 桌面端不可点击，移动端可点击展开 -->
            <div class="group-header" @click="toggleGroup(groupIndex)">
              <span class="group-title">{{ group.title }}</span>
              <span class="i-carbon-chevron-down group-arrow"></span>
            </div>
            <!-- 链接列表 -->
            <div class="group-links">
              <a
                v-for="(link, linkIndex) in group.links"
                :key="linkIndex"
                class="link-item"
                :href="link.url || '#'"
                @click.prevent="handleLinkClick(link)"
              >
                {{ link.text }}
              </a>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部版权区 -->
      <div class="footer-bottom">
        <p class="copyright">{{ copyright }}</p>
      </div>
    </div>

    <!-- 返回顶部按钮 -->
    <Transition name="fade">
      <button
        v-if="shouldShowBackToTop"
        class="back-to-top"
        title="返回顶部"
        @click="scrollToTop"
      >
        <span class="i-carbon-arrow-up"></span>
      </button>
    </Transition>
  </footer>
</template>

<style scoped>
/**
 * 响应式设计说明：
 * 使用 CSS Container Queries 实现基于容器宽度的响应式布局
 * 这样在编辑器画布中预览时，组件会根据画布宽度而非视口宽度响应
 */

.footer-bar {
  width: 100%;
  background-color: var(--footer-bg, #1e293b);
  color: var(--footer-text, #94a3b8);
  position: relative;
  /* 定义容器查询上下文 */
  container-type: inline-size;
  container-name: footer;
  /* 确保在 flex 容器中贴底 */
  flex-shrink: 0;
  margin-top: auto;
}

.footer-content {
  min-width: 320px;
  max-width: 100%;
  padding: 48px 24px 24px;
}

.footer-main {
  display: flex;
  flex-wrap: wrap;
  gap: 48px;
  margin-bottom: 32px;
}

/* 品牌信息 */
.footer-brand {
  flex: 1;
  min-width: 200px;
  max-width: 300px;
}

.brand-logo {
  font-size: 24px;
  font-weight: 700;
  color: var(--footer-link, #e2e8f0);
  margin-bottom: 12px;
}

.brand-description {
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 20px;
}

/* 社交媒体链接 */
.social-links {
  display: flex;
  gap: 12px;
}

.social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 20px;
  color: var(--footer-text, #94a3b8);
  background-color: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.social-btn:hover {
  color: var(--footer-link, #e2e8f0);
  background-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

/* 链接分组 */
.footer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
  flex: 2;
}

.link-group {
  min-width: 140px;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.group-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--footer-link, #e2e8f0);
}

.group-arrow {
  display: none;
  font-size: 16px;
  transition: transform 0.2s;
}

.link-group.is-expanded .group-arrow {
  transform: rotate(180deg);
}

.group-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.link-item {
  font-size: 14px;
  color: var(--footer-text, #94a3b8);
  text-decoration: none;
  transition: all 0.2s;
}

.link-item:hover {
  color: var(--footer-link, #e2e8f0);
  padding-left: 4px;
}

/* 底部版权区 */
.footer-bottom {
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  text-align: center;
}

.copyright {
  font-size: 13px;
  color: var(--footer-text, #64748b);
}

/* 返回顶部按钮 */
.back-to-top {
  position: fixed;
  right: 24px;
  bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  font-size: 24px;
  color: #ffffff;
  background-color: var(--color-primary, #3b82f6);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
  transition: all 0.3s;
  z-index: 100;
}

.back-to-top:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.5);
}

.back-to-top:active {
  transform: translateY(-2px);
}

/* 返回顶部按钮动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* ============================================
 * 响应式样式 - 使用 Container Queries
 * 基于容器宽度而非视口宽度
 * ============================================ */

/* 平板样式 (容器宽度 <= 768px) */
@container footer (max-width: 768px) {
  .footer-content {
    padding: 36px 20px 20px;
  }

  .footer-main {
    gap: 32px;
    flex-direction: column;
  }

  .footer-brand {
    max-width: none;
  }

  .footer-links {
    gap: 0;
    flex-direction: column;
  }

  .link-group {
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    min-width: 100%;
  }

  .link-group:last-child {
    border-bottom: none;
  }

  .group-header {
    padding: 16px 0;
    margin-bottom: 0;
    cursor: pointer;
  }

  .group-arrow {
    display: block;
  }

  .group-links {
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease, padding 0.3s ease;
    padding: 0;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding-bottom: 16px;
  }

  .back-to-top {
    right: 16px;
    bottom: 16px;
    width: 44px;
    height: 44px;
    font-size: 22px;
  }
}

/* 手机样式 (容器宽度 <= 480px) */
@container footer (max-width: 480px) {
  .footer-content {
    padding: 32px 16px 16px;
  }

  .footer-main {
    gap: 24px;
  }

  .brand-logo {
    font-size: 20px;
  }

  .brand-description {
    font-size: 13px;
  }

  .social-btn {
    width: 36px;
    height: 36px;
    font-size: 18px;
  }

  .group-title {
    font-size: 15px;
  }

  .link-item {
    font-size: 13px;
  }

  .copyright {
    font-size: 12px;
  }

  .back-to-top {
    right: 12px;
    bottom: 12px;
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
}

/* ============================================
 * 回退：同时保留媒体查询用于实际页面渲染
 * 当组件不在容器查询上下文中时使用
 * ============================================ */

@media (max-width: 768px) {
  .footer-content {
    padding: 36px 20px 20px;
  }

  .footer-main {
    gap: 32px;
    flex-direction: column;
  }

  .footer-brand {
    max-width: none;
  }

  .footer-links {
    gap: 0;
    flex-direction: column;
  }

  .link-group {
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    min-width: 100%;
  }

  .link-group:last-child {
    border-bottom: none;
  }

  .group-header {
    padding: 16px 0;
    margin-bottom: 0;
    cursor: pointer;
  }

  .group-arrow {
    display: block;
  }

  .group-links {
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease, padding 0.3s ease;
    padding: 0;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding-bottom: 16px;
  }

  .back-to-top {
    right: 16px;
    bottom: 16px;
    width: 44px;
    height: 44px;
    font-size: 22px;
  }
}

@media (max-width: 480px) {
  .footer-content {
    padding: 32px 16px 16px;
  }

  .footer-main {
    gap: 24px;
  }

  .brand-logo {
    font-size: 20px;
  }

  .brand-description {
    font-size: 13px;
  }

  .social-btn {
    width: 36px;
    height: 36px;
    font-size: 18px;
  }

  .group-title {
    font-size: 15px;
  }

  .link-item {
    font-size: 13px;
  }

  .copyright {
    font-size: 12px;
  }

  .back-to-top {
    right: 12px;
    bottom: 12px;
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
}
</style>
