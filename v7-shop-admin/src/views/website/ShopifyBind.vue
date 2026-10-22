<template>
  <div class="shopify-bind-container">
    <div
      v-if="state === 'loading'"
      v-loading="true"
      class="shopify-bind-loading"
      element-loading-text="正在绑定 Shopify 店铺..."
    ></div>
    <el-result v-else :icon="state" :sub-title="subTitle" :title="resultTitle">
      <template #extra>
        <el-button type="primary" @click="goThirdPartyWebsite">前往第三方商城</el-button>
      </template>
    </el-result>
  </div>
</template>

<script lang="ts" setup>
import { shopifyBind } from '/@/api/ThirdPartyWebsite'

defineOptions({
  name: 'ShopifyBind',
})

type BindState = 'loading' | 'success' | 'error' | 'warning'

// 回调错误码与提示文案（与后端 ARCH §5.2 错误码集合一致）
const ERROR_MESSAGES: Record<string, string> = {
  INVALID_STATE: '授权状态无效，请重新发起',
  INVALID_HMAC: '回调校验失败',
  INVALID_SHOP: '回调校验失败',
  TOKEN_EXCHANGE_FAILED: '获取授权失败',
  HANDLE_TAKEN: '该店铺已绑定到其他账号',
  TICKET_EXPIRED: '链接已过期，请重新发起',
  APP_NOT_CONFIGURED: '应用未配置，请联系管理员',
}

const route = useRoute()
const router = useRouter()
const state = ref<BindState>('loading')
const handle = ref<string>('')
const errorMessage = ref<string>('')

const queryValue = (key: string): string => {
  const value = route.query[key]
  return (Array.isArray(value) ? value[0] : value) || ''
}

const resultTitle = computed<string>(() => {
  if (state.value === 'success') return handle.value ? `已连接 ${handle.value}` : '已连接'
  if (state.value === 'warning') return '链接无效'
  return '连接失败'
})

const subTitle = computed<string>(() => {
  if (state.value === 'success') return 'Shopify 店铺已绑定到当前账号，订单将自动同步'
  if (state.value === 'warning') return '缺少绑定参数，请从 Shopify 后台重新打开应用'
  return errorMessage.value
})

const bindByTicket = async (ticket: string) => {
  state.value = 'loading'
  try {
    const { data }: any = await shopifyBind({ ticket })
    handle.value = data?.handle || ''
    state.value = 'success'
  } catch (error: any) {
    // request 封装抛出的是后端响应体 { code, msg }
    errorMessage.value = error?.msg || error?.message || '绑定失败，请重新发起'
    state.value = 'error'
  }
}

const goThirdPartyWebsite = () => {
  // 第三方商城菜单由后端下发，当前账号可能没有该路由
  if (router.hasRoute('ThirdPartyWebsite')) router.push({ name: 'ThirdPartyWebsite' })
  else router.push('/')
}

onMounted(() => {
  const ticket = queryValue('ticket')
  const status = queryValue('status')
  handle.value = queryValue('handle')
  if (ticket) {
    bindByTicket(ticket)
  } else if (status === 'success') {
    state.value = 'success'
  } else if (status === 'error') {
    const code = queryValue('code')
    errorMessage.value = ERROR_MESSAGES[code] || code || '未知错误'
    state.value = 'error'
  } else {
    state.value = 'warning'
  }
})
</script>

<style lang="scss" scoped>
.shopify-bind-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
  box-sizing: border-box;
}

.shopify-bind-loading {
  width: 320px;
  height: 160px;
}
</style>
