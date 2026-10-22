# PRD：Shopify 公开应用（App Store 上架版订单同步）

- slug：`shopify-public-app`
- 规模：full
- 文档目录：`docs/features/shopify-public-app/`（本仓库功能文档默认目录；状态文件 `STATUS.md` 只通过状态脚本改写）
- 关联文档：[上架清单工作稿](../../shopify-app-store-listing.md)、[现有同步教程](../../shopify-order-sync-guide.md)、文档站 `v7-shop-docs/docs/third-party-order-sync/shopify.mdx`
- 访谈日期：2026-10-22

---

## 1. 项目基线

| 项目 | 技术栈 | 与本功能的关系 | 验证命令（在各自目录执行） |
| --- | --- | --- | --- |
| `v7-shop-services` | Java 17 + Spring Boot 3.3，Gradle 多模块 | 主战场：OAuth、webhook、GraphQL 同步、合规处理、访问日志 | `./gradlew build`；`./gradlew :v7-shop-admin:test` |
| `v7-shop-admin` | Vue 3 + Vite 6 + Element Plus（pnpm） | 第三方商城表单改为「连接」流程、绑定路由、同步对话框限制 | `pnpm vue-tsc`；`pnpm lint:eslint`；`pnpm build` |
| `v7-shop-docs` | Docusaurus 3 | 开 `en` 语言，放隐私政策与英文安装指南 | `pnpm build` |
| `v7-shop-mall` | Nuxt 4 | 不涉及 | — |

现有实现要点（研发与架构直接引用）：

- 第三方商城实体 `ThirdPartyWebsite`（表 `t_third_party_websites`）：`handle` 与 `token` 全局唯一；`websiteType` 枚举 `V7_SHOP / SHOPLINE / XYZ / SHOPIFY`；`authStatus` 枚举 `INIT / AUTHED / ERROR / FROZEN`；已有 `clientId / clientSecret / tokenExpiresAt` 字段。
- Shopify 相关类位于 `v7-shop-admin` 模块 `cn.v7soft.admin`：`ShopifyWebsiteController`（`/third-party-website/shopify/doEdit`）、`ShopifyTokenService`（client_credentials 换 token，24 小时，临期刷新、并发锁）、`ShopifyOrderSyncService`（REST `orders.json`、`orders/count.json`、商品元字段、取消检查）、`ShopifyOrderSyncBot` + `ShopifyOrderSyncExecutor`（约每分钟轮询）、`ShopifyWebsiteStore`、`ShopifyHost`（handle 校验）。
- 认证：sa-token 注解式鉴权（`@SaCheckLogin` / `StpUtil.checkPermission`），无注解的端点即公开端点。租户识别在 `CompanyTenantInterceptor`：按请求 Origin 的顶级域名找公司；无 Origin 的服务端回调必须加入其路径白名单（现有白名单示例：`/shopline/`、`/temporary-order/sync`）。
- 登录按「手机号 + 当前租户」查用户（`findByTelephoneAndTenantId`），手机号跨租户不唯一，因此**不能在非后台域名上登录**。
- 订单归属人默认为添加店铺的人（`fillOwner()`），SKU 匹配、元字段补全、支付状态映射规则见现有教程，本功能全部保留。
- 后台 i18n 仅覆盖框架词条，业务页面未接 `$t`；文档站只有 `zh-Hans`。
- 品牌：白标域名 `htmallcms.com`（ht）、`xyzdwd.com`（xyz），对外品牌名 VantaSite。

---

## 2. 背景与目标

### 背景

现有 Shopify 订单同步靠「商家在 Dev Dashboard 建应用、复制 Client ID/Secret 填到后台」接入，token 由 client_credentials 换取。这条链路只对**与店铺同属一个 Shopify 组织**的应用有效，组织外商家的店铺无法接入。要服务外部商家，必须以公开应用的身份上架 Shopify App Store。

调研（2026-10-22，shopify.dev）给出的硬约束：

1. 新公开应用必须**只用 GraphQL Admin API**（要求 2.2.4），REST 已是 legacy。
2. 新公开应用必须使用**会过期的 offline access token**（1 小时）+ refresh token（90 天）。
3. 非嵌入式公开应用仍可上架，用 authorization code grant；App Bridge 条款不适用。
4. 订单中的收货人姓名/地址/电话/邮箱属于 **Protected customer data Level 2**，需申请审批并满足数据保护措施。
5. 必须订阅并正确处理 `customers/data_request`、`customers/redact`、`shop/redact` 三个合规 webhook。
6. `read_all_orders`（60 天以前订单）需单独申请。
7. 要求 2.3.1：安装应从 Shopify 自有入口发起。
8. 必须提供独立隐私政策页 URL；有收费必须走 Shopify 计费。

### 目标

- 任何 Shopify 商家（已持有 VantaSite 后台账号）能从 App Store 或后台发起安装，授权后店铺出现在「第三方商城」并开始同步订单，不再需要 Client ID/Secret。
- 同步链路满足公开应用审核的技术与合规要求，可提交审核。
- 现有凭证模式的店铺不受影响。

### 成功标准

- 从 App Store 安装到后台显示「已连接」，商家操作不超过 3 步（安装 → 填后台地址 → 登录）。
- 新订单经 webhook 到达审单列表的时间常态在 1 分钟内；webhook 丢失的订单由对账任务在 15 分钟内补上。
- 合规 webhook 与 `app/uninstalled` 全部正确响应（HMAC 失败 401、成功 2xx），动作 30 天内完成。
- Shopify 审核所需的技术项（OAuth、合规 webhook、隐私政策、GraphQL、expiring token）全部就绪。

---

## 3. 用户与场景

| 用户 | 场景 |
| --- | --- |
| 外部商家的运营（持有 VantaSite 后台账号） | 在 Shopify App Store 搜到 VantaSite 安装；或在后台「第三方商城」里填 handle 发起连接；之后在审单列表处理订单 |
| 自家组织内运营 | 继续用凭证模式接入组织内店铺；或改用公开应用 |
| 店铺归属运营 | 收到 `customers/data_request` 待办后，导出该客户订单信息交给商家 |
| Shopify 审核员 | 从 listing 安装到测试店铺，登录我们提供的测试后台账号，按测试说明验证同步、暂停、卸载 |
| 运维 | 在 Dev Dashboard 配置应用（URL、回调、scope、webhook）；维护中立域名与证书 |

---

## 4. 范围与非范围

### 范围

1. **OAuth 安装与绑定**：authorization code grant、expiring offline token + refresh、两条安装路径、一次性安装票据、绑定到当前登录运营。
2. **接入方式并存**：店铺增加「接入方式」（凭证模式 / 公开应用），两种模式共用同步代码。
3. **GraphQL 重写**：订单拉取、订单计数、商品元字段、取消状态全部改为 GraphQL Admin API，两种接入方式都走 GraphQL。
4. **订单 webhook**：`orders/create`、`orders/updated`、`orders/cancelled` 实时入库；低频 GraphQL 对账任务兜底；现有分页取消检查由 `orders/cancelled` + 对账替代。
5. **卸载与合规 webhook**：`app/uninstalled`、`shop/redact`、`customers/redact`、`customers/data_request`。
6. **受保护客户数据最小合规**：收货人信息访问日志、保留期与到期脱敏、隐私政策声明。
7. **后台改动**：添加 Shopify 店铺表单改为「连接」流程；新增绑定路由；列表显示接入方式与「已断开」状态；订单同步对话框在无 `read_all_orders` 时限制 60 天。
8. **落地页**：中立域名上的极简中英双语页：填写/选择后台地址后跳转；以及授权结果页（成功/失败）。
9. **文档站**：开 `en` 语言；新增英文隐私政策页、英文 Shopify 安装指南。
10. **审核资料配套**：测试说明、截图英文说明条的内容来源（按上架清单工作稿），不含设计稿制作。

### 非范围

- Shopify Billing API / 任何应用内收费（应用免费）。
- 嵌入式（App Bridge）体验。
- 自助注册新租户。
- `read_all_orders` 申请与 60 天以外历史同步（上架后二期）。
- 应用层字段加密（Level 2 的「静态加密」依赖基础设施磁盘加密，由运维确认）。
- 后台业务页面英文化。
- 自动向商家回传 `customers/data_request` 的数据包。
- App icon、Feature media、截图制作、录屏。
- Shopline 及其他平台的任何改动。

---

## 5. 业务流程

### 5.1 路径 1：从后台发起安装（主入口）

1. 运营在「第三方商城 → 添加」选 SHOPIFY，接入方式选「公开应用」，填店铺名称与 handle，点「连接 Shopify」。
2. 后端校验 handle（`ShopifyHost.normalize`）与占用情况，签发一次性安装票据（绑定：租户、当前用户、handle、nonce、过期时间 10 分钟），生成 Shopify 授权 URL（`https://{handle}.myshopify.com/admin/oauth/authorize?client_id&scope&redirect_uri&state=票据`），返回给前端整页跳转。
3. 商家在 Shopify 同意授权，Shopify 302 到中立域名的回调端点，带 `code`、`hmac`、`shop`、`state`。
4. 回调端点：校验 HMAC；用 `state` 找回票据（未过期、未使用、`shop` 与票据 handle 一致）；用 `code` 换取 expiring offline token（`expiring=1`）与 refresh token；创建或更新店铺记录（接入方式=公开应用，`authStatus=AUTHED`，归属=票据中的用户，租户=票据中的租户）；注册订单与合规 webhook（若用 TOML 声明则跳过）；标记票据已使用；302 回该租户后台的第三方商城列表（地址来自票据）并带成功提示。
5. 列表显示「已连接」，自动同步开始。

### 5.2 路径 2：从 App Store 发起安装

1. 商家在 App Store 点 Install，Shopify 先走授权，再把商家带到我们的 App URL（中立域名落地页），带 `shop`、`hmac` 等参数。（若 Shopify 先打开 App URL 再授权，则落地页在校验 HMAC 后先把商家重定向到授权 URL，`state` 为未绑定租户的预票据，回调后再回落地页；两种时序都要支持。）
2. 落地页校验 HMAC，签发「待绑定票据」（绑定：shop、token 已换取并暂存于待绑定记录、nonce、过期 30 分钟），展示中英双语说明：「请输入你的 VantaSite 后台地址」，提供输入框（支持 `xxx.xyzdwd.com` 这类完整域名）与常用品牌域名下拉。
3. 提交后跳转到 `https://{后台域名}/#/third-party-website/shopify-bind?ticket=...`（具体路由由架构定）。
4. 后台若未登录则先走正常登录（租户由域名识别），登录后进入绑定页，自动调用绑定接口携带票据。
5. 绑定接口：校验票据；检查 handle 未被其他租户占用；把待绑定记录转为正式店铺记录（归属=当前登录用户，租户=当前租户）；注册 webhook；回到列表并提示成功。
6. 待绑定记录 30 分钟未绑定则作废；商家可在 Shopify 后台点开应用重新进入落地页。

### 5.3 新订单到达

1. Shopify 推送 `orders/create` 到中立域名 webhook 端点，头部带 `X-Shopify-Hmac-Sha256`、`X-Shopify-Shop-Domain`、`X-Shopify-Topic`、`X-Shopify-Webhook-Id`。
2. 端点校验 HMAC（失败 401），按 shop domain 找店铺并设置租户上下文，以 `Webhook-Id` 去重，入队后立即返回 200。
3. 异步处理：按现有映射规则（商品、金额、币种模式、元字段补全、SKU 匹配、支付状态、取消状态、地址省份反查）写入审单列表；已存在则更新。
4. `orders/updated` 与 `orders/cancelled` 同样处理，覆盖现有「取消状态检查」。
5. 对账任务：每 15 分钟按店铺用 GraphQL 拉取 `updated_at >= 上次对账时间 - 冗余` 的订单，补写遗漏；店铺记录保存对账游标。

### 5.4 手动历史同步

与现有流程一致（选时间范围 → 显示数量 → 提交任务 → 任务中心看进度），改为 GraphQL 分页。无 `read_all_orders` 时前端时间范围控件只允许最近 60 天，并提示原因。

### 5.5 卸载与合规

| 事件 | 处理 |
| --- | --- |
| `app/uninstalled` | 校验后店铺 `authStatus` 置为「已断开」（新枚举值），清除 access/refresh token，`status` 置 INVALID；已同步订单不动。列表显示「已断开」，操作列只留「删除」。 |
| `shop/redact`（卸载后 48h） | 异步任务：该店铺所有已同步订单（审单表与正式订单表）的收货人姓名、电话、邮箱、地址明细字段脱敏（替换为固定占位，保留国家/省份用于统计）；删除店铺记录中的凭证字段；写合规处理记录。 |
| `customers/redact` | 按 payload 中的 `customer.id`、`customer.email`、`orders_to_redact` 找到该店铺下该客户的订单，脱敏同上字段；写合规处理记录。 |
| `customers/data_request` | 写合规处理记录，向店铺归属运营发站内通知（复用现有通知机制），通知内容含店铺、客户标识、涉及订单号、截止日期（收到后 30 天）；运营在后台导出后人工交付。 |

所有合规动作 30 天内完成；处理记录可在后台查询（最小实现：日志表 + 简单列表页或导出）。

---

## 6. 业务规则

### 接入方式

- 店铺记录新增字段「接入方式」：`CREDENTIALS`（凭证模式，现有）/ `PUBLIC_APP`（公开应用）。存量 Shopify 店铺默认 `CREDENTIALS`。
- 凭证模式表单与校验逻辑保持不变；公开应用模式下 Client ID/Secret 字段隐藏，编辑只允许改店铺名称、币种模式。
- 同一 handle 全局只允许一条记录（含 Shopline），与现有约束一致。公开应用安装到已被其他租户占用的 handle 时绑定失败并提示「该店铺已绑定到其他账号」。
- 同一店铺重新安装（卸载后再装）：命中已断开的记录则恢复该记录（保留历史同步游标与订单），归属改为本次绑定人。

### 令牌

- 公开应用模式：access token 1 小时、refresh token 90 天；临期（5 分钟）自动刷新，401 时强制刷新一次；refresh token 过期或被拒则店铺置 `ERROR` 并提示「需重新授权」，列表提供「重新连接」入口（走路径 1）。
- 应用的 Client ID/Secret 为全局配置（环境变量），不落店铺表。
- 凭证模式沿用现有 24 小时 client_credentials 逻辑。

### 权限范围

- 首版 scope：`read_orders`、`read_products`。
- 申请 Protected customer data Level 2：姓名、地址、电话、邮箱，用途写「审单、收货、面单打印」。
- `read_all_orders` 不在首版；店铺记录预留「是否具备全部订单权限」标记，用于前端限制时间范围。

### 订单同步

- 映射规则（商品、金额、币种模式、支付状态、元字段补全、SKU 匹配、归属人、取消、省份反查）与现有一致，全部迁移到 GraphQL 字段。
- webhook 与对账写入同一套幂等逻辑：按店铺 + Shopify 订单 ID 唯一。
- 对账任务只对 `PUBLIC_APP` 且已连接的店铺运行；凭证模式店铺若未配置 webhook，仍由现有每分钟轮询（改为 GraphQL）驱动。
- 自动同步只处理绑定之后创建的订单；绑定前订单走手动同步。

### 受保护客户数据

- 访问日志：对 Shopify 来源订单，记录「用户、时间、动作（查看详情/导出/打印面单）、订单 ID」；日志保留 12 个月。
- 保留期：订单最后更新后 12 个月，到期由定时任务脱敏收货人字段（金额、商品、状态保留）。天数为待决项，默认 12 个月。
- 脱敏规则统一：姓名→`[redacted]`，电话/邮箱→空，地址 1/2/邮编→空，国家/省份/城市保留。

### 安装票据

- 一次性，服务端存储（Redis），含 nonce；路径 1 票据 10 分钟，路径 2 待绑定票据 30 分钟；使用后立即失效；校验 `state`/`shop`/HMAC 任一失败即拒绝并记录。

### 公开端点

- 回调、落地页、所有 webhook 端点：无登录；加入 `CompanyTenantInterceptor` 白名单；租户由票据或 shop domain 解析后手动设置；处理完毕清理上下文。
- webhook 端点 5 秒内返回，正文处理异步。

---

## 7. 边界与异常

| 情况 | 处理 |
| --- | --- |
| 回调 HMAC 校验失败 / `state` 不存在或过期 / `shop` 与票据不一致 | 返回 400 的授权失败页（双语），提示重新发起；不写任何记录 |
| 换取 token 失败（Shopify 返回 4xx/5xx） | 授权失败页显示原因码；路径 1 回后台列表带失败提示 |
| handle 已被其他租户占用 | 绑定失败页：「该店铺已绑定到其他账号，请联系对方解绑」 |
| 商家未同意全部 scope | Shopify 不会回调成功；按失败处理 |
| 落地页收到无效或缺失 HMAC | 显示「请从 Shopify 后台打开本应用」并给出 App Store 链接 |
| 后台地址输错（不是我们的域名） | 落地页校验域名后缀属于已知品牌域名列表或已登记公司域名，否则提示 |
| 待绑定票据过期 | 绑定页提示过期，引导商家回 Shopify 后台点开应用重新进入 |
| 同一 webhook 重复投递 | 按 `Webhook-Id` 去重，重复直接 200 |
| webhook 的 shop 找不到店铺（已删除/从未绑定） | 返回 200 并记录；合规 webhook 同样 200（Shopify 要求） |
| webhook 处理时店铺处于暂停（status INVALID） | 订单 webhook 丢弃但记录；恢复后由对账任务补齐 |
| refresh token 失效 | 店铺 `ERROR` + 提示重新授权，停止同步；不自动删除 |
| 店铺冻结（402/423） | 沿用现有 FROZEN 处理 |
| GraphQL 限流（throttled） | 按 `extensions.cost.throttleStatus` 退避重试，沿用现有 resilience4j 限流/重试配置 |
| `shop/redact` 到达但店铺从未卸载（异常时序） | 仍执行脱敏并断开，记录异常 |
| 凭证模式店铺与公开应用同一 handle | 以先入为准，后者失败提示 |

---

## 8. 非功能要求

- **安全**：所有 webhook 与回调强制 HMAC；票据含 nonce 且一次性；Client Secret 只在环境变量；token 存储字段不回显；公开端点不泄露租户信息。
- **可用性**：webhook 端点在后端重启窗口外 99.9% 可达；对账任务保证 webhook 丢失时 15 分钟内补齐。
- **性能**：单店铺 webhook 峰值 100 单/分钟不丢；对账单次拉取按 GraphQL 成本预算分页。
- **可观测**：OAuth 成功/失败、webhook 接收/去重/失败、对账补单数、合规动作，均有结构化日志与计数。
- **合规**：隐私政策、访问日志、保留期按第 6 节；Level 2 申请表所列措施可逐项对应。
- **兼容**：存量凭证模式店铺升级后无需任何操作继续工作；REST 改 GraphQL 不改变订单字段语义。
- **配置**：应用 Client ID/Secret、中立域名、回调 URL、品牌域名列表、保留期天数均为配置项；Dev Dashboard 的 App URL、Redirect URLs、scope、webhook 订阅与配置项一致（建议用 `shopify.app.toml` 声明并纳入仓库）。
- **文档**：文档站 en 语言下提供隐私政策与安装指南；测试说明按上架清单工作稿更新为 OAuth 流程。

---

## 9. 验收标准

安装与绑定
- [ ] 路径 1：后台填 handle 点「连接」→ Shopify 授权 → 回到列表显示「已连接」，店铺归属当前登录人。
- [ ] 路径 2：从测试店铺后台/App Store 打开应用 → 落地页填后台地址 → 登录 → 自动绑定 → 列表显示「已连接」。
- [ ] 票据过期、`state` 伪造、HMAC 错误、handle 被占用四种情况均失败且不产生记录。
- [ ] 存量凭证模式店铺升级后无需操作继续同步；表单仍可新增凭证模式店铺。

令牌
- [ ] token 到期前自动刷新，日志可见；refresh token 失效时店铺变 `ERROR` 并可「重新连接」。

订单
- [ ] 在测试店铺下单，1 分钟内审单列表出现该订单，字段与现有 REST 版本一致（商品、金额、币种、支付状态、元字段补全、SKU 匹配、归属人、省份）。
- [ ] 取消订单后审单列表状态随 `orders/cancelled` 更新。
- [ ] 人为丢弃一次 webhook，对账任务 15 分钟内补上，且不产生重复订单。
- [ ] 手动历史同步走 GraphQL，范围限最近 60 天并有提示；重复同步不重复建单。
- [ ] 代码中不再有对 Shopify REST Admin API 的调用。

卸载与合规
- [ ] 卸载应用后店铺显示「已断开」，token 清除，同步停止，订单保留。
- [ ] 模拟 `shop/redact`：该店铺订单收货人字段脱敏，店铺凭证清除，处理记录可查。
- [ ] 模拟 `customers/redact`：仅该客户订单脱敏。
- [ ] 模拟 `customers/data_request`：归属运营收到站内通知，内容含截止日期。
- [ ] 三个合规 webhook 与订单 webhook：HMAC 错误返回 401，正确返回 2xx，响应时间 < 5 秒。

受保护客户数据
- [ ] 查看/导出/打印 Shopify 订单收货人信息产生访问日志。
- [ ] 保留期定时任务能对到期订单脱敏（测试用缩短的保留期）。

对外
- [ ] 文档站 en 语言可访问，隐私政策页返回 200，内容覆盖数据项、用途、保留期、redact 处理、联系方式。
- [ ] 落地页与授权结果页中英双语，手机宽度可用。
- [ ] 上架清单工作稿中的测试说明更新为 OAuth 流程并与实际一致。

---

## 10. 风险

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 要求 2.3.1「从 Shopify 自有入口发起安装」与路径 1 冲突 | 审核打回 | 路径 1 与路径 2 共用回调；被打回时把路径 1 降级为「去 App Store 安装」链接，代码改动仅前端一处 |
| REST → GraphQL 重写范围大，字段语义有差异（金额对象、ID 为 gid、分页为 cursor） | 工期、回归风险 | 以现有单元测试（`ShopifyOrderSyncServiceTest` 等）为基线补齐 GraphQL 版本测试；凭证模式店铺灰度先切 |
| Level 2 审批要求基础设施加密、备份加密等 | 审批不通过 | 运维提前核实 MySQL/备份加密状态；缺项列入待决 |
| 「免费应用但要求付费 VantaSite 账号」被视为平台外计费（1.2.1） | 审核打回 | listing 明示 Free + requires account，应用内无任何价格/付费入口；若仍被拒，再评估 Billing API |
| 非嵌入式允许上架只有社区官方答复，无文档正文 | 政策变动 | 架构上把回调/落地页与后台解耦，后续若需嵌入式仅加 App Bridge 外壳 |
| 中立域名未定 | 阻塞 Dev Dashboard 配置与联调 | 待决项 1；开发期可先用测试域名 |
| webhook 依赖公网可达的后端 | 本地联调困难 | 开发期用隧道工具；对账任务保证最终一致 |
| 票据/待绑定记录涉及跨域跳转 | 浏览器拦截、丢参 | 票据走 URL query，不依赖 cookie |

---

## 11. 待决事项

1. 中立域名（承载回调、webhook、落地页）的具体值与证书归属。
2. Level 2 基础设施事实：MySQL 静态加密、备份加密、测试/生产隔离、事件响应策略是否已有，由运维确认。
3. 收货人信息保留期天数（默认 12 个月）与访问日志保留期（默认 12 个月）。
4. 隐私政策英文正文的法务审阅。
5. Dev Dashboard 中应用归属的组织/Partner 账号；Support / Merchant review / App submission 三个邮箱。
6. 审核用测试环境地址与后台测试账号。
7. 凭证模式店铺是否要求配置 webhook（首版：可选）。
8. 对账周期（默认 15 分钟）与 webhook 异步队列实现（Redis/库表）由架构定。

---

## 访谈决策记录（2026-10-22）

| # | 决策 | 用户选择 |
| --- | --- | --- |
| Q1 | 分发形式 | A 公开上架 App Store |
| Q2 | 安装入口与租户归属 | a 路径 2 要求登录现有账号 |
| Q3 | 宿主域名与品牌 | a 中立域名 + VantaSite |
| Q4 | 计费 | a 免费 |
| Q5 | 后台侧安装入口 | 保留路径 1：后台填 handle 发起，归属当前登录运营 |
| Q6 | 新订单获取 | b webhook 为主 + 对账兜底 |
| Q7 | 权限范围 | a 首版不申请 `read_all_orders` |
| Q8 | Level 2 落实程度 | a 最小合规 |
| Q9 | 卸载与合规数据处理 | a 脱敏保留订单 |
| Q10 | 存量店铺 | a 两种接入方式并存、共用同步代码 |
| Q11 | 隐私政策与资料落点 | b 文档站开 en 语言 |
| Q12 | 后台英文界面 | b 不改界面，截图加英文说明 |
| Q13 | 路径 2 落地页回到租户 | a 落地页填后台地址 + 一次性票据跳后台登录绑定 |
