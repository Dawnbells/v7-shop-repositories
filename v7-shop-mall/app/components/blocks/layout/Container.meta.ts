/**
 * Container Block 元数据
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'container',
  name: '容器',
  icon: 'i-carbon-container-software',
  category: 'layout',
  description: '页面根容器，支持最大宽度限制和居中',

  propsSchema: [
    {
      key: 'maxWidth',
      label: '最大宽度',
      type: 'text',
      defaultValue: '100%',
      placeholder: '如 1200px 或 100%',
      description: '容器的最大宽度',
    },
    {
      key: 'centered',
      label: '水平居中',
      type: 'switch',
      defaultValue: false,
      description: '是否水平居中容器',
    },
  ],

  defaultProps: {
    maxWidth: '100%',
    centered: false,
  },

  styleSchema: [
    // 尺寸
    { key: 'width', label: '宽度', type: 'text', placeholder: '100%', group: 'size' },
    { key: 'minHeight', label: '最小高度', type: 'text', placeholder: 'auto', group: 'size' },
    // 背景
    { key: 'backgroundColor', label: '背景颜色', type: 'color', group: 'background' },
    { key: 'backgroundImage', label: '背景图片', type: 'text', placeholder: 'url(...)', group: 'background' },
    { key: 'backgroundSize', label: '背景尺寸', type: 'select', options: [
      { label: '覆盖', value: 'cover' },
      { label: '包含', value: 'contain' },
      { label: '自动', value: 'auto' },
    ], group: 'background' },
    { key: 'backgroundPosition', label: '背景位置', type: 'text', placeholder: 'center', group: 'background' },
    { key: 'backgroundRepeat', label: '背景重复', type: 'select', options: [
      { label: '不重复', value: 'no-repeat' },
      { label: '重复', value: 'repeat' },
      { label: '横向重复', value: 'repeat-x' },
      { label: '纵向重复', value: 'repeat-y' },
    ], group: 'background' },
    // 边框
    { key: 'borderWidth', label: '边框宽度', type: 'text', placeholder: '0', group: 'border' },
    { key: 'borderStyle', label: '边框样式', type: 'select', options: [
      { label: '无', value: 'none' },
      { label: '实线', value: 'solid' },
      { label: '虚线', value: 'dashed' },
      { label: '点线', value: 'dotted' },
    ], group: 'border' },
    { key: 'borderColor', label: '边框颜色', type: 'color', group: 'border' },
    { key: 'borderRadius', label: '圆角', type: 'text', placeholder: '0', group: 'border' },
    // 阴影
    { key: 'boxShadow', label: '阴影', type: 'text', placeholder: '0 2px 8px rgba(0,0,0,0.1)', group: 'shadow' },
    // 内边距
    { key: 'paddingTop', label: '上内边距', type: 'text', placeholder: '0', group: 'padding' },
    { key: 'paddingRight', label: '右内边距', type: 'text', placeholder: '0', group: 'padding' },
    { key: 'paddingBottom', label: '下内边距', type: 'text', placeholder: '0', group: 'padding' },
    { key: 'paddingLeft', label: '左内边距', type: 'text', placeholder: '0', group: 'padding' },
    // 外边距
    { key: 'marginTop', label: '上外边距', type: 'text', placeholder: '0', group: 'margin' },
    { key: 'marginBottom', label: '下外边距', type: 'text', placeholder: '0', group: 'margin' },
  ],

  eventsSchema: [
    { event: 'click', label: '点击', description: '点击容器时触发' },
  ],

  defaultStyle: {
    base: {
      width: '100%',
    },
  },

  isContainer: true,
  allowChildren: [],
  tags: ['容器', '布局', 'container', 'wrapper', 'root'],
}
