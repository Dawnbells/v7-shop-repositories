# Flow RPC 错误排查

## 1.5.10 上传 RPC 3 退回重派并按图片计数

新版 Flow 的 `maseQ` 返回 RPC 3（INVALID_ARGUMENT）时，单次无法判定是内容政策、图片格式还是请求字段问题。旧版 `/flow/uploadImage` 的 INVALID_ARGUMENT 一律走政策回退；而 `maseQ` 的 3 此前走通用 `FLOW_RPC_REJECTED`，首次失败就暂停整个 bridge，任务退回服务端后又会派回同一张图，形成反复 Run Now 的循环。本版改为：

- 上传 RPC 3 单独分类为 `FLOW_UPLOAD_REJECTED`，不进首次暂停名单。任务以可重试失败退回服务端重派；全局"连续失败 5 次"照常计数，如果 Flow 改了上传协议导致所有图都返回 3，5 张后仍会兜底停下。
- 本地按图片 SHA-256 摘要记住错误文案、ErrorInfo reason 和收到次数（`upload-rejection-state.js`），持久化到扩展 storage，重载后仍有效。
- 同一张图再派到本 bridge 时不再上传，直接用记住的错误以可重试失败退回并计数。这种短路失败对全局连续失败计数中立，也不触碰断连计数和预取任务。
- 含首次在内收到第 4 次时不再上传，按旧的政策回退链路处理：写本地政策缓存，上报 `policyFallback`（status `INVALID_ARGUMENT`，reason 取 ErrorInfo reason，否则 `INVALID_ARGUMENT`），服务端写入 `t_image_policy_cache`，该图以后在派发前就被拦下，不再翻译。
- 最后一次收到该图之后，连续 20 条其他任务都没再出现，就取消标记；之后再派回时重新从第一次算起。按本 bridge 收到并开始处理的任务数计算，不按时间。
- 生成 RPC `ogiZ0b` 的 3 以及其他 RPC 拒绝维持原有的首次失败暂停策略。服务端无需改动：失败上报走现有可重试分支，政策回退走现有 INVALID_ARGUMENT 契约。

验证范围：计数、阈值、20 条任务遗忘、重启后恢复、同图并发拒绝、短路退回与第 4 次政策回退的上报内容均有模拟测试；没有对真实 Google 账号发起上传。

## 1.5.8 验证辅助页加载修正

`api-2.3.5.1` 的 1.5.7 实现注册的 Trusted Types 默认规则只允许入口 `enterprise.js`，未允许 reCAPTCHA 后续从 `www.gstatic.com/recaptcha/` 加载的脚本。这是一个可导致初始化停在 `ready`、最终报 `Flow verification timed out` 的实现缺陷；仅凭该超时日志不能排除网络或 Google 侧验证延迟。

- 将脚本 URL 规则对齐到参考实现使用的 Google / gstatic HTTPS reCAPTCHA 资源目录，其他域名和目录仍拒绝。
- 辅助页身份增加初始化版本；升级后重建旧辅助页，避免不可覆盖的旧 Trusted Types 规则继续生效，不关闭用户自己打开的页面。
- 超时日志增加 `stage=script-load`、`stage=ready` 或 `stage=execute`，分别表示脚本加载、库初始化和 token 执行；脚本加载失败或 CSP 拒绝立即报告具体阶段。

此修正有二级脚本加载和旧辅助页迁移回归测试，尚未确认真实账号生成。重载扩展后再点 Run Now；若仍失败，保留新的阶段信息定位下一步。

## 1.5.7 可选 api-2.3.5.1 模式

设置中的 Generation method 增加 `api-2.3.5.1`，已有 `API` / `Flow UI` 配置保持不变。
该模式参考 TurboFlow 2.3.5.1 的图片调用流程，使用本地 RPC 常量和 REST 参数；不调用原扩展的登录、会员、批次授权、配置或统计服务。仍需在 Google Flow 网页中登录并打开项目。

- 新版 Flow：上传 `maseQ` → 生成 `ogiZ0b` → 必要时按生成媒体 ID 查询 `uurnC`。保留 `/u/<账号序号>/` 路径；不执行旧 API 模式的生成前额度查询。
- 上传和生成分别从扩展创建的非激活 `/about` 页获取新的 `IMAGE_GENERATION` 验证 token。辅助页不参与任务页识别；切换到其他模式或停止后，待当前翻译结束再关闭，用户自己打开的页面不关闭。
- 旧版 Flow：使用五分钟会话缓存、`flow/uploadImage` 和 `flowMedia:batchGenerateImages`。生成前获取页面验证 token。
- 继续使用当前项目的任务服务、并发、5–10 秒提交间隔、结果下载和上报；验证失败或协议拒绝沿用现有停止/恢复策略，不在此适配器内重复提交生成。

验证范围：协议模拟覆盖新旧站点、提交早于响应、并发结果隔离、暂停门禁、辅助页生命周期和配置兼容。真实账号新旧站点生成仍需重载扩展后，通过测试入口选择 `api-2.3.5.1` 验收；模拟测试不代表真实账号调用已通过。

## 1.5.5 API 协议修正（2026-09-28）

依据成功样本 `flow-api-check2.har` 及其中的前端脚本：

- `maseQ` 补齐第 11、12 个字段（数组索引 10、11）：工作流与媒体 ID 种子。同一次上传的传输重试保留种子、重新获取验证 token；下一次上传生成新种子。RPC 明确拒绝仍不重试。
- `nzlxg` 的真实方法是 `VideoFxService.GetCredits`，不是输入框聚焦。改名为 `RPC_GET_CREDITS`，在获取生成验证 token 之前完成额度查询，避免获取 token 后再等待该请求。UI 中两者有重叠；API 采用先完成额度检查的顺序。
- 额度查询完成后、验证完成后均重新检查提交暂停状态，防止等待期间已暂停却仍提交生成。
- `Zzl0ze` 使用 `projects/<id>` 资源名及成功样本中的查询参数，并兼容已带 `projects/` 前缀的输入。

验证：136 项测试通过；将 HAR 参数代入修正后的构造器，2 次上传、1 次生成、1 次项目查询均与成功样本完全一致。未对真实账号提交生成，仍需重载扩展和 Flow 页面后测试 API 模式。该版本修正已确认的协议差异，不代表已证明这些差异是风控根因；未新增模拟分析埋点或 Cookie 修改。

`maseQ` 是上传图片，`ogiZ0b` 是生成图片。HTTP 200 只表示传输成功；
`wrb.fr` 内部的 RPC 状态仍可能失败。

以下按 Google 的标准 `google.rpc.Code` 定义解释数值；Flow 私有 RPC 的具体拒绝原因仍需结合服务端错误消息和页面提示判断。

| 数值 | 名称 | 含义 |
| --- | --- | --- |
| 0 | OK | 成功 |
| 1 | CANCELLED | 操作被取消 |
| 2 | UNKNOWN | 原因未知 |
| 3 | INVALID_ARGUMENT | 参数无效 |
| 4 | DEADLINE_EXCEEDED | 超时，不能据此断定服务端未执行 |
| 5 | NOT_FOUND | 所需资源不存在 |
| 6 | ALREADY_EXISTS | 资源已存在 |
| 7 | PERMISSION_DENIED | 无权执行操作 |
| 8 | RESOURCE_EXHAUSTED | 资源或额度耗尽；不单指日限额 |
| 9 | FAILED_PRECONDITION | 当前状态不满足执行条件 |
| 10 | ABORTED | 操作中止，可能涉及并发冲突 |
| 11 | OUT_OF_RANGE | 超出有效范围 |
| 12 | UNIMPLEMENTED | 操作未实现或不受支持 |
| 13 | INTERNAL | 服务内部错误 |
| 14 | UNAVAILABLE | 服务暂不可用 |
| 15 | DATA_LOSS | 数据丢失或损坏 |
| 16 | UNAUTHENTICATED | 身份凭据无效 |

来源：[Google RPC 状态码定义](https://developers.google.com/actions-center/reference/grpc-api/status_codes)。

## 本次日志能确认什么

- `maseQ / 3`：上传请求参数被拒。仅凭 3 不能确定是图片格式、请求字段还是内容政策，不能自动把所有 3 都当作内容政策回退。
- `ogiZ0b / 7`：生成请求被拒绝访问。仅凭 7 不能确定是账号权限、模型权限还是验证问题，也不等同于登录过期。
  若 ErrorInfo reason 为 `PUBLIC_ERROR_UNUSUAL_ACTIVITY`（UI 上是 Tile 文案 "We noticed some unusual activity"），
  是账号/环境被风控：已实测官方 UI 手动生成也同样被拒，与请求结构无关。Bridge 按 `RECAPTCHA_BLOCKED` 走风控恢复链。
- `ogiZ0b / 8`：生成资源或额度不足。日限额是可能原因，也可能是其他配额或限速；等待时间不能仅由 8 推断。

## Bridge 处理方式

- RPC 8 默认分类为 `FLOW_RESOURCE_EXHAUSTED`；若服务端消息或 ErrorInfo reason 明确包含 `DAILY_QUOTA_REACHED`（包括 `PUBLIC_ERROR_PER_MODEL_DAILY_QUOTA_REACHED`，即模型每日额度已用尽），优先分类为 `DAILY_QUOTA_REACHED`。`PUBLIC_ERROR_HIGH_TRAFFIC` 等其他 RPC 8 不归为日限额，也不据此判断为动图。
- 两类额度错误都在第一次失败时停止领取新任务，在失败上报之前持久化停止状态；日限额显示对应提示。HTTP 429 在现代 Flow 接口上采用相同停止策略。额度错误不触发删项目或恢复链；并发任务的后续错误不能覆盖额度停止原因。
- 停止新的后台取任务请求和翻译提交，已提交的翻译继续生成、查询结果、下载并上报完成；不会因其他任务触发 8 而取消。
- 本地已预取的任务在暂停期间不启动；已经上传但尚未提交生成的任务会在提交前再次检查暂停状态，不再生成，并向后台上报可重试。已发出的取任务请求无法撤回，但返回的任务不会启动。等待额度恢复后手动点击 Run Now。
- RPC 16 / HTTP 401 提示重新登录。现代 Flow HTTP 403 映射为权限拒绝，不再误报登录失效。
- 上传 `maseQ` 的 RPC 3 自 1.5.10 起退回服务端重派并按图片计数，见文首；其他 RPC 拒绝保留原有的首次失败暂停策略，日志补上状态名称和可用的服务端消息。4、10、13、14 可能是暂时性故障；这里不新增自动重发生成请求，以免重复生成。
- RPC 拒绝和验证错误显示各自停止原因；“连续失败达到 5 次”只用于确实达到普通失败阈值的情况。
- 获取结果图片链接遇到 RPC 8、7 或认证失败时立即返回错误，不再等待最多 30 秒的资源就绪重试。其他就绪检查保留原行为。

## 验证范围

自动化测试覆盖 RPC 1–16 的状态名称、上传/生成错误信封、旧日志兼容、首次额度失败持久化、并发错误不覆盖停止原因、HTTP 401/403/429 和结果链接额度错误不重试。
另覆盖暂停后不向后台取任务、上传/验证期间暂停后不提交生成、已提交任务继续下载并上报成功，以及成功后仍保持暂停。
测试使用模拟响应；没有对真实 Google 账号发起生成请求。
