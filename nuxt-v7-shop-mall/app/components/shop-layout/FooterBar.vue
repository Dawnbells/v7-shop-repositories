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
  iconify?: string; // Iconify 图标类名
  text?: string; // 文字内容（用于文字标识）
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
    name: "NACEX",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" xml:space="preserve" width="1%" height="1%" class="logistics-logo nacex-logo" preserveAspectRatio="none" viewBox="900 238.431 2325.287 566.93"><g class="cls-3"><path d="M1006.968 294.352h120.603l175.971 283.667h1.23l44.931-283.667h120.604l-73.485 463.958h-120.604l-175.874-284.281h-1.23l-45.027 284.281H933.482zm599.525 383.349-44.765 80.608h-127.987l251.93-463.958h131.679l101.266 463.958h-128.603l-17.384-80.608zm122.309-239.977h-1.23l-73.231 147.679h100.297zm589.374 12.305c-19.35-33.228-56.52-51.072-97.747-51.072-73.838 0-132.647 56.61-144.05 128.604-11.599 73.224 30.477 126.143 105.546 126.143 39.382 0 81.783-19.075 111.787-49.227L2270.81 749.08c-41.946 12.307-72.328 21.537-109.863 21.537-64.609 0-122.243-24.613-163.317-68.302-43.763-46.149-57.593-106.452-46.678-175.369 10.038-63.379 43.685-124.297 95.2-169.831 52.94-46.765 123.264-75.07 188.488-75.07 38.766 0 73.09 8.615 106.44 23.382zm203.533-53.533-12.282 77.533h135.987l-16.179 102.145h-135.987l-12.669 79.991h143.37l-16.178 102.145h-263.975l73.486-463.957h263.974l-16.18 102.144zm222.921-102.144h148.293l51.756 119.989 89.77-119.989h148.293l-182.503 215.981 132.398 247.977H2987.42l-73.246-151.371-130.426 151.371h-145.831l220.798-247.977z"/></g></svg>`,
    url: "https://www.nacex.es",
  },
  {
    name: "Express POST",
    svg: `<svg width="314.9" height="48.263" viewBox="0 0 314.9 48.263" xmlns="http://www.w3.org/2000/svg"><g id="svgGroup" stroke-linecap="round" fill-rule="evenodd" font-size="9pt" font-weight="bold" fill="#e60012" stroke="#e60012" stroke-width="0" style="stroke:#e60012;stroke-width:0;fill:#e60012"><path d="M 19.95 32.313 L 19.95 36.263 L 0 36.263 L 0 0.563 L 19.95 0.563 L 19.95 4.513 L 4.5 4.513 L 4.5 15.663 L 19.05 15.663 L 19.05 19.563 L 4.5 19.563 L 4.5 32.313 L 19.95 32.313 Z M 262.75 35.063 L 262.75 30.763 Q 264.55 31.563 267.225 32.213 Q 269.9 32.863 272.75 32.863 A 10.394 10.394 0 0 0 278.429 31.557 A 5.527 5.527 0 0 0 278.775 31.313 Q 280.8 29.763 280.8 27.113 Q 280.8 25.363 280.05 24.163 Q 279.3 22.963 277.475 21.938 Q 275.65 20.913 272.4 19.763 A 18.798 18.798 0 0 1 265.902 16.083 A 10.923 10.923 0 0 1 265.525 15.713 Q 263.2 13.313 263.2 9.163 Q 263.2 6.313 264.65 4.288 Q 266.1 2.263 268.675 1.163 Q 271.25 0.063 274.6 0.063 Q 277.55 0.063 280 0.613 Q 282.45 1.163 284.45 2.063 L 283.05 5.913 Q 281.2 5.113 279.025 4.563 Q 276.85 4.013 274.5 4.013 Q 271.15 4.013 269.45 5.438 Q 267.75 6.863 267.75 9.213 A 5.563 5.563 0 0 0 268.5 12.213 Q 269.25 13.413 270.95 14.363 Q 272.65 15.313 275.55 16.413 Q 278.7 17.563 280.875 18.888 Q 283.05 20.213 284.175 22.063 Q 285.3 23.913 285.3 26.713 Q 285.3 31.463 281.85 34.113 A 14.431 14.431 0 0 1 273.272 36.752 A 24.855 24.855 0 0 1 272.55 36.763 Q 269.55 36.763 267 36.313 Q 264.45 35.863 262.75 35.063 Z M 23.85 36.263 L 33.55 22.563 L 24.3 9.463 L 29.3 9.463 L 36.2 19.563 L 43.05 9.463 L 48 9.463 L 38.75 22.563 L 48.5 36.263 L 43.5 36.263 L 36.2 25.563 L 28.8 36.263 L 23.85 36.263 Z M 130.6 35.063 L 130.6 31.063 Q 132.2 31.863 134.475 32.538 Q 136.75 33.213 139.1 33.213 A 9.262 9.262 0 0 0 143.652 32.33 A 3.744 3.744 0 0 0 143.95 32.138 Q 145.45 31.063 145.45 29.263 Q 145.45 28.263 144.9 27.463 Q 144.35 26.663 142.925 25.863 Q 141.5 25.063 138.85 24.063 Q 136.25 23.063 134.4 22.063 Q 132.55 21.063 131.55 19.663 Q 130.55 18.263 130.55 16.063 Q 130.55 12.663 133.325 10.813 Q 136.1 8.963 140.6 8.963 Q 143.05 8.963 145.175 9.438 Q 147.3 9.913 149.15 10.763 L 147.65 14.263 Q 145.95 13.563 144.1 13.063 Q 142.25 12.563 140.3 12.563 Q 137.6 12.563 136.175 13.438 Q 134.75 14.313 134.75 15.813 Q 134.75 16.913 135.4 17.688 Q 136.05 18.463 137.575 19.188 Q 139.1 19.913 141.65 20.913 Q 144.2 21.863 146 22.863 Q 147.8 23.863 148.75 25.288 Q 149.7 26.713 149.7 28.863 Q 149.7 32.763 146.8 34.763 A 13.294 13.294 0 0 1 139.304 36.761 A 23.554 23.554 0 0 1 139 36.763 Q 136.2 36.763 134.175 36.313 Q 132.15 35.863 130.6 35.063 Z M 154.55 35.063 L 154.55 31.063 Q 156.15 31.863 158.425 32.538 Q 160.7 33.213 163.05 33.213 A 9.262 9.262 0 0 0 167.602 32.33 A 3.744 3.744 0 0 0 167.9 32.138 Q 169.4 31.063 169.4 29.263 Q 169.4 28.263 168.85 27.463 Q 168.3 26.663 166.875 25.863 Q 165.45 25.063 162.8 24.063 Q 160.2 23.063 158.35 22.063 Q 156.5 21.063 155.5 19.663 Q 154.5 18.263 154.5 16.063 Q 154.5 12.663 157.275 10.813 Q 160.05 8.963 164.55 8.963 Q 167 8.963 169.125 9.438 Q 171.25 9.913 173.1 10.763 L 171.6 14.263 Q 169.9 13.563 168.05 13.063 Q 166.2 12.563 164.25 12.563 Q 161.55 12.563 160.125 13.438 Q 158.7 14.313 158.7 15.813 Q 158.7 16.913 159.35 17.688 Q 160 18.463 161.525 19.188 Q 163.05 19.913 165.6 20.913 Q 168.15 21.863 169.95 22.863 Q 171.75 23.863 172.7 25.288 Q 173.65 26.713 173.65 28.863 Q 173.65 32.763 170.75 34.763 A 13.294 13.294 0 0 1 163.254 36.761 A 23.554 23.554 0 0 1 162.95 36.763 Q 160.15 36.763 158.125 36.313 Q 156.1 35.863 154.55 35.063 Z M 58.05 32.863 L 57.75 32.863 Q 57.85 33.713 57.95 35.013 Q 58.05 36.313 58.05 37.263 L 58.05 48.263 L 53.65 48.263 L 53.65 9.463 L 57.25 9.463 L 57.85 13.113 L 58.05 13.113 Q 59.25 11.363 61.2 10.163 Q 63.15 8.963 66.4 8.963 Q 71.35 8.963 74.375 12.413 A 13.275 13.275 0 0 1 77.255 19.934 A 27.035 27.035 0 0 1 77.4 22.813 A 18.818 18.818 0 0 1 75.904 30.814 A 10.66 10.66 0 0 1 74.375 33.213 Q 71.35 36.763 66.35 36.763 Q 63.25 36.763 61.225 35.588 Q 59.2 34.413 58.05 32.863 Z M 303.8 4.513 L 303.8 36.263 L 299.3 36.263 L 299.3 4.513 L 288.15 4.513 L 288.15 0.563 L 314.9 0.563 L 314.9 4.513 L 303.8 4.513 Z M 125.45 21.063 L 125.45 23.713 L 107.1 23.713 A 10.269 10.269 0 0 0 109.288 30.487 A 6.989 6.989 0 0 0 109.425 30.638 Q 111.65 33.013 115.65 33.013 Q 118.2 33.013 120.175 32.538 Q 122.15 32.063 124.25 31.163 L 124.25 35.013 Q 122.2 35.913 120.2 36.338 Q 118.2 36.763 115.45 36.763 Q 111.65 36.763 108.725 35.213 Q 105.8 33.663 104.175 30.588 Q 102.55 27.513 102.55 23.063 Q 102.55 18.663 104.025 15.513 Q 105.5 12.363 108.175 10.663 Q 110.85 8.963 114.4 8.963 Q 117.85 8.963 120.325 10.463 Q 122.8 11.963 124.125 14.688 Q 125.45 17.413 125.45 21.063 Z M 257.15 18.363 Q 257.15 23.913 255.275 28.038 Q 253.4 32.163 249.75 34.463 Q 246.1 36.763 240.7 36.763 Q 235.15 36.763 231.475 34.463 Q 227.8 32.163 226 28.013 Q 224.2 23.863 224.2 18.313 Q 224.2 12.813 226 8.713 Q 227.8 4.613 231.475 2.313 Q 235.15 0.013 240.75 0.013 Q 246.1 0.013 249.75 2.288 Q 253.4 4.563 255.275 8.688 Q 257.15 12.813 257.15 18.363 Z M 195.75 0.563 L 205.2 0.563 A 21.07 21.07 0 0 1 212.466 1.637 A 9.563 9.563 0 0 1 215.4 3.313 Q 218.6 6.063 218.6 11.063 Q 218.6 14.013 217.275 16.588 Q 215.95 19.163 212.825 20.763 A 16.956 16.956 0 0 1 205.968 22.324 A 32.427 32.427 0 0 1 204.35 22.363 L 200.25 22.363 L 200.25 36.263 L 195.75 36.263 L 195.75 0.563 Z M 100.05 9.263 L 99.5 13.313 Q 98.85 13.163 98.075 13.063 Q 97.3 12.963 96.6 12.963 Q 94.55 12.963 92.75 14.088 Q 90.95 15.213 89.875 17.238 Q 88.8 19.263 88.8 21.963 L 88.8 36.263 L 84.4 36.263 L 84.4 9.463 L 88 9.463 L 88.5 14.363 L 88.7 14.363 Q 90 12.163 92.05 10.563 Q 94.1 8.963 96.9 8.963 Q 97.65 8.963 98.525 9.038 Q 99.4 9.113 100.05 9.263 Z M 228.95 18.363 A 19.959 19.959 0 0 0 230.612 26.996 A 12.31 12.31 0 0 0 231.8 28.988 A 10.235 10.235 0 0 0 240.512 32.862 A 18.66 18.66 0 0 0 240.7 32.863 A 10.781 10.781 0 0 0 248.722 30.027 A 8.837 8.837 0 0 0 249.6 28.988 A 16.3 16.3 0 0 0 252.328 20.451 A 29.38 29.38 0 0 0 252.4 18.363 A 20.23 20.23 0 0 0 250.825 9.883 A 12.132 12.132 0 0 0 249.6 7.788 A 10.072 10.072 0 0 0 241.125 3.966 A 18.662 18.662 0 0 0 240.75 3.963 A 10.777 10.777 0 0 0 232.417 7.077 A 9.179 9.179 0 0 0 231.825 7.788 Q 228.95 11.613 228.95 18.363 Z M 58.05 21.963 L 58.05 22.813 A 16.788 16.788 0 0 0 59.143 29.403 A 8.441 8.441 0 0 0 59.675 30.438 A 6.33 6.33 0 0 0 65.428 33.11 A 14.052 14.052 0 0 0 65.7 33.113 Q 68.15 33.113 69.725 31.763 Q 71.3 30.413 72.075 28.088 Q 72.85 25.763 72.85 22.763 A 13.561 13.561 0 0 0 71.157 15.543 A 8.644 8.644 0 0 0 71.075 15.413 Q 69.3 12.663 65.6 12.663 Q 61.5 12.663 59.825 14.963 A 10.935 10.935 0 0 0 58.12 20.553 A 24.13 24.13 0 0 0 58.05 21.963 Z M 204.8 4.413 L 200.25 4.413 L 200.25 18.513 L 203.85 18.513 Q 208.95 18.513 211.45 16.863 Q 213.95 15.213 213.95 11.263 Q 213.95 7.813 211.75 6.113 A 9.849 9.849 0 0 0 206.357 4.463 A 23.067 23.067 0 0 0 204.8 4.413 Z M 107.2 20.213 L 120.85 20.213 Q 120.8 16.813 119.25 14.688 Q 117.7 12.563 114.35 12.563 Q 111.2 12.563 109.375 14.588 Q 107.55 16.613 107.2 20.213 Z"/></g></svg>`,
    url: "#",
  },
];

// 展开的链接分组 (移动端折叠式设计，桌面端默认展开联系我们栏)
// 移动端断点检测
const isMobile = ref(false);

// 检测是否在移动端
function checkMobile() {
  const wasMobile = isMobile.value;
  isMobile.value = window.innerWidth <= 768;

  // 如果从桌面端变为移动端，重置展开状态为移动端默认状态（折叠）
  if (!wasMobile && isMobile.value) {
    expandedGroups.value = defaultExpandedGroups.value;
  }
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

  // 如果是移动端，使用默认的展开状态（移动端默认折叠所有分组）
  if (isMobile.value) {
    expandedGroups.value = defaultExpandedGroups.value;
  }

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
                <!-- SVG图标 -->
                <span
                  v-else-if="icon.svg"
                  :class="['logistics-logo', icon.class]"
                  v-html="icon.svg"
                ></span>
                <!-- Iconify图标 -->
                <span
                  v-else-if="icon.iconify"
                  :class="[icon.iconify, 'logistics-iconify']"
                ></span>
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

/* 物流图标样式 - 单色化处理适配深色背景 */
.logistics-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  height: 22px;
  transition: opacity 0.2s ease, transform 0.2s ease;
  /* 单色化处理：将图标统一为白色 */
  filter: brightness(0) invert(1);
  opacity: 0.7;
}

.logistics-icon:hover {
  opacity: 1;
  transform: translateY(-2px);
  /* 悬停时淡蓝色发光效果 */
  filter: drop-shadow(0 0 8px rgba(135, 206, 250, 0.6)) brightness(1.1);
}

/* Iconify 图标样式 */
.logistics-iconify {
  font-size: 20px;
  line-height: 1;
  color: currentColor;
}

.logistics-icon:hover .logistics-iconify {
  color: currentColor;
  filter: drop-shadow(0 0 8px rgba(135, 206, 250, 0.6));
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
  background: rgba(255, 255, 255, 0.15);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 20px;
}

/* Express POST 使用全局样式，跳过此定义 */

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

/* 底部区域 */
.footer-bottom {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  margin-top: 24px;
  padding-top: 24px;
}

.footer-bottom-content {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

/* 版权信息 */
.copyright {
  font-size: 0.875rem;
  color: var(--footer-text, #94a3b8);
  margin: 0;
  flex: 0 1 auto;
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

  .footer-bottom {
    margin-top: 0;
  }

  .footer-bottom-content {
    flex-direction: column-reverse;
    align-items: center;
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
.cls-3 {
  fill: #fe5000;
}
.logistics-logo {
  display: block;
  height: 14px;
  width: auto;
  max-width: 64px;
  object-fit: contain;
}

.nacex-logo {
  /* NACEX 原本是白色，单色化处理后显示为黑色 */
  filter: brightness(0) invert(1);
  height: 14px !important;
}

.nacex-logo:hover {
  /* hover 时显示品牌橙色，移除滤镜 */
  filter: none;
  fill: #fe5000 !important;
}

.logistics-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  height: 22px;
  transition: opacity 0.2s ease, transform 0.2s ease;
  /* 单色化处理：将图标统一为白色 */
  filter: brightness(0) invert(1);
  opacity: 0.7;

  &:hover {
    opacity: 1;
    transform: translateY(-2px);
    filter: none;
  }

  /* Express POST 特殊处理：保持黄底红字 */
  .express-post-logo {
    filter: none !important;
    opacity: 1 !important;
  }

  svg {
    display: block;
    height: 20px;
    width: auto;
    max-width: 64px;
  }
}

/* Express POST SVG 样式 - 黄底红字 */
.express-post-logo {
  display: block;
  height: 18px !important;
  width: auto;
  /* 保持原始颜色，不应用单色化滤镜 */
  filter: none !important;
  opacity: 1 !important;

  svg {
    display: block;
    height: 100%;
    width: auto;
  }

  &:hover {
    opacity: 0.85 !important;
    transform: translateY(-1px);
  }
}
</style>
