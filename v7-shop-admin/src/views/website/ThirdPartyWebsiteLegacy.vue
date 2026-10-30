<template>
  <!-- 第三方商城（凭证接入）：功能与第三方商城一致，新增 Shopify 额外支持 handle / Client ID / Client Secret 凭证模式 -->
  <third-party-website allow-credentials />
</template>

<script lang="ts" setup>
import ThirdPartyWebsite from './ThirdPartyWebsite.vue'

defineOptions({
  name: 'ThirdPartyWebsiteLegacy',
})
</script>
