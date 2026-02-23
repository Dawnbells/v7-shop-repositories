/**
 * Text Block 元数据
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'text',
  name: '文本',
  icon: 'i-carbon-text-font',
  category: 'basic',
  description: '用于显示文本内容，支持富文本',

  propsSchema: [
    {
      key: 'content',
      label: '内容',
      type: 'richtext',
      defaultValue: '请输入文本内容',
      description: '文本内容，支持 HTML',
    },
    {
      key: 'tag',
      label: '标签类型',
      type: 'select',
      defaultValue: 'p',
      options: [
        { label: '段落 (p)', value: 'p' },
        { label: '行内 (span)', value: 'span' },
        { label: '块级 (div)', value: 'div' },
        { label: '标题 1 (h1)', value: 'h1' },
        { label: '标题 2 (h2)', value: 'h2' },
        { label: '标题 3 (h3)', value: 'h3' },
        { label: '标题 4 (h4)', value: 'h4' },
        { label: '标题 5 (h5)', value: 'h5' },
        { label: '标题 6 (h6)', value: 'h6' },
      ],
      description: 'HTML 标签类型',
    },
    {
      key: 'align',
      label: '对齐方式',
      type: 'radio',
      defaultValue: 'left',
      options: [
        { label: '左对齐', value: 'left' },
        { label: '居中', value: 'center' },
        { label: '右对齐', value: 'right' },
        { label: '两端对齐', value: 'justify' },
      ],
    },
  ],

  defaultProps: {
    content: '请输入文本内容',
    tag: 'p',
    align: 'left',
  },

  styleSchema: [
    // 文字样式
    { key: 'fontSize', label: '字体大小', type: 'text', placeholder: '14px', group: 'text' },
    { key: 'fontWeight', label: '字体粗细', type: 'select', options: [
      { label: '正常', value: 'normal' },
      { label: '粗体', value: 'bold' },
      { label: '100', value: '100' },
      { label: '200', value: '200' },
      { label: '300', value: '300' },
      { label: '400', value: '400' },
      { label: '500', value: '500' },
      { label: '600', value: '600' },
      { label: '700', value: '700' },
      { label: '800', value: '800' },
      { label: '900', value: '900' },
    ], group: 'text' },
    { key: 'color', label: '文字颜色', type: 'color', group: 'text' },
    { key: 'lineHeight', label: '行高', type: 'text', placeholder: '1.6', group: 'text' },
    { key: 'letterSpacing', label: '字间距', type: 'text', placeholder: '0', group: 'text' },
    { key: 'textDecoration', label: '文字装饰', type: 'select', options: [
      { label: '无', value: 'none' },
      { label: '下划线', value: 'underline' },
      { label: '删除线', value: 'line-through' },
    ], group: 'text' },
    // 背景
    { key: 'backgroundColor', label: '背景颜色', type: 'color', group: 'background' },
    // 边距
    { key: 'marginTop', label: '上边距', type: 'text', placeholder: '0', group: 'margin' },
    { key: 'marginBottom', label: '下边距', type: 'text', placeholder: '0', group: 'margin' },
    { key: 'paddingTop', label: '上内边距', type: 'text', placeholder: '0', group: 'padding' },
    { key: 'paddingRight', label: '右内边距', type: 'text', placeholder: '0', group: 'padding' },
    { key: 'paddingBottom', label: '下内边距', type: 'text', placeholder: '0', group: 'padding' },
    { key: 'paddingLeft', label: '左内边距', type: 'text', placeholder: '0', group: 'padding' },
  ],

  eventsSchema: [
    { event: 'click', label: '点击', description: '点击文本时触发' },
  ],

  defaultStyle: {
    base: {
      fontSize: '14px',
      lineHeight: '1.6',
      color: 'inherit',
    },
  },

  isContainer: false,
  tags: ['文本', '段落', '标题', 'text', 'paragraph'],
}
