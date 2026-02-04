// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  // 全局 CSS（使用 UnoCSS 内置的 Tailwind reset）
  css: ['@unocss/reset/tailwind.css'],

  // 模块
  modules: ['@unocss/nuxt'],

  // 路由规则 - SSR/CSR 配置
  routeRules: {
    // 客户访问页面 - SSR（SEO + 首屏性能）
    '/': { ssr: true },
    '/product/**': { ssr: true },
    '/checkout': { ssr: true },
    '/order/**': { ssr: true },
    '/article/**': { ssr: true },
    '/p/**': { ssr: true },

    // 编辑器 - CSR（无需SEO，减轻服务器压力）
    '/builder/**': { ssr: false }
  },

  // 自动导入
  imports: {
    dirs: ['composables', 'utils']
  },

  // 组件自动导入
  components: [
    // 全局组件（用于 ComponentRenderer 动态渲染）
    { path: '~/components/shop', global: true },
    { path: '~/components/shop-layout', global: true },
    // 其他组件保持默认（按需导入）
    { path: '~/components', pathPrefix: false }
  ],

  runtimeConfig: {
    // 仅服务端可用（不会暴露给客户端）
    // 配置优先级：系统环境变量（NUXT_*） > .env 文件 > 默认值
    db: {
      host: 'localhost',      // 环境变量: NUXT_DB_HOST
      port: 3306,             // 环境变量: NUXT_DB_PORT
      user: 'root',           // 环境变量: NUXT_DB_USER
      password: '',           // 环境变量: NUXT_DB_PASSWORD
      database: 'shop',       // 环境变量: NUXT_DB_DATABASE
    },
    redis: {
      host: 'localhost',      // 环境变量: NUXT_REDIS_HOST
      port: 6379,             // 环境变量: NUXT_REDIS_PORT
      password: '',           // 环境变量: NUXT_REDIS_PASSWORD
      db: 0,                  // 环境变量: NUXT_REDIS_DB
    },
    // 风控服务地址
    riskServiceUrl: 'https://cloak.xmskyai.com', // 环境变量: NUXT_RISK_SERVICE_URL
    // 开发环境模拟域名（本地开发时使用此域名查询商城信息）
    devDomain: '',  // 环境变量: NUXT_DEV_DOMAIN
    // HTTP 代理（用于开发环境或需要代理的场景）
    httpProxy: '',  // 环境变量: NUXT_HTTP_PROXY

    // 客户端可用（会暴露给浏览器）
    public: {
      // iframe postMessage 允许的 origin 白名单
      // 多个 origin 用逗号分隔，如 "https://admin.example.com,https://dev.example.com"
      // 设置为 "*" 允许所有来源（仅用于开发环境）
      // 环境变量: NUXT_PUBLIC_ALLOWED_ORIGINS
      allowedOrigins: '',
    },
  }
})
