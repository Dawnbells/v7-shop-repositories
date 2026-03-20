<script setup lang="ts">
import { BRAND_SVG_ICONS, DEFAULT_LOGISTICS_ICONS } from "~/utils/svg-icons";

interface Props {
  showContact?: boolean;
  showSocial?: boolean;
  showProtocol?: boolean;
  showCopyright?: boolean;
  showPaymentIcons?: boolean;
  showBackToTop?: boolean;
  backToTopMode?: "auto" | "always" | "never";
  theme?: "dark" | "light" | "transparent" | "gradient";
  backgroundImage?: string;
  backgroundOverlay?: boolean;
  borderTop?: boolean;
  borderStyle?: "solid" | "gradient" | "none";
  socialStyle?: "rounded" | "square" | "circle" | "outline";
  socialSize?: "small" | "medium" | "large";
  socialPosition?: "brand" | "bottom" | "separate";
  backToTopStyle?: "circle" | "square" | "pill" | "rocket";
  backToTopPosition?: "right" | "left" | "center";
  enableAnimations?: boolean;
  hoverEffect?: "lift" | "glow" | "underline" | "none";
  showNewsletter?: boolean;
  contentMaxWidth?: string;
}

const props = withDefaults(defineProps<Props>(), {
  showContact: true,
  showSocial: true,
  showProtocol: true,
  showCopyright: true,
  showPaymentIcons: true,
  showBackToTop: true,
  backToTopMode: "auto",
  theme: "dark",
  backgroundOverlay: true,
  borderTop: true,
  borderStyle: "solid",
  socialStyle: "rounded",
  socialSize: "medium",
  socialPosition: "brand",
  backToTopStyle: "circle",
  backToTopPosition: "right",
  enableAnimations: true,
  hoverEffect: "lift",
  showNewsletter: false,
  contentMaxWidth: "1400px",
});

const emit = defineEmits<{
  subscribe: [email: string];
}>();

const { globalConfig } = usePageTheme();
const { protocolGroups, hasProtocolGroups, replacePlaceholders } =
  useProtocol();

const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

const siteName = computed(() => globalConfig.value?.siteName || "");
const slogan = computed(
  () => globalConfig.value?.slogan || globalConfig.value?.description || "",
);

const contactEmail = computed(() => globalConfig.value?.contactEmail);
const contactPhone = computed(() => globalConfig.value?.contactPhone);
const whatsapp = computed(() => globalConfig.value?.whatsapp);
const address = computed(() => globalConfig.value?.address);
const businessHours = computed(() => globalConfig.value?.businessHours);

const facebook = computed(() => globalConfig.value?.facebook);
const twitter = computed(() => globalConfig.value?.twitter);
const instagram = computed(() => globalConfig.value?.instagram);
const youtube = computed(() => globalConfig.value?.youtube);
const tiktok = computed(() => globalConfig.value?.tiktok);
const linkedin = computed(() => globalConfig.value?.linkedin);

const copyright = computed(() => globalConfig.value?.copyright || "");
const icp = computed(() => globalConfig.value?.icp);

const newsletterTitle = computed(
  () => globalConfig.value?.newsletterTitle || "订阅我们的新闻",
);

const hasContact = computed(
  () =>
    contactEmail.value ||
    contactPhone.value ||
    whatsapp.value ||
    address.value ||
    businessHours.value,
);

const hasSocial = computed(
  () =>
    facebook.value ||
    twitter.value ||
    instagram.value ||
    youtube.value ||
    tiktok.value ||
    linkedin.value,
);

const socialLinks = computed(() => {
  const links: { icon: string; url: string; name: string; svg?: string }[] = [];
  if (facebook.value)
    links.push({
      icon: "i-carbon-logo-facebook",
      url: facebook.value,
      name: "Facebook",
    });
  if (twitter.value)
    links.push({
      icon: "i-carbon-logo-twitter",
      url: twitter.value,
      name: "Twitter",
    });
  if (instagram.value)
    links.push({
      icon: "i-carbon-logo-instagram",
      url: instagram.value,
      name: "Instagram",
    });
  if (youtube.value)
    links.push({
      icon: "i-carbon-logo-youtube",
      url: youtube.value,
      name: "YouTube",
    });
  if (tiktok.value)
    links.push({
      icon: "",
      url: tiktok.value,
      name: "TikTok",
      svg: BRAND_SVG_ICONS.tiktok,
    });
  if (linkedin.value)
    links.push({
      icon: "i-carbon-logo-linkedin",
      url: linkedin.value,
      name: "LinkedIn",
    });
  return links;
});

interface ContactInfo {
  icon: string;
  value: string;
  type: "email" | "phone" | "whatsapp" | "address" | "text";
  svg?: string;
}

const contactInfoList = computed<ContactInfo[]>(() => {
  const list: ContactInfo[] = [];
  if (contactEmail.value)
    list.push({
      icon: "i-carbon-email",
      value: contactEmail.value,
      type: "email",
    });
  if (contactPhone.value)
    list.push({
      icon: "i-carbon-phone",
      value: contactPhone.value,
      type: "phone",
    });
  if (whatsapp.value)
    list.push({
      icon: "",
      value: whatsapp.value,
      type: "whatsapp",
      svg: BRAND_SVG_ICONS.whatsapp,
    });
  if (address.value)
    list.push({
      icon: "i-carbon-location",
      value: address.value,
      type: "address",
    });
  if (businessHours.value)
    list.push({
      icon: "i-carbon-time",
      value: businessHours.value,
      type: "text",
    });
  return list;
});

function handleContactClick(contact: ContactInfo) {
  if (import.meta.server) return;
  if (isInEditor.value) return;
  switch (contact.type) {
    case "email":
      window.location.href = `mailto:${contact.value}`;
      break;
    case "phone":
      window.location.href = `tel:${contact.value}`;
      break;
    case "whatsapp": {
      const phone = contact.value.replace(/\D/g, "");
      window.open(`https://wa.me/${phone}`, "_blank");
      break;
    }
    case "address": {
      const encoded = encodeURIComponent(contact.value);
      window.open(
        `https://www.google.com/maps/search/?api=1&query=${encoded}`,
        "_blank",
      );
      break;
    }
  }
}

function handleSocialClick(url: string) {
  if (import.meta.server) return;
  if (isInEditor.value) return;
  if (url && url !== "#") {
    window.open(url, "_blank", "noopener,noreferrer");
  }
}

// --- 折叠状态 ---
const isMobile = ref(false);
const expandedGroups = ref<Set<number | string>>(new Set(["contact"]));

function checkMobile() {
  if (import.meta.server) return;
  const wasMobile = isMobile.value;
  isMobile.value = window.innerWidth <= 768;
  if (!wasMobile && isMobile.value) expandedGroups.value = new Set();
}

function isGroupExpanded(index: number | string): boolean {
  return expandedGroups.value.has(index);
}

function toggleGroup(index: number | string) {
  if (isInEditor.value) return;
  if (!isMobile.value && index === "contact") return;
  if (expandedGroups.value.has(index)) expandedGroups.value.delete(index);
  else expandedGroups.value.add(index);
  expandedGroups.value = new Set(expandedGroups.value);
}

// --- 返回顶部 ---
const isBackToTopVisible = ref(false);

const shouldShowBackToTop = computed(() => {
  if (!props.showBackToTop) return false;
  if (props.backToTopMode === "never") return false;
  if (props.backToTopMode === "always") return true;
  return isBackToTopVisible.value;
});

function scrollToTop() {
  if (import.meta.server) return;
  if (isInEditor.value) return;
  window.scrollTo({ top: 0, behavior: "smooth" });
}

// --- 邮件订阅 ---
const newsletterEmail = ref("");
const newsletterSubmitted = ref(false);

function handleNewsletterSubmit() {
  if (!newsletterEmail.value) return;
  emit("subscribe", newsletterEmail.value);
  newsletterSubmitted.value = true;
  setTimeout(() => {
    newsletterSubmitted.value = false;
    newsletterEmail.value = "";
  }, 3000);
}

// --- 主题 CSS 变量 ---
const themePresets = {
  dark: {
    bg: "#1e293b",
    text: "#94a3b8",
    link: "#e2e8f0",
    border: "rgba(255,255,255,0.1)",
    socialBg: "rgba(255,255,255,0.1)",
  },
  light: {
    bg: "#f8fafc",
    text: "#475569",
    link: "#1e293b",
    border: "rgba(0,0,0,0.08)",
    socialBg: "rgba(0,0,0,0.05)",
  },
  transparent: {
    bg: "transparent",
    text: "#94a3b8",
    link: "#e2e8f0",
    border: "rgba(255,255,255,0.1)",
    socialBg: "rgba(255,255,255,0.1)",
  },
  gradient: {
    bg: "linear-gradient(135deg, #0f172a 0%, #1e3a5f 50%, #0f172a 100%)",
    text: "#cbd5e1",
    link: "#f1f5f9",
    border: "rgba(255,255,255,0.15)",
    socialBg: "rgba(255,255,255,0.12)",
  },
} as const;

const currentTheme = computed(
  () => themePresets[props.theme] || themePresets.dark,
);

const footerStyle = computed(() => {
  const t = currentTheme.value;
  const style: Record<string, string> = {
    "--footer-text": t.text,
    "--footer-link": t.link,
    "--footer-border": t.border,
    "--footer-social-bg": t.socialBg,
  };
  if (props.theme === "gradient") {
    style.background = t.bg;
  } else {
    style["--footer-bg"] = t.bg;
  }
  if (props.backgroundImage) {
    style.backgroundImage = `url(${props.backgroundImage})`;
    style.backgroundSize = "cover";
    style.backgroundPosition = "center";
  }
  style["--footer-content-max-width"] = props.contentMaxWidth;
  return style;
});

const isLightTheme = computed(() => props.theme === "light");

// --- CSS classes ---
const footerClasses = computed(() => [
  "block-footer",
  `theme-${props.theme}`,
  `social-style-${props.socialStyle}`,
  `social-size-${props.socialSize}`,
  `hover-${props.hoverEffect}`,
  `backtop-${props.backToTopStyle}`,
  `backtop-pos-${props.backToTopPosition}`,
  {
    "has-bg-image": !!props.backgroundImage,
    "has-overlay": !!props.backgroundImage && props.backgroundOverlay,
    "border-top-gradient": props.borderStyle === "gradient",
    "border-top-none": props.borderStyle === "none" || !props.borderTop,
    "no-animations": !props.enableAnimations,
    "is-light": isLightTheme.value,
  },
]);

const logisticsIcons = DEFAULT_LOGISTICS_ICONS;

onMounted(() => {
  checkMobile();
  if (isMobile.value) expandedGroups.value = new Set();
  window.addEventListener("resize", checkMobile, { passive: true });

  if (props.backToTopMode === "auto") {
    const handleScroll = () => {
      isBackToTopVisible.value = window.scrollY > 300;
    };
    window.addEventListener("scroll", handleScroll, { passive: true });
    handleScroll();
    onUnmounted(() => window.removeEventListener("scroll", handleScroll));
  }

  onUnmounted(() => window.removeEventListener("resize", checkMobile));
});
</script>

<template>
  <footer :class="footerClasses" :style="footerStyle">
    <div v-if="backgroundImage && backgroundOverlay" class="bg-overlay"></div>
    <div
      v-if="borderTop && borderStyle === 'gradient'"
      class="gradient-border"
    ></div>

    <div class="footer-content">
      <div class="footer-main">
        <!-- 品牌信息 -->
        <div class="footer-brand">
          <div v-if="siteName" class="brand-logo">{{ siteName }}</div>
          <p v-if="slogan" class="brand-description">{{ slogan }}</p>

          <!-- 社交媒体 (brand position) -->
          <div
            v-if="showSocial && hasSocial && socialPosition === 'brand'"
            class="social-links"
          >
            <button
              v-for="social in socialLinks"
              :key="social.name"
              class="social-btn"
              :title="social.name"
              @click="handleSocialClick(social.url)"
            >
              <span
                v-if="social.svg"
                v-html="social.svg"
                class="social-svg"
              ></span>
              <span v-else :class="social.icon"></span>
            </button>
          </div>

          <!-- 邮件订阅 -->
          <div v-if="showNewsletter" class="newsletter">
            <p class="newsletter-title">{{ newsletterTitle }}</p>
            <form
              class="newsletter-form"
              @submit.prevent="handleNewsletterSubmit"
            >
              <input
                v-model="newsletterEmail"
                type="email"
                placeholder="your@email.com"
                class="newsletter-input"
                required
              />
              <button
                type="submit"
                class="newsletter-btn"
                :disabled="newsletterSubmitted"
              >
                <span
                  v-if="newsletterSubmitted"
                  class="i-carbon-checkmark"
                ></span>
                <span v-else class="i-carbon-send"></span>
              </button>
            </form>
            <p v-if="newsletterSubmitted" class="newsletter-success">
              感谢订阅！
            </p>
          </div>
        </div>

        <!-- 协议分组链接 -->
        <template v-if="showProtocol && hasProtocolGroups">
          <div
            v-for="(group, groupIndex) in protocolGroups"
            :key="group.id"
            class="link-group"
            :class="{ 'is-expanded': isGroupExpanded(groupIndex) }"
          >
            <div class="group-header" @click="toggleGroup(groupIndex)">
              <span class="group-title">{{
                replacePlaceholders(group.name)
              }}</span>
              <span class="i-carbon-chevron-down group-arrow"></span>
            </div>
            <div class="group-links">
              <NuxtLink
                v-for="article in group.articles"
                :key="article.id"
                :to="`/article/${article.id}`"
                class="link-item"
                external
              >
                {{ replacePlaceholders(article.title) }}
              </NuxtLink>
            </div>
          </div>
        </template>

        <!-- 联系我们栏 -->
        <div v-if="showContact && hasContact" class="footer-contact">
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
              <span
                v-if="contact.svg"
                v-html="contact.svg"
                class="contact-icon contact-svg"
              ></span>
              <span v-else :class="contact.icon" class="contact-icon"></span>
              <span class="contact-value">{{ contact.value }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 社交媒体 (separate position) -->
      <div
        v-if="showSocial && hasSocial && socialPosition === 'separate'"
        class="social-section"
      >
        <div class="social-links social-center">
          <button
            v-for="social in socialLinks"
            :key="social.name"
            class="social-btn"
            :title="social.name"
            @click="handleSocialClick(social.url)"
          >
            <span
              v-if="social.svg"
              v-html="social.svg"
              class="social-svg"
            ></span>
            <span v-else :class="social.icon"></span>
          </button>
        </div>
      </div>

      <!-- 底部 -->
      <div class="footer-bottom">
        <div class="footer-bottom-content">
          <!-- 社交媒体 (bottom position) -->
          <div
            v-if="showSocial && hasSocial && socialPosition === 'bottom'"
            class="social-links"
          >
            <button
              v-for="social in socialLinks"
              :key="social.name"
              class="social-btn social-btn-sm"
              :title="social.name"
              @click="handleSocialClick(social.url)"
            >
              <span
                v-if="social.svg"
                v-html="social.svg"
                class="social-svg"
              ></span>
              <span v-else :class="social.icon"></span>
            </button>
          </div>

          <p v-if="showCopyright && copyright" class="copyright">
            {{ copyright }}
          </p>
          <a
            v-if="icp"
            href="https://beian.miit.gov.cn/"
            target="_blank"
            rel="noopener noreferrer"
            class="icp"
            >{{ icp }}</a
          >

          <div v-if="showPaymentIcons" class="payment-methods">
            <div class="payment-icons">
              <a
                v-for="(icon, index) in logisticsIcons"
                :key="index"
                :href="icon.url"
                class="logistics-icon"
                :title="icon.name"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span v-html="icon.svg" class="logistics-svg"></span>
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
        <span v-if="backToTopStyle === 'rocket'" class="i-carbon-rocket"></span>
        <span v-else class="i-carbon-arrow-up"></span>
      </button>
    </Transition>
  </footer>
</template>

<style scoped>
/* ================================================
   BASE
   ================================================ */
.block-footer {
  width: 100%;
  background-color: var(--footer-bg, #1e293b);
  color: var(--footer-text, #94a3b8);
  position: relative;
  container-type: inline-size;
  container-name: footer;
  flex-shrink: 0;
  margin-top: auto;
  overflow: hidden;
  border-top: 1px solid var(--footer-border, rgba(255, 255, 255, 0.1));
}

.block-footer.border-top-none {
  border-top: none;
}
.block-footer.border-top-gradient {
  border-top: none;
}

.gradient-border {
  height: 3px;
  background: linear-gradient(
    90deg,
    #3b82f6,
    #8b5cf6,
    #ec4899,
    #f59e0b,
    #3b82f6
  );
  background-size: 200% 100%;
  animation: gradient-shift 4s linear infinite;
}

@keyframes gradient-shift {
  0% {
    background-position: 0% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.bg-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.85);
  z-index: 0;
}

.has-bg-image .footer-content {
  position: relative;
  z-index: 1;
}

/* ================================================
   LIGHT THEME
   ================================================ */
.is-light {
  background-color: var(--footer-bg, #f8fafc);
}
.is-light .footer-bottom {
  border-top-color: var(--footer-border, rgba(0, 0, 0, 0.08));
}
.is-light .logistics-icon {
  filter: brightness(0);
  opacity: 0.5;
}
.is-light .logistics-icon:hover {
  filter: none;
  opacity: 1;
}
.is-light .social-btn {
  background-color: var(--footer-social-bg, rgba(0, 0, 0, 0.05));
}
.is-light .social-btn:hover {
  background-color: rgba(0, 0, 0, 0.1);
}
.is-light .link-group {
  border-top-color: var(--footer-border, rgba(0, 0, 0, 0.08));
}
.is-light .footer-contact {
  border-top-color: var(--footer-border, rgba(0, 0, 0, 0.08));
}
.is-light .newsletter-input {
  background: rgba(0, 0, 0, 0.04);
}

/* ================================================
   CONTENT
   ================================================ */
.footer-content {
  min-width: 320px;
  max-width: var(--footer-content-max-width, 1400px);
  margin-left: auto;
  margin-right: auto;
  padding: 48px 24px 24px;
}

/* ================================================
   MAIN LAYOUT
   ================================================ */
.footer-main {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.footer-brand {
  flex: 0 0 auto;
  min-width: 200px;
  max-width: 320px;
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
  margin: 0 0 20px;
}

/* ================================================
   SOCIAL MEDIA
   ================================================ */
.social-links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.social-center {
  justify-content: center;
}

.social-section {
  padding: 20px 0;
  display: flex;
  justify-content: center;
  border-top: 1px solid var(--footer-border, rgba(255, 255, 255, 0.1));
  margin-top: 24px;
}

.social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--footer-text, #94a3b8);
  background-color: var(--footer-social-bg, rgba(255, 255, 255, 0.1));
  border: none;
  cursor: pointer;
  transition: all 0.25s;
}

.social-size-small .social-btn {
  width: 32px;
  height: 32px;
  font-size: 16px;
}
.social-size-medium .social-btn {
  width: 40px;
  height: 40px;
  font-size: 20px;
}
.social-size-large .social-btn {
  width: 48px;
  height: 48px;
  font-size: 24px;
}
.social-btn-sm {
  width: 32px !important;
  height: 32px !important;
  font-size: 16px !important;
}

.social-style-rounded .social-btn {
  border-radius: 8px;
}
.social-style-square .social-btn {
  border-radius: 0;
}
.social-style-circle .social-btn {
  border-radius: 50%;
}
.social-style-outline .social-btn {
  background: transparent;
  border: 1.5px solid var(--footer-text, #94a3b8);
  border-radius: 50%;
}

.social-btn:hover {
  color: var(--footer-link, #e2e8f0);
  background-color: rgba(255, 255, 255, 0.2);
}

.social-style-outline .social-btn:hover {
  border-color: var(--footer-link, #e2e8f0);
  background: rgba(255, 255, 255, 0.08);
}

.social-svg {
  display: flex;
  align-items: center;
  justify-content: center;
}
.social-svg :deep(svg) {
  width: 1em;
  height: 1em;
}

/* ================================================
   HOVER EFFECTS
   ================================================ */
.hover-lift .social-btn:hover,
.hover-lift .link-item:hover,
.hover-lift .contact-item.clickable:hover {
  transform: translateY(-2px);
}

.hover-glow .social-btn:hover {
  box-shadow: 0 0 16px rgba(99, 102, 241, 0.4);
}
.hover-glow .link-item:hover {
  text-shadow: 0 0 8px rgba(99, 102, 241, 0.3);
}

.hover-underline .link-item {
  position: relative;
}
.hover-underline .link-item::after {
  content: "";
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 0;
  height: 1px;
  background: var(--footer-link, #e2e8f0);
  transition: width 0.25s;
}
.hover-underline .link-item:hover::after {
  width: 100%;
}

.hover-none .social-btn:hover,
.hover-none .link-item:hover,
.hover-none .contact-item.clickable:hover {
  transform: none;
}

.no-animations,
.no-animations * {
  transition: none !important;
  animation: none !important;
}

/* ================================================
   LINK GROUPS
   ================================================ */
.link-group {
  min-width: 160px;
  flex: 1;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 16px 16px 16px 0;
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
  align-items: center;
  gap: 10px;
  padding: 0;
}

.link-item {
  font-size: 14px;
  color: var(--footer-text, #94a3b8);
  text-decoration: none;
  text-align: center;
  transition: all 0.2s;
}

.link-item:hover {
  color: var(--footer-link, #e2e8f0);
}

/* ================================================
   CONTACT
   ================================================ */
.footer-contact {
  order: 99;
  min-width: 200px;
  flex: 0 0 auto;
}
.footer-contact .group-header {
  cursor: default;
  justify-content: start;
}
.footer-contact .toggle-icon {
  display: none;
  font-size: 16px;
  transition: transform 0.2s;
}

.contact-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0;
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
}
.contact-item.clickable:hover .contact-icon {
  color: var(--primary-color, #60a5fa);
}

.contact-icon {
  font-size: 20px;
  color: var(--footer-link, #e2e8f0);
  flex-shrink: 0;
}

.contact-svg {
  display: flex;
  align-items: center;
  justify-content: center;
}
.contact-svg :deep(svg) {
  width: 20px;
  height: 20px;
}

.contact-value {
  color: var(--footer-text, #94a3b8);
  word-break: break-word;
  flex: 1;
}

/* ================================================
   NEWSLETTER
   ================================================ */
.newsletter {
  margin-top: 20px;
}

.newsletter-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--footer-link, #e2e8f0);
  margin: 0 0 10px;
}

.newsletter-form {
  display: flex;
  gap: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--footer-border, rgba(255, 255, 255, 0.15));
}

.newsletter-input {
  flex: 1;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--footer-link, #e2e8f0);
  border: none;
  outline: none;
  font-size: 14px;
  min-width: 0;
}

.newsletter-input::placeholder {
  color: var(--footer-text, #94a3b8);
  opacity: 0.7;
}

.newsletter-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 16px;
  background: var(--primary-color, #3b82f6);
  color: #fff;
  border: none;
  cursor: pointer;
  font-size: 18px;
  transition: background 0.2s;
}

.newsletter-btn:hover:not(:disabled) {
  background: #2563eb;
}
.newsletter-btn:disabled {
  background: #22c55e;
}
.newsletter-success {
  font-size: 13px;
  color: #22c55e;
  margin: 8px 0 0;
}

/* ================================================
   LOGISTICS / PAYMENT ICONS
   ================================================ */
.payment-methods {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: center;
}

.payment-icons {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  align-items: center;
}

.logistics-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  height: 22px;
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
  filter: brightness(0) invert(1);
  opacity: 0.7;
}

.logistics-icon:hover {
  opacity: 1;
  transform: translateY(-2px);
  filter: none;
}

.logistics-icon :deep(svg) {
  display: block;
  height: 24px;
  width: auto;
  max-width: 80px;
}

/* ================================================
   FOOTER BOTTOM
   ================================================ */
.footer-bottom {
  border-top: 1px solid var(--footer-border, rgba(255, 255, 255, 0.1));
  margin-top: 24px;
  padding-top: 24px;
}

.footer-bottom-content {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.copyright {
  font-size: 0.875rem;
  color: var(--footer-text, #94a3b8);
  margin: 0;
  flex: 0 1 auto;
}

.icp {
  color: var(--footer-text, #94a3b8);
  text-decoration: none;
  font-size: 0.875rem;
  transition: color 0.2s;
}

.icp:hover {
  color: var(--footer-link, #e2e8f0);
}

/* ================================================
   BACK TO TOP
   ================================================ */
.back-to-top {
  position: fixed;
  bottom: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background-color: var(--primary-color, #3b82f6);
  border: none;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
  transition: all 0.3s;
  z-index: 100;
}

.backtop-pos-right .back-to-top {
  right: 32px;
}
.backtop-pos-left .back-to-top {
  left: 32px;
}
.backtop-pos-center .back-to-top {
  left: 50%;
  transform: translateX(-50%);
}

.backtop-circle .back-to-top {
  width: 48px;
  height: 48px;
  font-size: 24px;
  border-radius: 50%;
}
.backtop-square .back-to-top {
  width: 48px;
  height: 48px;
  font-size: 24px;
  border-radius: 8px;
}
.backtop-pill .back-to-top {
  width: 56px;
  height: 40px;
  font-size: 22px;
  border-radius: 20px;
}
.backtop-rocket .back-to-top {
  width: 48px;
  height: 48px;
  font-size: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.4);
}

.back-to-top:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.5);
}

.backtop-pos-center .back-to-top:hover {
  transform: translateX(-50%) translateY(-4px);
}
.backtop-rocket .back-to-top:hover {
  box-shadow: 0 6px 16px rgba(239, 68, 68, 0.5);
}
.back-to-top:active {
  transform: translateY(-2px);
}

.fade-enter-active,
.fade-leave-active {
  transition:
    opacity 0.3s,
    transform 0.3s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* ================================================
   RESPONSIVE - Container Queries
   ================================================ */
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
    border-top: 1px solid var(--footer-border, rgba(255, 255, 255, 0.1));
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
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
    padding: 0 16px 0 24px;
  }

  .footer-contact .contact-list.is-visible {
    max-height: 500px;
    padding: 0 16px 16px 24px;
  }

  .link-group {
    border-top: 1px solid var(--footer-border, rgba(255, 255, 255, 0.1));
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

  .group-arrow {
    display: block;
    font-size: 16px;
    line-height: 1;
    position: absolute;
    right: 16px;
  }

  .group-links {
    max-height: 0;
    overflow: hidden;
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
    padding: 0;
    align-items: start;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding: 0 0 16px 24px;
  }

  .footer-bottom {
    margin-top: 0;
  }
  .footer-bottom-content {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .footer-bottom-content .payment-methods {
    order: -1;
  }
  .footer-bottom-content .copyright {
    order: 10;
  }
  .social-section {
    margin-top: 0;
  }
  .back-to-top {
    right: 16px;
    bottom: 64px;
    width: 44px;
    height: 44px;
    font-size: 22px;
  }
}

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
  .social-size-medium .social-btn {
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
  .copyright {
    font-size: 12px;
  }
  .back-to-top {
    right: 12px;
    bottom: 56px;
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
}

/* ================================================
   RESPONSIVE - Media Query Fallback
   ================================================ */
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
    border-top: 1px solid var(--footer-border, rgba(255, 255, 255, 0.1));
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
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
    padding: 0 16px 0 24px;
  }

  .footer-contact .contact-list.is-visible {
    max-height: 500px;
    padding: 0 16px 16px 24px;
  }

  .link-group {
    border-top: 1px solid var(--footer-border, rgba(255, 255, 255, 0.1));
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

  .group-arrow {
    display: block;
    font-size: 16px;
    line-height: 1;
    position: absolute;
    right: 16px;
  }

  .group-links {
    max-height: 0;
    overflow: hidden;
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
    padding: 0;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding: 0 0 16px 24px;
  }

  .footer-bottom {
    margin-top: 0;
  }
  .footer-bottom-content {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .footer-bottom-content .payment-methods {
    order: -1;
  }
  .footer-bottom-content .copyright {
    order: 10;
  }
  .back-to-top {
    right: 16px;
    bottom: 64px;
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
  .copyright {
    font-size: 12px;
  }
  .back-to-top {
    right: 12px;
    bottom: 56px;
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
}
</style>
