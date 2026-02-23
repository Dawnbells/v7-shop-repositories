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
