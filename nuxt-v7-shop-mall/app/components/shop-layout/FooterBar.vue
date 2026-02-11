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
  icon?: string; // 图标类名（用于Iconify图标）
  svg?: string; // SVG代码（用于自定义SVG图标）
  imageUrl?: string; // 图片URL（用于base64图片等）
  url?: string; // 链接地址
}

interface LogisticsIcon {
  name: string;
  svg?: string; // SVG代码（用于自定义SVG图标）
  imageUrl?: string; // 图片URL（用于base64图片等）
  url?: string; // 链接地址
  class?: string; // 自定义CSS类名
  external?: boolean; // 是否外部链接
  logoClass?: string; // Logo图片的CSS类名
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

// 默认的物流图标（来自 spa-classic 模板）
const DEFAULT_LOGISTICS_ICONS: LogisticsIcon[] = [
  {
    name: "DPD",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" class="logistics-logo" data-name="DPD Black" viewBox="0 0 1942.48 850.39"><path d="M1260.93 625.51c-36.67 9.71-84.44 14.51-125.97 14.51-106.6 0-177.19-56.73-177.19-160.56 0-98.27 65.75-161.92 161.97-161.92 21.45 0 44.27 2.73 58.13 9.68V184.58h83.06v440.94Zm-83.06-224.94c-13.15-6.23-30.45-9.69-51.25-9.69-50.49 0-84.42 31.16-84.42 85.83 0 58.84 36.67 92.08 95.51 92.08 10.39 0 26.3-.72 40.15-3.46V400.58Zm764.6 224.94c-36.71 9.71-84.46 14.51-125.99 14.51-106.58 0-177.21-56.73-177.21-160.56 0-98.27 65.78-161.92 162-161.92 21.45 0 44.29 2.73 58.14 9.68V184.58h83.06v440.94Zm-83.06-224.94c-13.17-6.23-30.48-9.69-51.23-9.69-50.52 0-84.43 31.16-84.43 85.83 0 58.84 36.68 92.08 95.51 92.08 10.37 0 26.3-.72 40.15-3.46V400.58Zm-467.87-.68c13.83-5.55 33.18-7.61 49.8-7.61 51.23 0 86.53 29.76 86.53 83.03 0 62.85-39.1 91.27-91.38 92v72.66c1.38 0 2.77.05 4.18.05 107.27 0 171.65-60.19 171.65-167.47 0-97.59-68.51-155.02-169.57-155.02-51.2 0-101.76 11.77-134.97 25.6v414.61h83.77V399.89Z" class="cls-1"/><path d="M507 379.53c-3.44 2-8.82 1.85-12.18-.23l-19.75-11.74c-1.61-.99-3.08-2.59-4.2-4.51-.06-.11-.13-.22-.2-.33-1.26-2.06-1.98-4.23-2.05-6.22l-.5-23.02c-.15-3.88 2.41-8.61 5.86-10.62l237.37-138.29L378.26 3.03C374.59.99 369.75 0 364.91 0c-4.85 0-9.69 1-13.37 3.03L18.45 184.57 391.61 401.8c3.45 1.89 6.09 6.38 6.09 10.43v316.9c0 3.98-2.85 8.55-6.33 10.41l-20.08 11.15c-1.67.89-3.79 1.36-6.01 1.36h-.38c-2.41.05-4.65-.42-6.41-1.36l-20.15-11.15c-3.43-1.82-6.22-6.41-6.22-10.41v-282.5c-.18-2.07-1.69-4.59-3.35-5.54L0 249.72v374.85c0 8.39 5.91 18.73 13.16 22.97l338.59 199.69c3.62 2.12 8.39 3.17 13.15 3.16 4.77-.01 9.53-1.01 13.15-3.16l338.64-199.69c7.22-4.29 13.12-14.58 13.11-22.97V249.72L506.99 379.53Z" class="cls-2"/></svg>`,
    url: "https://www.dpd.com",
  },
  {
    name: "DHL",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="143.5" height="20" viewBox="0 0 143.5 20"><g fill="#d40511"><path d="M0 18.5h17.4l-1 1.4H0zm143.5 1.4h-21.3l1.1-1.4h20.3v1.4zM0 15.9h19.4l-1.1 1.4H0zm0-2.6h21.4l-1.1 1.4H0zm143.5 4h-19.3l1.1-1.4h18.3v1.4zm-16.3-4h16.3v1.4h-17.4zM18.8 19.9 28 7.6h11.4c1.3 0 1.3.5.6 1.3-.6.8-1.7 2.3-2.3 3.1-.3.5-.9 1.2 1 1.2H54C52.8 15 48.6 20 41.2 20c-6-.1-22.4-.1-22.4-.1m52.7-6.6-5 6.7H53.4l5-6.7zm19.1 0-5 6.7H72.4l5-6.7zm4.3 0s-1 1.3-1.4 1.9c-1.7 2.2-.2 4.8 5.2 4.8h21.2l5-6.7z"/><path d="m25.3 0-4.6 6.1h25c1.3 0 1.3.5.6 1.3-.6.8-1.7 2.3-2.3 3.1-.3.4-.9 1.2 1 1.2h10.2s1.7-2.2 3-4.1c1.9-2.5.2-7.7-6.5-7.7-6 .1-26.4.1-26.4.1m66.4 11.7H59.5L68.3 0h13.2l-5 6.7h5.9l5-6.7h13.2zM118.8 0 110 11.7H96L104.8 0z"/></g></svg>`,
    url: "https://www.dhl.com",
  },
  {
    name: "GLS",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="110" height="50" fill="none" viewBox="0 0 110 50"><path fill="#FFD100" d="M95.785 39.87c2.734 0 4.95-2.21 4.95-4.935s-2.216-4.934-4.95-4.934-4.95 2.21-4.95 4.934 2.216 4.934 4.95 4.934"/><path fill="#003087" d="M25.238 23.974v6.456h5.389c-.788 1.231-2.563 1.887-3.766 1.887-4.056 0-6.085-2.304-6.085-6.954 0-5.135 2.677-7.762 7.991-7.762 2.11 0 4.868.525 7.829 1.456v-7.722c-.853-.364-2.15-.647-3.813-.93-1.704-.284-3.205-.405-4.504-.405-5.273 0-9.45 1.415-12.573 4.245S11 20.875 11 25.687q0 6.61 3.65 10.432 3.651 3.88 9.857 3.881c4.154 0 7.972-2.149 9.202-4.877l-.027 4.391h6.01v-15.54zm17.453 15.54v-29.03h9.33v21.47H62.2v7.56zm22.915-.727v-7.924c1.38.444 3.002.808 4.827 1.132q2.739.485 4.747.485c2.271 0 3.448-.566 3.448-1.577 0-.646-.365-.97-2.028-1.334l-3.246-.728c-5.557-1.253-8.152-4.043-8.152-8.773 0-3.114 1.095-5.58 3.245-7.399 2.147-1.78 5.15-2.669 8.922-2.669 2.596 0 6.572.525 9.005 1.133v7.56a48 48 0 0 0-4.583-.93c-1.825-.284-3.245-.405-4.3-.405-1.987 0-3.123.566-3.123 1.577 0 .606.527 1.012 1.623 1.294l3.772.89c5.516 1.293 8.072 4.123 8.072 8.935 0 3.073-1.137 5.498-3.367 7.277q-3.346 2.668-9.249 2.668c-3.732 0-7.504-.525-9.613-1.212"/></svg>`,
    url: "https://gls-group.com",
  },
  {
    name: "Express POST",
    imageUrl: `data:image/webp;base64,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`,
    url: "#",
  },
  {
    name: "NACEX",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" xml:space="preserve" width="1%" height="1%" class="logistics-logo nacex-logo" preserveAspectRatio="none" viewBox="900 238.431 2325.287 566.93"><g fill="#FFF"><path d="M1006.968 294.352h120.603l175.971 283.667h1.23l44.931-283.667h120.604l-73.485 463.958h-120.604l-175.874-284.281h-1.23l-45.027 284.281H933.482zm599.525 383.349-44.765 80.608h-127.987l251.93-463.958h131.679l101.266 463.958h-128.603l-17.384-80.608zm122.309-239.977h-1.23l-73.231 147.679h100.297zm589.374 12.305c-19.35-33.228-56.52-51.072-97.747-51.072-73.838 0-132.647 56.61-144.05 128.604-11.599 73.224 30.477 126.143 105.546 126.143 39.382 0 81.783-19.075 111.787-49.227L2270.81 749.08c-41.946 12.307-72.328 21.537-109.863 21.537-64.609 0-122.243-24.613-163.317-68.302-43.763-46.149-57.593-106.452-46.678-175.369 10.038-63.379 43.685-124.297 95.2-169.831 52.94-46.765 123.264-75.07 188.488-75.07 38.766 0 73.09 8.615 106.44 23.382zm203.533-53.533-12.282 77.533h135.987l-16.179 102.145h-135.987l-12.669 79.991h143.37l-16.178 102.145h-263.975l73.486-463.957h263.974l-16.18 102.144zm222.921-102.144h148.293l51.756 119.989 89.77-119.989h148.293l-182.503 215.981 132.398 247.977H2987.42l-73.246-151.371-130.426 151.371h-145.831l220.798-247.977z"/></g></svg>`,
    url: "https://www.nacex.es",
  },
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

      <!-- 底部版权和物流图标区域 -->
      <div class="footer-bottom">
        <div class="footer-bottom-content">
          <p v-if="copyright" class="copyright">{{ copyright }}</p>
          <!-- 物流/支付图标区域 -->
          <div v-if="showPaymentIcons" class="payment-methods">
            <span class="payment-label">物流</span>
            <div class="payment-icons">
              <a
                v-for="(icon, index) in DEFAULT_LOGISTICS_ICONS"
                :key="index"
                :href="icon.url"
                :class="['logistics-icon', icon.class]"
                :title="icon.name"
                :target="icon.external ? '_blank' : undefined"
                :rel="icon.external ? 'noopener noreferrer' : undefined"
                @click="
                  icon.external ? (e: Event) => e.preventDefault() : undefined
                "
              >
                <!-- SVG图标 -->
                <span
                  v-if="icon.svg"
                  v-html="icon.svg"
                  class="logistics-svg"
                ></span>
                <!-- 图片图标 -->
                <img
                  v-else-if="icon.imageUrl"
                  :src="icon.imageUrl"
                  :alt="icon.name"
                  :class="['logistics-logo', icon.logoClass]"
                />
              </a>
            </div>
          </div>
        </div>
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

/* PC端：group-links 保持纵向排列 */
.group-links {
  flex-direction: column;
  align-items: center;
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
  display: none; /* 已合并到 footer-bottom 中 */
}

.payment-icons {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  align-items: center;
  align-content: center;
}

/* 物流图标样式 - 与 default-footer.vue 一致 */
.logistics-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  height: 22px;
  transition: opacity 0.2s ease, transform 0.2s ease;
  opacity: 0.9;
}

.logistics-icon:hover {
  opacity: 1;
  transform: translateY(-2px);
}

.logistics-icon svg {
  display: block;
  height: 20px;
  width: auto;
  max-width: 64px;
}

.logistics-logo {
  display: block;
  height: 14px;
  width: auto;
  max-width: 64px;
  object-fit: contain;
}

.nacex-logo {
  filter: brightness(0) saturate(100%) invert(45%) sepia(100%) saturate(2000%)
    hue-rotate(0deg) brightness(1.1) contrast(1.2);
  height: 14px !important;
}

.express-post-wrapper {
  background: #ffcc00;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 20px;
}

.express-post-logo {
  display: block;
  height: 20px;
  width: auto;
  max-width: 64px;
  object-fit: contain;
  mix-blend-mode: multiply;
  filter: contrast(1.1) brightness(0.95);
}

/* 支付方式容器 - 与 default-footer.vue 一致 */
.payment-methods {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: center;
}

.payment-label {
  font-size: 0.875rem;
  color: #4b5563;
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
    justify-content: start;
    align-items: start;
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

  .footer-bottom-content {
    text-align: center;
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

  .footer-payment {
    margin-top: 0;
  }
}
</style>
<style>
.cls-1 {
  fill: #414042;
}
.cls-1,
.cls-2 {
  stroke-width: 0px;
}
.cls-2 {
  fill: #dc0032;
}
.logistics-logo {
  display: block;
  height: 14px;
  width: auto;
  max-width: 64px;
  object-fit: contain;
}

.nacex-logo {
  filter: brightness(0) saturate(100%) invert(45%) sepia(100%) saturate(2000%)
    hue-rotate(0deg) brightness(1.1) contrast(1.2);
  height: 14px !important;
}

.logistics-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  height: 22px;
  transition: opacity 0.2s ease, transform 0.2s ease;
  opacity: 0.9;

  &:hover {
    opacity: 1;
    transform: translateY(-2px);
  }

  svg {
    display: block;
    height: 20px;
    width: auto;
    max-width: 64px;
  }
}
</style>
