# 一级域名 SSL 占位证书方案（方案 G + 现有状态机）

本文档描述「可编辑主题 / 域名」场景下，**占位证书**与 **Let’s Encrypt 真实证书** 的衔接设计，保证在真实证书未就绪时，Nginx 仍能加载有效 PEM、**`push` 同步后服务可正常 reload**，且不引入新的证书申请状态枚举。

---

## 1. 背景与目标

### 1.1 问题

- Nginx 配置中 `ssl_certificate` / `ssl_certificate_key` 指向固定路径下的 PEM 文件。
- 若路径下文件不存在、为空或 PEM 非法，**`nginx -t` 失败**，reload 被拒绝，甚至影响整实例。
- 一级域名数量大、动态增删，**不宜**为每个域名由运维手工生成占位证书。
- **不应**从其他业务域名拷贝真实证书链 + 私钥作为占位：私钥扩散、吊销/续期连带风险、浏览器告警语义混乱。

### 1.2 目标

1. **添加一级域名**（且配置了可用于 DNS 验证的云平台账号等）后，尽快触发真实证书申请；在此之前，目标路径上已有**合法 PEM**（自签占位）。
2. **添加二级记录**并生成 Nginx 配置时，所引用的一级域名证书路径**始终可读**，避免「证书未好则 Nginx 起不来」。
3. **不扩展** `certificateRequestStatus`：仍使用现有 **`IDLE` / `QUEUE` / `REQUESTING` / …** 语义，**不增加** `PLACEHOLDER` 等枚举值。
4. 占位与真实证书的切换对运维透明：**同一路径**，先写占位内容，真实下发后**原子替换**文件内容。

---

## 2. 方案 G：内存占位 + 按需落盘

### 2.1 核心思路

| 阶段 | 行为 |
|------|------|
| **应用启动** | 在容器/进程内调用 `openssl req -x509 …` 生成**一次**自签证书（ECDSA P-256，建议 `CN=placeholder.invalid`，有效期如 36500 天），将 **fullchain PEM** 与 **privkey PEM** 以字符串形式保存在**内存**（如 Spring `@Component` 单例字段）。**不写**固定 `_placeholder` 目录（可选：启动失败时仍可将临时文件删净）。 |
| **创建一级域名**（保存成功且需要走证书路径时） | 若目标目录下尚无有效 PEM（或校验失败），将内存中的两段 PEM **写入** `/www/certs/{companyId}/{apex}/fullchain.pem` 与 `privkey.pem`。随后将状态置为 **`QUEUE`**（与现有一致），触发异步申请真实证书。 |
| **真实证书申请成功** | 将 Let’s Encrypt 产出的文件 **原子覆盖** 上述两个路径（推荐 `mv` / 先写临时文件再 `rename`），再执行既有 **`push.sh`** 等流程。 |
| **申请失败 / 重试** | 路径上仍为占位 PEM；状态回到可重试的 **`IDLE`** 或失败态（与现有产品语义一致），由既有补偿或人工触发再次入队。 |

### 2.2 与「磁盘 `_placeholder/` + cp」的差异

- 方案 G **不依赖**全局目录 `_placeholder/`，占位材料只在**进程内存**中保留一份主本。
- 各一级域名目录下的 PEM 是**从内存写出**的副本；多实例部署时，各 Admin 实例内存中的自签内容**可能不同**，但不影响 Nginx 校验 PEM 格式与启动，仅「占位阶段」各节点文件字节可能不一致（真实证书下发后由统一流程覆盖即可）。

### 2.3 运行环境

- Admin 服务运行在 Docker（如 `openjdk:…-bookworm`）中时，镜像需具备 **`openssl` 可执行文件**（当前基础镜像 + certbot 依赖已通常自带；若精简镜像，建议在 `Dockerfile` 中 `apt-get install -y openssl` 显式声明）。
- 启动时执行一次 `openssl` 子进程生成 PEM，失败应打明确错误日志并可选择**阻止应用启动**（避免后续大量创建域名写占位失败）。

---

## 3. 状态机：沿用现有枚举，不增加 PLACEHOLDER

### 3.1 原则

- **不新增** `certificateRequestStatus = PLACEHOLDER`。
- 「磁盘上已是占位 PEM、尚未换成 Let’s Encrypt」这一事实，通过 **文件存在 + 与内存占位字节一致或经工具校验为自签** 与 **状态仍为申请前/排队/申请中** 组合表达，而非单独枚举值。

### 3.2 建议语义对照（与实现保持产品一致即可）

| `certificateRequestStatus` | 磁盘 `/www/certs/{co}/{apex}/` | 说明 |
|----------------------------|-------------------------------|------|
| `IDLE` | 无文件或无效 | 未创建或未写过占位。 |
| `IDLE`（创建域名后立即） | 已写入**占位 PEM** | 已落盘占位，尚未入队或刚保存。 |
| `QUEUE` / `REQUESTING` | 占位或部分写入中的临时态 | 与现有一致；真实 certbot 完成前多为占位。 |
| 成功态（与现有字段一致，如完成/有效） | **真实 LE 链 + 私钥** | 替换完成。 |
| 失败态 | 多为仍占位 | 可再次从 `IDLE`/`QUEUE` 重试。 |

具体枚举名以代码 `CertificateRequestStatus` 及 `SSLCertificate` 嵌入字段为准；本文只约束 **不新增 PLACEHOLDER 类型**。

---

## 4. 与现有模块的衔接（实现清单）

以下路径以仓库 `v7-shop-services/v7-shop-admin` 为主。

| 模块 | 职责 |
|------|------|
| **新增 `PlaceholderCertHolder`（或等价 Bean）** | `@PostConstruct`：调用 `openssl` 生成临时文件，读入内存字符串后删除临时文件；提供 `writeToCompanyDomain(Long companyId, String apex)` 或 `getPemPair()`。 |
| **`SslApplicationRunner` 或等价启动逻辑** | 确保在受理证书请求前，`PlaceholderCertHolder` 已完成初始化。 |
| **`TopLevelDomainService` / `TopLevelDomainController`** | 创建或更新一级域名、且需要证书路径时：若目标 PEM 缺失或 `SslCertificateUtil.valid` 失败，则调用 `PlaceholderCertHolder` 写入占位；再按现有逻辑置 `QUEUE` 并发布事件。 |
| **`CertificateRequestListener` + `BaseSslCertificateRequester`** | 成功后将 LE 结果 **原子写入** 同路径，覆盖占位；失败不删占位，避免 Nginx 断档。 |
| **`UnsupportedSslCertificateRequester`** | 不再内置业务域名的 `DEFAULT_FULLCHAIN`/`DEFAULT_PRIVKEY` 常量；兜底改为调用 `PlaceholderCertHolder` 写盘，或读内存写盘。 |
| **`NginxConfigWriter` + `SubDomainService` / `FrontServerService.pushAndRefresh`** | 模板仍指向 `/www/certs/{companyId}/{apex}/…`；占位写盘后再写 conf / push，行为与现有一致。 |
| **`DomainExpiryCheckTask`** | 若需区分占位与真实：可通过解析证书 `Subject`/`Issuer`/`NotAfter` 或与内存占位比对，避免对占位误报「业务证书过期」。 |

---

## 5. 二级域名与 Nginx（与方案 G 的关系）

- 二级域名生成的 Nginx 配置继续引用**一级域名**证书目录（通配/同 apex 策略与现网一致）。
- 只要在一级域名创建（或保存）路径上**先于或同步于**首次 Nginx 写入完成占位落盘，即可满足「写配置时证书路径已有效」。
- **`push.sh`** 仍为运维侧脚本：负责分发 `/www/certs`、`/www/nginx` 并在主机上 `nginx -t && nginx -s reload`（脚本不在本仓库时需单独维护）。

---

## 6. 安全与合规

1. **占位证书**应为专用自签（如 `CN=placeholder.invalid`），**禁止**使用线上业务域名的 LE 证书 + 私钥作为默认占位（避免泄露与误用）。
2. 历史上若已在源码或镜像中嵌入真实证书 PEM，应**轮换密钥**并清理 Git 历史中的敏感内容（独立运维流程）。
3. 占位阶段浏览器会提示**不受信任**或**名称不匹配**（若访问域名与 CN 不一致），属预期；真实证书生效后恢复正常。

---

## 7. 验收要点

- [ ] 新建一级域名后，`/www/certs/{companyId}/{apex}/` 下两文件存在且 `openssl x509 -in fullchain.pem -text -noout` 可解析。
- [ ] 该状态下 `nginx -t` 通过（在目标节点上）。
- [ ] 真实证书申请成功后，同路径文件被替换且浏览器信任（或证书链正确）。
- [ ] 申请失败时路径上仍有可读 PEM，reload 不失败。
- [ ] 未引入新的 `certificateRequestStatus` 枚举值。

---

## 8. 文档维护

- 实现变更后，可将本节与 `mall-guide/domain-setup.mdx` 中「SSL 证书」小节交叉引用（用户面向说明 vs 本技术设计）。
- 本文路径：`v7-shop-docs/docs/mall-guide/ssl-placeholder-scheme-g.md`。
