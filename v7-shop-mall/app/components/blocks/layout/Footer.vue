<script setup lang="ts">
import { BRAND_SVG_ICONS } from "~/utils/svg-icons";

/**
 * Footer Block - 页脚组件
 * 支持品牌信息、协议链接分组、联系方式、社交媒体、版权声明
 * 响应式设计：移动端折叠式链接分组
 *
 * 数据来源：
 * - 协议组：useProtocol() -> protocolGroups (由 04-protocol.ts 中间件预加载)
 * - 站点配置：usePageTheme() -> globalConfig (由 03-landing.ts 中间件预加载)
 */

interface Props {
  showContact?: boolean;
  showSocial?: boolean;
  showProtocol?: boolean;
  showCopyright?: boolean;
  showPaymentIcons?: boolean;
  showBackToTop?: boolean;
  backToTopMode?: "auto" | "always" | "never";
}

const props = withDefaults(defineProps<Props>(), {
  showContact: true,
  showSocial: true,
  showProtocol: true,
  showCopyright: true,
  showPaymentIcons: true,
  showBackToTop: true,
  backToTopMode: "auto",
});

// 主题相关数据
const { globalConfig } = usePageTheme();

// 协议相关数据
const { protocolGroups, hasProtocolGroups, replacePlaceholders } =
  useProtocol();

// 品牌信息
const siteName = computed(() => globalConfig.value?.siteName || "");
const slogan = computed(
  () => globalConfig.value?.slogan || globalConfig.value?.description || "",
);

// 联系方式
const contactEmail = computed(() => globalConfig.value?.contactEmail);
const contactPhone = computed(() => globalConfig.value?.contactPhone);
const whatsapp = computed(() => globalConfig.value?.whatsapp);
const address = computed(() => globalConfig.value?.address);
const businessHours = computed(() => globalConfig.value?.businessHours);

// 社交媒体
const facebook = computed(() => globalConfig.value?.facebook);
const twitter = computed(() => globalConfig.value?.twitter);
const instagram = computed(() => globalConfig.value?.instagram);
const youtube = computed(() => globalConfig.value?.youtube);
const tiktok = computed(() => globalConfig.value?.tiktok);
const linkedin = computed(() => globalConfig.value?.linkedin);

// 版权信息
const copyright = computed(() => globalConfig.value?.copyright || "");
const icp = computed(() => globalConfig.value?.icp);

// 是否有联系方式
const hasContact = computed(
  () =>
    contactEmail.value ||
    contactPhone.value ||
    whatsapp.value ||
    address.value ||
    businessHours.value,
);

// 是否有社交媒体
const hasSocial = computed(
  () =>
    facebook.value ||
    twitter.value ||
    instagram.value ||
    youtube.value ||
    tiktok.value ||
    linkedin.value,
);

// 社交媒体链接列表
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

// 联系方式列表
interface ContactInfo {
  icon: string;
  value: string;
  type: "email" | "phone" | "whatsapp" | "address" | "text";
  svg?: string;
}

const contactInfoList = computed<ContactInfo[]>(() => {
  const list: ContactInfo[] = [];
  if (contactEmail.value) {
    list.push({
      icon: "i-carbon-email",
      value: contactEmail.value,
      type: "email",
    });
  }
  if (contactPhone.value) {
    list.push({
      icon: "i-carbon-phone",
      value: contactPhone.value,
      type: "phone",
    });
  }
  if (whatsapp.value) {
    list.push({
      icon: "",
      value: whatsapp.value,
      type: "whatsapp",
      svg: BRAND_SVG_ICONS.whatsapp,
    });
  }
  if (address.value) {
    list.push({
      icon: "i-carbon-location",
      value: address.value,
      type: "address",
    });
  }
  if (businessHours.value) {
    list.push({
      icon: "i-carbon-time",
      value: businessHours.value,
      type: "text",
    });
  }
  return list;
});

// 处理联系方式点击
function handleContactClick(contact: ContactInfo) {
  if (import.meta.server) return;
  switch (contact.type) {
    case "email":
      window.location.href = `mailto:${contact.value}`;
      break;
    case "phone":
      window.location.href = `tel:${contact.value}`;
      break;
    case "whatsapp":
      const phone = contact.value.replace(/\D/g, "");
      window.open(`https://wa.me/${phone}`, "_blank");
      break;
    case "address":
      const encodedAddress = encodeURIComponent(contact.value);
      window.open(
        `https://www.google.com/maps/search/?api=1&query=${encodedAddress}`,
        "_blank",
      );
      break;
  }
}

// 处理社交媒体点击
function handleSocialClick(url: string) {
  if (import.meta.server) return;
  if (url && url !== "#") {
    window.open(url, "_blank", "noopener,noreferrer");
  }
}

// 移动端折叠状态
const isMobile = ref(false);
const expandedGroups = ref<Set<number | string>>(new Set(["contact"]));

function checkMobile() {
  if (import.meta.server) return;
  const wasMobile = isMobile.value;
  isMobile.value = window.innerWidth <= 768;
  if (!wasMobile && isMobile.value) {
    expandedGroups.value = new Set();
  }
}

function isGroupExpanded(index: number | string): boolean {
  return expandedGroups.value.has(index);
}

function toggleGroup(index: number | string) {
  if (!isMobile.value && index === "contact") return;
  if (expandedGroups.value.has(index)) {
    expandedGroups.value.delete(index);
  } else {
    expandedGroups.value.add(index);
  }
  expandedGroups.value = new Set(expandedGroups.value);
}

// 返回顶部
const isBackToTopVisible = ref(false);

const shouldShowBackToTop = computed(() => {
  if (!props.showBackToTop) return false;
  if (props.backToTopMode === "never") return false;
  if (props.backToTopMode === "always") return true;
  return isBackToTopVisible.value;
});

function scrollToTop() {
  if (import.meta.server) return;
  window.scrollTo({ top: 0, behavior: "smooth" });
}

// 默认物流图标
const logisticsIcons = DEFAULT_LOGISTICS_ICONS;

// 生命周期
onMounted(() => {
  checkMobile();
  if (isMobile.value) {
    expandedGroups.value = new Set();
  }
  window.addEventListener("resize", checkMobile, { passive: true });

  if (props.backToTopMode === "auto") {
    const handleScroll = () => {
      isBackToTopVisible.value = window.scrollY > 300;
    };
    window.addEventListener("scroll", handleScroll, { passive: true });
    handleScroll();

    onUnmounted(() => {
      window.removeEventListener("scroll", handleScroll);
    });
  }

  onUnmounted(() => {
    window.removeEventListener("resize", checkMobile);
  });
});
</script>

<template>
  <footer class="block-footer">
    <div class="footer-content">
      <!-- 主要内容区 -->
      <div class="footer-main">
        <!-- 品牌信息 -->
        <div class="footer-brand">
          <div v-if="siteName" class="brand-logo">{{ siteName }}</div>
          <p v-if="slogan" class="brand-description">{{ slogan }}</p>
          <!-- 社交媒体链接 -->
          <div v-if="showSocial && hasSocial" class="social-links">
            <button
              v-for="social in socialLinks"
              :key="social.name"
              class="social-btn"
              :title="social.name"
              @click="handleSocialClick(social.url)"
            >
              <span v-if="social.svg" v-html="social.svg" class="social-svg"></span>
              <span v-else :class="social.icon"></span>
            </button>
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
              <span v-if="contact.svg" v-html="contact.svg" class="contact-icon contact-svg"></span>
              <span v-else :class="contact.icon" class="contact-icon"></span>
              <span class="contact-value">{{ contact.value }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部版权和物流图标区域 -->
      <div class="footer-bottom">
        <div class="footer-bottom-content">
          <p v-if="showCopyright && copyright" class="copyright">
            {{ copyright }}
          </p>
          <a
            v-if="icp"
            href="https://beian.miit.gov.cn/"
            target="_blank"
            rel="noopener noreferrer"
            class="icp"
          >
            {{ icp }}
          </a>
          <!-- 物流图标区域 -->
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
        <span class="i-carbon-arrow-up"></span>
      </button>
    </Transition>
  </footer>
</template>

<style scoped>
.block-footer {
  width: 100%;
  background-color: var(--footer-bg, #1e293b);
  color: var(--footer-text, #94a3b8);
  position: relative;
  container-type: inline-size;
  container-name: footer;
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
  margin-top: 0;
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

.social-svg {
  display: flex;
  align-items: center;
  justify-content: center;
}

.social-svg :deep(svg) {
  width: 20px;
  height: 20px;
}

/* 链接分组 */
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

/* 物流图标 */
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
  height: 20px;
  width: auto;
  max-width: 64px;
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
  transition:
    opacity 0.3s,
    transform 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* 响应式 - Container Queries */
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
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
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
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
    padding: 0;
    justify-content: start;
    align-items: start;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding-bottom: 16px;
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
    bottom: 12px;
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
}

/* 回退媒体查询 */
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
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
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
    transition:
      max-height 0.3s ease,
      padding 0.3s ease;
    padding: 0;
  }

  .link-group.is-expanded .group-links {
    max-height: 500px;
    padding-bottom: 16px;
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
