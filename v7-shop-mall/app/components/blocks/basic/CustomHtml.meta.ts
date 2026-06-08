/**
 * CustomHtml Block 元数据
 * 自定义 HTML 组件 - 在沙箱 iframe 中渲染商家粘贴的 HTML
 */

import type { ComponentMeta } from "~/types/component-meta";

export const meta: ComponentMeta = {
  type: "customhtml",
  name: "自定义HTML",
  icon: "i-carbon-code",
  category: "basic",
  description:
    "粘贴任意 HTML，在隔离的沙箱 iframe 中渲染（无法访问商城 Cookie/DOM/接口）",

  // 仅商品详情页可用
  allowedPages: ["product-detail"],

  propsSchema: [
    {
      key: "html",
      label: "HTML 代码",
      type: "textarea",
      defaultValue: "",
      placeholder: "<div>在此粘贴 HTML 代码</div>",
      description:
        "内容在隔离的沙箱 iframe 中运行，无法访问商城 Cookie、DOM 或内部接口",
    },
    {
      key: "height",
      label: "高度",
      type: "text",
      defaultValue: "auto",
      placeholder: "auto 或 400px",
      description:
        "auto = 按内容自动撑高；填具体值（如 400px）= 固定高度并内部滚动",
    },
  ],

  defaultProps: {
    html: "",
    height: "auto",
  },

  styleSchema: [
    { key: "backgroundColor", label: "背景颜色", type: "color", group: "background" },
    { key: "borderWidth", label: "边框宽度", type: "text", placeholder: "0", group: "border" },
    {
      key: "borderStyle",
      label: "边框样式",
      type: "select",
      options: [
        { label: "无", value: "none" },
        { label: "实线", value: "solid" },
        { label: "虚线", value: "dashed" },
      ],
      group: "border",
    },
    { key: "borderColor", label: "边框颜色", type: "color", group: "border" },
    { key: "borderRadius", label: "圆角", type: "text", placeholder: "0", group: "border" },
    { key: "marginTop", label: "上边距", type: "text", placeholder: "0", group: "margin" },
    { key: "marginBottom", label: "下边距", type: "text", placeholder: "0", group: "margin" },
    { key: "paddingTop", label: "上内边距", type: "text", placeholder: "0", group: "padding" },
    { key: "paddingRight", label: "右内边距", type: "text", placeholder: "0", group: "padding" },
    { key: "paddingBottom", label: "下内边距", type: "text", placeholder: "0", group: "padding" },
    { key: "paddingLeft", label: "左内边距", type: "text", placeholder: "0", group: "padding" },
  ],

  isContainer: false,
  tags: ["html", "自定义", "代码", "嵌入", "embed", "customhtml"],
};
