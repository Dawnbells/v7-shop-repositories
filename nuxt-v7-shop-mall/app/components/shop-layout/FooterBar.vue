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
  description: "页脚组件，支持品牌信息、绑定协议分组链接、社交媒体、版权声明",
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
      key: "copyright",
      label: "版权信息",
      type: "text",
      defaultValue: "© 2024 商城. All rights reserved.",
      placeholder: "版权声明文字",
    },
    {
      key: "showPaymentIcons",
      label: "显示支付/物流图标",
      type: "switch",
      defaultValue: true,
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
    copyright: "© 2024 商城. All rights reserved.",
    showPaymentIcons: true,
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

interface PaymentIcon {
  name: string;
  icon?: string;
  imageUrl?: string;
  svg?: string;
  url?: string;
}

interface ContactInfo {
  icon: string;
  label: string;
  value: string;
  type: "email" | "phone" | "whatsapp" | "address" | "text";
}

interface Props {
  logoText?: string;
  description?: string;
  copyright?: string;
  showPaymentIcons?: boolean;
  showBackToTop?: boolean;
  backToTopMode?: "auto" | "always" | "never";
}

const props = withDefaults(defineProps<Props>(), {
  logoText: "商城",
  description: "为您提供优质的购物体验",
  copyright: "© 2024 商城. All rights reserved.",
  showPaymentIcons: true,
  showBackToTop: true,
  backToTopMode: "auto",
});

// 固化的预览数据（用于编辑器预览）
const FOOTER_PREVIEW_DATA: LinkGroup[] = [
  {
    title: "购物指南",
    links: [
      { text: "购物流程", url: "/help/shopping-guide" },
      { text: "支付方式", url: "/help/payment" },
      { text: "配送说明", url: "/help/delivery" },
      { text: "退换货政策", url: "/help/return-policy" },
    ],
  },
  {
    title: "法律条款",
    links: [
      { text: "用户协议", url: "/protocol/user-agreement" },
      { text: "隐私政策", url: "/protocol/privacy-policy" },
      { text: "服务条款", url: "/protocol/terms-of-service" },
      { text: "知识产权", url: "/protocol/intellectual-property" },
    ],
  },
  {
    title: "客户服务",
    links: [
      { text: "联系我们", url: "/contact" },
      { text: "常见问题", url: "/faq" },
      { text: "售后服务", url: "/after-sales" },
    ],
  },
];

// 链接分组来自当前落地页绑定的协议（pageContext.protocolGroups）
// 编辑器预览时使用固化的预览数据
const pageContext = usePageContext(["protocolGroups"]);

// 检测是否在编辑器环境中
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

const linkGroups = computed<LinkGroup[]>(() => {
  // 优先使用真实数据（生产环境）
  const contextGroups = pageContext.value.protocolGroups;
  if (Array.isArray(contextGroups) && contextGroups.length > 0) {
    return contextGroups;
  }

  // 编辑器预览时，使用固化的预览数据
  if (isInEditor.value) {
    return FOOTER_PREVIEW_DATA;
  }

  // 都没有，返回空
  return [];
});

// 社交媒体平台映射表
const SOCIAL_PLATFORMS = [
  { key: "facebook", icon: "i-carbon-logo-facebook", name: "Facebook" },
  { key: "twitter", icon: "i-carbon-logo-twitter", name: "Twitter" },
  { key: "instagram", icon: "i-carbon-logo-instagram", name: "Instagram" },
  { key: "youtube", icon: "i-carbon-logo-youtube", name: "YouTube" },
  { key: "tiktok", icon: "i-simple-icons-tiktok", name: "TikTok" },
  { key: "linkedin", icon: "i-carbon-logo-linkedin", name: "LinkedIn" },
] as const;

// 注入站点配置（编辑器预览 + 生产页面均通过 provide/inject 传递）
const siteConfig = inject<Ref<Record<string, any>>>("siteConfig", ref({}));

const socialLinks = computed<SocialLink[]>(() => {
  return SOCIAL_PLATFORMS.filter((p) => siteConfig.value?.[p.key]).map((p) => ({
    icon: p.icon,
    url: siteConfig.value[p.key] as string,
    name: p.name,
  }));
});

// 联系方式信息（从 siteConfig 中读取）
const contactInfoList = computed<ContactInfo[]>(() => {
  const list: ContactInfo[] = [];

  if (siteConfig.value?.contactEmail) {
    list.push({
      icon: "i-carbon-email",
      label: "",
      value: siteConfig.value.contactEmail,
      type: "email",
    });
  }

  if (siteConfig.value?.contactPhone) {
    list.push({
      icon: "i-carbon-phone",
      label: "",
      value: siteConfig.value.contactPhone,
      type: "phone",
    });
  }

  if (siteConfig.value?.whatsapp) {
    list.push({
      icon: "i-logos-whatsapp-icon",
      label: "",
      value: siteConfig.value.whatsapp,
      type: "whatsapp",
    });
  }

  if (siteConfig.value?.address) {
    list.push({
      icon: "i-carbon-location",
      label: "",
      value: siteConfig.value.address,
      type: "address",
    });
  }

  if (siteConfig.value?.businessHours) {
    list.push({
      icon: "i-carbon-time",
      label: "",
      value: siteConfig.value.businessHours,
      type: "text",
    });
  }

  return list;
});

// 处理联系方式点击
function handleContactClick(contact: ContactInfo) {
  switch (contact.type) {
    case "email":
      window.location.href = `mailto:${contact.value}`;
      break;
    case "phone":
      window.location.href = `tel:${contact.value}`;
      break;
    case "whatsapp":
      // WhatsApp 链接格式
      const phone = contact.value.replace(/\D/g, ""); // 移除非数字字符
      window.open(`https://wa.me/${phone}`, "_blank");
      break;
    case "address":
      // Google Maps 搜索链接
      const encodedAddress = encodeURIComponent(contact.value);
      window.open(
        `https://www.google.com/maps/search/?api=1&query=${encodedAddress}`,
        "_blank"
      );
      break;
    case "text":
      // 营业时间等文本信息不可点击
      break;
  }
}

// 处理链接点击
function handleLinkClick(link: FooterLink) {
  if (link.url) {
    navigateTo(link.url);
  }
}

// 处理社交媒体点击
function handleSocialClick(social: SocialLink) {
  if (social.url && social.url !== "#") {
    window.open(social.url, "_blank", "noopener,noreferrer");
  }
}

// 返回顶部
function scrollToTop() {
  window.scrollTo({ top: 0, behavior: "smooth" });
}

// 是否显示联系我们栏（至少有一个联系方式）
const showContactSection = computed(() => contactInfoList.value.length > 0);

// 默认的物流/支付图标
const DEFAULT_PAYMENT_ICONS: PaymentIcon[] = [
  { name: "Visa", icon: "i-logos-visa" },
  { name: "Mastercard", icon: "i-logos-mastercard" },
  { name: "PayPal", icon: "i-logos-paypal" },
  { name: "American Express", icon: "i-logos-amex" },
  { name: "DPD", icon: "i-simple-icons-dpd" },
  { name: "DHL", icon: "i-simple-icons-dhl" },
  { name: "GLS", icon: "i-simple-icons-gls" },
  { name: "Express Post", icon: "i-carbon-delivery-parcel" },
  { name: "Nacex", icon: "i-carbon-delivery-truck" },
];

// 展开的链接分组 (移动端折叠式设计，桌面端默认展开联系我们栏)
// 移动端断点检测
const isMobile = ref(false);

// 检测是否在移动端
function checkMobile() {
  isMobile.value = window.innerWidth <= 768;
}

// 默认的展开状态
const defaultExpandedGroups = computed(() => {
  // 移动端默认不展开任何分组
  if (isMobile.value) {
    return new Set<number | string>();
  }
  // 桌面端默认展开联系我们栏
  return new Set(["contact"]);
});

const expandedGroups = ref<Set<number | string>>(new Set(["contact"]));

// 返回顶部按钮可见性
const isBackToTopVisible = ref(false);

// 是否显示返回顶部按钮
const shouldShowBackToTop = computed(() => {
  if (!props.showBackToTop) return false;
  if (props.backToTopMode === "never") return false;
  if (props.backToTopMode === "always") return true;
  return isBackToTopVisible.value;
});

// 判断分组是否展开
function isGroupExpanded(index: number | string): boolean {
  return expandedGroups.value.has(index);
}

// 切换分组展开状态
function toggleGroup(index: number | string) {
  // 移动端允许切换，桌面端联系我们栏不可切换
  if (!isMobile.value && index === "contact") return;

  if (expandedGroups.value.has(index)) {
    expandedGroups.value.delete(index);
  } else {
    expandedGroups.value.add(index);
  }
  // 触发响应式更新
  expandedGroups.value = new Set(expandedGroups.value);
}

// 监听窗口大小变化，更新移动端状态
onMounted(() => {
  checkMobile();
  window.addEventListener("resize", checkMobile, { passive: true });

  // 返回顶部按钮滚动监听
  if (props.backToTopMode !== "auto") return;

  const handleScroll = () => {
    isBackToTopVisible.value = window.scrollY > 300;
  };

  window.addEventListener("scroll", handleScroll, { passive: true });
  handleScroll();

  onUnmounted(() => {
    window.removeEventListener("resize", checkMobile);
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

        <!-- 协议分组链接（来自落地页绑定的协议） -->
        <template v-if="linkGroups.length > 0">
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
        </template>

        <!-- 联系我们栏（根据 siteConfig 动态渲染） -->
        <div v-if="showContactSection" class="footer-contact">
          <div class="group-header" @click="toggleGroup('contact')">
            <span class="group-title">联系我们</span>
            <span
              :class="[
                'toggle-icon',
                isMobile && expandedGroups.has('contact')
                  ? 'i-carbon-chevron-up'
                  : 'i-carbon-chevron-down',
              ]"
            ></span>
          </div>
          <div
            class="contact-list"
            :class="{ 'is-visible': expandedGroups.has('contact') }"
          >
            <div
              v-for="(contact, index) in contactInfoList"
              :key="index"
              class="contact-item"
              :class="{ clickable: contact.type !== 'text' }"
              @click="handleContactClick(contact)"
            >
              <span :class="contact.icon" class="contact-icon"></span>
              <span class="contact-value">{{ contact.value }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 物流/支付图标区域 -->
      <div v-if="showPaymentIcons" class="footer-payment">
        <div class="payment-icons">
          <div
            v-for="(icon, index) in DEFAULT_PAYMENT_ICONS"
            :key="index"
            class="payment-icon"
            :title="icon.name"
          >
            <img
              v-if="icon.imageUrl"
              :src="icon.imageUrl"
              :alt="icon.name"
              class="payment-logo"
            />
            <span v-else :class="icon.icon"></span>
          </div>
        </div>
      </div>

      <!-- 底部版权区 -->
      <div class="footer-bottom" :class="{ 'has-payment': showPaymentIcons }">
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
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

/* 品牌信息 */
.footer-brand {
  flex: 1;
  min-width: 200px;
  max-width: 300px;
  padding-bottom: 24px;
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
  flex-wrap: wrap;
  gap: 12px;
}

.social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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
  min-width: 400px;
}

.link-group {
  min-width: 160px;
  flex: 1;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 16px;
  min-height: 56px;
}

.group-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--footer-link, #e2e8f0);
  flex: 0 0 auto;
}

.group-arrow {
  display: none;
  font-size: 16px;
  transition: transform 0.2s;
  position: absolute;
  right: 16px;
  line-height: 1;
}

.link-group.is-expanded .group-arrow {
  transform: rotate(180deg);
}

.group-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 24px;
}

.link-item {
  font-size: 14px;
  color: var(--footer-text, #94a3b8);
  text-decoration: none;
  transition: all 0.2s;
  padding: 0 24px;
}

.link-item:hover {
  color: var(--footer-link, #e2e8f0);
}

/* 联系我们栏 */
.footer-contact {
  min-width: 200px;
  flex: 1;
}

/* PC端：标题不可点击 */
.footer-contact .group-header {
  cursor: default;
  justify-content: start;
}

/* PC端：隐藏折叠图标 */
.footer-contact .toggle-icon {
  display: none;
  font-size: 16px;
  transition: transform 0.2s;
}

/* PC端：group-links 横向居中显示 */
.group-links {
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: center;
}

.contact-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 24px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  transition: all 0.2s;
}

.contact-item.clickable {
  cursor: pointer;
}

.contact-item.clickable:hover {
  color: var(--footer-link, #e2e8f0);
  transform: translateX(2px);
}

.contact-item.clickable:hover .contact-icon {
  color: var(--footer-link-hover, #60a5fa);
}

.contact-icon {
  font-size: 20px;
  color: var(--footer-link, #e2e8f0);
  flex-shrink: 0;
}

.contact-value {
  color: var(--footer-text, #94a3b8);
  word-break: break-word;
  flex: 1;
}

/* 物流/支付图标区域 */
.footer-payment {
  padding: 24px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
  margin-top: 24px;
}

.payment-icons {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: center;
  align-items: center;
}

.payment-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 40px;
  font-size: 24px;
  color: var(--footer-text, #94a3b8);
  background-color: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  transition: all 0.2s;
}

.payment-icon:hover {
  background-color: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

/* 底部版权区 */
.footer-bottom {
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  text-align: center;
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

/* 当显示物流图标时，版权信息靠左对齐 */
.footer-bottom.has-payment {
  text-align: left;
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
  background-color: var(--primary-color, #3b82f6);
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
    flex-direction: column;
  }

  .footer-brand {
    max-width: none;
  }

  .footer-contact {
    min-width: 100%;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
  }

  .footer-contact .group-header {
    display: flex;
    align-items: center;
    justify-content: start;
    position: relative;
    padding: 16px;
    min-height: 56px;
    cursor: pointer;
  }

  .footer-contact .toggle-icon {
    display: block;
    font-size: 16px;
    line-height: 1;
    position: absolute;
    right: 16px;
  }

  .footer-contact .contact-list {
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease, padding 0.3s ease;
    padding: 0 24px;
  }

  /* 当联系我们栏展开时 - 使用 class 控制而非 :has() 选择器 */
  .footer-contact .contact-list.is-visible {
    max-height: 500px;
    padding: 0 24px 16px;
  }

  .link-group {
    border-top: 1px solid rgba(255, 255, 255, 0.1);
    min-width: 100%;
  }

  .group-header {
    display: flex;
    align-items: center;
    justify-content: start;
    padding: 16px;
    min-height: 56px;
    cursor: pointer;
  }

  .group-title {
    flex: 0 0 auto;
  }

  .group-arrow {
    display: block;
    font-size: 16px;
    line-height: 1;
    position: absolute;
    right: 16px;
  }

  .group-links {
    flex-direction: column;
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease, padding 0.3s ease;
    padding: 0;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding-bottom: 16px;
  }

  .payment-icon {
    width: 56px;
    height: 36px;
    font-size: 22px;
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
    flex-direction: column;
  }

  .footer-brand {
    max-width: none;
  }

  .footer-contact {
    min-width: 100%;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
  }

  .footer-contact .group-header {
    display: flex;
    align-items: center;
    justify-content: start;
    position: relative;
    padding: 16px;
    min-height: 56px;
    cursor: pointer;
  }

  .footer-contact .toggle-icon {
    display: block;
    font-size: 16px;
    line-height: 1;
    position: absolute;
    right: 16px;
  }

  .footer-contact .contact-list {
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease, padding 0.3s ease;
    padding: 0 24px;
  }

  .footer-contact .contact-list.is-visible {
    max-height: 500px;
    padding: 0 24px 16px;
  }

  .link-group {
    border-top: 1px solid rgba(255, 255, 255, 0.1);
    min-width: 100%;
  }

  .group-header {
    display: flex;
    align-items: center;
    justify-content: start;
    padding: 16px;
    min-height: 56px;
    cursor: pointer;
  }

  .group-title {
    flex: 0 0 auto;
  }

  .group-arrow {
    display: block;
    font-size: 16px;
    line-height: 1;
    position: absolute;
    right: 16px;
  }

  .group-links {
    flex-direction: column;
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease, padding 0.3s ease;
    padding: 0;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding-bottom: 16px;
  }

  .payment-icon {
    width: 56px;
    height: 36px;
    font-size: 22px;
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

  .contact-item {
    font-size: 13px;
  }

  .contact-icon {
    font-size: 16px;
  }

  .payment-icon {
    width: 52px;
    height: 34px;
    font-size: 20px;
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
