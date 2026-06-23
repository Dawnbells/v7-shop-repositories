<script setup lang="ts">
// Set document-level attributes and metadata from site config.
// The builder route stays ltr so the editor UI is not mirrored.
const { siteConfig } = usePageContext()
const { buildImageUrl } = useImageUrl()
const route = useRoute()

const globalConfig = computed(() => siteConfig.value?.globalConfig || {})

const htmlDir = computed(() => {
  if (route.path.startsWith('/builder')) return 'ltr'
  return globalConfig.value.rtlMode ? 'rtl' : 'ltr'
})

const faviconHref = computed(() => buildImageUrl(globalConfig.value.favicon))
const titleSuffix = computed(() => globalConfig.value.browserTabTitle || '')
const metaDescription = computed(
  () => globalConfig.value.seoDescription || globalConfig.value.description || '',
)
const metaKeywords = computed(() => globalConfig.value.seoKeywords || '')

function formatTitle(title?: string) {
  const suffix = titleSuffix.value
  if (!title) return suffix || ''
  if (!suffix || title === suffix || title.endsWith(` - ${suffix}`)) {
    return title
  }
  return `${title} - ${suffix}`
}

useHead(() => ({
  htmlAttrs: {
    dir: htmlDir.value,
  },
  titleTemplate: formatTitle,
  link: faviconHref.value
    ? [
        {
          key: 'site-favicon',
          rel: 'icon',
          href: faviconHref.value,
        },
        {
          key: 'site-shortcut-icon',
          rel: 'shortcut icon',
          href: faviconHref.value,
        },
      ]
    : [],
  meta: [
    ...(metaDescription.value
      ? [
          {
            key: 'description',
            name: 'description',
            content: metaDescription.value,
          },
          {
            key: 'og-description',
            property: 'og:description',
            content: metaDescription.value,
          },
        ]
      : []),
    ...(metaKeywords.value
      ? [
          {
            key: 'keywords',
            name: 'keywords',
            content: metaKeywords.value,
          },
        ]
      : []),
  ],
}))
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>
