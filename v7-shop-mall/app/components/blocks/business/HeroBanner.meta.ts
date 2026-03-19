/**
 * HeroBanner Block 元数据
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'herobanner',
  name: '顶部海报',
  icon: 'i-carbon-image-search',
  category: 'business',
  description: '首页顶部海报组件，支持单张图片和轮播图两种模式',

  allowedPages: ['home'],

  propsSchema: [
    {
      key: 'items',
      label: '海报图片',
      type: 'json',
      defaultValue: [],
      description: '海报图片列表，每项包含 src（图片地址）、alt（替代文本）、link（跳转链接）',
    },
    {
      key: 'autoplay',
      label: '自动播放',
      type: 'switch',
      defaultValue: true,
      description: '多张图片时是否自动轮播',
    },
    {
      key: 'interval',
      label: '切换间隔',
      type: 'number',
      defaultValue: 4000,
      min: 1000,
      max: 10000,
      step: 500,
      description: '自动轮播的切换间隔（毫秒）',
      showIf: 'autoplay === true',
    },
    {
      key: 'showIndicators',
      label: '显示指示器',
      type: 'switch',
      defaultValue: true,
      description: '是否显示底部指示器（小圆点）',
    },
    {
      key: 'objectFit',
      label: '图片填充',
      type: 'select',
      defaultValue: 'cover',
      options: [
        { label: '覆盖 (cover)', value: 'cover' },
        { label: '包含 (contain)', value: 'contain' },
        { label: '填充 (fill)', value: 'fill' },
      ],
      description: '图片在容器中的填充方式',
    },
  ],

  defaultProps: {
    items: [],
    autoplay: true,
    interval: 4000,
    showIndicators: true,
    objectFit: 'cover',
  },

  styleSchema: [
    {
      key: 'height',
      label: '高度',
      type: 'text',
      defaultValue: '400px',
      placeholder: '400px',
      group: 'size',
    },
    {
      key: 'maxWidth',
      label: '最大宽度',
      type: 'text',
      defaultValue: '100%',
      placeholder: '100%',
      group: 'size',
    },
    {
      key: 'borderRadius',
      label: '圆角',
      type: 'text',
      defaultValue: '0',
      placeholder: '0',
      group: 'border',
    },
    {
      key: 'marginTop',
      label: '上外边距',
      type: 'text',
      defaultValue: '0',
      placeholder: '0',
      group: 'spacing',
    },
    {
      key: 'marginBottom',
      label: '下外边距',
      type: 'text',
      defaultValue: '0',
      placeholder: '0',
      group: 'spacing',
    },
    {
      key: 'marginLeft',
      label: '左外边距',
      type: 'text',
      defaultValue: 'auto',
      placeholder: 'auto',
      group: 'spacing',
    },
    {
      key: 'marginRight',
      label: '右外边距',
      type: 'text',
      defaultValue: 'auto',
      placeholder: 'auto',
      group: 'spacing',
    },
  ],

  defaultStyle: {
    base: {
      height: '400px',
      maxWidth: '100%',
      borderRadius: '0',
      marginTop: '0',
      marginBottom: '0',
      marginLeft: 'auto',
      marginRight: 'auto',
    },
    mobile: {
      height: '200px',
    },
  },

  isContainer: false,
  tags: ['海报', '轮播', 'banner', 'carousel', 'slider', '首页', '广告'],
}
