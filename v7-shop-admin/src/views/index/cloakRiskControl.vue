<template>
  <div class="cloak-risk-control-container no-background-container">
    <!-- <vab-card> -->
    <!-- <template #header>
        <div class="card-header">
          <span>斗篷风控页面</span>
          <el-button :icon="Refresh" :loading="loading" @click="fetchRiskControlUrl">
            刷新
          </el-button>
        </div>
      </template> -->
    <div v-loading="loading" class="iframe-wrapper">
      <iframe
        v-if="riskControlUrl"
        allowfullscreen
        class="risk-control-iframe"
        frameborder="0"
        :src="riskControlUrl"
      />
      <el-empty v-else description="暂无风控页面" />
    </div>
    <!-- </vab-card> -->
  </div>
</template>

<script lang="ts" setup>
import { inject } from 'vue'
import { getTemporaryRiskControlUrl } from '/@/api/cloakRiskControl'

defineOptions({
  name: 'CloakRiskControl',
})

const $baseMessage = inject<any>('$baseMessage')

const loading = ref<boolean>(false)
const riskControlUrl = ref<string>('')

/**
 * 获取临时风控页面 URL
 */
const fetchRiskControlUrl = async () => {
  try {
    loading.value = true
    const { data } = await getTemporaryRiskControlUrl()
    if (data) {
      riskControlUrl.value = data
    } else {
      $baseMessage('获取风控页面 URL 失败', 'error', 'hey')
      riskControlUrl.value = ''
    }
  } catch (error: any) {
    console.error('获取风控页面 URL 失败:', error)
    $baseMessage(error?.msg || '获取风控页面 URL 失败', 'error', 'hey')
    riskControlUrl.value = ''
  } finally {
    loading.value = false
  }
}

// 组件挂载时自动获取 URL
onMounted(() => {
  fetchRiskControlUrl()
})
</script>

<style lang="scss" scoped>
.cloak-risk-control-container {
  height: 100%;

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .iframe-wrapper {
    width: 100%;
    height: calc(100vh - 200px);
    min-height: 600px;

    .risk-control-iframe {
      width: 100%;
      height: 100%;
      border: none;
    }
  }
}
</style>
