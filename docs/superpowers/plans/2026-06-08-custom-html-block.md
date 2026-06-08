# 自定义 HTML 块（沙箱 iframe）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `v7-shop-mall` 商品详情页新增一个 `customhtml` 区块，让商家在属性面板粘贴任意 HTML，前台用沙箱 `<iframe srcdoc>` 隔离渲染。

**Architecture:** 两个新文件（`CustomHtml.vue` + `CustomHtml.meta.ts`）放入 `app/components/blocks/basic/`，靠现有 glob 插件自动注册（`type` 由文件名小写推导为 `customhtml`），零改动现有文件。用户 HTML 仅进入 `<iframe sandbox srcdoc>`（**不含 `allow-same-origin`**），运行在独立不透明源中，无法访问商城 Cookie/DOM/接口，也不污染父文档（含 SSR）。

**Tech Stack:** Nuxt 4 + Vue 3 `<script setup>` + TypeScript + UnoCSS。无测试框架——验证用 `pnpm build`（类型/编译门禁）+ 真实浏览器手动验证。

**关联 spec:** `docs/superpowers/specs/2026-06-08-custom-html-block-design.md`

**安全红线（实现全程必须遵守）：**
1. `sandbox` **绝不可**加入 `allow-same-origin`（与 `allow-scripts` 同时存在会使沙箱失效，等同回到商城源）。
2. 用户 HTML **只能**进入 iframe 的 `:srcdoc` 属性，**绝不可**经 `v-html` / `innerHTML` 进入父文档。

---

## 文件结构

| 文件 | 责任 | 操作 |
|------|------|------|
| `v7-shop-mall/app/components/blocks/basic/CustomHtml.meta.ts` | 区块元数据：palette 入口、属性/样式 schema、限定 `product-detail` | 新建 |
| `v7-shop-mall/app/components/blocks/basic/CustomHtml.vue` | 区块组件：沙箱 iframe 渲染、自动撑高、编辑器 overlay、空状态 | 新建 |

无需修改任何现有文件：`app/plugins/blocks.ts` 与 `app/plugins/blocks-meta.client.ts` 的 glob 会自动拾取两个新文件。文件名 `CustomHtml.vue` 必须与 meta 的 `type: 'customhtml'` 对应（文件名小写 == type）。

---

## Task 1: 区块元数据 `CustomHtml.meta.ts`

**Files:**
- Create: `v7-shop-mall/app/components/blocks/basic/CustomHtml.meta.ts`

- [ ] **Step 1: 创建元数据文件**

写入以下完整内容：

```ts
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
```

- [ ] **Step 2: 提交**

```bash
cd v7-shop-mall
git add app/components/blocks/basic/CustomHtml.meta.ts
git commit -m "feat(mall): 新增 customhtml 区块元数据

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 区块组件 `CustomHtml.vue`

**Files:**
- Create: `v7-shop-mall/app/components/blocks/basic/CustomHtml.vue`

实现要点：
- 单根元素 `<div class="custom-html-block">`，接收 NodeRenderer 透传的 `:style`（styleSchema 样式落在外层容器）。
- `srcdoc` = `<meta>` + 样式重置 + **用户 HTML** + **受信撑高脚本**（固定字符串，非用户可控）。
- `sandbox` 为静态字面量（不可被任何响应式值篡改），**不含 `allow-same-origin`**。
- 自动撑高：iframe 内脚本 `parent.postMessage({__v7Resize:h})`；父组件仅当 `e.source === iframe.contentWindow` 且 payload 为合法数字时更新高度（不校验 `e.origin`，因不透明源下其值为 `"null"`）。
- `height` 为具体值时固定高度 + 内部滚动；为 `auto` 时自动撑高（测量前用 150px 初值，避免 SSR 0 高度）。
- `inject('isInEditor')`：编辑器内叠加透明 overlay 让区块可被选中/拖拽，并显示「HTML」角标。
- 空内容：编辑器显示占位，前台不渲染。

- [ ] **Step 1: 创建组件文件**

写入以下完整内容：

```vue
<script setup lang="ts">
/**
 * CustomHtml Block - 自定义 HTML 组件
 *
 * 在沙箱 <iframe srcdoc> 中渲染商家粘贴的任意 HTML。
 * 沙箱不含 allow-same-origin，用户代码运行在独立的不透明源中，
 * 无法访问商城的 Cookie / localStorage / DOM / 内部接口，
 * 也无法在后台 builder 中攻击 admin 会话。
 *
 * 安全红线（修改时务必遵守）：
 * 1. sandbox 绝不可加入 allow-same-origin（与 allow-scripts 同时存在会使沙箱失效）。
 * 2. 用户 HTML 只能进入 iframe 的 srcdoc，绝不可经 v-html/innerHTML 进入父文档。
 */

interface Props {
  /** 商家粘贴的 HTML 代码 */
  html?: string;
  /** 高度：'auto' 按内容自动撑高；具体值（如 '400px'）固定高度并内部滚动 */
  height?: string;
}

const props = withDefaults(defineProps<Props>(), {
  html: "",
  height: "auto",
});

// 编辑器上下文标记（由页面/画布 provide）
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 是否有内容
const hasHtml = computed(() => !!props.html && props.html.trim().length > 0);

// 是否固定高度
const isFixedHeight = computed(() => !!props.height && props.height !== "auto");

// iframe 元素引用与自动测量高度
const iframeEl = ref<HTMLIFrameElement | null>(null);
const autoHeight = ref<number | null>(null);

// 注入到 srcdoc 的受信撑高脚本（固定内容，非用户可控）
// 闭合标签写成 <\/script> 以免提前结束本 SFC 的 script 块
const RESIZE_SCRIPT =
  "<script>(function(){function p(){var h=Math.max(document.documentElement.scrollHeight||0,document.body?document.body.scrollHeight:0);parent.postMessage({__v7Resize:h},'*');}if(document.readyState==='complete'){p();}window.addEventListener('load',p);try{var r=new ResizeObserver(p);r.observe(document.documentElement);if(document.body){r.observe(document.body);}}catch(e){}setTimeout(p,50);setTimeout(p,300);setTimeout(p,1000);})();<\/script>";

// 组装 srcdoc：meta + 样式重置 + 用户 HTML + 撑高脚本
const srcdoc = computed(
  () =>
    '<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:0}*{box-sizing:border-box}</style></head><body>' +
    (props.html || "") +
    RESIZE_SCRIPT +
    "</body></html>",
);

// iframe 高度
const frameHeight = computed(() => {
  if (isFixedHeight.value) return props.height as string;
  if (autoHeight.value != null) return autoHeight.value + "px";
  return "150px"; // SSR/未测量前的初始高度，客户端测量后自动修正
});

// 接收 iframe 上报的高度（仅信任本 iframe 的消息）
function onMessage(e: MessageEvent) {
  const el = iframeEl.value;
  if (!el || e.source !== el.contentWindow) return;
  const data = e.data as unknown;
  if (
    data &&
    typeof data === "object" &&
    typeof (data as Record<string, unknown>).__v7Resize === "number"
  ) {
    const h = (data as Record<string, number>).__v7Resize;
    if (h > 0 && h < 20000) autoHeight.value = Math.ceil(h);
  }
}

onMounted(() => {
  window.addEventListener("message", onMessage);
});

onBeforeUnmount(() => {
  window.removeEventListener("message", onMessage);
});
</script>

<template>
  <div class="custom-html-block">
    <template v-if="hasHtml">
      <!-- 安全红线：sandbox 不含 allow-same-origin；用户 HTML 仅经 :srcdoc 进入隔离 iframe -->
      <iframe
        ref="iframeEl"
        class="custom-html-frame"
        :srcdoc="srcdoc"
        sandbox="allow-scripts allow-popups allow-forms allow-popups-to-escape-sandbox allow-presentation"
        referrerpolicy="no-referrer"
        title="custom-html"
        :style="{ height: frameHeight }"
      />
      <!-- 编辑器内叠加透明层，保证区块可被选中/拖拽，并显示角标 -->
      <div v-if="isInEditor" class="custom-html-overlay">
        <span class="custom-html-badge">HTML</span>
      </div>
    </template>

    <!-- 空内容：编辑器显示占位，前台不渲染 -->
    <div v-else-if="isInEditor" class="custom-html-empty">
      请在右侧粘贴 HTML 代码
    </div>
  </div>
</template>

<style scoped>
.custom-html-block {
  position: relative;
  width: 100%;
}

.custom-html-frame {
  display: block;
  width: 100%;
  border: 0;
}

.custom-html-overlay {
  position: absolute;
  inset: 0;
  pointer-events: auto;
}

.custom-html-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 1px 6px;
  font-size: 11px;
  line-height: 1.4;
  color: #fff;
  background: rgba(59, 130, 246, 0.9);
  border-radius: 3px;
  pointer-events: none;
}

.custom-html-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  padding: 16px;
  color: #94a3b8;
  font-size: 13px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.05);
}
</style>
```

- [ ] **Step 2: 构建验证（类型 + 编译门禁）**

Run:
```bash
cd v7-shop-mall && pnpm build
```
Expected: 构建成功（exit 0），无 TypeScript / 模板编译错误。

> 备注：mall 无独立 typecheck 脚本，`nuxt build` 即类型+编译门禁。若构建环境无网络导致 `@nuxt/fonts` 拉取字体失败，可改用 `pnpm dev` 启动并观察无编译报错。

- [ ] **Step 3: 提交**

```bash
git add app/components/blocks/basic/CustomHtml.vue
git commit -m "feat(mall): 新增 customhtml 区块组件（沙箱 iframe 渲染）

商家粘贴的 HTML 在 <iframe sandbox srcdoc>（无 allow-same-origin）中
隔离渲染，无法访问商城 Cookie/DOM/接口；含 postMessage 自动撑高与
编辑器 overlay。

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 验证（含 XSS 隔离证据）

本任务无代码改动，逐项验证行为；若发现问题回到 Task 2 修复。

**Files:**
- 临时创建（验证后删除，不提交）：`v7-shop-mall/.tmp-sandbox-check.html`

- [ ] **Step 1: 安全隔离验证（真实浏览器，独立于 mall 后端）**

创建临时验证页 `v7-shop-mall/.tmp-sandbox-check.html`，内容与组件的沙箱配置一致：

```html
<!doctype html>
<meta charset="utf-8" />
<h1>Parent page (模拟商城源)</h1>
<script>
  // 在父页面种一个"会话"cookie，验证 iframe 能否读取它
  document.cookie = "v7_session=SECRET_VALUE; path=/";
</script>
<iframe
  sandbox="allow-scripts allow-popups allow-forms allow-popups-to-escape-sandbox allow-presentation"
  referrerpolicy="no-referrer"
  style="width: 100%; height: 220px; border: 1px solid #ccc"
  srcdoc="<body style='font-family:monospace'><script>document.body.innerHTML='cookie=['+document.cookie+']<br>origin=['+location.origin+']';parent.postMessage({__v7Resize:document.body.scrollHeight},'*');<\/script></body>"
></iframe>
<script>
  window.addEventListener("message", function (e) {
    console.log("[parent] message from frame:", e.data, "origin:", e.origin);
  });
</script>
```

在浏览器中打开该文件（`file://` 直接打开即可）。

Expected（隔离生效的证据）：
- iframe 内显示 `cookie=[]`（**读不到**父页面的 `v7_session`）
- iframe 内显示 `origin=[null]`（不透明源）
- 控制台打印 `message from frame: {__v7Resize: <数字>} origin: null`（postMessage 撑高通道工作，且来源源为 `null`，印证父侧用 `e.source` 身份校验而非 `e.origin` 的设计）

- [ ] **Step 2: 删除临时验证页**

```bash
cd v7-shop-mall && rm -f .tmp-sandbox-check.html
```

- [ ] **Step 3: 应用内功能验证（如有可用的 DB/Redis dev 环境）**

前置：按 `v7-shop-mall/.env.example` 配置，设 `NUXT_DEV_DOMAIN` 模拟商城域名，`pnpm dev`。

逐项确认：
- 在 `/builder` 选择商品详情页，组件面板 `basic` 分组出现「自定义HTML」；拖入画布。
- 属性面板「HTML 代码」粘贴 `<h2 style="color:teal">Hello</h2><p>段落</p>` → 画布中 iframe 正确渲染、按内容自动撑高；区块仍可点击选中（overlay 生效）、显示「HTML」角标。
- 粘贴 `<script>alert(document.cookie)</script>` → 弹窗内容为空（`alert()` 弹出空字符串），admin/builder 不受影响。
- 「高度」填 `200px` 且内容较高 → iframe 固定 200px 且内部出现滚动条。
- 清空「HTML 代码」→ 画布显示虚线占位「请在右侧粘贴 HTML 代码」。
- 保存后访问真实商品详情页 → 前台 SSR 正确显示该 HTML；查看页面源码确认用户 HTML 位于 iframe 的 `srcdoc` 属性内、**未**出现在父文档正文中。

> 若无可用 dev 环境，Step 1 的浏览器隔离验证 + Step 在 Task 2 的 `pnpm build` 已覆盖核心安全与编译正确性；应用内验证可在具备环境时补做。

- [ ] **Step 4: 最终提交（若 Step 3 有微调则提交，否则跳过）**

```bash
cd v7-shop-mall && git add -A && git commit -m "test(mall): 验证 customhtml 沙箱隔离与渲染

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage（对照 spec 各节）：**
- 需求①（粘贴任意 HTML）→ Task 1 `html` textarea 属性 ✅
- 需求②（前台含 SSR 渲染）→ Task 2 `:srcdoc` 绑定（SSR 输出 iframe，用户码在帧内运行）✅
- 需求③（限定详情页）→ Task 1 `allowedPages: ['product-detail']` ✅
- 需求④（安全隔离 + 文档）→ Task 2 sandbox 配置 + 红线注释；Task 3 浏览器隔离证据；spec 第 7 节安全分析 ✅
- 自动撑高（spec 6.2）→ Task 2 RESIZE_SCRIPT + `onMessage` + `frameHeight` ✅
- 固定高度 + 内部滚动（spec 6.2）→ Task 2 `isFixedHeight`/`frameHeight` ✅
- 编辑器 overlay + 角标 + 空占位（spec 6.2）→ Task 2 模板 + 样式 ✅
- styleSchema 容器样式（spec 6.3）→ Task 1 styleSchema ✅
- 自动注册（spec 6.4）→ 文件命名约定，无需改插件 ✅

**2. Placeholder scan：** 无 TBD/TODO；所有代码步骤含完整代码与确切命令。✅

**3. Type consistency：**
- `props.html` / `props.height` 在 meta（`html`/`height`）与组件（`Props`）命名一致 ✅
- `__v7Resize` 在注入脚本与 `onMessage` 中拼写一致 ✅
- `type: 'customhtml'`（meta）== 文件名 `CustomHtml.vue` 小写（注册推导）✅
- `isInEditor` 注入键与 CanvasNode/产品页的 `provide` 键一致 ✅
