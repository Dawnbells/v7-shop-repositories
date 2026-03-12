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
    // 组件分类图标 (ComponentPanel.vue)
    'i-carbon-cube',
    'i-carbon-grid',
    'i-carbon-shopping-cart',
    'i-carbon-gift',
    'i-carbon-text-creation',
    // 布局组件图标 (*.meta.ts)
    'i-carbon-row',
    'i-carbon-column',
    'i-carbon-document',
    // 类型图标 (VariableValueEditor, ObjectEditor, VariableInput, VariableManager)
    'i-carbon-hashtag',
    'i-carbon-toggle-off',
    'i-carbon-image',
    'i-carbon-text-align-left',
    'i-carbon-list-checked',
    'i-carbon-list',
    'i-carbon-json',
    'i-carbon-help',
    // 预设数据集图标 (preset-datasets.ts)
    'i-carbon-product',
    'i-carbon-globe',
    'i-carbon-rocket',
    // 数据源分组图标 (type-matching.ts)
    'i-carbon-data-base',
    'i-carbon-variable',
    // 设备切换图标 (BuilderCanvas.vue)
    'i-carbon-laptop',
    'i-carbon-tablet',
    'i-carbon-mobile',
    // 属性面板标签图标 (PropertyPanel.vue)
    'i-carbon-settings-adjust',
    'i-carbon-paint-brush',
    'i-carbon-touch-interaction',
  ],
})
