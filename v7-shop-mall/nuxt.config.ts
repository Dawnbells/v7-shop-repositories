// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  modules: ['@unocss/nuxt'],

  css: ['@unocss/reset/tailwind.css'],

  runtimeConfig: {
    db: {
      host: 'localhost',
      port: 3306,
      user: 'root',
      password: '',
      database: 'shop',
    },
  },
})
