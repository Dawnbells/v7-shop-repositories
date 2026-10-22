# 任务拆分：Shopify 公开应用

- slug：`shopify-public-app`；方案见 `ARCH.md`，需求见 `PRD.md`（同目录）。
- 进度文件：`docs/features/shopify-public-app/progress/<任务ID>.md`（研发子代理维护）。
- 仓库根：`E:\V7Soft\Repositories\v7-shop-repositories`。各子项目独立构建，验证命令在各自目录执行。
- **文件归属是硬约束**：任务只改自己名下的文件；需要别的任务的东西只能依赖 ARCH.md 里的契约。新建文件按下表路径命名。

## 依赖图

```
T1 基础层 ─┬─▶ T2 OAuth 安装与绑定
           ├─▶ T3 GraphQL 订单同步
           ├─▶ T4 webhook / 合规 / 脱敏     （端到端订单 webhook 需 T3 合入后验证）
           └─▶ T7 受保护数据访问日志
T5 后台前端（无依赖，按契约开发）
T6 文档站 / 应用声明 / 上架清单（无依赖）
```

第一批并行：T1、T5、T6。T1 回报后第二批并行：T2、T3、T4、T7。

## 通用验证命令

| 项目 | 命令（在该项目目录） |
| --- | --- |
| 后端编译 | `./gradlew :v7-soft-core:compileJava :v7-shop-dao:compileJava :v7-shop-admin:compileJava :v7-shop-entrance:compileJava`（Windows 用 `gradlew.bat`） |
| 后端 Shopify 相关测试 | `./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.Shopify*" --tests "cn.v7soft.admin.utils.Shopify*" --tests "cn.v7soft.admin.task.executor.Shopify*"` |
| 后端全量测试 | `./gradlew :v7-shop-admin:test` |
| 后台前端 | `pnpm install --frozen-lockfile`，`pnpm vue-tsc`，`pnpm lint:eslint` |
| 文档站 | `pnpm install --frozen-lockfile`，`pnpm build` |

后端测试风格：JUnit 5 + Mockito（`@ExtendWith(MockitoExtension.class)`，mock `RestTemplate`），参考 `ShopifyTokenServiceTest`、`ShopifyOrderSyncServiceTest`。不要求数据库。

---

## T1 基础层：数据模型、配置、GraphQL 客户端、token、票据

依赖：无。并行：T5、T6。

### 修改范围与文件归属

`v7-shop-services`：
- `v7-soft-core/src/main/java/cn/v7soft/core/enums/ServiceResponseEnum.java`：新增 `ERR_SHOPIFY_APP_NOT_CONFIGURED`（消息「Shopify 公开应用未配置」）。
- `v7-shop-dao/.../entities/primary/ThirdPartyWebsite.java`：§3.1 新字段。
- `v7-shop-dao/.../enums/ThirdPartyAuthStatusEnum.java`：加 `DISCONNECTED`。
- 新建 `v7-shop-dao/.../enums/ShopifyAccessMode.java`。
- 新建实体 `ShopifyWebhookEvent`、`ShopifyComplianceRecord`、`ProtectedDataAccessLog`（`v7-shop-dao/.../entities/primary/`）及仓库 `ShopifyWebhookEventRepository`、`ShopifyComplianceRecordRepository`、`ProtectedDataAccessLogRepository`（`v7-shop-dao/.../repositories/primary/`）。仓库需提供：`ShopifyWebhookEventRepository.existsByWebhookId`、`findTop50ByStateAndNextAttemptAtBeforeOrderByReceivedAtAsc`；`ProtectedDataAccessLogRepository.deleteByAccessTimeBefore`。
- `v7-shop-dao/.../entities/primary/TemporaryOrder.java`、`Order.java`：加 `piiRedactedAt`（`pii_redacted_at`）。
- `v7-shop-dao/.../repositories/primary/ThirdPartyWebsiteRepository.java`：新增 `updatePublicAppTokens`、`markDisconnected`、`updateReconcileTime`、`clearCredentials`、`findByStatusAndAuthStatusAndWebsiteTypeAndAccessMode`。
- 新建 `v7-shop-admin/.../config/ShopifyAppProperties.java`。
- 新建 `v7-shop-admin/.../utils/ShopifyHmac.java`。
- 新建 `v7-shop-admin/.../service/impl/ShopifyGraphqlClient.java`。
- 新建 `v7-shop-admin/.../service/impl/ShopifyInstallTicketStore.java`（含 `ShopifyInstallTicket` record 与 `TicketType`，可放同包 `dto/`）。
- `v7-shop-admin/.../service/impl/ShopifyTokenService.java`：公开应用分支、`exchangeAuthorizationCode`、`refreshPublicAppToken`、`ExchangedToken`。
- `v7-shop-admin/.../service/impl/ShopifyWebsiteStore.java`：§5.4 新方法。
- `v7-shop-admin/.../service/IShopifyOrderSyncService.java`：新增 `importWebhookOrder`、`reconcileOrders` 为 **`default` 方法**，默认抛 `UnsupportedOperationException`（T3 覆盖实现；T1 不改 `ShopifyOrderSyncService.java`）。
- `v7-shop-admin/.../service/dto/ThirdPartyWebsiteDto.java`、`controller/resp/ThirdPartyWebsiteResponse.java`：新增字段。
- `v7-shop-admin/.../interceptors/CompanyTenantInterceptor.java`：白名单加 `/shopify-app/`。
- `v7-shop-entrance/src/main/resources/application.yml.example`：§2 配置块。
- 测试：`ShopifyTokenServiceTest.java`（补公开应用分支）、新建 `utils/ShopifyHmacTest.java`、`service/impl/ShopifyGraphqlClientTest.java`、`service/impl/ShopifyInstallTicketStoreTest.java`。

### 输入输出契约
ARCH.md §2、§3、§4、§5.4 全部。`ShopifyGraphqlClient.execute` 的重试与错误转换规则按 §5.4 注释。`ShopifyTokenService.getAccessToken` 对 `accessMode == null` 按 `CREDENTIALS` 处理。

### 验收标准
- [ ] 编译通过；实体启动时可由 hbm2ddl 建表加列（新列全部可空或带默认，不得 NOT NULL 无默认）。
- [ ] `ShopifyHmac.verifyQuery` 对 Shopify 文档示例（`code=0907a61c0c8d55e99db179b68161bc00&shop=some-shop.myshopify.com&state=0.6784241404160823&timestamp=1337178173`，secret `hush`，hmac `700e2dadb827fcc8609e9d5ce208b2e9cdaab9df07390d2cbca10d7c328fc4bf`）返回 true，改任一字节返回 false。
- [ ] `verifyBody` 用 base64 HMAC 校验，常量时间比较。
- [ ] `exchangeAuthorizationCode` 发送 `expiring=1`，解析 `refresh_token`、`refresh_token_expires_in`；`refreshPublicAppToken` 发送 `grant_type=refresh_token`。
- [ ] `getAccessToken`：PUBLIC_APP 临期刷新走 refresh；refresh 4xx → `markWebsiteAuthError(id,"授权已失效，请重新连接")` 并抛 `ERR_TOKEN_INVALID`。
- [ ] `ShopifyGraphqlClient`：401 刷新重试一次；`THROTTLED` 退避重试最多 3 次；其他 errors 抛 `ERR_TOKEN_INVALID`；402/423 调 `markWebsiteAuthError`。
- [ ] `markDisconnected` 写入 `DISCONNECTED`、token 占位 `DISCONNECTED:{id}`、`status=INVALID`、`uninstalledAt`。
- [ ] 票据：create 返回 32 字节 hex nonce，get 过期返回空，delete 后 get 为空（用 mock `RedisTemplate` 验证 key 与 TTL）。

### 测试要求
上述验收项各有单元测试；`ShopifyTokenServiceTest` 现有用例全部仍通过。

### 验证命令
后端编译 + Shopify 相关测试（见通用表）。

---

## T2 OAuth 安装与绑定、落地页

依赖：T1（合入 T1 分支后开始）。并行：T3、T4、T7。

### 修改范围与文件归属
- 新建 `v7-shop-admin/.../service/IShopifyInstallService.java`、`service/impl/ShopifyInstallService.java`（授权 URL、回调处理、`upsert`、webhook 注册）。
- 新建 `v7-shop-admin/.../controller/ShopifyAppController.java`（`/shopify-app/landing`、`/shopify-app/oauth/callback`、`/shopify-app/landing/resolve`）。
- `v7-shop-admin/.../controller/ShopifyWebsiteController.java`：新增 `install-url`、`bind`；`doEdit` 对 `PUBLIC_APP` 的限制。
- `v7-shop-admin/.../controller/req/EditShopifyWebsiteRequest.java`：`clientId` 去掉 `@NotBlank`，凭证模式在控制器里校验。
- 新建请求类 `controller/req/ShopifyInstallUrlRequest.java`、`ShopifyBindRequest.java`、`ShopifyLandingResolveRequest.java`；响应 `controller/resp/ShopifyInstallUrlResponse.java`。
- 新建 `v7-shop-entrance/src/main/resources/static/shopify-app/landing.html`（内联 CSS/JS，中英双语，手机宽度可用，无外部依赖）。
- 测试：新建 `ShopifyInstallServiceTest.java`、`controller/ShopifyAppControllerTest.java`（用 `MockMvc` standalone 或直接调用方法，mock 依赖）。

### 输入输出契约
ARCH.md §5.1、§5.2、§6.3、§9。授权 URL 的 `redirect_uri` = `{app-host}/shopify-app/oauth/callback`，`scope` = 配置 `scopes`。错误码集合见 §5.2。

### 验收标准
- [ ] `install-url`：handle 规范化、占用规则、`returnOrigin` 必须解析到当前租户，返回 `authorizeUrl` 含正确 `client_id/scope/redirect_uri/state`。
- [ ] 回调：HMAC/shop/state/handle 任一不符不换 token、不写库、按票据类型跳转到对应错误页。
- [ ] `ADMIN` 票据成功：店铺新建或恢复，owner = 票据用户，租户 = 票据租户，`lastSyncOrderTime = now`，票据删除，302 到 `{returnOrigin}/#/shopify-bind?status=success&handle=`。
- [ ] `STORE` 票据成功：token 写入票据并续期，302 到 `landing.html?ticket=`。
- [ ] `resolve`：票据过期 400 `TICKET_EXPIRED`；域名解析不到公司 400 `UNKNOWN_HOST`；成功返回 `https://{host}/#/shopify-bind?bindTicket=`（参数名不能是 `ticket`，会被后台 SSO 登录拦截）。
- [ ] `bind`：需登录与权限；占用规则；成功创建 `PUBLIC_APP` 店铺并删除票据。
- [ ] `/shopify-app/landing` 对已连接店铺 302 `status=connected`；未连接签发票据并 302 到授权 URL。
- [ ] `register-webhooks-on-install=true` 时调用 `webhookSubscriptionCreate` 并先查重；失败只记日志。
- [ ] 任何日志不含 token/secret。

### 测试要求
以上每条有单元测试；HMAC 用 T1 的 `ShopifyHmac`（真实实现，不 mock）。

### 验证命令
后端编译 + Shopify 相关测试。

---

## T3 GraphQL 订单同步重写、对账、webhook 订单导入

依赖：T1。并行：T2、T4、T7。

### 修改范围与文件归属
- 新建 `v7-shop-admin/.../service/impl/ShopifyGraphqlOrderAdapter.java`（GraphQL 节点 → REST 形状 JSON；附带元字段缓存填充）。
- `v7-shop-admin/.../service/impl/ShopifyOrderSyncService.java`：`countOrders`、`loadOrders`、`checkCancelledOrders`、`verifyAndUpdateAuthStatus`（凭证模式验证改用 GraphQL `ordersCount`）、`fetchMetafieldsForLineItems`（缓存优先，未命中走 GraphQL `nodes`）改为 GraphQL；实现 `importWebhookOrder`、`reconcileOrders`；删除所有 REST Admin API 调用（允许保留 `/admin/oauth/access_token`，那是 OAuth 端点）。
- `v7-shop-admin/.../task/executor/ShopifyOrderSyncExecutor.java`：§6.5 两种驱动方式。
- `v7-shop-admin/.../service/impl/TemporaryOrderService.java`：`fillTemporaryOrder` 对 `piiRedactedAt != null` 的订单不覆盖 `deliveryInfo`/`riskInfo`（§6.6）。
- `v7-shop-admin/.../service/dto/ShopifyCancelCheckResult.java`（如需）。
- 测试：`ShopifyOrderSyncServiceTest.java`、`ShopifyOrderSyncExecutorTest.java`、`ShopifyManualSyncTaskTest.java`（按需调整 mock），新建 `ShopifyGraphqlOrderAdapterTest.java`，测试资源 `src/test/resources/shopify/order-graphql.json` 与 `order-rest.json`（同一订单两种形状）。

### 输入输出契约
ARCH.md §5.4（`importWebhookOrder`、`reconcileOrders`）、§6.1（查询与映射表）、§6.5。`loadOrders` 签名与 `ShoplineOrderLoadResult` 不变（`nextPageInfo` 装 `endCursor`），`TaskExecutorService` 的手动同步循环无需改动。`originOrderId` 必须等于 `legacyResourceId`。

### 验收标准
- [ ] `grep -rn "admin/api/" v7-shop-admin/src/main` 只剩 `graphql.json` 一种端点。
- [ ] 适配器覆盖转换器读取的全部键（测试：GraphQL 样例经适配器 + 转换器得到的 `EditTemporaryOrderRequest` 与 REST 样例得到的逐字段相等）。
- [ ] 元字段随订单查询内联返回，自动/手动同步不再逐商品请求；webhook 路径未命中缓存时用 `nodes` 批量查询。
- [ ] 自动同步按 `created_at` 游标分页，重复订单由现有跳过逻辑去重；手动同步 `updateExisting=true` 行为不变。
- [ ] `reconcileOrders`：新订单创建、已存在且 `cancelled_at` 非空的标记取消、其余跳过，返回最大 `updated_at`。
- [ ] `importWebhookOrder(updateExisting=false)` 已存在返回 false 且不改库；`true` 时不存在则创建。
- [ ] 执行器：`CREDENTIALS` 店铺节奏不变；`PUBLIC_APP` 店铺只做对账且间隔 ≥ 配置值，`lastReconcileTime` 推进。
- [ ] 已脱敏订单被更新时收货人字段保持 `[redacted]`。
- [ ] 限流 `THROTTLED` 场景经客户端退避后成功（mock 两次响应）。

### 测试要求
现有 Shopify 测试类全部通过（允许为适配 GraphQL 改写 mock 的请求/响应形状，不得删除断言）。

### 验证命令
后端编译 + Shopify 相关测试 + `./gradlew :v7-shop-admin:test`。

---

## T4 webhook 接收与处理、卸载、合规、脱敏、保留期

依赖：T1。并行：T2、T3、T7。端到端订单 webhook 依赖 T3 的实现，单测用 mock `IShopifyOrderSyncService`。

### 修改范围与文件归属
- 新建 `v7-shop-admin/.../controller/ShopifyWebhookController.java`。
- 新建 `service/impl/ShopifyWebhookService.java`（落库/去重）、`service/impl/ShopifyWebhookProcessor.java`（`@Scheduled` 消费，§6.4）、`service/impl/ShopifyComplianceService.java`（§6.6）、`service/impl/ShopifyPiiRedactionService.java`（脱敏 + `@Scheduled` 保留期任务 + 访问日志清理）。
- 新建 dao 仓库（仅新文件）：`v7-shop-dao/.../repositories/primary/TemporaryOrderPiiRepository.java`、`OrderPiiRepository.java`（各自 `extends BaseRepository<...>`，只放脱敏 `@Modifying` JPQL：按店铺、按订单 ID 列表、按 email/phone、按过期）；风险记录 IP 清空同样以新仓库文件实现（`TemporaryOrderRiskRecordInfoPiiRepository`、`OrderRiskRecordInfoPiiRepository`，先确认这两个实体的 IP 字段名）。
- 测试：新建 `ShopifyWebhookControllerTest`、`ShopifyWebhookServiceTest`、`ShopifyWebhookProcessorTest`、`ShopifyComplianceServiceTest`、`ShopifyPiiRedactionServiceTest`。

### 输入输出契约
ARCH.md §3.2–§3.4、§5.3、§5.4（`ShopifyPiiRedactionService`）、§6.4、§6.6。通知用 `INoticeService.createNotice(title, content, "SHOPIFY_COMPLIANCE", ownerUserId)`。

### 验收标准
- [ ] HMAC 错误 401 且不落库；重复 `webhook_id` 返回 200 不重复落库；未知 topic `IGNORED`；端点不做业务处理。
- [ ] 处理器按 topic 分发到正确方法，设置并清理租户上下文；异常退避 `2^attempts` 分钟，5 次后 `FAILED`；店铺暂停时订单 topic 直接 DONE 并记 note。
- [ ] `app/uninstalled` → `markDisconnected`；已同步订单不动。
- [ ] `shop/redact` → 合规记录 + 按店铺脱敏（审单表与正式订单表）+ 清凭证 + `affected_count`。
- [ ] `customers/redact` → 仅该客户订单（`orders_to_redact` 或 email/phone 匹配）脱敏。
- [ ] `customers/data_request` → 记录 `MANUAL` + 归属人站内通知，内容含 `due_at`。
- [ ] 脱敏字段集与保留字段集与 §6.6 一致；`piiRedactedAt` 写入；风险记录 IP 清空。
- [ ] 保留期任务按配置天数脱敏到期订单并清理过期访问日志；全租户执行（silent）。

### 测试要求
以上各条单元测试；脱敏 JPQL 以字符串断言或 mock 仓库验证参数。

### 验证命令
后端编译 + Shopify 相关测试。

---

## T5 后台前端

依赖：无（按 ARCH.md §5.1、§7 契约开发）。并行：T1、T6。

### 修改范围与文件归属（`v7-shop-admin`）
- `src/api/ThirdPartyWebsite.ts`：`shopifyInstallUrl`、`shopifyBind`。
- `src/views/website/vabAutoComponents/ThirdPartyWebsiteEdit.vue`：接入方式单选、「连接 Shopify」、`PUBLIC_APP` 编辑限制。
- `src/views/website/ThirdPartyWebsite.vue`：接入方式列、「已断开」标签、「重新连接」。
- `src/views/website/vabAutoComponents/ThirdPartyOrderSync.vue`：60 天限制与提示。
- 新建 `src/views/website/ShopifyBind.vue`；`src/router/index.ts` 的 `constantRoutes` 增加 `/shopify-bind`。

### 验收标准
- [ ] 添加 → SHOPIFY → 默认公开应用：仅店铺名称/HANDLE/币种模式 + 「连接 Shopify」，点击后整页跳转到返回的 `authorizeUrl`；切凭证模式显示现有表单且保存行为不变。
- [ ] 列表显示接入方式；`DISCONNECTED` 灰标签；`PUBLIC_APP` 且 `DISCONNECTED/ERROR` 出现「重新连接」。
- [ ] `/shopify-bind?bindTicket=` 未登录先登录再回到本页并自动绑定；`status=success|error` 正确显示；有「前往第三方商城」按钮。
- [ ] 同步对话框对 `SHOPIFY && !hasAllOrdersScope` 限制 60 天并提示。
- [ ] `pnpm vue-tsc`、`pnpm lint:eslint` 无错误。

### 测试要求
项目无前端单测框架，以 `vue-tsc` + eslint + 手工截图说明为准（在 progress 文件里记录验证步骤）。

### 验证命令
`pnpm install --frozen-lockfile`、`pnpm vue-tsc`、`pnpm lint:eslint`。

---

## T6 文档站英文化、隐私政策、应用声明、上架清单更新

依赖：无。并行：T1、T5。

### 修改范围与文件归属
- `v7-shop-docs/docusaurus.config.ts`（i18n、localeDropdown）。
- 新建 `v7-shop-docs/docs/privacy-policy.mdx`（中文）、`v7-shop-docs/i18n/en/docusaurus-plugin-content-docs/current/privacy-policy.mdx`（英文）、`v7-shop-docs/i18n/en/docusaurus-plugin-content-docs/current/third-party-order-sync/shopify.mdx`（英文安装指南）、`v7-shop-docs/i18n/en/code.json`（如 build 需要）。
- `v7-shop-docs/docs/third-party-order-sync/shopify.mdx`：改为公开应用流程，凭证模式移到附录。
- 新建 `v7-shop-services/shopify.app.toml`（ARCH.md §8 模板，`client_id` 与 `app-host` 用占位并注释）。
- `docs/shopify-app-store-listing.md`：第 0 节改为「已实现项/待办项」，Testing instructions 与截图顺序改为 OAuth 流程，Privacy policy URL 填 `https://docs.vantasite.com/en/privacy-policy`。

### 隐私政策必含内容（中英一致）
收集的数据项（订单、收货人姓名/电话/邮箱/地址、客户 IP/UA、商品）、用途（审单、发货、面单、风控）、来源（Shopify API 与 webhook）、保留期（订单最后更新后 12 个月脱敏；可配置）、商家/客户删除请求的处理（`customers/redact`、`shop/redact` 的脱敏与时限 30 天）、数据不出售不共享给第三方、传输与存储保护措施概述、联系方式占位（邮箱待定）、生效日期。

### 验收标准
- [ ] `pnpm build` 通过；`/en/privacy-policy` 与 `/en/third-party-order-sync/shopify` 可访问；中文站不受影响。
- [ ] TOML 字段与 ARCH.md §8 一致。
- [ ] 上架清单的测试说明与 PRD §5 流程一致。

### 验证命令
`pnpm install --frozen-lockfile`、`pnpm build`（在 `v7-shop-docs`）。

---

## T7 受保护客户数据访问日志

依赖：T1。并行：T2、T3、T4。

### 修改范围与文件归属
- 新建 `v7-shop-admin/.../service/IProtectedDataAccessLogService.java`、`service/impl/ProtectedDataAccessLogService.java`。
- `v7-shop-admin/.../controller/TemporaryOrderController.java`：`/page` 记录 `LIST/TEMPORARY`。
- `v7-shop-admin/.../controller/OrderController.java`：`/page` 记录 `LIST/ORDER`，`/download` 记录 `EXPORT/ORDER`。
- 测试：新建 `ProtectedDataAccessLogServiceTest`，控制器层以服务 mock 验证调用。

### 输入输出契约
ARCH.md §3.4、§5.4（`record`）、§6.7。只记录 `platform == SHOPIFY` 的行；`orderIds` 空不记录；写入失败只记日志。

### 验收标准
- [ ] 分页返回含 Shopify 订单时写一条日志，`order_count` 与 `order_ids` 正确（最多 500 个）；不含时不写。
- [ ] 导出写 `EXPORT`。
- [ ] 日志写入异常不影响接口响应。

### 验证命令
后端编译 + `./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.ProtectedDataAccessLogServiceTest"`。

---

## 交付记录（2026-10-22）

基线：main `3f01b44e`（文档）→ `125b1f16`（bindTicket 改名）。第二批分支均已合入 T1 分支。所有 worktree 在 `.claude/worktrees/agent-<id>/`，未合并主分支、未清理。

| 任务 | 分支 | 末次提交 | 验证结果 | 状态 |
| --- | --- | --- | --- | --- |
| T1 基础层 | `worktree-agent-a8743e5cc99251fb9` | `f63fe37e` | 四模块编译通过；Shopify 相关 151 个测试通过；全量 admin 测试通过 | done |
| T2 OAuth 安装与绑定 | `worktree-agent-a2e2eeeebf4d1c0ab` | `54303ff1` | 编译通过；Shopify 相关 178 个测试通过（T2 新增 50） | done |
| T3 GraphQL 同步 | `worktree-agent-a4d5acf8e19bdc0aa` | `dfe0bfcd` | 编译通过；全量 admin 测试 606 个通过；`admin/api/` 只剩 graphql.json | done |
| T4 webhook/合规/脱敏 | `worktree-agent-abb90c2b12723b6a6` | `8c6ef969` | 编译通过；全量 admin 测试通过（T4 新增 63）；14 个文件全为新建 | done |
| T5 后台前端 | `worktree-agent-a09780d546b815bcc` | `bad959d2` | vue-tsc 基线 83 错未新增；eslint 基线即无法运行；prettier 通过 | done |
| T6 文档站/TOML/上架清单 | `worktree-agent-aa9e93c2cb2349bf7` | `fdf5ac73` | 中英文 `pnpm build` 通过；tsc 通过 | done |
| T7 访问日志 | `worktree-agent-a80a3ad8c66e285e8` | `bea1d2ae` | 编译通过；全量 admin 测试 594 个通过 | done |

### 与契约的出入（已接受，QA 按此验收）
- **T1**：`ShopifyInstallTicket.TicketType` 嵌套枚举；`ShopifyWebhookEvent.State`/`ShopifyComplianceRecord.State` 嵌套枚举；GraphQL 客户端内含限流与 429 重试；DISCONNECTED 店铺不刷新 token；`clearCredentials`/`markDisconnected` 同时清 `refreshTokenExpiresAt`；多一个 `ShopifyWebsiteStoreTest`。
- **T2**：`resolve` 自行解析顶级域名（`DomainUtils` 只接受 request）；webhook 注册字段用 `uri`（2025-04 起替代 `callbackUrl`）；`EditShopifyWebsiteRequest.handle` 也去掉 `@NotBlank`；`bind` 占用失败不删票据；回调意外异常统一 `INVALID_STATE`。
- **T3**：因 GraphQL 单次成本上限 1000 点，订单每页 8 单、行项目首屏 5 个、元字段按 6 个 key 查；取消检查每页 50 单；浏览器 UA 在 GraphQL 无对应字段（webhook 路径仍有）；客户邮箱/电话、落地页/来源页改用未废弃字段；凭证模式验证直接 POST graphql.json（新建店铺无 id）；对账游标取 max(返回值, since)；多一个 `TemporaryOrderServiceRedactedSyncTest`。
- **T4**：脱敏改原生 SQL（JPQL 会自动带 `status <> 'DELETED'`，软删除订单会漏脱敏）；备注写在 `error` 列；风险记录同时清 `real_ip` 及归属地；email 匹配忽略大小写；缺 `X-Shopify-Webhook-Id` 时用正文哈希去重；落库异常返回 500 让 Shopify 重投。
- **T5**：URL 参数 `bindTicket`（`ticket` 会被后台 SSO 票据登录拦截并登出）；商城类型挪到表单首行；同步对话框多一条起始时间校验。
- **T6**：文档站页面都在 `/docs` 前缀下，`/en/privacy-policy` 由构建期插件生成跳转页指向 `/en/docs/privacy-policy`（开发服务器下不生效）；上架清单的 App details/Features 顺手改为与事实一致。
- **T7**：临时订单分页响应无 platform，改按实体筛选；`record` 多一个带 totalCount 的重载；导出日志记提交时刻的订单集合；同步写入不用 `@Async`（异步线程丢租户与登录人）。

### 风险与待办（QA 与上线前）
1. **未对真实 Shopify 店铺跑过**：GraphQL 字段均按 shopify.dev 文档核对，任一字段在配置版本不存在整条查询即失败；QA 需用凭证模式店铺实测一次自动同步、手动同步、取消检查。
2. **凭证模式吞吐下降**：每轮 8 单，有下一页 10 秒后再跑，约 48 单/分钟（原 REST 每页 100）。可在 QA 后按实测成本调大 `orders(first:)`。
3. **webhook 处理器无分布式锁**：多实例部署时合规记录与站内通知可能重复；导入与脱敏幂等。
4. **首轮保留期任务**可能一次脱敏大量旧订单，预发先评估耗时。
5. **App Store 安装时序**：TOML 默认 Shopify 托管安装，非嵌入式应用「先授权还是先打开 App URL」官方未明确；落地页两种时序都支持，联调实测。
6. **后台请求查不到其他租户店铺**（open-in-view 带租户过滤）：跨租户 handle 占用靠唯一键冲突兜底转 `HANDLE_TAKEN`；软删除的同 handle 店铺会挡住重新安装（现有行为）。
7. **暂停期间订单补齐**依赖对账游标不推进，QA 验证「暂停 → 下单 → 恢复 → 15 分钟内出现」。
8. **前端工具链**：`vue-tsc` 基线 83 错、`lint:eslint` 基线无法运行（lockfile 的 eslint-config 要求 eslint 9），与本功能无关。
9. **文档站构建**：本机需 `npx pnpm@8`，worktree 内需 hoisted 安装（Windows 路径长度）。
10. **环境事故**：本次会话中 `~/.claude/scripts/feature-status.py` 与 `feature-developer` 代理定义被清理，STATUS.md 未记录 tasks 确认、派发与 stage=dev；`/qa-merge` 前需恢复脚本并补录。
11. 待决事项仍为 PRD §11（中立域名、邮箱、Level 2 基础设施事实、法务审阅、测试账号）。

### 建议合并顺序
T1 → T3 → T2 → T4 → T7 → T5 → T6（T2/T3/T4/T7 均含 T1 的合并提交；T5、T6 无后端依赖，可任意时机合入）。
