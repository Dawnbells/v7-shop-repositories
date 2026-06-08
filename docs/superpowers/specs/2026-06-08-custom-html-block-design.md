# 自定义 HTML 块（沙箱 iframe）设计文档

- **日期**：2026-06-08
- **项目**：`v7-shop-mall`（Nuxt 4 storefront）
- **状态**：已评审，待实现
- **适用页面**：商品详情页（`product-detail`）

## 1. 背景与目标

商家需要在商品详情页中插入一段**自定义 HTML**：在区块的属性面板里粘贴 HTML，前台按粘贴的代码渲染显示。

`v7-shop-mall` 的 CMS 区块系统（`app/components/blocks/`）已具备这种"可视化搭建 + 区块渲染"的能力，区块由商家（后台 `v7-shop-admin` 中嵌入的 `/builder` iframe）编辑，最终在 storefront 通过 SSR 渲染给终端顾客。

**核心目标**：以**不破坏商城安全边界**的方式，提供"粘贴任意 HTML 并在前台显示"的能力。用户明确要求"检查安全问题"，因此安全性是本设计的一等约束。

## 2. 需求

1. 新增一个区块，商家可在属性面板粘贴任意 HTML 字符串。
2. 前台（含 SSR）按该 HTML 渲染显示。
3. 限定在商品详情页可用（`allowedPages: ['product-detail']`）。
4. 充分隔离/控制安全风险，并在文档中说明威胁模型、已消除风险、残余风险与处置。

## 3. 现状与约束（已核对代码）

- **区块自动注册**：
  - 组件：`app/plugins/blocks.ts` 用 `import.meta.glob('~/components/blocks/**/*.vue', { eager: true })` 扫描，`type` 由**文件名小写**推导（`extractBlockType`，正则 `blocks/(?:[\w-]+/)?(\w+)\.vue$`）。即 `CustomHtml.vue` → `type = 'customhtml'`。
  - 元数据：`app/plugins/blocks-meta.client.ts` 用 glob 扫描 `**/*.meta.ts`（**仅客户端**注册，供 builder 面板使用）。
  - **结论**：新增区块只需放入两个文件，无需改动任何现有文件。
- **两处渲染管线**：
  - storefront：`app/components/renderer/NodeRenderer.vue`，SSR 完整渲染，`<component :is> v-bind`。
  - builder：`app/components/builder/CanvasNode.vue`，同一批组件在 admin iframe 内**实时渲染**。
  - **含义**：区块组件的渲染方式同时影响 storefront 与 admin builder 两个上下文。
- **既有 `v-html` 现状**：`Text.vue`、`ArticleDetail.vue`、`ProductIntroduction.vue`、产品页 fallback 均使用**未净化的 `v-html`**；`package.json` **无任何 HTML 净化库**（无 DOMPurify / sanitize-html）。
- **既有"注入自定义代码"先例**：`app/composables/useEmbedPixel.ts` 已支持商家粘贴 HTML 像素代码并注入 `<head>` 的 `<script>`。说明平台**已将 website-admin 视为可注入脚本的可信角色**；但本块面向"页面内容"而非全站 head 注入，并选择更强的隔离。
- **编辑器上下文判定**：页面/画布通过 `provide('isInEditor', ref(true|false))` 注入，组件可 `inject` 得知是否在 builder 内。
- **属性编辑器**：`PropertyField.vue` 对 `type: 'textarea'` 渲染多行 `<textarea>`，适合粘贴原始 HTML 代码。
- **SSR 与 meta 的关系**：meta 仅在客户端注册，`NodeRenderer` 在 SSR 时拿不到 `meta.defaultStyle`/`defaultProps`，因此组件须用 `withDefaults` 自带默认值以保证 SSR 正确（与 `Text.vue` 一致）。

## 4. 方案概述

新增 **`自定义HTML`** 区块，前台用 **沙箱 `<iframe srcdoc>`**（**不带 `allow-same-origin`**）渲染商家粘贴的 HTML。

**为什么用沙箱 iframe 而不是 `v-html`**：
- `v-html` 在 **SSR** 下会把用户 HTML 直接拼入服务端文档，**内联 `<script>` 会在商城源上执行** → 面向所有访客的**存储型 XSS**；同一组件在 **builder iframe** 内执行 → 危及后台编辑者的 admin 会话。
- 沙箱 `<iframe srcdoc>`（无 `allow-same-origin`）让用户代码运行在**独立的不透明源**中：无法访问商城的 Cookie / localStorage / DOM / 内部 API，无法触达 `window.parent`，在 builder 内也无法攻击 admin 会话。脚本仍可运行（满足嵌入/统计需求），但被隔离在沙箱内。**用户代码不进入父文档**，因此 SSR 内联脚本在商城源执行的风险被消除。

## 5. 备选方案与权衡

评审中比较了四种渲染策略，已选 **A. 沙箱 iframe**：

| 方案 | 说明 | 取舍 |
|------|------|------|
| **A. 沙箱 iframe（已选）** | `<iframe sandbox srcdoc>`，无 `allow-same-origin` | 隔离最强、仍允许脚本/嵌入；需处理自动撑高；部分需同源的第三方嵌入受限 |
| B. 原样 `v-html` | 与现有区块一致 | 功能最全、与现状一致；但任何编辑者都能在商城源执行 JS（设计上的存储型 XSS），仅当编辑者完全可信才可接受 |
| C. DOMPurify 净化 | 剥离 script/事件处理器/危险属性 | 最安全，但无法嵌入第三方脚本；且"自定义富文本"已有 `Text` 块覆盖 |
| D. 混合模式开关 | 默认净化 + 可选沙箱嵌入 | 最灵活但工作量最大，本期 YAGNI |

## 6. 详细设计

### 6.1 文件清单（2 个新文件，零改动现有文件）

| 文件 | 作用 |
|------|------|
| `v7-shop-mall/app/components/blocks/basic/CustomHtml.vue` | 组件本体，`type` 自动推导为 `customhtml` |
| `v7-shop-mall/app/components/blocks/basic/CustomHtml.meta.ts` | 元数据，`type: 'customhtml'`（必须与文件名一致） |

### 6.2 组件 `CustomHtml.vue`

**Props**（`withDefaults` 自带默认值，保证 SSR 无 meta 也正确）：

| Prop | 类型 | 默认 | 说明 |
|------|------|------|------|
| `html` | `string` | `''` | 粘贴的 HTML 代码 |
| `height` | `string` | `'auto'` | `'auto'`=按内容自动撑高；填 `'400px'` 等=固定高度+内部滚动 |

**srcdoc 构造**（`computed`）：拼接顺序为
1. `<meta charset="utf-8">` + `<meta name="viewport" content="width=device-width, initial-scale=1">`
2. body 样式重置（`margin:0`）
3. **用户 HTML**（`props.html`）
4. 一段**受信任的自动撑高脚本**（固定内容，非用户可控）

通过 `:srcdoc="srcdocContent"` 绑定，Vue 会对属性值做 HTML 属性转义，用户**无法从 srcdoc 属性逃逸**到父文档；用户代码只能影响自身 iframe 文档。

**sandbox 令牌**（写死为安全集合，**不开放给用户配置**，避免踩坑）：

```
allow-scripts allow-popups allow-forms allow-popups-to-escape-sandbox allow-presentation
```

- **绝不包含 `allow-same-origin`**：在 srcdoc 上与 `allow-scripts` 同时存在会使沙箱形同虚设（iframe 将与父文档同源，可读取商城 Cookie、调用同源 API、移除自身 sandbox 后重载）。这是**最关键红线**，代码中以注释固定，并在 review 检查清单中明确。

**自动撑高**：
- 注入的撑高脚本用 `ResizeObserver` 观察 `document.body`，通过 `postMessage({ __v7Resize: <height> }, '*')` 上报高度。
- 父组件监听 `window` 的 `message` 事件，**仅当 `event.source === iframe.contentWindow` 且 payload 为合法数字**时才更新高度（不信任任意消息内容/来源）。
- 每个实例各自持有 iframe ref 与监听器，多实例互不串扰。
- SSR 阶段无客户端 JS，iframe 以初始 `min-height`（来自 `height` 或一个小默认值）渲染，hydration 后撑高脚本生效，减少跳动。
- 当 `height` 为固定值时，禁用自动撑高，iframe 固定高度 + 内部滚动。

**编辑器内交互**：
- `inject('isInEditor')` 判断是否在 builder。
- builder 内 iframe 会吞掉指针事件导致无法选中/拖拽区块，故叠加一层透明 overlay（`position:absolute; inset:0; pointer-events:auto`，仅编辑态启用）让区块仍可被选中/拖拽，并显示「HTML」角标。

**空状态**：
- `html` 为空：前台不渲染任何内容；编辑器内渲染虚线占位「请在右侧粘贴 HTML 代码」。

**样式**：iframe 外层容器宽度默认 `100%`、`border:0`、`display:block`；styleSchema 中的边框/圆角/背景/边距作用于外层容器。

### 6.3 元数据 `CustomHtml.meta.ts`

- `type: 'customhtml'`、`name: '自定义HTML'`、`icon`（如 `i-carbon-code`）、`category: 'basic'`
- `description`：包含安全提示（"内容在隔离沙箱中运行"）
- `allowedPages: ['product-detail']`
- `propsSchema`：
  - `html`：`type: 'textarea'`，label「HTML 代码」，placeholder/description 带安全提示
  - `height`：`type: 'text'`，默认 `'auto'`，placeholder `auto 或 400px`
- `styleSchema`：宽度、外边距（上/下）、内边距、边框、圆角、背景（参照 `Text.meta.ts` 常规项）
- `defaultProps: { html: '', height: 'auto' }`
- `isContainer: false`
- `tags: ['html', '自定义', '代码', '嵌入', 'embed', 'customhtml']`

### 6.4 注册机制

无需手动注册：`blocks.ts` / `blocks-meta.client.ts` 的 glob 会自动拾取两个新文件。仅需保证文件名 `CustomHtml.vue` 与 meta 的 `type: 'customhtml'` 对应（文件名小写 == type）。

## 7. 🔒 安全分析

**威胁模型**：半可信的商家编辑者（website-admin / builder 用户）→ 终端顾客 + 后台运营者（builder 中可能预览商家页面的更高权限者）。

**已消除（靠 iframe 沙箱，无 `allow-same-origin`）**：
- ✅ 商城源的存储型 XSS：Cookie/会话窃取、以访客身份调用内部 Nitro API、DOM 数据外泄 —— 独立不透明源隔离。
- ✅ builder/admin 上下文执行：同组件同沙箱，编辑/预览时 admin 会话受保护。
- ✅ SSR 内联脚本在商城源执行：用户代码只在 iframe 内运行，不进入父文档。

**残余风险与处置**：
- ⚠️ iframe 内的钓鱼/伪造 UI、`allow-popups` 开新窗、`allow-forms` 表单 —— 商家半可信前提下接受；可按需移除 `allow-popups`/`allow-forms` 收紧。
- ⚠️ 浏览器内挖矿/重 JS（沙箱不限制 CPU）—— 已知接受；可选项：在 srcdoc 注入限制性 CSP `<meta>`（会影响部分合法嵌入）。
- ⚠️ **需同源的第三方嵌入**（部分 YouTube/地图等，依赖嵌套帧的 `allow-same-origin`）在严格沙箱下可能受限 —— 强隔离的代价；如需完整嵌入保真，未来可用"独立沙箱子域"方案（额外基建，本期不做）。
- ⚠️ 存储授权 —— 假设主题保存接口已限定 website-admin 权限（后端职责，本块不改变该边界；如未限定属既有问题）。

**红线（实现时必须遵守）**：
- sandbox **不得**包含 `allow-same-origin`。
- 用户 HTML **只能**进入 iframe 的 `srcdoc`，**绝不**经 `v-html`/`innerHTML` 进入父文档。

**可选纵深防御（留待选择，非本期必需）**：
- 对 `html` 长度做上限校验，避免滥用/体积膨胀。
- iframe 加 `referrerpolicy="no-referrer"`，避免向嵌入资源泄漏商城 URL。
- storefront 响应级 CSP（`frame-src`/`child-src`）作为额外层（属更大范围改动）。
- **不建议**在保存时做 sanitize —— 会剥掉本功能存在意义的脚本。

## 8. 验证方案

- **隔离生效**：详情页 builder 拖入块，粘贴 `<script>alert(document.cookie)</script>` → 弹窗中 cookie 为空（证明独立源隔离），且 admin/builder 不受影响。
- **正常渲染**：粘贴图文/样式 HTML → 前台 SSR 正确显示，自动撑高无明显跳动。
- **固定高度**：`height: '400px'` → iframe 固定高度、内部滚动正常。
- **空内容**：前台不渲染；编辑器显示占位。
- **多实例**：同页两个块各自撑高、互不影响。
- **构建**：`pnpm build`（mall）通过。

## 9. 范围外 / 已知限制 / 未来增强

- **范围外**：现有 `Text` / `ArticleDetail` / `ProductIntroduction` 的裸 `v-html` 属既有同信任级问题，不在本次改动内（可作为独立后续项评估）。
- **已知限制**：依赖嵌套帧 `allow-same-origin` 的第三方嵌入可能不可用（见安全分析）。
- **未来增强**：独立沙箱子域以提升嵌入保真度；按需的 CSP/长度校验；"是否允许脚本"开关（关闭时改用固定高度）。
