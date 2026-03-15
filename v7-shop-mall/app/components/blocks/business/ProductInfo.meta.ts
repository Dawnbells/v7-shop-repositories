/**
 * ProductInfo Block 元数据
 * 商品信息组件 - 显示商品标题、主图轮播、简介和价格
 */

import type { ComponentMeta } from '~/types/component-meta'

export const meta: ComponentMeta = {
  type: 'product-info',
  name: '商品信息',
  icon: 'i-carbon-product',
  category: 'business',
  description: '展示商品标题、主图轮播、简介和价格信息',

  allowedPages: ['product-detail'],

  propsSchema: [
    {
      key: 'showSummary',
      label: '显示简介',
      type: 'switch',
      defaultValue: true,
      description: '是否显示商品简介',
    },
    {
      key: 'showOriginPrice',
      label: '显示原价',
      type: 'switch',
      defaultValue: true,
      description: '是否显示原价（划线价）',
    },
    {
      key: 'indicatorStyle',
      label: '指示器样式',
      type: 'select',
      defaultValue: 'dots',
      options: [
        { label: '圆点', value: 'dots' },
        { label: '数字', value: 'numbers' },
        { label: '缩略图', value: 'thumbnails' },
        { label: '无', value: 'none' },
      ],
      description: '轮播图指示器的显示样式',
    },
    {
      key: 'indicatorPosition',
      label: '指示器位置',
      type: 'select',
      defaultValue: 'bottom',
      options: [
        { label: '图片底部', value: 'bottom' },
        { label: '图片外部', value: 'outside' },
      ],
      description: '指示器的显示位置',
    },
    {
      key: 'autoplay',
      label: '自动播放',
      type: 'switch',
      defaultValue: false,
      description: '是否自动轮播图片',
    },
    {
      key: 'autoplayInterval',
      label: '轮播间隔',
      type: 'number',
      defaultValue: 3000,
      min: 1000,
      max: 10000,
      step: 500,
      description: '自动轮播的时间间隔（毫秒）',
      showIf: 'autoplay === true',
    },
  ],

  defaultProps: {
    showSummary: true,
    showOriginPrice: true,
    indicatorStyle: 'dots',
    indicatorPosition: 'bottom',
    autoplay: false,
    autoplayInterval: 3000,
  },

  styleSchema: [
    // 图片样式
    {
      key: '--product-image-radius',
      label: '图片圆角',
      type: 'text',
      defaultValue: '8px',
      placeholder: '8px',
      group: 'image',
    },
    {
      key: '--product-image-bg',
      label: '图片背景色',
      type: 'color',
      placeholder: '#f5f5f5',
      group: 'image',
    },
    // 标题样式
    {
      key: '--product-title-size',
      label: '标题字号',
      type: 'text',
      defaultValue: '20px',
      placeholder: '20px',
      group: 'title',
    },
    {
      key: '--product-title-weight',
      label: '标题字重',
      type: 'select',
      defaultValue: '600',
      options: [
        { label: '正常', value: '400' },
        { label: '中等', value: '500' },
        { label: '粗体', value: '600' },
        { label: '加粗', value: '700' },
      ],
      group: 'title',
    },
    {
      key: '--product-title-color',
      label: '标题颜色',
      type: 'color',
      placeholder: '默认使用全局 textColor',
      group: 'title',
    },
    // 价格样式
    {
      key: '--product-price-size',
      label: '价格字号',
      type: 'text',
      defaultValue: '24px',
      placeholder: '24px',
      group: 'price',
    },
    {
      key: '--product-price-weight',
      label: '价格字重',
      type: 'select',
      defaultValue: '700',
      options: [
        { label: '中等', value: '500' },
        { label: '粗体', value: '600' },
        { label: '加粗', value: '700' },
      ],
      group: 'price',
    },
    {
      key: '--product-price-color',
      label: '价格颜色',
      type: 'color',
      placeholder: '默认使用全局 primaryColor',
      group: 'price',
    },
    {
      key: '--product-origin-price-size',
      label: '原价字号',
      type: 'text',
      defaultValue: '14px',
      placeholder: '14px',
      group: 'price',
    },
    {
      key: '--product-origin-price-color',
      label: '原价颜色',
      type: 'color',
      defaultValue: '#9ca3af',
      placeholder: '#9ca3af',
      group: 'price',
    },
    // 简介样式
    {
      key: '--product-summary-size',
      label: '简介字号',
      type: 'text',
      defaultValue: '14px',
      placeholder: '14px',
      group: 'summary',
    },
    {
      key: '--product-summary-color',
      label: '简介颜色',
      type: 'color',
      defaultValue: '#6b7280',
      placeholder: '#6b7280',
      group: 'summary',
    },
    // 指示器样式
    {
      key: '--product-indicator-color',
      label: '指示器颜色',
      type: 'color',
      placeholder: 'rgba(255, 255, 255, 0.5)',
      group: 'indicator',
    },
    {
      key: '--product-indicator-active-color',
      label: '指示器激活颜色',
      type: 'color',
      placeholder: '默认使用全局 primaryColor',
      group: 'indicator',
    },
    // 间距
    {
      key: '--product-details-padding',
      label: '详情区内边距',
      type: 'text',
      defaultValue: '16px 0',
      placeholder: '16px 0',
      group: 'spacing',
    },
  ],

  eventsSchema: [
    { event: 'click', label: '点击', description: '点击商品信息时触发' },
  ],

  defaultStyle: {
    base: {
      '--product-image-radius': '8px',
      '--product-title-size': '20px',
      '--product-title-weight': '600',
      '--product-price-size': '24px',
      '--product-price-weight': '700',
      '--product-origin-price-size': '14px',
      '--product-origin-price-color': '#9ca3af',
      '--product-summary-size': '14px',
      '--product-summary-color': '#6b7280',
      '--product-details-padding': '16px 0',
    },
    mobile: {
      '--product-title-size': '18px',
      '--product-price-size': '20px',
      '--product-details-padding': '12px 0',
    },
  },

  isContainer: false,
  tags: ['商品', '产品', '信息', '轮播', '价格', 'product', 'info', 'carousel'],
}
