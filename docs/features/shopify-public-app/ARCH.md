# 技术方案：Shopify 公开应用

- slug：`shopify-public-app`（PRD 见同目录 `PRD.md`，访谈决策见其末尾）
- 文档目录：`docs/features/shopify-public-app/`
- 日期：2026-10-22

---

## 0. 结论先行

1. **不新增工程**。公开端点（OAuth 回调、落地页、webhook）全部放在 `v7-shop-admin` 模块、由 `v7-shop-entrance` 对外提供，路径前缀统一 `/shopify-app/**`，在 `CompanyTenantInterceptor` 白名单放行。中立域名由反向代理指向后端，落地页是后端 `static/` 下的静态双语 HTML。
2. **REST → GraphQL 用「适配器」而不是重写转换器**。新增 `ShopifyGraphqlOrderAdapter` 把 GraphQL 订单节点映射成现有转换器所认的 REST 形状 JSON（Shopify webhook 的订单 payload 本来就是 REST 形状），现有 `convertShopifyOrderToTemporary` 及其测试保持不变；商品元字段随 GraphQL 订单查询内联返回，不再逐商品请求。
3. **webhook 先落库再异步处理**。单一端点 `/shopify-app/webhooks` 校验 HMAC 后把事件写入 `t_shopify_webhook_events`（`webhook_id` 唯一去重）立即返回 200，由定时处理器消费。无新基础设施。
4. **接入方式并存**。`ThirdPartyWebsite.accessMode`：`CREDENTIALS`（现有）/ `PUBLIC_APP`。两者共用 GraphQL 客户端与订单转换；差别只在拿 token 的方式和驱动方式（凭证模式仍每分钟轮询，公开应用 webhook + 15 分钟对账）。
5. **脱敏按 `contextInfo.websiteId` 定位店铺**，不改订单表结构；仅加一列 `pii_redacted_at` 标记。
6. **无迁移文件**。项目 `hbm2ddl.auto=update`，实体改动即建表加列；新列一律可空或带默认值，避免存量行报错。

---

## 1. 模块职责

```
v7-shop-dao        实体/枚举/仓库：ThirdPartyWebsite 新字段、ShopifyAccessMode、ThirdPartyAuthStatusEnum.DISCONNECTED、
                   ShopifyWebhookEvent、ShopifyComplianceRecord、ProtectedDataAccessLog、TemporaryOrder/Order.piiRedactedAt
v7-shop-admin
  config/          ShopifyAppProperties（shopify.app.*）
  utils/           ShopifyHmac（OAuth 查询串 hex HMAC、webhook 正文 base64 HMAC）
  service/impl/
    ShopifyGraphqlClient         GraphQL 调用、401 刷新重试、限流退避
    ShopifyTokenService          凭证模式（现有）+ 公开应用 refresh_token 刷新
    ShopifyWebsiteStore          店铺读写（新增断开、更新双 token、对账游标）
    ShopifyInstallTicketStore    Redis 安装票据
    ShopifyInstallService        授权 URL、回调处理、绑定
    ShopifyGraphqlOrderAdapter   GraphQL 节点 → REST 形状 JSON
    ShopifyOrderSyncService      订单计数/拉取/取消/对账/webhook 订单导入（改为 GraphQL）
    ShopifyWebhookService        事件落库
    ShopifyWebhookProcessor      事件消费（@Scheduled）
    ShopifyComplianceService     卸载、redact、data_request
    ShopifyPiiRedactionService   脱敏 SQL + 保留期任务
    ProtectedDataAccessLogService 访问日志
  controller/
    ShopifyWebsiteController     （现有）+ install-url、bind
    ShopifyAppController         /shopify-app/oauth/callback、/shopify-app/landing、/shopify-app/landing/resolve
    ShopifyWebhookController     /shopify-app/webhooks
  task/executor/
    ShopifyOrderSyncExecutor     凭证模式轮询（GraphQL）+ 公开应用对账
v7-shop-entrance   static/shopify-app/landing.html（双语落地页/结果页）
v7-shop-admin(前端) 第三方商城表单「连接 Shopify」、/shopify-bind 绑定页、列表接入方式/已断开/重新连接、同步对话框 60 天限制
v7-shop-docs       en 语言：privacy-policy、shopify 安装指南
仓库根             v7-shop-services/shopify.app.toml（应用声明：URL、回调、scope、webhook）
```

---

## 2. 配置

`application.yml.example` 新增（环境变量名即约定）：

```yaml
shopify:
  app:
    client-id: ${SHOPIFY_APP_CLIENT_ID:}
    client-secret: ${SHOPIFY_APP_CLIENT_SECRET:}
    # 中立域名，含协议，无尾斜杠，例如 https://shopify.vantasite.com
    app-host: ${SHOPIFY_APP_HOST:}
    api-version: ${SHOPIFY_API_VERSION:2026-07}
    # 必须与 shopify.app.toml 的 [access_scopes] 一致
    scopes: ${SHOPIFY_APP_SCOPES:read_orders,read_products}
    # 安装成功后是否用 GraphQL 注册订单 webhook（TOML 已声明时保持 false）
    register-webhooks-on-install: ${SHOPIFY_REGISTER_WEBHOOKS:false}
    install-ticket-ttl-minutes: 10
    pending-ticket-ttl-minutes: 30
    # 后台「连接 Shopify」链接（意向）有效期
    connect-intent-ttl-minutes: 60
    # App Store 安装页，上架批准后填写；为空时连接链接跳说明页
    install-url: ${SHOPIFY_APP_INSTALL_URL:}
    reconcile-interval-minutes: 15
    reconcile-lookback-minutes: 30
    pii-retention-days: 365
    access-log-retention-days: 365
```

`ShopifyAppProperties`（`@ConfigurationProperties("shopify.app")`，`cn.v7soft.admin.config`）。`client-id/secret` 为空时：公开应用相关端点返回 503 并记录「未配置」，凭证模式不受影响。

Redis：复用 `RedisConfig.jsonRedisTemplate`（`RedisTemplate<String,Object>`）。

---

## 3. 数据模型

### 3.1 `t_third_party_websites`（`ThirdPartyWebsite`）新增列

| 字段 | 列 | 类型 | 说明 |
| --- | --- | --- | --- |
| `accessMode` | `access_mode` | `varchar(30)`，可空 | `ShopifyAccessMode`：`CREDENTIALS` / `PUBLIC_APP`。**null 视为 CREDENTIALS**（存量行） |
| `refreshToken` | `refresh_token` | `varchar(1024)`，可空 | 公开应用 |
| `refreshTokenExpiresAt` | `refresh_token_expires_at` | datetime，可空 | |
| `grantedScopes` | `granted_scopes` | `varchar(512)`，可空 | 回调返回的 scope 原文 |
| `hasAllOrdersScope` | `has_all_orders_scope` | boolean，默认 false | `grantedScopes` 含 `read_all_orders` |
| `installedAt` | `installed_at` | datetime，可空 | 最近一次授权成功时间 |
| `uninstalledAt` | `uninstalled_at` | datetime，可空 | 最近一次 `app/uninstalled` |
| `lastReconcileTime` | `last_reconcile_time` | datetime，可空 | 对账游标（Shopify `updated_at`，东八区 LocalDateTime，与现有 `lastCancelCheckTime` 同约定） |

`token` 列现为 `nullable=false, unique`：断开时**不能置 null**。约定：断开时写入 `"DISCONNECTED:" + id`（保证唯一），`tokenExpiresAt` 置为当前时间。

`ThirdPartyAuthStatusEnum` 新增 `DISCONNECTED`（卸载后）。`ShopifyWebsiteStore.findActiveWebsites()` 只取 `AUTHED`，断开店铺自然不参与同步。

新枚举 `cn.v7soft.dao.enums.ShopifyAccessMode { CREDENTIALS, PUBLIC_APP }`。

`ThirdPartyWebsiteDto` 同步新增：`accessMode`、`refreshToken`、`refreshTokenExpiresAt`、`hasAllOrdersScope`、`lastReconcileTime`、`companyId`。
`ThirdPartyWebsiteResponse` 新增：`accessMode`、`hasAllOrdersScope`、`installedAt`。

### 3.2 `t_shopify_webhook_events`（`ShopifyWebhookEvent`，继承 `BaseEntity`，**不**走租户基类，跨租户表）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `webhook_id` | varchar(64)，唯一索引 `uk_webhook_id` | `X-Shopify-Webhook-Id` |
| `topic` | varchar(64) | `X-Shopify-Topic` |
| `shop_domain` | varchar(128)，索引 | `X-Shopify-Shop-Domain` |
| `api_version` | varchar(16) | `X-Shopify-API-Version` |
| `website_id` | bigint，可空 | 按 shop_domain 解析；找不到为 null |
| `company_id` | bigint，可空 | 来自店铺 |
| `payload` | LONGTEXT | 原始正文 |
| `state` | varchar(16)，索引 | `PENDING` / `DONE` / `FAILED` / `IGNORED` |
| `attempts` | int 默认 0 | |
| `next_attempt_at` | datetime | 退避 |
| `error` | varchar(1000) | 最近错误 |
| `received_at` / `processed_at` | datetime | |

### 3.3 `t_shopify_compliance_records`（`ShopifyComplianceRecord`，跨租户表）

| 列 | 说明 |
| --- | --- |
| `topic` | `shop/redact` / `customers/redact` / `customers/data_request` |
| `shop_domain`、`website_id`、`company_id` | |
| `customer_id`、`customer_email`、`customer_phone` | 可空 |
| `order_ids` | TEXT，Shopify 订单 ID 逗号分隔 |
| `state` | `PENDING` / `DONE` / `MANUAL`（data_request 交人工） |
| `received_at`、`due_at`（received_at + 30 天）、`completed_at` | |
| `affected_count` | 脱敏行数 |
| `note` | varchar(1000) |

### 3.4 `t_protected_data_access_logs`（`ProtectedDataAccessLog`，继承 `BaseTenantEntity`）

| 列 | 说明 |
| --- | --- |
| `user_id`、`user_name` | 操作人 |
| `action` | `LIST` / `EXPORT` |
| `order_type` | `TEMPORARY` / `ORDER` |
| `order_ids` | TEXT，本次返回/导出的 Shopify 订单主键，逗号分隔，最多 500 个 |
| `order_count` | int |
| `access_time` | datetime，索引 |

### 3.5 `t_temporary_orders`、`t_orders` 新增列

`pii_redacted_at` datetime 可空。已脱敏的订单不再重复处理，同步更新时若 `piiRedactedAt != null` 则**不回填**收货人字段（见 §6.6）。

---

## 4. 公开端点与租户

`CompanyTenantInterceptor` 白名单新增 `path.startsWith("/shopify-app/")`。这些端点：

- 无 sa-token 注解即免登录；
- 自行解析租户（票据里的 companyId 或店铺的 companyId），处理前 `TenantContext.setCurrentTenant(companyId, companyService.companyCached(companyId))`，`finally` 里 `TenantContext.clear()`；查店铺本身用 `TenantContext.silent()`；
- 不返回任何租户信息；错误只返回错误码。

---

## 5. 接口契约

### 5.1 后台（需登录）—— `ShopifyWebsiteController`

**POST `/third-party-website/shopify/install-url`** 权限 `third-party-website.create`（带 `id` 重连时 `.update`）

请求：
```json
{ "id": "可选，重连已有记录", "nickName": "可选，新增默认取 Shopify 店铺名", "currencyMode": "SHOP_MONEY|PRESENTMENT_MONEY" }
```
不接收店铺标识：App Store 要求 2.3.1 禁止让商家手填 myshopify 域名，店铺由商家在 Shopify 中选择。Origin/Referer 取 `returnOrigin`（`DomainUtils`，必须能解析出当前租户公司，否则 `PARAMETER_ILLEGAL`）。
- 新增（无 `id`）：签发 `INTENT` 票据（handle 为空，TTL `connect-intent-ttl-minutes`），返回中转地址 `{ "authorizeUrl": "{app-host}/shopify-app/connect?intent={nonce}" }`。前端主按钮是「复制连接链接」，用于 Shopify 与后台在不同浏览器登录、或把链接转给店主。
- 重连（带 `id`）：handle 取自原记录（必须是当前租户的公开应用记录），签发 `ADMIN` 票据，返回 `{ "authorizeUrl": "https://xxx.myshopify.com/admin/oauth/authorize?client_id=..&scope=read_orders,read_products&redirect_uri={app-host}/shopify-app/oauth/callback&state={nonce}" }`。
错误：应用未配置 → `ServiceResponseEnum` 新增 `ERR_SHOPIFY_APP_NOT_CONFIGURED`（HTTP 503 语义，消息「Shopify 公开应用未配置」）。

**POST `/third-party-website/shopify/bind`** 权限 `third-party-website.create`

请求 `{ "ticket": "nonce", "nickName": "可选，默认 handle", "currencyMode": "可选，默认 SHOP_MONEY" }`
处理：取 `STORE` 票据且 `pendingToken != null`，否则 `PARAMETER_ILLEGAL "票据不存在或已过期"`；handle 占用规则同上；创建/恢复店铺（见 §6.3）；删除票据。
响应：`ThirdPartyWebsiteResponse`（与 `doEdit` 相同结构，含 `accessMode`）。

**POST `/third-party-website/shopify/doEdit`**（现有）：`accessMode=PUBLIC_APP` 的记录只允许改 `nickName`、`currencyMode`，其余字段忽略，不再调用 `verifyAndUpdateAuthStatus`。`EditShopifyWebsiteRequest.clientId` 去掉 `@NotBlank`，改在控制器里对凭证模式校验非空（公开应用记录没有 clientId）。新增（无 id）请求仍为凭证模式。

**POST `/third-party-website/page`**（现有）响应行新增 `accessMode`、`hasAllOrdersScope`。

### 5.2 公开端点 —— `ShopifyAppController`（`/shopify-app`）

**GET `/shopify-app/connect?intent={nonce}`**  后台连接链接的中转。意向有效 → 写 cookie `v7_shopify_connect={nonce}`（`Path=/shopify-app/; HttpOnly; Secure; SameSite=Lax`，Max-Age = `connect-intent-ttl-minutes`），302 到 `install-url`（未配置时 `/shopify-app/landing.html?status=install`）；无效 → 清 cookie，302 `status=error&code=TICKET_EXPIRED`。Lax 保证 Shopify 安装后顶层跳回 `/landing` 时带上 cookie。

**GET `/shopify-app/landing`**  Shopify 打开 App URL 时进入。Query：`shop`、`hmac`、`timestamp`、其余透传；Cookie：`v7_shopify_connect`（可选）。
1. `ShopifyHmac.verifyQuery(params, secret)` 失败 → 302 `/shopify-app/landing.html?status=invalid`。
2. `shop` 必须匹配 `^[a-z0-9][a-z0-9-]*\.myshopify\.com$`，handle = 去后缀。
3. `TenantContext.silent()` 查 `getByHandle`：凭证模式/非 Shopify 记录占用 → `HANDLE_TAKEN`；存在且 `accessMode=PUBLIC_APP` 且 `AUTHED` → 302 所属租户总后台 `https://admin.{company.domain}/`（取不到合法域名时 `landing.html?status=connected`）。
4. cookie 中有未消费的意向：handle 被其他租户的公开应用记录占用 → `HANDLE_TAKEN`（意向保留）；否则以意向的租户签发带 handle 的 `INTENT` 票据（nonce 作为 state），删除意向、清 cookie，302 到授权 URL。cookie 中的意向已失效时清 cookie。
5. 否则签发 `STORE` 票据（无租户），302 到授权 URL（`state=nonce`）。

**GET `/shopify-app/oauth/callback`** Query：`code`、`hmac`、`shop`、`state`、`timestamp`、`host`（可选）
1. HMAC 校验；`shop` 格式校验；`state` 取票据，票据 `handle` 必须等于 shop 的 handle；三者任一失败 → 结果页 `status=invalid`（`ADMIN` 票据则回后台 `status=error&code=INVALID_STATE`）。
2. 换 token：`POST https://{shop}/admin/oauth/access_token`，JSON `{client_id, client_secret, code, expiring: 1}` → `{access_token, scope, expires_in, refresh_token, refresh_token_expires_in}`。失败 → `code=TOKEN_EXCHANGE_FAILED`。
3. `ADMIN` 票据：设租户 = 票据 companyId，创建/恢复店铺（§6.3，owner = 票据 userId），按配置注册 webhook，删除票据，302 `{returnOrigin}/#/shopify-bind?status=success&handle={handle}`。失败 302 `{returnOrigin}/#/shopify-bind?status=error&code={code}&handle={handle}`。
   `INTENT` 票据：写库同 `ADMIN`，但授权的浏览器不一定登录了后台，成功 302 `/shopify-app/landing.html?status=linked&handle={handle}`，失败回结果页错误码。
   新建且未填名称时，写库后用 `query { shop { name } }` 取 Shopify 店铺名作为名称，失败保留 handle。
4. `STORE` 票据：把 token 写入票据（`pendingToken`），TTL 重置为 `pending-ticket-ttl-minutes`，302 `/shopify-app/landing.html?ticket={nonce}`。

**POST `/shopify-app/landing/resolve`** JSON `{ "ticket": "nonce", "adminHost": "xxx.xyzdwd.com 或 https://xxx.xyzdwd.com/..." }`
1. 票据必须为 `STORE` 且有 `pendingToken`，否则 400 `{ "code": "TICKET_EXPIRED" }`。
2. `adminHost` 规范化为 host；`DomainUtils` 取顶级域名，`companyService.identityCached(topLevelDomain)` 为空 → 400 `{ "code": "UNKNOWN_HOST" }`。
3. 200 `{ "redirectUrl": "https://{host}/#/shopify-bind?ticket={nonce}" }`。

错误码集合（结果页与后台共用）：`INVALID_STATE`、`INVALID_HMAC`、`INVALID_SHOP`、`TOKEN_EXCHANGE_FAILED`、`HANDLE_TAKEN`、`TICKET_EXPIRED`、`UNKNOWN_HOST`、`APP_NOT_CONFIGURED`。

**静态页 `/shopify-app/landing.html`**（`v7-shop-entrance/src/main/resources/static/shopify-app/landing.html`，纯 HTML+JS，中英双语，手机宽度可用）按 query 分支：
品牌统一显示应用名 **V7 Order Connector**。
- `ticket`（落地页自身的 query，这里不会经过后台 SPA，可保留 `ticket`）：「关联你已有的 V7 账号」（B2B 服务，按 App Store 对需签约服务的例外，安装后第一步是用已有账号关联店铺），输入总后台地址，调用 `/shopify-app/landing/resolve`；成功后不自动跳转，给出「在本浏览器登录并完成关联」与「复制关联链接」（V7 在另一个浏览器登录时用）；附开通账号的联系方式；错误码映射双语文案。
- `status=linked&handle=`：后台连接链接安装成功，「店铺已连接，请回到 V7 后台刷新列表」。
- `status=install`：连接链接已记住，提示在本浏览器的 Shopify 后台安装/打开应用（上架前没有 App Store 安装页时）。
- `status=connected`：「该店铺已连接到 V7，请登录后台查看」（取不到租户域名时的兜底）。
- `status=invalid|error`：「请从 Shopify 后台重新打开本应用」。

### 5.3 Webhook —— `ShopifyWebhookController`

**POST `/shopify-app/webhooks`**  正文 `byte[]`（必须用原始字节算 HMAC）。头：`X-Shopify-Topic`、`X-Shopify-Hmac-Sha256`、`X-Shopify-Shop-Domain`、`X-Shopify-Webhook-Id`、`X-Shopify-API-Version`。
- HMAC 不符 → **401** 空体。
- `webhook_id` 已存在 → 200。
- 其余：`ShopifyWebhookService.enqueue(...)` 落库 `PENDING`（店铺找不到且 topic 非合规类 → `IGNORED`；合规类即使找不到店铺也落 `PENDING`，处理时记录后 DONE），返回 **200**。
- 支持的 topic：`orders/create`、`orders/updated`、`orders/cancelled`、`app/uninstalled`、`shop/redact`、`customers/redact`、`customers/data_request`；其他 topic 落 `IGNORED` 返回 200。
- 响应必须在 5 秒内。

### 5.4 内部接口（Java）

```java
// IShopifyOrderSyncService 新增
/** 把 webhook 订单 payload（REST 形状）导入审单表。updateExisting=true 时覆盖已存在订单。返回是否新建 */
boolean importWebhookOrder(ThirdPartyWebsiteDto website, JSONObject orderPayload, boolean updateExisting);
/** 对账：拉取 updated_at >= since 的订单，新订单创建，已存在且已取消的标记取消；返回处理到的最大 updated_at */
LocalDateTime reconcileOrders(ThirdPartyWebsiteDto website, LocalDateTime since);

// ShopifyGraphqlClient
public record GraphqlResult(JSONObject data, JSONArray errors, JSONObject extensions) {}
GraphqlResult execute(ThirdPartyWebsiteDto website, String query, Map<String, Object> variables);
// 401 → tokenService.forceRefresh 后重试一次；errors[].extensions.code == "THROTTLED" → 按 extensions.cost.throttleStatus 等待后重试，最多 3 次；
// 用户错误（errors 非空且非 THROTTLED）→ 抛 ServiceResponseEnum.ERR_TOKEN_INVALID(websiteId, errors.toString())；HTTP 402/423 → websiteStore.markWebsiteAuthError(unavailableMessage)

// ShopifyTokenService
String getAccessToken(ThirdPartyWebsiteDto website);        // 按 accessMode 分支：CREDENTIALS 现有逻辑；PUBLIC_APP 用 refresh_token
String forceRefresh(Long websiteId, String rejectedToken);  // 同上
public record ExchangedToken(String accessToken, LocalDateTime accessExpiresAt, String refreshToken, LocalDateTime refreshExpiresAt, String scope) {}
ExchangedToken exchangeAuthorizationCode(String handle, String code);
ExchangedToken refreshPublicAppToken(String handle, String refreshToken);
// refresh 失败（4xx）→ websiteStore.markWebsiteAuthError(id, "授权已失效，请重新连接") 并抛 ERR_TOKEN_INVALID

// ShopifyWebsiteStore 新增
List<ThirdPartyWebsite> findActivePublicAppWebsites();                 // VALID + AUTHED + SHOPIFY + PUBLIC_APP
Optional<ThirdPartyWebsite> findByShopDomain(String shopDomain);        // silent 查询，handle = 去 .myshopify.com
void updatePublicAppTokens(Long id, ExchangedToken token);
void markDisconnected(Long id);                                         // DISCONNECTED + token 占位 + status INVALID + uninstalledAt
void updateReconcileTime(Long id, LocalDateTime time);
void clearCredentials(Long id);                                         // shop/redact：token 占位、refreshToken=null、clientSecret=null

// ShopifyInstallTicketStore（Redis key shopify:install:ticket:{nonce}）
public record ShopifyInstallTicket(String nonce, TicketType type, String handle, Long companyId, Long userId,
        String nickName, CurrencyMode currencyMode, Long websiteId, String returnOrigin,
        ExchangedToken pendingToken, LocalDateTime createdAt) {}
enum TicketType { ADMIN, STORE }
String create(ShopifyInstallTicket ticket, Duration ttl);   // 返回 nonce（32 字节随机，hex）
Optional<ShopifyInstallTicket> get(String nonce);
void update(ShopifyInstallTicket ticket, Duration ttl);
void delete(String nonce);

// ShopifyHmac
static boolean verifyQuery(Map<String, String[]> params, String secret);   // 去掉 hmac，键排序，k=v 用 & 连接，HMAC-SHA256 hex，常量时间比较
static boolean verifyBody(byte[] body, String headerBase64, String secret); // HMAC-SHA256 base64

// ShopifyPiiRedactionService
int redactByWebsite(Long companyId, Long websiteId);
int redactByCustomer(Long companyId, Long websiteId, List<String> originOrderIds, String email, String phone);
int redactExpired(int retentionDays);     // 全租户，silent

// ProtectedDataAccessLogService
void record(String action, String orderType, List<Long> orderIds);   // 当前登录人、当前租户；orderIds 为空不记录
```

---

## 6. 关键流程

### 6.1 GraphQL 订单查询（`ShopifyOrderSyncService`）

端点 `POST https://{handle}.myshopify.com/admin/api/{api-version}/graphql.json`，头 `X-Shopify-Access-Token`。

- 计数：`{ ordersCount(query: $q) { count precision } }`，`q` 为 `created_at:>='2026-01-01T00:00:00Z' created_at:<='...'`（UTC，现有 `formatUtc`）。
- 列表：`orders(first: 100, after: $cursor, query: $q, sortKey: CREATED_AT, reverse: false)`；对账用 `sortKey: UPDATED_AT` 且 `q = updated_at:>='...'`；分页用 `pageInfo { hasNextPage endCursor }`，`endCursor` 放进 `ShoplineOrderLoadResult.nextPageInfo`（无下一页为 null）。自动同步游标改用 `created_at:>='lastSyncOrderTime'`，已存在订单由现有 `findByOriginOrderId` 跳过逻辑去重；`lastSyncOrderId` 继续记录但不再作为查询条件。
- 节点字段（最小集，适配器按此映射）：

| GraphQL | REST 形状（适配器输出键） |
| --- | --- |
| `legacyResourceId` | `id`（字符串数字，**保持与存量 originOrderId 一致**） |
| `id` | `admin_graphql_api_id` |
| `name`、`createdAt`、`updatedAt`、`cancelledAt`、`cancelReason`、`closedAt` | `name`、`created_at`、`updated_at`、`cancelled_at`、`cancel_reason`、`closed_at` |
| `displayFinancialStatus` | `financial_status`（小写，`PARTIALLY_PAID`→`partially_paid`） |
| `displayFulfillmentStatus` | `fulfillment_status`（`FULFILLED`→`fulfilled`，`PARTIALLY_FULFILLED`→`partial`，`UNFULFILLED`→null） |
| `currencyCode`、`presentmentCurrencyCode` | `currency`、`presentment_currency` |
| `totalPriceSet / subtotalPriceSet / totalShippingPriceSet / totalTaxSet / totalDiscountsSet { shopMoney{amount currencyCode} presentmentMoney{...} }` | `total_price_set` 等 `{shop_money:{amount,currency_code}, presentment_money:{...}}`，并补 `total_price`、`subtotal_price`、`total_tax`、`total_discounts` = shopMoney.amount |
| `email`、`phone`、`customerLocale`、`note`、`tags`、`sourceName`、`landingPageUrl`、`referrerUrl`、`clientIp` | `email`、`phone`、`customer_locale`、`note`、`tags`（逗号连接）、`source_name`、`landing_site`、`referring_site`、`browser_ip` |
| `customerJourneySummary { lastVisit { userAgent } }` | `client_details.user_agent` |
| `customAttributes { key value }` | `note_attributes[{name,value}]` |
| `customer { legacyResourceId email firstName lastName phone }` | `customer{id,email,first_name,last_name,phone}` |
| `shippingAddress` / `billingAddress { firstName lastName name company phone address1 address2 city province provinceCode zip country countryCodeV2 }` | `shipping_address` / `billing_address{first_name,last_name,name,company,phone,address1,address2,city,province,province_code,zip,country,country_code}` |
| `paymentGatewayNames` | `payment_gateway_names`、`gateway`（首个） |
| `lineItems(first: 100) { nodes { id name title variantTitle sku quantity originalUnitPriceSet{...} discountedTotalSet{...} totalDiscountSet{...} variant { legacyResourceId sku } product { legacyResourceId metafields(first: 20, namespace: "xyz") { nodes { key value } } } } }` | `line_items[{id(gid 尾号),name,title,variant_title,sku,quantity,price(=originalUnit shopMoney),price_set,total_discount_set,variant_id,product_id}]` |

适配器把每个商品的元字段写入传给转换器的 `metafieldsCache`（key = product legacy id），转换器的 `fetchMetafieldsForLineItems` 改为：缓存命中直接用；未命中（webhook 路径）调 `ShopifyGraphqlClient` 查 `nodes(ids: [gid://shopify/Product/{id}]) { ... on Product { legacyResourceId metafields(first: 20, namespace: "xyz") { nodes { key value } } } }`。

**覆盖规则**：T3 必须 grep 现有转换器中所有 `order.getStr/getJSONObject/getJSONArray/getBool...` 读取的键，逐一在适配器里给出映射，并写一个测试：用一份 GraphQL 节点样例经适配器后，转换器产出的 `EditTemporaryOrderRequest` 与同一订单的 REST 样例产出一致。

### 6.2 token

公开应用：`access_token` 1 小时、`refresh_token` 90 天（轮换）。`getAccessToken` 流程同现有（临期 5 分钟刷新、按店铺加锁、二次读库），分支点在 `refresh()`：`PUBLIC_APP` → `refreshPublicAppToken(handle, refreshToken)`，成功 `updatePublicAppTokens`；失败 → `markWebsiteAuthError(id, "授权已失效，请重新连接")`。

刷新请求：`POST https://{shop}/admin/oauth/access_token` JSON `{client_id, client_secret, grant_type: "refresh_token", refresh_token}`，响应同换 code。

### 6.3 创建/恢复店铺（`ShopifyInstallService.upsert`）

```
查 getByHandle(handle)（silent）
  无记录 → 新建：websiteType=SHOPIFY, accessMode=PUBLIC_APP, authStatus=AUTHED, status=VALID,
           owner=指定用户, companyId=指定租户, nickName, currencyMode, lastSyncTime=now,
           lastSyncOrderTime=now（只同步绑定之后的订单）, token/refresh/过期/scopes/installedAt
  有记录且 companyId != 租户 → HANDLE_TAKEN
  有记录且 accessMode=CREDENTIALS → HANDLE_TAKEN
  有记录且 PUBLIC_APP → 恢复：authStatus=AUTHED, status=VALID, owner=本次用户, token 等更新, installedAt=now, uninstalledAt=null,
           保留 lastSyncOrderTime/lastReconcileTime；若 uninstalledAt 不为空则 lastReconcileTime = uninstalledAt - lookback
写库后若 register-webhooks-on-install=true：GraphQL webhookSubscriptionCreate（topics ORDERS_CREATE/ORDERS_UPDATED/ORDERS_CANCELLED/APP_UNINSTALLED，
  callbackUrl={app-host}/shopify-app/webhooks，format JSON），先 webhookSubscriptions(first:50) 查重；失败只记日志不影响绑定。
```

### 6.4 webhook 处理（`ShopifyWebhookProcessor`，`@Scheduled(fixedDelay = 2000, initialDelay = 30000)`）

每轮取 `state=PENDING and next_attempt_at<=now` 最早 50 条；每条：
1. 店铺：`website_id` 为空且 topic 为合规类 → 记 `ShopifyComplianceRecord`（state DONE，note「店铺不存在」）→ DONE。
2. `TenantContext.setCurrentTenant(companyId)`。
3. 按 topic：
   - `orders/create` → `importWebhookOrder(dto, payload, false)`（已存在则跳过）
   - `orders/updated` → `importWebhookOrder(dto, payload, true)`（不存在则创建）
   - `orders/cancelled` → `temporaryOrderService.markCustomerCancelled(payload.id)`
   - `app/uninstalled` → `websiteStore.markDisconnected(id)`
   - `shop/redact` → `ShopifyComplianceService.shopRedact`
   - `customers/redact` → `ShopifyComplianceService.customersRedact`
   - `customers/data_request` → `ShopifyComplianceService.customersDataRequest`
4. 成功 DONE；异常 `attempts++`，`next_attempt_at = now + 2^attempts 分钟`，≥5 次 FAILED 并记 error。
5. 店铺 `status=INVALID`（暂停）时订单类 topic 直接 DONE 并记 note「店铺已暂停」；恢复后由对账补齐。

### 6.5 对账与轮询（`ShopifyOrderSyncExecutor`）

- `CREDENTIALS` 店铺：现有节奏（每分钟 `loadOrders` AUTO + 每 5 分钟取消检查），底层改 GraphQL。
- `PUBLIC_APP` 店铺：每 `reconcile-interval-minutes`：`since = lastReconcileTime ?: installedAt`，`reconcileOrders(dto, since - lookback)`，更新 `lastReconcileTime` 为返回值。不做每分钟轮询，不做独立取消检查。

### 6.6 合规与脱敏（`ShopifyComplianceService` / `ShopifyPiiRedactionService`）

- `shop/redact`：记录 → `redactByWebsite(companyId, websiteId)` → `clearCredentials` → 记录 DONE + affected_count。
- `customers/redact`：记录 → `redactByCustomer(companyId, websiteId, orders_to_redact, customer.email, customer.phone)` → DONE。
- `customers/data_request`：记录 state=MANUAL → `noticeService.createNotice(title="Shopify 客户数据请求", content="店铺 {handle}，客户 {email}，涉及订单 {ids}，请在 {due_at} 前导出并交付商家", type="SHOPIFY_COMPLIANCE", userId=店铺 owner)`。
- 脱敏 SQL（JPQL，仓库新增 `@Modifying`）：对 `TemporaryOrder` 与 `Order`，条件 `companyId = ? and platform = SHOPIFY and contextInfo.websiteId = ? and piiRedactedAt is null`（按客户时再加 `originOrderId in (?)` 或 `deliveryInfo.email = ?` 或 `deliveryInfo.phone = ?`）；set `deliveryInfo.firstName='[redacted]', lastName='[redacted]', phone='', phoneLast8=null, email=null, address=null, postalCode=null, district=null, remark=null, piiRedactedAt=now`；`riskInfo.remoteIp` 置空（风险记录是独立实体，按订单 id 再更新一次）。`city/province/country` 保留。
- 保留期任务 `@Scheduled(cron = "0 0 3 * * ?")`：`redactExpired(pii-retention-days)`，条件 `platform=SHOPIFY and updateTime < now - days and piiRedactedAt is null`，silent 全租户；同时删除 `access_time < now - access-log-retention-days` 的访问日志。
- 导入更新（`importWebhookOrder(updateExisting=true)` / 手动同步）遇到 `piiRedactedAt != null` 的订单：不更新 `deliveryInfo`、`riskInfo`，其余字段照常。

### 6.7 访问日志

`TemporaryOrderController./page` 与 `OrderController./page` 响应前：筛出 `platform == SHOPIFY` 的行，`record("LIST", type, ids)`；`OrderController./download` 同理 `record("EXPORT", "ORDER", ids)`。日志写入异步（`@Async` 或直接在事务外），失败不影响主流程。

---

## 7. 前端（`v7-shop-admin`）

- `src/api/ThirdPartyWebsite.ts` 新增 `shopifyInstallUrl(data)`（`/third-party-website/shopify/install-url`）、`shopifyBind(data)`（`/third-party-website/shopify/bind`）。
- `ThirdPartyWebsiteEdit.vue`：`websiteType=SHOPIFY` 时增加「接入方式」单选（公开应用 / 凭证模式，默认公开应用）。公开应用：显示店铺名称、HANDLE、币种模式，底部按钮「连接 Shopify」→ 调 `shopifyInstallUrl` → `window.location.href = authorizeUrl`。凭证模式：现有表单。编辑 `accessMode=PUBLIC_APP` 记录：只显示店铺名称、币种模式，保存调 `doEditShopify`。
- `ThirdPartyWebsite.vue`：新列「接入方式」；`authStatus=DISCONNECTED` 显示灰色「已断开」标签（tooltip「商家已卸载应用」）；`PUBLIC_APP` 且 `authStatus in (DISCONNECTED, ERROR)` 操作列显示「重新连接」（调 `shopifyInstallUrl` 带 id）；「编辑」对 `PUBLIC_APP` 仍可用。
- `ThirdPartyOrderSync.vue`：`row.websiteType=SHOPIFY && !row.hasAllOrdersScope` 时起始时间最小值 = 今天 - 60 天，默认值也改为该值，表单下方提示「未获得全部订单权限，只能同步最近 60 天」。
- 新页 `src/views/website/ShopifyBind.vue`，路由加入 `src/router/index.ts` 的 `constantRoutes`：`{ path: '/shopify-bind', name: 'ShopifyBind', component, meta: { hidden: true, title: 'Shopify 绑定' } }`（**不加**白名单，未登录会被导到登录页并保留 `redirect`）。逻辑：有 `bindTicket` → 调 `shopifyBind({ ticket: bindTicket })`（URL 参数名用 `bindTicket`，避免与 SSO 的 `ticket` 撞名），成功显示「已连接 {handle}」；有 `status=success` → 显示成功；`status=error` → 按 `code` 显示文案。页面提供按钮「前往第三方商城」（`router.push({ name: 'ThirdPartyWebsite' })`，若该路由名不存在则回首页）。

---

## 8. 文档站与应用声明

- `v7-shop-docs/docusaurus.config.ts`：`i18n.locales = ["zh-Hans", "en"]`，`localeConfigs.en.label = "English"`，navbar 加 `localeDropdown`。
- `i18n/en/docusaurus-plugin-content-docs/current/privacy-policy.mdx`（`sidebar_position` 置底）；`i18n/en/docusaurus-plugin-content-docs/current/third-party-order-sync/shopify.mdx`（英文安装指南：从 App Store 或后台安装、绑定、同步、暂停、卸载、数据与隐私要点）；`docs/privacy-policy.mdx` 中文同内容；中文 `shopify.mdx` 更新为公开应用流程并保留凭证模式作为「组织内自用」附录。隐私政策 URL：`https://docs.vantasite.com/en/privacy-policy`。
- `v7-shop-services/shopify.app.toml`：
  ```toml
  client_id = "<在 Dev Dashboard 填>"
  name = "VantaSite Order Sync"
  application_url = "https://<app-host>/shopify-app/landing"
  embedded = false
  [access_scopes]
  scopes = "read_orders,read_products"
  [auth]
  redirect_urls = ["https://<app-host>/shopify-app/oauth/callback"]
  [webhooks]
  api_version = "2026-07"
  [[webhooks.subscriptions]]
  topics = ["orders/create", "orders/updated", "orders/cancelled", "app/uninstalled"]
  uri = "https://<app-host>/shopify-app/webhooks"
  [[webhooks.subscriptions]]
  compliance_topics = ["customers/data_request", "customers/redact", "shop/redact"]
  uri = "https://<app-host>/shopify-app/webhooks"
  ```
- `docs/shopify-app-store-listing.md` 的「Testing instructions」与「Screenshots 顺序」改为 OAuth 流程（T6 负责）。

---

## 9. 权限与安全

- OAuth `state` = 一次性票据 nonce；回调同时校验 HMAC、shop 格式、票据 handle；票据使用即删。
- webhook 用原始字节算 HMAC，常量时间比较；失败 401 不落库。
- 票据内含 token 的时间不超过 30 分钟；Redis 值为 JSON，不写日志。
- `clientSecret` 只在配置；日志不打印 token/refresh token/secret。
- 公开端点不设租户前不得触碰租户表；落地页 `resolve` 只返回我们自己域名的跳转地址（必须解析到已登记公司）。
- 前端 `/shopify-bind` 不在路由白名单，绑定接口需登录与 `third-party-website.create`。

---

## 10. 性能与容量

- webhook 端点只做 HMAC + 一次插入，目标 < 200 ms。
- 处理器单线程每 2 秒 50 条，足以覆盖单店 100 单/分钟；多店时顺序处理，如积压可把 `fixedDelay` 调小（配置化不在首版）。
- GraphQL 每页 100 单 + 内联元字段，成本约 1 单 ≈ 10–15 点，单页 < 1500 点，低于 2000 点上限；限流由 `throttleStatus` 退避。
- 现有每店铺 2 次/秒限流器复用于 GraphQL。

---

## 11. 兼容与回滚

- 存量 `SHOPIFY` 店铺 `accessMode` 为 null → 视为 `CREDENTIALS`，行为与升级前一致（只是底层改 GraphQL）。
- 新列全部可空/带默认；新表独立；`DISCONNECTED` 枚举只会由新代码写入。
- 回滚到旧版本：旧代码不认识 `DISCONNECTED`（枚举读取会报错）——回滚前先把 `DISCONNECTED` 行 `UPDATE` 为 `ERROR`，其余列旧代码忽略。
- GraphQL 版本由 `api-version` 配置，可独立调整。

---

## 12. 关键决策记录

| 决策 | 选择 | 原因 |
| --- | --- | --- |
| 公开端点落点 | admin 模块 `/shopify-app/**` | 复用 sa-token 注解式鉴权与租户拦截器白名单机制，不加工程 |
| REST → GraphQL | 适配器到 REST 形状 | webhook payload 本就是 REST 形状；转换器与测试零改动；一份映射表可测 |
| webhook 队列 | 数据库表 + 定时器 | 无新中间件；天然去重与可观测；30 天合规处理要求可追溯 |
| 安装票据 | Redis JSON，一次性 | 跨域跳转不能依赖 cookie；短 TTL；无需建表 |
| 店铺定位 | `contextInfo.websiteId` | 已存在于审单与正式订单，无需加列 |
| 断开时 token | 写占位值 | `token` 列 NOT NULL + 唯一，改列约束风险大于收益 |
| 公开应用驱动方式 | webhook + 15 分钟对账 | 官方建议；对账保证最终一致；凭证模式不改节奏 |
