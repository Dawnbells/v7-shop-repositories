# Shopify 应用商店上架清单（订单同步）

> 工作稿。18 项检查按 Dev Dashboard 的分组逐项列出：官方要求、我们的草案、还缺什么。
> 官方依据：[App Store 要求清单](https://shopify.dev/docs/apps/launch/app-requirements-checklist)、[图片规范 4.4.4 / 4.4.5](https://shopify.dev/changelog/posts/clearer-standards-for-app-listing-images)。

## 0. 先决条件：已实现 / 待办

原实现（`client_credentials` 换 token + 轮询 REST）只适用于与店铺同属一个 Shopify 组织的应用。功能「Shopify 公开应用」（`docs/features/shopify-public-app/`）已补齐公开上架所需的代码与文档；凭证模式保留为「组织内自用」。

### 已实现（本功能）

| 项 | 实现 |
| --- | --- |
| 安装 / 授权 | OAuth 授权码流程，两条入口：后台「第三方商城 → 添加 → SHOPIFY → 公开应用 → 连接 Shopify」；从 App Store / 店铺后台打开 → 落地页填 VantaSite 后台地址 → 登录 → 自动绑定。state 为一次性票据，回调校验 HMAC |
| 访问令牌 | expiring offline token（access 1 小时、refresh 90 天），临期自动刷新，失效后店铺「连接异常」并可「重新连接」 |
| Admin API | 全部改为 GraphQL Admin API（`2026-07`），代码中不再调用 REST |
| 订单同步 | `orders/create`、`orders/updated`、`orders/cancelled` webhook 实时入审单列表，约 15 分钟一次对账兜底；手动历史同步限最近 60 天 |
| 合规 webhook | `customers/data_request`、`customers/redact`、`shop/redact`；HMAC 不符返回 401，正常返回 200；脱敏与 30 天内交付有处理记录 |
| 卸载处理 | `app/uninstalled` → 店铺「已断开」、清除 token、停止同步，已同步订单保留 |
| 受保护客户数据措施 | 订单最后更新后 12 个月自动脱敏收货人信息（可配置）；收货人信息查看/导出访问日志 |
| 隐私政策 | `https://docs.vantasite.com/en/privacy-policy`（文档站中英双语） |
| 应用声明 | `v7-shop-services/shopify.app.toml`（App URL、回调、scope、webhook 订阅） |
| 计费 | 免费，无需接 Billing API |

### 待办（上架前）

| 项 | 说明 |
| --- | --- |
| 受保护客户数据 Level 2 申请 | 在 Dev Dashboard 的 API access requests 申请姓名、地址、电话、邮箱；用途写「审单、收货、面单打印、风控」，措施对照隐私政策第 5–7 节（保留期脱敏、访问日志、权限控制、HTTPS） |
| `read_all_orders` | 二期单独申请；获批前历史同步限 60 天 |
| 邮箱 | support、merchant review、app submission、Emergency developer contact；隐私政策中的 `support@vantasite.com` 为占位 |
| 域名 | 中立域名 `<app-host>`（反向代理到后端 `/shopify-app/**`）与文档站 `docs.vantasite.com` 上线，HTTPS 证书就绪；填入 `shopify.app.toml` 与 `SHOPIFY_APP_HOST` |
| 应用声明落地 | 在 Dev Dashboard 创建公开应用，`client_id` 填入 `shopify.app.toml` 后 `shopify app deploy` |
| 测试账号 | 测试环境后台地址与审核账号（见第 7 节） |
| 素材 | App icon、Feature media、截图、录屏 |

---

## 1. Basic app information

### App name（≤30 字符）

草案：`VantaSite Order Sync`（20 字符）。品牌名前置。品牌已定为 VantaSite。

### App icon

- 1200×1200 px，JPEG 或 PNG，正方形，四周留白。
- 在 Dev Dashboard 上传。
- **需要你提供**：设计稿。

### App category

- 建议：Orders and shipping → 订单管理 / 订单同步类。
- **提交后不能改**，选前确认。
- 选了类别后会多出 Category-specific tags、Languages、App capabilities 三项，也要填。

---

## 2. App store listing content

### App introduction（≤100 字符）

```
Pull new and past Shopify orders into VantaSite for review, SKU matching and fulfillment.
```

### App details（≤500 字符）

```
Connect your Shopify store to VantaSite. New orders arrive in near real time and land in your order review list, where your team checks addresses, matches SKUs and releases orders for fulfillment. Sync orders from the last 60 days in the background and follow progress in the task center. Product metafields can fill in local product names, SKU codes and order owners automatically. Record amounts in shop currency or presentment currency. Pause or remove the connection at any time.
```

不要出现「最快」「永不」「保证」之类绝对化或比较性措辞，也不要写数据性承诺。

### Features（3–5 条，每条 ≤80 字符）

```
Near real-time sync of new orders into the VantaSite review list
Sync orders from the last 60 days by date range, in the background
Auto-fill product names, SKU codes and order owner from metafields
Payment status mapping with shop or presentment currency modes
Pause, resume or remove a store connection in one click
```

### Feature media

- 1 张 1600×900 的主视觉图（16:9），或 2–3 分钟视频。
- 必须展示应用真实界面，不能只是 logo，不能和第 1 张截图相同。
- 图里不要出现价格文字（Shopify 会 OCR 截图）。

### Screenshots

- 3–6 张桌面截图，**精确 1600×900**；Chrome 里把 devicePixelRatio 设为 1 再截，否则高分屏会出 2400×1350。
- 不能带浏览器边框、桌面壁纸；每张展示不同功能或状态。
- 建议顺序（与 OAuth 安装流程一致）：
  1. 第三方商城列表，Shopify 店铺接入方式「公开应用」、授权状态「已连接」
  2. 添加 Shopify 店铺表单：接入方式选「公开应用」，「连接 Shopify」按钮
  3. 落地页「输入你的 VantaSite 后台地址」（从 App Store 安装后进入，中英双语）
  4. 绑定成功页「已连接 {handle}」
  5. 审单列表按「订单平台 = SHOPIFY」筛选，显示 webhook 推送的新订单
  6. 订单同步对话框：选时间范围（最近 60 天）、显示订单数量，任务中心同步进度
- 注意：后台界面是中文，而上架语言是英文。审核员看不懂截图内容可能被打回，建议截英文界面（若后台有 i18n）或在图上加英文说明条。

### Support

| 字段 | 填什么 | 状态 |
| --- | --- | --- |
| Support email | 商家联系用邮箱 | 需要你提供 |
| Support URL | 帮助中心或文档站入口 | `https://docs.vantasite.com/en/third-party-order-sync/shopify` |
| Privacy policy URL | 独立隐私政策页，必填，HTTP 200 | `https://docs.vantasite.com/en/privacy-policy`（文档站上线后核对返回 200） |

### Resources（可选但建议填）

- Documentation URL：`https://docs.vantasite.com/en/third-party-order-sync/shopify`（英文安装指南，已完成）。
- FAQ URL：英文安装指南的 FAQ 小节 `https://docs.vantasite.com/en/third-party-order-sync/shopify#faq`。
- Changelog、Support portal：可留空。
- Demo store：开发店铺链接，可选。

---

## 3. Pricing details

### Pricing plans

- 免费：选 Recurring charge，加一个 plan 标记 Free，价格 0。
- 收费：必须接 Shopify Billing API，商家能自助升降级，账单出现在店铺的 charge history。
- 企业定制价写在「Description of additional charges」。
- **已定**：免费。

---

## 4. App discovery content

### App card subtitle（第三方资料多说 ≤62 字符，以表单为准）

```
Sync Shopify orders into your VantaSite order review workflow
```

写成价值陈述，不要堆关键词。

### App store search terms（最多 5 个，每个一个完整意思）

```
order sync
order management
order import
ERP integration
fulfillment
```

---

## 5. Install requirements

### Sales Channel requirements

- 本应用只读订单，不依赖 Online Store 等销售渠道，选「无要求」。

---

## 6. Contact information

| 字段 | 用途 | 状态 |
| --- | --- | --- |
| Merchant review email | 商家评价通知 | 需要你提供 |
| App submission email | 审核沟通邮箱，审核员会往这里发问题 | 需要你提供，建议专人盯 |

另外 Partner/Dev Dashboard 里的 Emergency developer contact 也要填且保持有效。

---

## 7. App testing information

### Test account

- 审核员需要登录 VantaSite 后台看同步结果，要给一个测试环境的后台账号（有「第三方商城」新增权限、能看审单列表）。
- 账号信息只填在这个表单里，不要写进 listing 正文。
- **需要你提供**：测试环境地址、账号。

### Screencast URL

- YouTube（unlisted）/ Vimeo / Loom，英文或带英文字幕。
- 内容：从 listing 安装（OAuth 授权）→ 落地页填后台地址 → 登录并自动绑定 → 店铺显示已连接 → 在 Shopify 下测试单 → 1 分钟内审单列表出现订单 → 最近 60 天历史同步 → 暂停/恢复 → 卸载后显示已断开，完整走一遍，60–120 秒即可。
- **待录制**：在测试环境部署本功能后录制。

### Testing instructions（草案，英文，每步写预期结果，与 PRD §5 流程一致）

```
The VantaSite back office is in Chinese; English labels are given with the Chinese text in brackets.
Test account credentials and the back-office address are in the test account section.

1. Install the app on the test store from the app listing and approve the requested scopes (read_orders, read_products).
   Expected: you land on the VantaSite page "Enter your VantaSite back-office address".
2. Enter the back-office address from the test account section and continue.
   Expected: you are taken to the VantaSite sign-in page.
3. Sign in with the test account.
   Expected: the store is linked automatically and the page shows "Connected [已连接]".
4. Click "Go to third-party stores [前往第三方商城]".
   Expected: the store is listed with access mode "Public app [公开应用]" and status "Connected [已连接]".
5. In the test store admin, create a test order.
   Expected: within about 1 minute the order appears in Order review [审单] when filtered by "Platform = SHOPIFY [订单平台 = SHOPIFY]".
6. In Third-party stores [第三方商城], click "Sync orders [订单同步]" on the store, pick a range within the last 60 days and confirm.
   Expected: a task appears in the Task center [任务中心] and completes; orders in that range appear in Order review without duplicates.
   Ranges older than 60 days cannot be selected because the app does not request read_all_orders.
7. Turn the store's status switch off, create another test order, then turn the switch back on.
   Expected: no order is synced while paused; after resuming, the order appears within about 15 minutes; no duplicates are created.
8. Uninstall the app from the test store (Settings > Apps).
   Expected: the store status in VantaSite changes to "Disconnected [已断开]"; orders already synced remain.
```

---

## 需要你拍板 / 提供的清单

已定：公开 App Store 上架、品牌与应用名（VantaSite / VantaSite Order Sync）、免费、隐私政策放在文档站（`https://docs.vantasite.com/en/privacy-policy`）。

仍需提供：

1. App icon、Feature media 设计稿。
2. 四个邮箱：support、merchant review、app submission、emergency developer contact。
3. 中立域名 `<app-host>` 与文档站域名上线。
4. 测试环境地址与后台账号。
5. 后台是否有英文界面（影响截图与录屏；暂按中文界面 + 英文标注处理）。
