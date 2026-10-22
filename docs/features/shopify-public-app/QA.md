# QA 验收结论：Shopify 公开应用

- slug：`shopify-public-app`
- 日期：2026-10-22
- 验收方式：7 个任务分支按依赖顺序合入集成分支 `qa/shopify-public-app`（worktree `.claude/worktrees/qa-shopify-public-app`），跑自动验证 + 两个只读审查（后端 / 前端与文档）逐条对照 PRD §9、TASKS 各任务验收标准；缺陷退回原任务返工后重新合入复验。
- 说明：`/qa-merge` 技能定义与状态脚本在本次会话中丢失，本文件为手动流程产出；STATUS.md 未更新，待脚本恢复后补录。

## 1. 自动验证（返工后，集成分支 `4fd983b1`）

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 后端编译 | `gradlew :v7-soft-core:compileJava :v7-shop-dao:compileJava :v7-shop-admin:compileJava :v7-shop-entrance:compileJava` | 通过 |
| 后端全量测试 | `gradlew :v7-shop-admin:test` | 776 个用例，0 失败、0 错误 |
| 后台前端类型检查 | `pnpm vue-tsc` | 83 个错误，与基线持平（基线 83，改动文件内只有 `ThirdPartyWebsite.vue` 两处旧代码） |
| 后台前端 eslint | `pnpm lint:eslint` | 基线即无法运行（lockfile 的 eslint-config 要求 eslint 9，项目为 8.57），以 prettier 代替 |
| 文档站构建 | `npx pnpm@8 build`（hoisted 安装） | 中英文均 SUCCESS，`/en/docs/privacy-policy`、`/en/docs/third-party-order-sync/shopify` 已生成 |
| REST 残留 | `grep -rn "admin/api/" v7-shop-admin/src/main` | 只剩 `graphql.json` |

返工前首轮：后端 734 个测试通过、前端 83 错与基线持平、文档站中英文构建通过。

## 2. 审查发现与处理

| # | 级别 | 任务 | 缺陷 | 处理 | 返工提交 |
| --- | --- | --- | --- | --- | --- |
| B1 | 阻塞 | T4 / T2 | webhook 按 handle 找店铺不区分接入方式，同 handle 的凭证模式店铺会被 `app/uninstalled` 断开、被 `shop/redact` 全量脱敏并清 clientSecret | T4：webhook 只关联公开应用店铺，凭证模式店铺收到任何 topic 都不改库（订单类 IGNORED，合规类只记录）；T2：落地页在签发票据前拦下被占用的 handle，不进入 OAuth | `85a7563b`、`55149f90` |
| F1 | 严重 | T5 | 60 天同步限制条件把凭证模式店铺也算进去（后端只有公开应用路径写 `hasAllOrdersScope`），存量店铺被限制为只能同步最近 60 天 | 条件改为 `accessMode === 'PUBLIC_APP' && !hasAllOrdersScope` | `4ac6f324` |
| B2 | 一般 | T4 | `shop/redact` 只清凭证不断开店铺，异常时序未记录 | 未断开则先 `markDisconnected` 并在合规记录写明异常时序 | `85a7563b` |
| B3 | 一般 | T3 | 对账与 `orders/updated` 会把绑定前创建的历史订单写入审单表（ARCH §6.4 与 PRD §6 冲突，以 PRD 为准） | 公开应用店铺中 created_at 早于 `lastSyncOrderTime` 的订单不新建；已存在订单照常更新/取消 | `80cbbe61` |
| B4 | 一般 | T3 | 对账进度不按页落库，异常或 300 秒超时丢整轮进度，积压大时可能永久停滞 | 每页落库游标、240 秒时间预算、异常返回已推进游标、下一轮起点不早于上一轮游标 | `80cbbe61` |
| F3 | 一般 | T5 | 绑定成功后 URL 残留 `bindTicket`，刷新再次调 bind 显示失败 | bind 返回后 `router.replace` 改写为 `status=` | `4ac6f324` |
| F4 | 一般 | T2 / T6 | 落地页接受任意子站后台地址，未登录时登录页会跳到 `admin.` 总后台且丢 redirect，票据丢失 | `resolve` 统一规范化为 `admin.{顶级域名}`；落地页与文档说明填总后台地址 | `55149f90`、`15c07961` |
| F2 | 一般 | T6 | 上架清单测试说明的预期文案与落地页实际文案不一致 | 按落地页实际文案改写 | `15c07961` |
| B7 | 建议 | T4 | `customers/data_request` 遇店铺已软删除时重试到 FAILED | 置 MANUAL 并写明原因，不抛异常 | `85a7563b` |
| B5 | 建议 | T3 | 按订单 INFO 打印客户 IP/UA | 降为 DEBUG | `80cbbe61` |
| F5/F6/F7/F8 | 建议 | T5 / T6 / T2 | HANDLE_TAKEN 文案、隐私政策与指南措辞、上架清单改填真实隐私政策地址、invalid 页补后台重连提示 | 均已改 | `4ac6f324`、`15c07961`、`55149f90` |

### 未处理、记入后续
- B6：`/orders/{userId}/list` 与面单打印路径未记访问日志（ARCH §6.7 只要求列表分页与导出；PRD §6 提到打印）。建议作为二期补齐。
- 多实例部署：webhook 处理器无分布式锁（合规记录/通知可能重复）；单 JVM 内的 token 刷新锁挡不住两台机器同时刷新同一 refresh token。首版单实例部署可接受，扩容前需处理。
- `customers/redact` 只给电话时按精确匹配，库内格式与 Shopify E.164 不一致会匹配不到（邮箱与订单 id 匹配不受影响）。
- 对账回看窗口在正常运行中几乎不生效（起点取上一轮游标），只在重启后首轮按完整回看；Shopify 搜索索引延迟导致的漏单由订单 webhook 主路径兜住。
- 凭证模式自动同步吞吐约 48 单/分钟（GraphQL 成本上限导致每页 8 单），实测成本后可调大。

## 3. 验收标准对照（PRD §9）

| 条目 | 结论 | 依据 |
| --- | --- | --- |
| 路径 1 后台发起安装 → 已连接，归属当前登录人 | 代码通过，待真实环境 | `ShopifyInstallServiceTest`、`ShopifyAppControllerTest` |
| 路径 2 App Store → 落地页 → 登录 → 自动绑定 | 代码通过，待真实环境 | 同上 + `ShopifyBind.vue` 审查 |
| 票据过期 / state 伪造 / HMAC 错误 / handle 占用均失败且不写记录 | 通过 | 回调负例测试断言不换 token、不 save |
| 存量凭证模式店铺升级后继续同步，表单仍可新增凭证模式 | 通过（F1、B1 修复后） | 执行器/token/response 三处 null 按凭证模式；webhook 不作用于凭证模式店铺 |
| token 到期自动刷新；refresh 失效 → ERROR 可重新连接 | 通过 | `ShopifyTokenServiceTest` 公开应用用例 |
| 新订单 1 分钟内进入审单列表，字段与 REST 版一致 | 代码通过，待真实环境 | 适配器对比测试（GraphQL 与 REST 样例逐字段相等）；UA 字段 GraphQL 无对应（已接受） |
| 取消订单随 `orders/cancelled` 更新 | 通过 | 处理器测试 |
| 丢弃一次 webhook，对账 15 分钟内补上且不重复 | 代码通过（B4 修复后），待真实环境 | 对账测试 |
| 手动历史同步走 GraphQL，限 60 天并提示；重复同步不重复建单 | 通过 | T3/T5 测试与审查 |
| 代码中无 REST Admin API 调用 | 通过 | grep |
| 卸载后已断开、token 清除、同步停止、订单保留 | 通过 | 处理器 + store 测试 |
| 模拟 `shop/redact` / `customers/redact` / `customers/data_request` | 通过（B2、B7 修复后） | 合规服务测试；脱敏 SQL 表列名已与实体逐一对照 |
| 合规与订单 webhook：HMAC 错 401、正确 2xx、< 5 秒 | 通过（时延待真实环境） | 控制器测试 |
| 查看/导出 Shopify 订单收货人信息产生访问日志 | 通过 | T7 测试 |
| 保留期任务脱敏到期订单 | 通过（未在真实 MySQL 上跑过） | 脱敏服务测试 + Hibernate 解析临时验证 |
| 文档站 en 可访问、隐私政策 200 且内容完整 | 通过（内容），上线后验证 200 | 构建产物 |
| 落地页与结果页中英双语、手机宽度可用 | 通过 | T2 在 375px 下查看 |
| 上架清单测试说明与实际一致 | 通过（F2 修复后） | 审查 |

## 4. 必须在真实环境验证的项（上线前）
1. 用一个真实店铺（凭证模式即可）跑自动同步、手动同步、取消检查，确认 2026-07 GraphQL 字段与成本（任一字段不存在整条查询失败）。
2. 公开应用：换 code 带 `expiring=1` 的响应、refresh token 轮换；App Store 托管安装的时序（先授权还是先打开 App URL）。
3. 真实 webhook 的 HMAC、重投时 `X-Shopify-Webhook-Id` 是否不变、5 秒内响应。
4. 暂停 → 下单 → 恢复 → 15 分钟内补齐。
5. 首轮保留期任务耗时（预发评估）。
6. 托管侧 `/en/docs/privacy-policy` 返回 200。
7. 后台菜单下发的「第三方商城」路由名是否为 `ThirdPartyWebsite`（否则绑定页「前往第三方商城」回首页）。
8. 后台与 API 不同域部署时，`install-url` 请求的 Origin 能否解析到当前租户。

## 5. 结论

**通过，建议合并。** 阻塞与严重缺陷均已修复并复验；剩余项为真实环境验证与后续优化，不阻塞合入主分支。合并顺序：T1 → T3 → T2 → T4 → T7 → T5 → T6（与集成分支一致）。

## 6. 合并记录

- 用户确认（2026-10-22）原话：「确认合并」。
- 合并顺序 T1 → T3 → T2 → T4 → T7 → T5 → T6，全部无冲突；合并后 main 与集成分支 `qa/shopify-public-app`（`4fd983b1`）内容 diff 为空。
- main 合并提交：`13feb9b4`。
- 任务分支保留（`worktree-agent-*`），worktree 已删除；集成分支 `qa/shopify-public-app` 保留，其 worktree 已删除。
- STATUS.md 待状态脚本恢复后补录：tasks 已确认（「按此派发」）、7 个任务 done、qa 已确认（「确认合并」）、stage 为 done。
