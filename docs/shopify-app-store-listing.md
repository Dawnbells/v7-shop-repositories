# Shopify 应用商店上架清单（订单同步）

> 工作稿。18 项检查按 Dev Dashboard 的分组逐项列出：官方要求、我们的草案、还缺什么。
> 官方依据：[App Store 要求清单](https://shopify.dev/docs/apps/launch/app-requirements-checklist)、[图片规范 4.4.4 / 4.4.5](https://shopify.dev/changelog/posts/clearer-standards-for-app-listing-images)。

## 0. 先决条件：代码层面的差距（上架前必须补）

当前实现（`ShopifyTokenService` 用 `grant_type=client_credentials` 换 token，后台轮询 REST `/admin/api/2026-07/orders.json`）只适用于**与店铺同属一个 Shopify 组织**的 Dev Dashboard 应用。公开上架后商家的店铺不在我们组织内，这条链路拿不到 token。

| 项 | 现状 | 上架要求 |
| --- | --- | --- |
| 安装 / 授权 | 商家手填 Client ID / Secret | OAuth 安装流程（authorization code grant；若做嵌入式则 token exchange + App Bridge），安装页第一步就是 OAuth |
| 合规 webhook | 无 | 必须订阅并处理 `customers/data_request`、`customers/redact`、`shop/redact`；HMAC 校验失败返回 401，正常返回 2xx |
| 隐私政策 | 无 | 独立页面 URL，必填，返回 200 |
| 受保护客户数据 | 组织内自用默认可读 | 公开应用需在 Dev Dashboard 申请 Protected customer data access（姓名/地址/电话/邮箱），并写明用途 |
| 计费 | 无 | 免费则标 Free；收费必须走 Shopify Billing API |
| 卸载处理 | 无 | 建议订阅 `app/uninstalled`，自动停用店铺并清除 token |

**替代方案**：如果只是想让组织外的客户店铺也能用，Shopify 的「custom distribution」（一个应用对应一家店铺，安装链接分发，不进应用商店、不用审核、不用填这 18 项）也许更贴近需求。需要先确认到底要哪一种。

---

## 1. Basic app information

### App name（≤30 字符）

草案：`VantaSite Order Sync`（20 字符）。品牌名前置。待定：以哪个品牌（xyz / ht / VantaSite）上架。

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
Connect your Shopify store to VantaSite. New orders are picked up about every minute and land in your order review list, where your team checks addresses, matches SKUs and releases orders for fulfillment. Sync past orders for any date range in the background and follow progress in the task center. Product metafields can fill in local product names, SKU codes and order owners automatically. Record amounts in shop currency or presentment currency. Pause or remove the connection at any time.
```

不要出现「最快」「永不」「保证」之类绝对化或比较性措辞，也不要写数据性承诺。

### Features（3–5 条，每条 ≤80 字符）

```
Near real-time sync of new orders into the VantaSite review list
Historical order sync by date range, run in the background
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
- 建议顺序：
  1. 第三方商城列表，Shopify 店铺显示「已连接」
  2. 添加 Shopify 店铺表单（上架后应替换为 OAuth 安装页）
  3. 订单同步对话框：选时间范围、显示订单数量
  4. 任务中心里的同步进度
  5. 审单列表按「订单平台 = SHOPIFY」筛选
  6. 订单详情（含 SKU 匹配结果）
- 注意：后台界面是中文，而上架语言是英文。审核员看不懂截图内容可能被打回，建议截英文界面（若后台有 i18n）或在图上加英文说明条。

### Support

| 字段 | 填什么 | 状态 |
| --- | --- | --- |
| Support email | 商家联系用邮箱 | 需要你提供 |
| Support URL | 帮助中心或文档站入口 | 可用 v7-shop-docs 的 Shopify 页面 |
| Privacy policy URL | 独立隐私政策页，必填，HTTP 200 | 需要新建页面 |

### Resources（可选但建议填）

- Documentation URL：v7-shop-docs 的 `/third-party-order-sync/shopify` 页面（需要有英文版）。
- FAQ URL：可以用 `shopify-order-sync-guide.md` 的常见问题部分做成英文页。
- Changelog、Support portal：可留空。
- Demo store：开发店铺链接，可选。

---

## 3. Pricing details

### Pricing plans

- 免费：选 Recurring charge，加一个 plan 标记 Free，价格 0。
- 收费：必须接 Shopify Billing API，商家能自助升降级，账单出现在店铺的 charge history。
- 企业定制价写在「Description of additional charges」。
- **待定**：免费还是收费。

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
- 内容：从安装（OAuth 授权）→ 店铺显示已连接 → 在 Shopify 下测试单 → 审单列表出现订单 → 历史订单同步 → 暂停/删除，完整走一遍，60–120 秒即可。
- **待录制**：要等 OAuth 安装流程做完再录。

### Testing instructions（草案，英文，每步写预期结果）

```
1. Install the app on the test store from the install link. Approve the requested scopes (read_orders, read_products, read_all_orders).
   Expected: you are redirected to VantaSite and the store appears under "Third-party stores" with status "Connected".
2. Log in to VantaSite (credentials in the test account section).
3. In the test store admin, create a test order.
   Expected: within about 1 minute the order appears in VantaSite → Order review, filter "Platform = SHOPIFY".
4. In Third-party stores, click "Sync orders" on the test store, pick the last 30 days, confirm.
   Expected: a task appears in the task center and completes; past orders appear in Order review.
5. Toggle the store status off, then on.
   Expected: sync pauses and resumes; no duplicate orders are created.
6. Uninstall the app from the test store.
   Expected: the store status in VantaSite changes to "Disconnected".
```

---

## 需要你拍板 / 提供的清单

1. 上架形式：公开 App Store，还是 custom distribution。
2. 品牌与应用名。
3. 免费还是收费。
4. App icon、Feature media 设计稿。
5. 三个邮箱：support、merchant review、app submission。
6. 隐私政策页面的域名（放在哪个站点）。
7. 测试环境地址与后台账号。
8. 后台是否有英文界面（影响截图与录屏）。
