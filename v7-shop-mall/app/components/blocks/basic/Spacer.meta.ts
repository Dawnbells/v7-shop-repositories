/**
 * Spacer Block 元数据
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'spacer',
  name: '间距',
  icon: 'i-carbon-arrows-vertical',
  category: 'basic',
  description: '用于在页面布局中添加垂直间距',

  propsSchema: [
    {
      key: 'height',
      label: '高度',
      type: 'text',
      defaultValue: '20px',
      placeholder: '20px',
      description: '间距高度，支持 px、rem、em 等单位',
    },
  ],

  defaultProps: {
    height: '20px',
  },

  styleSchema: [
    { key: 'backgroundColor', label: '背景颜色', type: 'color', group: 'background' },
  ],

  defaultStyle: {
    base: {
      width: '100%',
    },
  },

  isContainer: false,
  tags: ['间距', '空白', '分隔', 'spacer', 'divider'],
}
