// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  modules: ['@unocss/nuxt'],

  css: ['@unocss/reset/tailwind.css'],

  // 组件配置
  components: {
    dirs: [
      // 默认组件目录
      '~/components',
      // blocks 组件目录，使用 Block 前缀，全局注册
      {
        path: '~/components/blocks',
        prefix: 'Block',
        global: true,
      },
    ],
  },

  runtimeConfig: {
    db: {
      host: 'localhost',
      port: 3306,
      user: 'root',
      password: '',
      database: 'shop',
    },
    // 开发环境模拟域名（本地调试时使用此域名查询商城信息）
    devDomain: '',
    // 风控服务地址
    riskServiceUrl: '',
    // HTTP 代理（用于开发环境或需要代理的场景）
    httpProxy: '',
  },
})
