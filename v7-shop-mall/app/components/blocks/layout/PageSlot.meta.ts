/**
 * PageSlot Block 元数据
 * 页面内容插槽组件
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'pageslot',
  name: '页面插槽',
  icon: 'i-carbon-document',
  category: 'layout',
  description: '在布局中标记页面内容的插入位置',

  propsSchema: [
    {
      key: 'label',
      label: '占位符文字',
      type: 'text',
      defaultValue: '页面内容区域',
      placeholder: '在编辑器中显示的占位文字',
    },
  ],

  defaultProps: {
    label: '页面内容区域',
  },

  defaultStyle: {
    base: {
      width: '100%',
      minHeight: '200px',
    },
  },

  isContainer: false,
  
  // 仅允许在布局页面中使用
  allowedPages: ['layout'],
}
