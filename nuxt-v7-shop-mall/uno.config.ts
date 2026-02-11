import {
  defineConfig,
  presetAttributify,
  presetIcons,
  presetTypography,
  presetUno,
  presetWebFonts,
  transformerDirectives,
  transformerVariantGroup
} from 'unocss'

export default defineConfig({
  shortcuts: [
    // 布局
    ['flex-center', 'flex items-center justify-center'],
    ['flex-between', 'flex items-center justify-between'],
    ['flex-col-center', 'flex flex-col items-center justify-center'],

    // 编辑器布局
    ['builder-panel', 'bg-gray-50 dark:bg-gray-900 border-gray-200 dark:border-gray-700'],
    ['builder-sidebar', 'w-64 h-full overflow-y-auto builder-panel border-r'],
    ['builder-canvas', 'flex-1 h-full overflow-auto bg-gray-100 dark:bg-gray-800'],
    ['builder-property', 'w-80 h-full overflow-y-auto builder-panel border-l'],

    // 组件面板项
    ['component-item', 'p-3 rounded-lg cursor-move hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors'],

    // 画布组件
    ['canvas-component', 'relative border-2 border-transparent hover:border-blue-400 transition-colors'],
    ['canvas-component-selected', 'border-blue-500 ring-2 ring-blue-200'],

    // 属性面板
    ['property-group', 'mb-4 p-3 bg-white dark:bg-gray-800 rounded-lg'],
    ['property-label', 'text-sm text-gray-600 dark:text-gray-400 mb-1'],
    ['property-input', 'w-full px-3 py-2 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all']
  ],

  theme: {
    colors: {
      primary: {
        50: '#eff6ff',
        100: '#dbeafe',
        200: '#bfdbfe',
        300: '#93c5fd',
        400: '#60a5fa',
        500: '#3b82f6',
        600: '#2563eb',
        700: '#1d4ed8',
        800: '#1e40af',
        900: '#1e3a8a'
      }
    },
    breakpoints: {
      sm: '640px',
      md: '768px',
      lg: '1024px',
      xl: '1280px'
    }
  },

  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({
      scale: 1.2,
      cdn: 'https://esm.sh/'
    }),
    presetTypography(),
    presetWebFonts({
      fonts: {
        sans: 'Inter:400,500,600,700',
        mono: 'Fira Code:400,500'
      }
    })
  ],

  transformers: [
    transformerDirectives(),
    transformerVariantGroup()
  ],

  safelist: [
    // 响应式断点相关
    'max-w-375px', 'max-w-768px', 'max-w-1024px',
    // 常用图标
    'i-carbon-add', 'i-carbon-trash-can', 'i-carbon-edit',
    'i-carbon-phone', 'i-carbon-tablet', 'i-carbon-laptop',
    'i-carbon-text-font', 'i-carbon-image', 'i-carbon-button',
    'i-carbon-container', 'i-carbon-grid',
    'i-carbon-notification', 'i-carbon-settings', 'i-carbon-view',
    'i-carbon-template', 'i-carbon-close', 'i-carbon-save',
    'i-carbon-color-palette', 'i-carbon-chevron-down',
    'i-carbon-arrow-up', 'i-carbon-arrow-down',
    'i-carbon-drag-horizontal', 'i-carbon-warning',
    'i-carbon-search', 'i-carbon-cube',
    'i-carbon-shopping-cart', 'i-carbon-document-add',
    'i-carbon-data-base', 'i-carbon-close-outline',
    // 页头页脚图标
    'i-carbon-application-web', 'i-carbon-bookmark',
    'i-carbon-language', 'i-carbon-user',
    'i-carbon-chevron-right',
    'i-carbon-logo-wechat', 'i-carbon-logo-twitter',
    'i-carbon-logo-facebook', 'i-carbon-logo-instagram',
    'i-carbon-logo-youtube', 'i-carbon-logo-linkedin',
    'i-simple-icons-tiktok',
    // 站点配置分组图标
    'i-carbon-information', 'i-carbon-row-collapse',
    'i-carbon-globe', 'i-carbon-data-vis-4',
    'i-carbon-settings-adjust', 'i-carbon-folder',
    'i-carbon-hashtag', 'i-carbon-toggle-off',
    'i-carbon-text-align-left', 'i-carbon-list-checked',
    'i-carbon-list', 'i-carbon-json', 'i-carbon-unknown',
    'i-carbon-touch-1', 'i-carbon-copy', 'i-carbon-parameter',
    'i-carbon-circle-dash',
    // 全局皮肤分组图标
    'i-carbon-paint-brush', 'i-carbon-crop', 'i-carbon-fit-to-screen',
    'i-carbon-reset', 'i-carbon-image-search',
    // 物流图标
    'i-ph-truck'
  ]
})
