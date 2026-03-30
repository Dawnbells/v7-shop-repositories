import {
  defineConfig,
  presetAttributify,
  presetIcons,
  presetUno,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'

export default defineConfig({
  content: {
    pipeline: {
      include: [
        /\.vue($|\?)/,
        /\.meta\.ts($|\?)/,
        /constants\/.*\.ts($|\?)/,
        /utils\/type-matching\.ts($|\?)/,
        /composables\/useCheckoutPage\.ts($|\?)/,
      ],
    },
  },
  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({
      scale: 1.2,
      warn: true,
    }),
  ],
  transformers: [
    transformerDirectives(),
    transformerVariantGroup(),
  ],
  safelist: [
    'i-carbon-phone',
    'i-carbon-logo-twitter',
    'i-carbon-shopping-cart',
    'i-carbon-document',
    'i-carbon-image',
    'i-carbon-rocket',
    'i-carbon-delivery',
    'i-carbon-wallet',
  ],
})
