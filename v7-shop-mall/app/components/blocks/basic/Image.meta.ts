/**
 * Image Block 元数据
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'image',
  name: '图片',
  icon: 'i-carbon-image',
  category: 'basic',
  description: '显示图片，支持 CDN 路径自动转换和加载失败降级',

  propsSchema: [
    {
      key: 'src',
      label: '图片地址',
      type: 'image',
      defaultValue: '',
      description: '图片路径，支持相对路径和完整 URL',
    },
    {
      key: 'alt',
      label: '替代文本',
      type: 'text',
      defaultValue: '',
      placeholder: '图片描述',
      description: '图片无法显示时的替代文本',
    },
    {
      key: 'objectFit',
      label: '填充方式',
      type: 'select',
      defaultValue: 'cover',
      options: [
        { label: '覆盖 (cover)', value: 'cover' },
        { label: '包含 (contain)', value: 'contain' },
        { label: '填充 (fill)', value: 'fill' },
        { label: '原始 (none)', value: 'none' },
        { label: '缩小 (scale-down)', value: 'scale-down' },
      ],
      description: '图片在容器中的填充方式',
    },
    {
      key: 'fallback',
      label: '启用降级',
      type: 'switch',
      defaultValue: true,
      description: '图片加载失败时是否尝试备用 CDN',
    },
    {
      key: 'loading',
      label: '加载方式',
      type: 'select',
      defaultValue: 'lazy',
      options: [
        { label: '懒加载 (lazy)', value: 'lazy' },
        { label: '立即加载 (eager)', value: 'eager' },
      ],
      description: '图片加载策略',
    },
  ],

  defaultProps: {
    src: '',
    alt: '',
    objectFit: 'cover',
    fallback: true,
    loading: 'lazy',
  },

  styleSchema: [
    { key: 'width', label: '宽度', type: 'text', placeholder: '100%', group: 'size' },
    { key: 'height', label: '高度', type: 'text', placeholder: 'auto', group: 'size' },
    { key: 'maxWidth', label: '最大宽度', type: 'text', placeholder: '100%', group: 'size' },
    { key: 'maxHeight', label: '最大高度', type: 'text', placeholder: 'none', group: 'size' },
    { key: 'borderRadius', label: '圆角', type: 'text', placeholder: '0', group: 'border' },
    { key: 'backgroundColor', label: '背景颜色', type: 'color', group: 'background' },
  ],

  defaultStyle: {
    base: {
      width: '100%',
      height: 'auto',
    },
  },

  isContainer: false,
  tags: ['图片', '图像', 'image', 'img', 'picture'],
}
