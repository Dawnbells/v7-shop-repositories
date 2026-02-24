/**
 * Row Block 元数据
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'row',
  name: '横向布局',
  icon: 'i-carbon-row',
  category: 'layout',
  description: 'Flexbox 横向排列容器',

  propsSchema: [
    {
      key: 'justify',
      label: '主轴对齐',
      type: 'select',
      defaultValue: 'flex-start',
      options: [
        { label: '起始对齐', value: 'flex-start' },
        { label: '居中', value: 'center' },
        { label: '末尾对齐', value: 'flex-end' },
        { label: '两端对齐', value: 'space-between' },
        { label: '均匀分布', value: 'space-around' },
        { label: '等间距', value: 'space-evenly' },
      ],
      description: '子元素在主轴（水平方向）的对齐方式',
    },
    {
      key: 'align',
      label: '交叉轴对齐',
      type: 'select',
      defaultValue: 'stretch',
      options: [
        { label: '拉伸', value: 'stretch' },
        { label: '起始对齐', value: 'flex-start' },
        { label: '居中', value: 'center' },
        { label: '末尾对齐', value: 'flex-end' },
        { label: '基线对齐', value: 'baseline' },
      ],
      description: '子元素在交叉轴（垂直方向）的对齐方式',
    },
    {
      key: 'wrap',
      label: '换行方式',
      type: 'select',
      defaultValue: 'nowrap',
      options: [
        { label: '不换行', value: 'nowrap' },
        { label: '换行', value: 'wrap' },
        { label: '反向换行', value: 'wrap-reverse' },
      ],
      description: '子元素是否换行',
    },
    {
      key: 'gap',
      label: '间距',
      type: 'text',
      defaultValue: '0',
      placeholder: '如 16px 或 1rem',
      description: '子元素之间的间距',
    },
  ],

  defaultProps: {
    justify: 'flex-start',
    align: 'stretch',
    wrap: 'nowrap',
    gap: '0',
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
  tags: ['横向', '布局', 'row', 'flex', 'horizontal'],
}
