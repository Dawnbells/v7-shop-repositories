<script setup lang="ts">
/**
 * Footer Block - 页脚组件
 * 展示联系方式、社交媒体、协议链接和版权信息
 * 
 * 协议组数据由 04-protocol.ts 中间件预加载到 pageContext.protocolGroups
 * 
 * 样式属性（通过 styleSchema 配置，由渲染器注入到根元素 style）：
 * - backgroundColor: 背景颜色
 * - --footer-text-color: 文字颜色
 * - --footer-link-color: 链接颜色
 * - paddingTop/Bottom/Left/Right: 内边距
 */

interface Props {
  showContact?: boolean
  showSocial?: boolean
  showProtocol?: boolean
  showCopyright?: boolean
  layout?: 'simple' | 'standard' | 'columns'
}

const props = withDefaults(defineProps<Props>(), {
  showContact: true,
  showSocial: true,
  showProtocol: true,
  showCopyright: true,
  layout: 'standard',
})

// 主题相关数据
const { globalConfig } = usePageTheme()

// 协议相关数据
const { protocolGroups, hasProtocolGroups, replacePlaceholders } = useProtocol()

const contactEmail = computed(() => globalConfig.value?.contactEmail)
const contactPhone = computed(() => globalConfig.value?.contactPhone)
const whatsapp = computed(() => globalConfig.value?.whatsapp)
const address = computed(() => globalConfig.value?.address)

const facebook = computed(() => globalConfig.value?.facebook)
const twitter = computed(() => globalConfig.value?.twitter)
const instagram = computed(() => globalConfig.value?.instagram)
const youtube = computed(() => globalConfig.value?.youtube)
const tiktok = computed(() => globalConfig.value?.tiktok)
const linkedin = computed(() => globalConfig.value?.linkedin)

const copyright = computed(() => globalConfig.value?.copyright || '')
const icp = computed(() => globalConfig.value?.icp)

const hasContact = computed(() => 
  contactEmail.value || contactPhone.value || whatsapp.value || address.value
)

const hasSocial = computed(() => 
  facebook.value || twitter.value || instagram.value || 
  youtube.value || tiktok.value || linkedin.value
)

const hasProtocol = hasProtocolGroups

const socialLinks = computed(() => {
  const links = []
  if (facebook.value) links.push({ icon: 'i-carbon-logo-facebook', url: facebook.value, label: 'Facebook' })
  if (twitter.value) links.push({ icon: 'i-carbon-logo-twitter', url: twitter.value, label: 'Twitter' })
  if (instagram.value) links.push({ icon: 'i-carbon-logo-instagram', url: instagram.value, label: 'Instagram' })
  if (youtube.value) links.push({ icon: 'i-carbon-logo-youtube', url: youtube.value, label: 'YouTube' })
  if (tiktok.value) links.push({ icon: 'i-carbon-logo-tiktok', url: tiktok.value, label: 'TikTok' })
  if (linkedin.value) links.push({ icon: 'i-carbon-logo-linkedin', url: linkedin.value, label: 'LinkedIn' })
  return links
})
</script>

<template>
  <footer class="block-footer footer-bar" :class="[`layout-${layout}`]">
    <!-- 标准/多列布局：上方内容区 -->
    <div v-if="layout !== 'simple'" class="footer-content">
      <!-- 联系方式 -->
      <div v-if="showContact && hasContact" class="footer-section footer-contact">
        <h4 class="section-title">联系我们</h4>
        <ul class="contact-list">
          <li v-if="contactEmail">
            <i class="i-carbon-email" />
            <a :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
          </li>
          <li v-if="contactPhone">
            <i class="i-carbon-phone" />
            <a :href="`tel:${contactPhone}`">{{ contactPhone }}</a>
          </li>
          <li v-if="whatsapp">
            <i class="i-carbon-logo-whatsapp" />
            <a :href="`https://wa.me/${whatsapp.replace(/\D/g, '')}`" target="_blank" rel="noopener">
              {{ whatsapp }}
            </a>
          </li>
          <li v-if="address">
            <i class="i-carbon-location" />
            <span>{{ address }}</span>
          </li>
        </ul>
      </div>

      <!-- 协议链接 -->
      <template v-if="showProtocol && hasProtocol">
        <div v-for="group in protocolGroups" :key="group.id" class="footer-section footer-protocol">
          <h4 class="section-title">{{ replacePlaceholders(group.name) }}</h4>
          <ul class="protocol-list">
            <li v-for="article in group.articles" :key="article.id">
              <NuxtLink :to="`/article/${article.id}`">
                {{ replacePlaceholders(article.title) }}
              </NuxtLink>
            </li>
          </ul>
        </div>
      </template>

      <!-- 社交媒体 -->
      <div v-if="showSocial && hasSocial" class="footer-section footer-social">
        <h4 class="section-title">关注我们</h4>
        <div class="social-links">
          <a
            v-for="link in socialLinks"
            :key="link.label"
            :href="link.url"
            :aria-label="link.label"
            target="_blank"
            rel="noopener noreferrer"
            class="social-link"
          >
            <i :class="link.icon" />
          </a>
        </div>
      </div>
    </div>

    <!-- 简单布局：协议链接横向排列 -->
    <div v-if="layout === 'simple' && showProtocol && hasProtocol" class="footer-simple-protocol">
      <template v-for="group in protocolGroups" :key="group.id">
        <NuxtLink
          v-for="article in group.articles"
          :key="article.id"
          :to="`/article/${article.id}`"
          class="simple-protocol-link"
        >
          {{ replacePlaceholders(article.title) }}
        </NuxtLink>
      </template>
    </div>

    <!-- 底部版权区 -->
    <div v-if="showCopyright" class="footer-bottom">
      <span v-if="copyright" class="copyright">{{ copyright }}</span>
      <a
        v-if="icp"
        href="https://beian.miit.gov.cn/"
        target="_blank"
        rel="noopener noreferrer"
        class="icp"
      >
        {{ icp }}
      </a>
    </div>
  </footer>
</template>

<style scoped>
.block-footer {
  width: 100%;
  box-sizing: border-box;
  background-color: var(--surface-color, #ffffff);
  color: var(--footer-text-color, var(--text-color, #1e293b));
}

.footer-content {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
  margin-bottom: 24px;
}

.layout-columns .footer-content {
  justify-content: space-between;
}

.layout-standard .footer-content {
  justify-content: flex-start;
}

.footer-section {
  min-width: 160px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--footer-text-color, var(--text-color, #1e293b));
}

.contact-list,
.protocol-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.contact-list li,
.protocol-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.contact-list li i {
  font-size: 16px;
  color: var(--footer-link-color, var(--primary-color, #3b82f6));
  flex-shrink: 0;
}

.contact-list a,
.protocol-list a {
  color: var(--footer-link-color, var(--primary-color, #3b82f6));
  text-decoration: none;
  transition: opacity 0.2s;
}

.contact-list a:hover,
.protocol-list a:hover {
  opacity: 0.8;
  text-decoration: underline;
}

.social-links {
  display: flex;
  gap: 12px;
}

.social-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--footer-social-bg, rgba(0, 0, 0, 0.05));
  color: var(--footer-link-color, var(--primary-color, #3b82f6));
  transition: background-color 0.2s, transform 0.2s;
}

.social-link:hover {
  background-color: var(--footer-social-bg-hover, rgba(0, 0, 0, 0.1));
  transform: translateY(-2px);
}

.social-link i {
  font-size: 18px;
}

.footer-simple-protocol {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
  margin-bottom: 16px;
}

.simple-protocol-link {
  color: var(--footer-link-color, var(--primary-color, #3b82f6));
  text-decoration: none;
  font-size: 13px;
  transition: opacity 0.2s;
}

.simple-protocol-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}

.footer-bottom {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color, #e2e8f0);
  font-size: 12px;
  color: var(--text-secondary-color, #64748b);
}

.layout-standard .footer-bottom,
.layout-columns .footer-bottom {
  justify-content: space-between;
}

.icp {
  color: var(--text-secondary-color, #64748b);
  text-decoration: none;
  transition: color 0.2s;
}

.icp:hover {
  color: var(--footer-link-color, var(--primary-color, #3b82f6));
}

/* 移动端响应式 */
@container (max-width: 640px) {
  .footer-content {
    flex-direction: column;
    gap: 24px;
  }

  .footer-section {
    width: 100%;
  }

  .social-links {
    justify-content: center;
  }

  .footer-bottom {
    flex-direction: column;
    gap: 8px;
    text-align: center;
  }
}
</style>
