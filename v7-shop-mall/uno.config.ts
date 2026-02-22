import {
  defineConfig,
  presetAttributify,
  presetIcons,
  presetUno,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'

export default defineConfig({
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
    // site-config.schema.ts 中动态使用的图标
    'i-carbon-information',
    'i-carbon-phone',
    'i-carbon-row-collapse',
    'i-carbon-logo-twitter',
    'i-carbon-search',
    'i-carbon-settings',
    'i-carbon-color-palette',
    'i-carbon-text-font',
    'i-carbon-crop',
    'i-carbon-fit-to-screen',
    'i-carbon-folder',
  ],
})
