<template>
  <div class="default-layout">
    <slot />
  </div>
</template>

<style scoped>
.default-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* 确保 footer 在底部 */
.default-layout > :deep(.footer-bar),
.default-layout > :deep(.block-footer) {
  flex-shrink: 0;
  margin-top: auto;
}

/* header 不被压缩 */
.default-layout > :deep(.header-bar) {
  flex-shrink: 0;
}

/* 主内容区撑满剩余空间 */
.default-layout > :deep(.product-page),
.default-layout > :deep(.layout-renderer) {
  flex: 1 0 auto;
}
</style>
