<script setup lang="ts">
// RTL 模式：根据站点功能设置给 <html> 设置 dir
// 仅作用于真实店铺前台；/builder 编辑器路由强制 ltr，避免编辑器界面整体翻转
const { siteConfig } = usePageContext()
const route = useRoute()

const htmlDir = computed(() => {
  if (route.path.startsWith('/builder')) return 'ltr'
  return siteConfig.value?.globalConfig?.rtlMode ? 'rtl' : 'ltr'
})

useHead({
  htmlAttrs: {
    dir: htmlDir,
  },
})
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>
