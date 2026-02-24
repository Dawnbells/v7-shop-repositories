/**
 * Grid Block 元数据
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'grid',
  name: '网格布局',
  icon: 'i-carbon-grid',
  category: 'layout',
  description: 'CSS Grid 网格容器',

  propsSchema: [
    {
      key: 'columns',
      label: '列定义',
      type: 'text',
      defaultValue: '3',
      placeholder: '如 3 或 1fr 2fr 1fr',
      description: '列数或列定义，数字表示等分列数',
    },
    {
      key: 'rows',
      label: '行定义',
      type: 'text',
      defaultValue: 'auto',
      placeholder: '如 auto 或 100px 200px',
      description: '行定义，默认自动',
    },
    {
      key: 'gap',
      label: '间距',
      type: 'text',
      defaultValue: '0',
      placeholder: '如 16px 或 1rem',
      description: '网格间距',
    },
    {
      key: 'columnGap',
      label: '列间距',
      type: 'text',
      defaultValue: '',
      placeholder: '如 16px',
      description: '单独设置列间距，不设置则使用间距值',
    },
    {
      key: 'rowGap',
      label: '行间距',
      type: 'text',
      defaultValue: '',
      placeholder: '如 16px',
      description: '单独设置行间距，不设置则使用间距值',
    },
    {
      key: 'justifyItems',
      label: '水平对齐',
      type: 'select',
      defaultValue: 'stretch',
      options: [
        { label: '拉伸', value: 'stretch' },
        { label: '起始', value: 'start' },
        { label: '居中', value: 'center' },
        { label: '末尾', value: 'end' },
      ],
      description: '子元素水平对齐方式',
    },
    {
      key: 'alignItems',
      label: '垂直对齐',
      type: 'select',
      defaultValue: 'stretch',
      options: [
        { label: '拉伸', value: 'stretch' },
        { label: '起始', value: 'start' },
        { label: '居中', value: 'center' },
        { label: '末尾', value: 'end' },
      ],
      description: '子元素垂直对齐方式',
    },
  ],

  defaultProps: {
    columns: 3,
    rows: 'auto',
    gap: '0',
    columnGap: '',
    rowGap: '',
    justifyItems: 'stretch',
    alignItems: 'stretch',
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
  tags: ['网格', '布局', 'grid', 'layout'],
}
