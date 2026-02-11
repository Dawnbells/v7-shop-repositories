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
const DEFAULT_PAYMENT_ICONS: PaymentIcon[] = [
  {
    name: "DPD",
    svg: `<svg id="DPD_Black" xmlns="http://www.w3.org/2000/svg" data-name="DPD Black" viewBox="0 0 1942.48 850.39" class="logistics-logo"><path class="cls-1" d="m1260.93,625.51c-36.67,9.71-84.44,14.51-125.97,14.51-106.6,0-177.19-56.73-177.19-160.56,0-98.27,65.75-161.92,161.97-161.92,21.45,0,44.27,2.73,58.13,9.68v-142.64h83.06v440.94Zm-83.06-224.94c-13.15-6.23-30.45-9.69-51.25-9.69-50.49,0-84.42,31.16-84.42,85.83,0,58.84,36.67,92.08,95.51,92.08,10.39,0,26.3-.72,40.15-3.46v-164.75Zm764.6,224.94c-36.71,9.71-84.46,14.51-125.99,14.51-106.58,0-177.21-56.73-177.21-160.56,0-98.27,65.78-161.92,162-161.92,21.45,0,44.29,2.73,58.14,9.68v-142.64h83.06v440.94Zm-83.06-224.94c-13.17-6.23-30.48-9.69-51.23-9.69-50.52,0-84.43,31.16-84.43,85.83,0,58.84,36.68,92.08,95.51,92.08,10.37,0,26.3-.72,40.15-3.46v-164.75Zm-467.87-.68c13.83-5.55,33.18-7.61,49.8-7.61,51.23,0,86.53,29.76,86.53,83.03,0,62.85-39.1,91.27-91.38,92v72.66c1.38,0,2.77.05,4.18.05,107.27,0,171.65-60.19,171.65-167.47,0-97.59-68.51-155.02-169.57-155.02-51.2,0-101.76,11.77-134.97,25.6v414.61h83.77v-357.85Z"></path><path class="cls-2" d="m507,379.53c-3.44,2-8.82,1.85-12.18-.23l-19.75-11.74c-1.61-.99-3.08-2.59-4.2-4.51-.06-.11-.13-.22-.2-.33-1.26-2.06-1.98-4.23-2.05-6.22l-.5-23.02c-.15-3.88,2.41-8.61,5.86-10.62l237.37-138.29L378.26,3.03C374.59.99,369.75,0,364.91,0c-4.85,0-9.69,1-13.37,3.03L18.57l373.45,184.16,217.23c3.45,1.89,6.09,6.38,6.09,10.43v316.9c0,3.98-2.85,8.55-6.33,10.41l-20.08,11.15c-1.67.89-3.79,1.36-6.01,1.36h-.38c-2.41.05-4.65-.42-6.41-1.36l-20.15-11.15c-3.43-1.82-6.22-6.41-6.22-10.41v-282.5c-.18-2.07-1.69-4.59-3.35-5.54L0,249.72v374.85c0,8.39,5.91,18.73,13.16,22.97l338.59,199.69c3.62,2.12,8.39,3.17,13.15,3.16,4.77-.01,9.53-1.01,13.15-3.16l338.64-199.69c7.22-4.29,13.12-14.58,13.11-22.97V249.72l-222.81,129.81Z"></path></svg>`,
    url: "https://www.dpd.com",
  },
  {
    name: "DHL",
    svg: `<svg enable-background="new 0 0 143.5 20" height="20" viewBox="0 0 143.5 20" width="143.5" xmlns="http://www.w3.org/2000/svg"><g fill="#d40511"><path d="m0 18.5h17.4l-1 1.4h-16.4z"></path><path d="m143.5 19.9h-21.3l1.1-1.4h20.3v1.4z"></path><path d="m0 15.9h19.4l-1.1 1.4h-18.3z"></path><path d="m0 13.3h21.4l-1.1 1.4h-20.3z"></path><path d="m143.5 17.3h-19.3l1.1-1.4h18.3v1.4z"></path><path d="m127.2 13.3h16.3v1.4h-17.4z"></path><path d="m18.8 19.9 9.2-12.3h11.4c1.3 0 1.3.5.6 1.3-.6.8-1.7 2.3-2.3 3.1-.3.5-.9 1.2 1 1.2h15.3c-1.2 1.8-5.4 6.8-12.8 6.8-6-.1-22.4-.1-22.4-.1z"></path><path d="m71.5 13.3-5 6.7h-13.1l5-6.7z"></path><path d="m90.6 13.3-5 6.7h-13.2l5-6.7z"></path><path d="m94.9 13.3s-1 1.3-1.4 1.9c-1.7 2.2-.2 4.8 5.2 4.8h21.2l5-6.7z"></path><path d="m25.3 0-4.6 6.1h25c1.3 0 1.3.5.6 1.3-.6.8-1.7 2.3-2.3 3.1-.3.4-.9 1.2 1 1.2h10.2s1.7-2.2 3-4.1c1.9-2.5.2-7.7-6.5-7.7-6 .1-26.4.1-26.4.1z"></path><path d="m91.7 11.7h-32.2l8.8-11.7h13.2l-5 6.7h5.9l5-6.7h13.2z"></path><path d="m118.8 0-8.8 11.7h-14l8.8-11.7z"></path></g></svg>`,
    url: "https://www.dhl.com",
  },
  {
    name: "GLS",
    svg: `<svg width="110" height="50" viewBox="0 0 110 50" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M95.7846 39.8693C98.5185 39.8693 100.735 37.6603 100.735 34.9354C100.735 32.2104 98.5185 30.0015 95.7846 30.0015C93.0507 30.0015 90.8345 32.2104 90.8345 34.9354C90.8345 37.6603 93.0507 39.8693 95.7846 39.8693Z" fill="#FFD100"/><path d="M25.238 23.9744V30.4299H30.6265C29.8392 31.6613 28.064 32.3174 26.8606 32.3174C22.8045 32.3174 20.7765 30.0131 20.7765 25.3631C20.7765 20.2283 23.4532 17.6008 28.7671 17.6008C30.8762 17.6008 33.635 18.126 36.5955 19.0565V11.3345C35.7431 10.9708 34.4458 10.688 32.7826 10.4041C31.0789 10.1212 29.5778 10 28.2795 10C23.0061 10 18.8284 11.4153 15.7058 14.2449C12.5821 17.0755 11 20.8758 11 25.6874C11 30.0939 12.2173 33.571 14.6507 36.1188C17.0842 38.7059 20.37 40 24.5072 40C28.6614 40 32.4786 37.851 33.7086 35.1225L33.682 39.5141H39.6914V23.9744H25.238Z" fill="#003087"/><path d="M42.6914 39.514V10.4849H52.0209V31.9537H62.2017V39.514H42.6914Z" fill="#003087"/><path d="M65.606 38.7867V30.8627C66.9854 31.3072 68.6081 31.6709 70.4334 31.9952C72.2588 32.3184 73.8409 32.4801 75.1798 32.4801C77.451 32.4801 78.6278 31.9144 78.6278 30.9031C78.6278 30.2566 78.2629 29.9323 76.5997 29.5686L73.3544 28.8413C67.7973 27.5876 65.2017 24.7985 65.2017 20.0677C65.2017 16.9542 66.2973 14.4884 68.447 12.669C70.5945 10.889 73.5966 10 77.3689 10C79.9645 10 83.9406 10.5253 86.374 11.1325V18.6928C85.1162 18.3695 83.6163 18.0463 81.7909 17.7624C79.9656 17.4795 78.5456 17.3583 77.4916 17.3583C75.5041 17.3583 74.3679 17.924 74.3679 18.9352C74.3679 19.5413 74.8949 19.9465 75.9905 20.2293L79.7629 21.1183C85.2795 22.4124 87.8345 25.2419 87.8345 30.0535C87.8345 33.1266 86.6984 35.552 84.4676 37.331C82.2369 39.11 79.1537 39.9989 75.2192 39.9989C71.4874 39.9989 67.7151 39.4736 65.606 38.7867Z" fill="#003087"/></svg>`,
    url: "https://gls-group.com",
  },
  {
    name: "Express POST",
    svg: `<svg width="64" height="20" viewBox="0 0 64 20" fill="none" xmlns="http://www.w3.org/2000/svg"><rect width="64" height="20" fill="#FFCC00"/><text x="32" y="14" font-family="Arial, sans-serif" font-size="10" font-weight="bold" fill="#003087" text-anchor="middle">EXPRESS</text><text x="32" y="7" font-family="Arial, sans-serif" font-size="5" fill="#003087" text-anchor="middle">POST</text></svg>`,
    url: "#",
  },
  {
    name: "NACEX",
    svg: `<svg id="Capa_1" xmlns="http://www.w3.org/2000/svg" viewBox="900 238.431 2325.287 566.93" width="64" height="20"><g><path fill="#FFFFFF" d="M1006.968,294.352h120.603l175.971,283.667h1.23l44.931-283.667h120.604l-73.485,463.958h-120.604l-175.874-284.281h-1.23l-45.027,284.281H933.482L1006.968,294.352z"></path><path fill="#FFFFFF" d="M1606.493,677.701l-44.765,80.608h-127.987l251.93-463.958h131.679l101.266,463.958h-128.603l-17.384-80.608H1606.493z M1728.802,437.724h-1.23l-73.231,147.679h100.297L1728.802,437.724z"></path><path fill="#FFFFFF" d="M2318.176,450.029c-19.35-33.228-56.52-51.072-97.747-51.072c-73.838,0-132.647,56.61-144.05,128.604c-11.599,73.224,30.477,126.143,105.546,126.143c39.382,0,81.783-19.075,111.787-49.227L2270.81,749.08c-41.946,12.307-72.328,21.537-109.863,21.537c-64.609,0-122.243-24.613-163.317-68.302c-43.763-46.149-57.593-106.452-46.678-175.369c10.038-63.379,43.685-124.297,95.2-169.831c52.94-46.765,123.264-75.07,188.488-75.07c38.766,0,73.09,8.615,106.44,23.382L2318.176,450.029z"></path><path fill="#FFFFFF" d="M2521.709,396.496l-12.282,77.533h135.987l-16.179,102.145h-135.987l-12.669,79.991h143.37l-16.178,102.145h-263.975l73.486-463.957h263.974l-16.18,102.144H2521.709L2521.709,396.496z"></path><path fill="#FFFFFF" d="M2744.63,294.352h148.293l51.756,119.989l89.77-119.989h148.293l-182.503,215.981l132.398,247.977H2987.42l-73.246-151.371L2783.748,758.31h-145.831l220.798-247.977L2744.63,294.352z"></path></g></svg>`,
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
            <div class="payment-icons">
              <div
                v-for="(icon, index) in DEFAULT_PAYMENT_ICONS"
                :key="index"
                class="payment-icon"
                :title="icon.name"
              >
                <!-- SVG图标 -->
                <span
                  v-if="icon.svg"
                  v-html="icon.svg"
                  class="payment-svg"
                ></span>
                <!-- 图片图标 -->
                <img
                  v-else-if="icon.imageUrl"
                  :src="icon.imageUrl"
                  :alt="icon.name"
                  class="payment-logo"
                />
                <!-- 图标类（回退） -->
                <span v-else :class="icon.icon"></span>
              </div>
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

/* SVG图标样式 */
.payment-svg {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.payment-svg svg {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
}

.payment-logo {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
}

/* 底部版权区 */
.footer-bottom {
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

.footer-bottom-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

@media (min-width: 768px) {
  .footer-bottom-content {
    flex-direction: row;
  }
}

.copyright {
  font-size: 13px;
  color: var(--footer-text, #64748b);
  margin: 0;
}

/* 支付方式容器 */
.payment-methods {
  display: flex;
  align-items: center;
  gap: 12px;
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
