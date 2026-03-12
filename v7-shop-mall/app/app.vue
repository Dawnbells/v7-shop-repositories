<script setup lang="ts">
const { safePageType, trackingId } = usePageContext()
</script>

<template>
  <SafePage v-if="safePageType" :type="safePageType" :tracking-id="trackingId" />

  <NuxtLayout v-else>
    <NuxtPage />
  </NuxtLayout>
</template>
