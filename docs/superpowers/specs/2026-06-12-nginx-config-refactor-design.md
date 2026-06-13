# Nginx 配置体系改造 — 设计文档

**日期：** 2026-06-12
**模块：** 后端 `v7-shop-services`（admin）+ 新增顶级目录 `nginx-front/`（Go agent + docker compose）
**替代对象：** `NginxConfigWriter` 每域名生成 conf + `push.sh` 推 git + 前端机 cron 拉取 的整条链路

---

## 1. 背景

### 1.1 现状链路

```
Java(NginxConfigWriter, 硬编码模板)
  └─ 写 /www/nginx/<前端机名>/<域名>.conf   (每域名一个文件)
       └─ push.sh 提交并推送到 git 配置仓库
            └─ 各前端 nginx 机 cron 定时 git pull + reload
```

- **拓扑**：客户 → 前端 Nginx 服务器（所有公司共享入口，可多台、按国家分配）→ 各公司的服务服务器。每个公司一整套服务（Java/mall/老服务各自独立 docker compose），可能多公司同机、端口不同。前端与服务服务器同机房、互联延迟低。
- 模板两套：`NUXT_MALL`（代理 nuxt）与 legacy `THYMELEAF`/`VIKE`（仍在线上服务）。upstream 名硬编码在 `NginxConfigType` 枚举。
- 证书由 Aliyun/Gcore 云平台 API 申请（DNS 验证，**无 ACME HTTP-01 依赖**），Java 落盘到本机 `/www/certs/<companyId>/<域名>/`，随 conf 一起经 git 分发。
- 占位证书机制（scheme-g）：证书未签发前先写占位证书，保证 nginx 配置可加载。

### 1.2 痛点（本次要解决的）

1. **生效慢 + 无反馈闭环**：cron 拉取分钟级延迟；`nginx -t` 是否通过、reload 是否成功，Java 完全不知道——`FrontServer.requiredUpdate` 置 true 后**没有任何消费方重置它**。
2. **每域名一个 conf 不可扩展**：域名增长 → 文件爆炸、配置体积膨胀。
3. **证书分发方式**：私钥跟着 conf 走 git 仓库，链路黑盒（`push.sh` 不在仓库、不带参数）。
4. 改 nginx 模板必须发后端版（模板硬编码在 Java text block 里）。

---

## 2. 目标与非目标

**目标**

- 前端 nginx 配置收敛为**一份静态通用配置 + 按公司渲染的少量 map/upstream 片段**，彻底消灭 per-domain conf。
- 变更**秒级生效**（agent 轮询 + graceful reload），且有完整反馈闭环（轮询即回报）。
- 证书分发受控：声明式对账（多删少补、指纹比对增量下载）、token 鉴权。
- **公司侧零新增组件**：Java 只新增 HTTP 接口，不再 exec 任何 nginx 命令、不再 push git。
- 前端机一个 docker compose + `.env` 拉起，全新机器可一键初始化（消灭 `NginxTransfer.java` 式人肉迁移）。
- **硬约束**：任何公司的 Spring Boot 停止/重启**不得影响**前端 nginx 服务存量流量。

**非目标 / 范围外**

- 不做公司服务器高可用/主动健康检查/故障自动切换（但数据结构预留多 target，见 4.5）。
- 不改变证书申请链路（Aliyun/Gcore requester、占位证书机制照旧）。
- 不动 `FrontServer` 的 DNS 级故障切换（failoverIp/ipSwitched）既有逻辑。
- 不引入 OpenResty/Lua/Caddy——只用 vanilla nginx ≥ 1.27.4 的原生能力。

---

## 3. 已确认的设计决策（访谈纪要）

| 维度 | 决策 | 理由 |
|---|---|---|
| TLS 终止点 | **保持在前端 nginx**（否决 SNI 透传方案） | 透传需公司侧新增 TLS nginx + proxy_protocol 改造，迁移负担大；同机房下边缘缓存虽不关键，但保留无代价 |
| per-domain conf | 消灭，改为**通用 server block + 变量证书路径 + `ssl_certificate_cache`** | nginx 1.27.4 原生支持，无需 Lua |
| 配置/证书分发 | **Go agent 轮询各公司 Java 的 manifest API**，声明式对账 | 公司故障互相隔离；agent 持本地状态，控制面挂掉只影响变更不影响流量 |
| 公司侧 | **不部署任何 agent/nginx**，Java 只新增接口（manifest + 证书下载） | 用户明确要求取消公司侧 TLS 终止 nginx 与 agent |
| upstream 地址来源 | **公司 API 自描述**（manifest 返回各服务类型地址） | 公司换机器/换端口只改自己配置，前端 `.env` 不动 |
| agent 实现 | Go 单二进制，**注释教学级密度**（用户不熟 Go），新顶级目录 `nginx-front/` | 单文件部署、systemd/容器皆可 |
| 前端部署形态 | 一个 docker compose（nginx + agent 两容器），配置全走 `.env`，数据挂宿主机持久卷 | 用户指定 |
| 故障转移 | manifest 的 target 设计为列表，**v1 只填一个**，agent 渲染成 upstream 块 | 将来加备机只改数据不改架构 |
| 老模板域名 | THYMELEAF/VIKE 仍在用，**一并迁移**（服务类型→对应 upstream + 缓存行为） | `NginxConfigType` 保留为服务类型语义 |
| 迁移策略 | **新前端机平移**：新机全量同步验证后，按公司批次切解析；老机清空后下线 | 用户选定；回切=解析改回，天然按公司灰度 |
| 回报机制 | **轮询即回报**：agent 拉取时带上已应用版本与结果，Java 顺手落库 | 无反向回调、前端不暴露端口 |
| 批量删除护栏 | 单轮删除 > max(100条, 30%) → 冻结该公司更新 + 回报 error，运维 `touch /data/v7-front/ack-<公司>` 解冻；阈值 `.env` 可配 | 防公司侧 bug 误清万级域名；小集合清空不受影响 |
| 多前端机模型 | **全量独立副本**，manifest 无按机过滤；`?agent=` 仅回报归属，落独立表 `t_front_agent_report` | 加前端机=复制 compose；manifest 全局缓存一份；与 `FrontServer`/租户彻底解耦 |
| 重启复原 | 现场（配置/证书/state）全在持久卷：重启**秒级、零下载、零 reload、零控制面依赖**；**proxy_cache 不持久化**（冷启动回源重建）；内容寻址幂等收敛 | 「最小代价重启」需求；同机房回源代价低，用户选定不持久化缓存 |
| 资源调优 | `.env` 配 CPU/内存/磁盘配额：CPU/内存映射真实 docker 限制，磁盘为**计算配额**+水位保护；agent 按配额（未配则读宿主规格）自动渲染 worker/连接数/各缓存尺寸，留 ~25% 冗余；**任何公式可被 `TUNE_*` 变量显式覆盖** | 一套 compose 适配规格不一的前端机（参考机型 2C4G80G 已实算），免手工调参 |
| 日志封顶 | **access 默认 off**（排障时 `.env` 开启走 reload），`error_log` 永远保留；开启的日志落持久卷由 agent 轮转（超 256m→gzip→保留 4 份→USR1），硬上限 ~2.5G 计入磁盘预算；agent 自身 stdout `info` 级仅状态变化输出 | 2C4G 小机省 IO/盘；日志不可无界增长；不新增组件 |
| 鉴权与 token | **token 必选**（通道分发私钥，HTTPS 不提供调用方认证）：每公司一个、全前端机共用，`openssl rand -hex 32` 生成入密码库，双 token 三步轮换，恒定时间比较 + 失败鉴权告警 + 回报超时标红；不做 IP 白名单/mTLS | 加前端机零公司侧改动；按需轮换不强制定期 |

> **为何不用 git+webhook 改良**：多公司 Java 并发写同一仓库要处理冲突，且 webhook、校验回报都要另建通道——不如 agent 直连各公司 API，故障域天然按公司隔离。
>
> **为何重启不用重新下载证书**：所有渲染产物落持久卷，nginx 重启直接用上次校验通过的本地配置；agent 重启读本地状态文件，带版本轮询，版本未变零下载。只有空卷全新部署才全量同步一次。

---

## 4. 架构设计

### 4.1 总体拓扑

```
                       ┌─────────────────────────────────────────┐
                       │  前端机（docker compose，可多台）          │
 客户 ──HTTPS──▶ nginx │  ── 通用配置 + include 各公司片段           │
                       │  agent ──轮询──▶ 公司A Java /front-agent/* │──▶ 公司A mall/vike/thymeleaf
                       │        ──轮询──▶ 公司B Java /front-agent/* │──▶ 公司B ...
                       └─────────────────────────────────────────┘
```

- 「公司」= 一个 Java 实例（一套部署）。**manifest 按公司（租户）隔离**：agent 用各公司管理后台域名调用，`FrontAgentInterceptor` 按请求 Host 解析公司并设置租户，A 公司的 token + 域名取不到 B 公司的域名与私钥。多公司同实例时，`.env` 为每家公司各配一条（不同管理域名、可共享同实例 token），各得各的 map 片段。<!-- 实施修订：弃用原 silent 跨租户方案，租户隔离更安全 -->
- **每台前端机都是全量独立副本**：manifest 不做任何按机过滤，所有前端机拿到同一份内容。新增前端机 = 复制 compose + `.env`（改唯一标识），纯水平扩展；任何一台都能服务全部域名，DNS 切换即互备。原「域名按国家分配前端机」（`Country→FrontServer`）只保留 DNS/解析层职责，与配置分发彻底无关。

### 4.2 前端持久卷目录布局（宿主机挂载）

```
/data/v7-front/
├── releases/                      # agent 每次成功应用生成一个版本目录
│   ├── 20260612T1030-a1b2c3/
│   │   ├── _system/               # agent 渲染的调优配置（main/events/http，由资源配额计算）
│   │   ├── xyz/                   # 公司目录（.env 里的公司名）
│   │   │   ├── upstreams.conf     # upstream xyz_mall { server 10.0.0.5:3000; } ...
│   │   │   ├── routes.map         # 域名 → upstream 名（含泛域名后缀条目）
│   │   │   ├── certs.map          # SNI → 证书目录（解决泛域名 SNI ≠ 证书文件名）
│   │   │   ├── caches.map         # 域名 → 缓存档位（legacy /static/ 用 IMAGE_CACHE 等）
│   │   │   └── certs/<顶级域名>/fullchain.pem + privkey.pem
│   │   └── ht/ ...
│   └── 20260612T1145-d4e5f6/
├── active -> releases/20260612T1145-d4e5f6     # 原子 symlink 切换
├── placeholder/fullchain.pem + privkey.pem     # 内置自签占位证书（未知 SNI 兜底）
├── state.json                                  # agent 状态：每公司已应用版本/最近错误
└── logs/
```

nginx 主配置只 `include /data/v7-front/active/*/upstreams.conf;` 等 glob，公司增删 = 目录增删 + reload。

### 4.3 nginx 通用配置（核心片段）

```nginx
# ── http 上下文 ──────────────────────────────────────────────
# 万级域名的 map 哈希必须显式扩容：默认 map_hash_max_size=2048 在 2 万条目下
# nginx -t 直接报 "could not build map_hash" 失败。bucket 取 256 兼容超长域名。
map_hash_max_size    65536;
map_hash_bucket_size 256;

# 证书选择：SNI → 证书目录。hostnames 模式支持 .example.com 后缀匹配，
# 因此 us.example.com 的握手能命中 example.com 的证书目录。
map $ssl_server_name $cert_dir {
    hostnames;
    default     /data/v7-front/placeholder;          # 未知域名→占位证书完成握手
    include     /data/v7-front/active/*/certs.map;   # .example.com  /data/v7-front/active/xyz/certs/example.com;
}

# 路由：Host → upstream 名（agent 按「域名→服务类型」渲染，值如 xyz_mall / xyz_vike）
map $host $backend {
    hostnames;
    default     "";                                   # 空 = 未知域名
    include     /data/v7-front/active/*/routes.map;
}

# 缓存档位：legacy 域名 /static/ 走 IMAGE_CACHE，nuxt 域名为 off（不误吃 30 天缓存）
map $host $image_cache {
    hostnames;
    default     off;
    include     /data/v7-front/active/*/caches.map;
}

# 边缘缓存用容器内路径——刻意不持久化（已确认决策：同机房回源代价低），
# 重启后冷启动、回源自然重建；必须复原的「现场」只有配置/证书/agent 状态。
proxy_cache_path /var/cache/nginx/image levels=1:2 keys_zone=IMAGE_CACHE:64m max_size=10g inactive=30d;
proxy_cache_path /var/cache/nginx/nuxt  levels=1:2 keys_zone=NUXT_CACHE:64m  max_size=5g  inactive=365d;

server {                                # 80：全域跳转，无 per-domain 内容（证书走 DNS 验证，无 HTTP-01）
    listen 80 default_server;
    return 301 https://$host$request_uri;
}

server {                                # 443：唯一的通用 server
    listen 443 ssl default_server;
    http2 on;
    server_name _;

    ssl_certificate         $cert_dir/fullchain.pem;     # 变量证书路径（≥1.15.9）
    ssl_certificate_key     $cert_dir/privkey.pem;
    ssl_certificate_cache   max=16384 inactive=10m valid=5m;  # ≥1.27.4；注意是 per-worker LRU：
                                                              # 容纳热点集即可，冷域名握手时读盘+解析 <1ms；
                                                              # 内存估算 = 热点张数 × ~15KB × worker 数
    ssl_protocols TLSv1.3 TLSv1.2;
    # ……其余 ssl_*、安全响应头与现有模板一致（HSTS/nosniff/Referrer-Policy/Permissions-Policy）

    # 未知域名：占位证书完成握手后，HTTP 层直接断连
    if ($backend = "") { return 444; }

    location /_nuxt/ {                  # nuxt 静态资源（与现 NUXT_MALL 模板等价）
        proxy_pass http://$backend;
        proxy_cache NUXT_CACHE;
        proxy_cache_valid 200 365d;
        # ……proxy_set_header / expires / immutable 同现模板
    }
    location /static/ {                 # legacy 图片缓存；nuxt 域名经 caches.map 得 off
        proxy_pass http://$backend;
        proxy_cache $image_cache;       # proxy_cache 支持变量（≥1.7.9）
        proxy_cache_valid 200 301 302 30d;
    }
    location / {
        proxy_pass http://$backend;
        # ……X-Real-IP / X-Forwarded-For / X-Forwarded-Proto 同现模板（真实 IP 链路不变）
    }

    # access_log 由 _system/ 渲染控制：默认 off；开启时写 /data/v7-front/logs/access.log（log_format 含 $host）
}
```

行为差异说明（接受的变化）：
- **access_log 从 per-domain 文件改为统一文件 + `$host` 字段，且默认关闭**（`NGINX_ACCESS_LOG` 控制，排障时开启走 reload 生效；变量路径日志有 fd 开销，不取）。
- 老模板里 `root /vhost/v7-shop-mallix`、`ssl_trusted_certificate`（无 stapling，死配置）丢弃。
- map 是静态配置，**域名增删仍需 reload**——graceful reload 不断连接，生效延迟 = 轮询间隔 + reload，秒级，可接受。证书续期即使错过 reload，`valid=5m` 也保证 5 分钟内重读磁盘。
- 尺寸类参数（worker 数、连接数、证书缓存、proxy_cache 容量、会话缓存）**不写死在基础配置**，由 agent 按「资源限制与自动调优」表渲染进 release 的 `_system/` 配置，上文数值为 8G 内存档示例。另：`proxy_pass http://$backend` 的值是 upstream 块名，**无需 resolver**。

#### 规模与容量（压测口径：2 公司 × 各 1 万有效域名 = 2 万域名）

| 项目 | 量级 | 备注 |
|---|---|---|
| map 片段文本 | ~3-4 MB / 约 6 万行 | 每域名在 routes/certs/caches 各 1 行；泛域名一行覆盖主域+全部子域 |
| map 哈希驻留内存 | ~10-20 MB | 依赖 `map_hash_max_size 65536`，默认值会 `nginx -t` 失败 |
| 证书磁盘 | ~120 MB / 4 万文件 | 2 万张 × ~6KB（fullchain+privkey） |
| reload 成本 | 亚秒级 | 解析 6 万行 + 重建哈希；graceful 不断连接 |
| `ssl_certificate_cache` | per-worker LRU | `max=16384` 容纳热点；全量驻留约 15KB×2万×worker 数，按需取舍 |
| manifest JSON | ~1.2 MB/公司/次 | 仅版本变化时全量传输，日常 304 空载 |
| 首次全量同步 | 4 万个证书文件请求 | agent 32 路并发下载，分钟级完成 |

同规模对比旧方案：2 万个 conf 文件 / 4 万个 server block / 巨型 server_names 哈希，reload 秒~十秒级——新方案配置体积与 reload 成本均低一个数量级以上。

### 4.4 Java 侧新增 API（每公司实例各自提供）

新增 `FrontAgentController`（路径前缀 `/front-agent`，部署层前缀由 agent `.env` 的 baseUrl 承担；**仅 GET 只读接口**；端点用公司管理后台的 https 域名——要求公网可信证书，内网/自签场景由 agent 侧 per-company `caFile`/`insecureSkipVerify` 兜底）。`CompanyTenantInterceptor` 跳过该路径（其 Origin/Referer 域名解析不适用于服务端调用），由 `FrontAgentInterceptor`（order=100）完成 token 鉴权 + 按 Host 设租户。

**鉴权——token 不可省**：HTTPS 只解决「agent 信任服务器」，不提供「服务器识别调用方」；而本通道分发**证书私钥**与全量域名清单，无 token 等于公网任何人可下载任意店铺私钥。采用静态 Bearer token（mTLS 属过度设计）。token 全生命周期：

- **归属**：每公司一个、全前端机共用——加前端机零公司侧改动；吊销粒度 = 公司级，走轮换即可。
- **生成与存放**：公司接入时运维 `openssl rand -hex 32` 生成，事实源存运维密码库；公司侧配 `FRONT_AGENT_TOKENS=<t1>[,<t2>]`（env/`application.yml` 不进 git，走配置中心则可热轮换），前端侧填 `.env` 的 `COMPANIES`。新前端机 bootstrap 时 token 从密码库（或现有机 `.env`）取。
- **轮换（按需：人员变动/泄露怀疑/机器下线，不强制定期）三步**：公司侧加新 token（新旧并存）→ 各前端机换 `.env` + `docker compose up -d` → 确认回报正常后公司侧移除旧 token。顺序颠倒的兜底：agent 收 401 → 冻结该公司在最后已知良好 + 本地 error 日志；**401 同时中断「轮询即回报」**，由后台「回报超时（>5 分钟）标红」兜底告警——该机制同时覆盖 agent 失联/断网/鉴权失败三类故障。
- **服务端加固**：token 恒定时间比较；失败鉴权记录来源 IP 并告警（防扫描）；不做 IP 白名单（运维同步成本 > 收益，已评估弃用）。

**① manifest（清单 + 轮询即回报）**

```
GET {baseUrl}/front-agent/manifest?agent=<前端机标识>&appliedVersion=<已应用版本>&status=ok|error&message=<错误摘要>
```

- 入参里的 `agent/appliedVersion/status/message` 即回报：Java 按 `agentName` upsert 到**独立回报表 `t_front_agent_report`**（agentName 唯一键 + appliedVersion + status + error + reportedAt），后台直接可见每台前端机的同步状态。`agent` 仅作回报归属，**不参与任何过滤**；前端机无需在任何公司库预建记录，首次轮询自动出现。`FrontServer` 不动，`requiredUpdate` 字段废弃。
- 响应（`version` = 对内容算 SHA-256，**免维护自增版本号**；与 `appliedVersion` 相同时返回 `304 Not Modified`，正文为空）：

```json
{
  "version": "sha256:9f86d08...",
  "services": { "NUXT_MALL": ["10.0.0.5:3000"], "VIKE": ["10.0.0.5:8081"], "THYMELEAF": ["10.0.0.5:8080"] },
  "domains": [
    { "domain": "example.com", "serviceType": "NUXT_MALL", "fullchainSha256": "ab12...", "privkeySha256": "ef56..." },
    { "domain": "old-shop.com", "serviceType": "THYMELEAF", "fullchainSha256": "cd34...", "privkeySha256": "gh78..." }
  ]
}
```

- `services` 值为**地址列表**（v1 单元素；预留多 target → agent 渲染 upstream 多 server 实现 backup）。
- `domains` 为顶级域名粒度（泛域名覆盖全部子域名，与现模板 `%1$s *.%1$s` 语义一致）。
- 「有效域名」判定与现 `writeNginx` 触发条件对齐：有活跃 WEBSITE 子域名绑定、证书目录存在（占位或真实均可——`fullchainSha256`/`privkeySha256` 就是磁盘实际内容的指纹，**占位证书先行的现有行为自动保留**；双指纹让 agent 可对两个文件分别校验下载完整性）。
- 域名过期/删除（`DomainExpiryCheckTask`、`doDelete`）不再调任何 nginx 代码：域名从 manifest 消失，agent 下个周期自动清理。
- **性能约束（万级域名）**：证书指纹**严禁每次轮询现场读盘哈希**（1 万文件 × 15s 轮询 × N 台前端机会打穿磁盘）。实施方案：`CertFingerprintCache` 以「mtime+size」为失效键做 SHA-256 备忘录（全量读盘哈希退化为全量 stat，微秒级，且对任何写入路径鲁棒），外加 manifest 快照短 TTL 缓存（默认 3s，按租户分桶）；全量副本模型下同一快照服务任意多台 agent。

**② 证书下载**

```
GET {baseUrl}/front-agent/cert/{domain}/fullchain.pem
GET {baseUrl}/front-agent/cert/{domain}/privkey.pem
```

直接流式返回 `/www/certs/<companyId>/<domain>/` 的文件（Java 本来就把证书落在本地磁盘，此处只是读出来）。

### 4.5 Go agent（`nginx-front/agent/`，注释教学级密度）

主循环每 `POLL_INTERVAL`（默认 15s）对每家公司独立执行**声明式对账**：

```
for 每家公司 (并行、互不影响):
    GET manifest(带上次 appliedVersion + 上次结果)        # 轮询即回报
    304 → 本公司无变化，跳过
    200 → 对比本地: 多余域名标记删除 / certSha256 不一致或缺失 → 下载证书
          渲染该公司的 upstreams.conf / routes.map / certs.map / caches.map

若任一公司有变化:
    构建新 release 目录（未变化的公司目录用硬链接复制，零拷贝）
    用影子主配置对新 release 跑 nginx -t                  # agent 镜像基于同款 nginx 镜像
    通过 → 原子切换 active symlink → 向 nginx master 发 SIGHUP (graceful reload)
         → 更新 state.json（各公司 appliedVersion）
    失败 → 丢弃 release，active 不动（保持最后已知良好），状态记错误，下轮回报给对应公司
```

可靠性规则（满足「Spring Boot 重启不影响前端」硬约束）：

- **最后已知良好**：公司 API 超时/5xx/解析失败 → 该公司片段保持原样，指数退避重试；**绝不因控制面异常清空配置**。
- manifest 返回**空域名列表**是合法语义（该公司清空了），与「请求失败」严格区分；但受下方删除护栏约束。
- **批量删除护栏**：单轮删除条数 > max(100, 当前总数×30%)（`.env` 可配）→ 冻结该公司更新（保持最后已知良好）、持续回报 error；运维确认后 `touch /data/v7-front/ack-<公司名>` 解冻放行。小集合清空（阈值内）正常应用；公司整体下线走「改 `.env` 删公司」路径，不受护栏影响。
- **渲染期句法收容**：agent 生成物只允许是 map 行与 upstream 块；域名、IP:port 走正则白名单，服务类型走枚举——任何公司返回再离谱的数据，也写不出能改变 server 结构的配置。
- **证书密码学校验**：下载后先做 x509 解析 + 公私钥配对 + SHA-256 比对，验不过不落盘、回报该公司 error——坏证书到不了 nginx 面前（变量证书路径是 `nginx -t` 的盲区，必须在 agent 层拦）。
- **reload 防抖**：最小 reload 间隔 30s（`.env` 可配），密集变更合并为一次应用，防 reload 风暴导致旧 worker 堆积；空闲期变更仍是秒级生效。
- 删除证书只在「manifest 成功返回且不含该域名 + 未触发护栏」时执行；release 目录保留最近 5 个版本，**retention 永不删除 active 指向的 release**，人工回滚 = 切回 symlink + reload。
- agent 崩溃/重启：nginx 完全不受影响（只读本地文件）；agent 起来后读 `state.json` 续轮询。首次全量同步（空卷）证书下载 32 路并发。
- **内容寻址幂等收敛**：release 以渲染内容哈希命名；新渲染结果哈希 == 当前 active 哈希 → 只更新 `state.json`，**不建 release、不切 symlink、不 reload**。由此 `state.json` 丢失/损坏也能自愈：当作首次轮询全量拉取重渲染，与现场一致即零动作收敛，不产生多余 reload。
- **磁盘水位保护**：agent 每轮顺带检查数据卷可用空间，低于 max(2G, 5%) 时冻结新证书/新 release 写入并回报 error——宁可暂停更新，不可写满磁盘；日志轮转照常运行以释放空间，水位恢复自动解冻。

容器形态（`nginx-front/docker-compose.yml`）：

- `nginx` 服务：官方 `nginx:1.29-alpine`（镜像 tag 由 `.env` 的 `NGINX_IMAGE` **统一锁定**，不自动升级），挂 `/data/v7-front` + 基础配置，published 80/443，CPU/内存限制来自 `.env` 配额（不填即不限）；`restart: unless-stopped` + TCP 443 healthcheck（`nofile` 上限随调优值带出），access/error log 落持久卷由 agent 轮转封顶，docker 自身 stdout 日志驱动设 `max-size`；**入口脚本检测 `active` 缺失（空卷首启）时种子化内置最小 release**（空 map + 占位证书 + 默认调优），保证「nginx 不依赖 agent」在第一次启动同样成立；官方镜像 `STOPSIGNAL SIGQUIT` + `stop_grace_period: 30s`，计划内重启优雅排空在途连接。worker 崩溃由 master 毫秒级自动拉起（监控 error.log 的 `worker process exited` 告警即可）。**不依赖 agent**——agent 挂掉/缺席，nginx 照常启动与服务。
- `agent` 服务：基于同一 `NGINX_IMAGE` 构建 + Go 静态二进制（保证 `nginx -t` 与数据面**同版本**同行为）；`pid: "service:nginx"` 共享进程命名空间，对 PID 1（nginx master）发 `SIGHUP`，**不挂 docker.sock**。nginx 容器被**重建**（非进程重启）会令共享 pid 命名空间失效：agent 发信号失败即自杀退出，由 `restart: unless-stopped` 拉起并挂接新命名空间，自愈无需人工。

#### 重启与现场复原（最小代价重启）

必须复原的「现场」= **配置 + 证书 + agent 状态**，全部在宿主持久卷；proxy_cache 刻意不持久化（冷启动回源重建）。任何重启路径收敛为：

| 时刻 | 动作 | 依赖 |
|---|---|---|
| t0 | `restart: unless-stopped` 自动拉起容器 | 仅 docker |
| t0+~1s | nginx 解析 active release（6 万行 map 亚秒级），立即恢复服务 | 仅本地盘——**所有公司 Java 全挂也照常恢复** |
| 并行 | agent 读 `state.json`，带 `appliedVersion` 轮询 → 304 → **零下载、零 reload** | 公司 API（失败仅延迟更新，不影响服务） |
| 首批握手 | TLS 会话缓存清空 → 客户端全量握手（`ssl_session_tickets off` 维持现状，接受）；每域名首次握手读盘解析证书 <1ms | 本地盘 |

恢复 = **秒级、零下载、零 reload、零控制面依赖**。

#### 资源限制与自动调优

`.env` 三个可选配额：`FRONT_CPU_LIMIT`/`FRONT_MEM_LIMIT` 映射为 compose 的**真实容器限制**（不填 = 不限，即宿主机全量）；`FRONT_DISK_LIMIT` **仅为计算配额**——bind mount 无法容器级强制，由磁盘水位保护兜底。agent 取值优先级：`.env` > 宿主规格（容器内读 `/proc/cpuinfo`、`/proc/meminfo`、数据卷容量，语义恰好等于「未配置用宿主机大小」），按下表（整体留 ~25% 冗余）渲染 `_system/{main,events,http}.conf`，与 map 同走「内容寻址 + nginx -t + reload」管线，改 `.env` 重启 agent 即自动收敛：

| 指令 | 公式 |
|---|---|
| `worker_processes` | CPU≥4 → CPU−1（留 1 核给 agent/系统），否则取 CPU，下限 1 |
| `worker_connections` | clamp(4096, MEM_MB × 2, 65536)（8G → 16384） |
| `worker_rlimit_nofile` | worker_connections × 2 |
| `ssl_certificate_cache max` | ≤2G→4096；≤4G→8192；≤8G→16384；>8G→32768 |
| `ssl_session_cache` | MEM ≥ 4G → `shared:SSL:32m`，否则 10m |
| `proxy_cache max_size` | 缓存预算 =（磁盘配额 − 日志上限 − release/证书 ~2G）× 0.6，image:nuxt = 2:1，下限 2g/1g |
| `keys_zone` | 随 max_size 等比（每 10g 配 80m），下限 64m |

任何公式结果都可被 `.env` 的 **`TUNE_*` 前缀变量显式覆盖**（如 `TUNE_SSL_CERT_CACHE_MAX=4096`、`TUNE_WORKER_CONNECTIONS=4096`）：agent 先算公式、再套覆盖、后渲染；`NGINX_ACCESS_LOG` 开关同属调优渲染产物（改它走 reload，不重启 nginx）。**`.env` 变更一律 `docker compose up -d` 重建容器生效——`restart` 不重读 `.env`。**

> **参考机型 2C / 4G / 80G SSD / 1Gbps（生产前端机实配）实算**：workers=2、connections=8192、nofile=16384、证书缓存 8192 档（2 worker 共 ~250MB，扛 2 万域名无压力，必要时 `TUNE_SSL_CERT_CACHE_MAX=4096` 减半）、会话缓存 32m、image_cache ~30g + nuxt_cache ~15g、日志预算 ≤2.5G（access 默认 off 时仅 error 一路）、水位线 4G。整机画像：nginx ~400-500MB + agent ~50MB，4G 内存富余充足。

#### 日志封顶（agent 内建轮转）

**access 日志默认关闭**（`NGINX_ACCESS_LOG=off`，省小机 IO 与磁盘；排障时置 on，经 agent 渲染 + graceful reload 生效，无需重启 nginx）；`error_log`（warn 级）**永远开启**。开启的日志写 `/data/v7-front/logs/`；agent 每轮询周期顺带检查：单文件超 `NGINX_LOG_MAX_FILE`（默认 256m）→ 轮转改名 → gzip 压缩 → 保留 `NGINX_LOG_KEEP`（默认 4）份 → 对 nginx master 发 `USR1` 重开日志句柄。**硬上限 ≈ 256m × 5 × 2 路 ≈ 2.5G**（access 关闭时仅 error 一路），计入磁盘预算；日志在卷上可直接 grep（格式含 `$host` 可按域名检索），容器重建不丢。agent 自身日志走 stdout（`AGENT_LOG_LEVEL=info`：仅在应用新 release/护栏冻结/水位告警等状态变化时输出，排障用 `debug`），由 docker `max-size` 封顶。

`.env` 设计：

```dotenv
FRONT_SERVER_NAME=prod-dwd-fsn-01        # 本机唯一标识，仅用于回报归属（?agent=），不做任何过滤
POLL_INTERVAL=15s
DATA_DIR=/data/v7-front
NGINX_IMAGE=nginx:1.29-alpine            # nginx 与 agent 基础镜像统一锁定，杜绝 nginx -t 版本错位
FRONT_CPU_LIMIT=                         # 可选：容器 CPU 限制；空 = 宿主机全量
FRONT_MEM_LIMIT=                         # 可选：容器内存限制；空 = 宿主机全量
FRONT_DISK_LIMIT=                        # 可选：磁盘计算配额；空 = 数据卷所在盘容量
NGINX_LOG_MAX_FILE=256m                  # 日志轮转单文件上限
NGINX_LOG_KEEP=4                         # 轮转保留份数（gzip）
NGINX_ACCESS_LOG=off                     # access 日志开关：默认关（error_log 永远保留）；排障时改 on
AGENT_LOG_LEVEL=info                     # agent 日志级别：info=仅状态变化输出一行；排障用 debug
# 调优覆盖：任何 TUNE_* 变量覆盖对应自动调优公式，如 TUNE_SSL_CERT_CACHE_MAX=4096
# 注意：改 .env 一律 `docker compose up -d` 重建容器生效——`restart` 不会重读 .env
# 公司清单：JSON 数组（名称、API 基地址、token；可选 caFile/insecureSkipVerify 应对内网/自签证书）
COMPANIES='[
  {"name":"xyz","baseUrl":"https://admin.xyz-example.com","token":"<random-64>"},
  {"name":"ht","baseUrl":"https://admin.ht-example.com","token":"<random-64>"}
]'
```

新增公司 = 改 `.env` + `docker compose restart agent`（nginx 不动）。

---

## 5. 数据流

```
商户绑定域名 / 证书签发或续期 / 域名过期清理
   └─ Java 正常落库 + 落证书盘（完全不再碰 nginx）
        └─ manifest 内容随之变化 → version(SHA-256) 变化

agent 每 15s
   └─ GET manifest?agent=X&appliedVersion=V&status=ok
        ├─ Java: 记录该前端机已应用 V + 结果 → 后台可见   # 闭环
        ├─ 304 → 结束
        └─ 200 → 增量下载证书 → 渲染片段 → 新 release → nginx -t
                  ├─ 通过 → 切 symlink → SIGHUP → state.json
                  └─ 失败 → 保持旧配置 + 下轮回报 error
```

---

## 6. 边界情况

| 情况 | 处理 |
|---|---|
| 某公司 Java 重启/宕机 | 该公司片段冻结在最后已知良好版本，其他公司正常更新；nginx 流量零影响 ✅（硬约束） |
| manifest 返回空列表 | 语义合法（与请求失败严格区分）；但万级公司清空必触发删除护栏，需 touch 解冻确认 |
| 某公司域名单轮骤减 | 超 max(100, 30%) → 冻结该公司、回报 error，其他公司不受影响；`touch ack-<公司>` 解冻 |
| 下载到损坏/不配对的证书 | agent x509 解析+公私钥配对校验拒收，不落盘，该公司回报 error，nginx 无感 |
| manifest 变更风暴 | reload 防抖（最小间隔 30s）合并应用，防旧 worker 堆积 |
| nginx worker 段错误 | master 毫秒级拉起新 worker，其余 worker 持续服务；error.log 告警 |
| 容器/宿主机重启遇坏配置 | 不可能成立：active 永远指向「同版本 nginx -t 验过的不可变 release」，symlink 原子切换无半写态 |
| 容器/宿主机重启（常规） | 秒级恢复：nginx 直载 active release，agent 304 短路零下载零 reload；proxy_cache 冷启动回源重建（接受） |
| `state.json` 丢失/损坏 | agent 按首次轮询全量拉取重渲染，内容哈希与 active 一致 → 零动作收敛，无多余 reload |
| nginx 容器被重建（非进程重启） | agent 共享 pid 命名空间失效 → 发信号失败自杀，restart 策略拉起重挂新命名空间，自愈 |
| 磁盘水位不足 | 冻结新证书/新 release 写入 + 回报 error；日志轮转继续释放空间，水位恢复自动解冻 |
| 空卷首次启动 | nginx 入口脚本种子化内置最小 release（空 map+占位证书+默认调优），agent 首轮同步替换 |
| token 轮换顺序错误 / 401 | agent 冻结该公司在最后已知良好 + 本地 error；回报同时中断，由后台「回报超时标红」兜底发现 |
| `nginx -t` 失败（坏数据/坏证书） | active 不切换，丢弃 release，错误回报给**引入变化的那家公司**，其余公司变更随下一轮重试合并应用 |
| 未知 SNI / 未绑定域名 | 占位证书完成握手 + HTTP 444 断连（行为与现状「无 conf 则连不上」等效且更可控） |
| 证书申请中的新域名 | manifest 即下发占位证书内容（现 placeholderCertHolder 行为保留），真实证书签发后 certSha256 变化触发替换 |
| 泛子域名 `us.example.com` | `certs.map`/`routes.map` 均为 `hostnames` 后缀条目（`.example.com`），SNI 与 Host 都能命中 ✅ |
| 同域名同时出现在两家公司 manifest | agent 检测跨公司冲突：后到者拒绝写入并回报 error（防一家公司配置错误劫持他家域名） |
| 证书续期但恰好没触发 reload | `ssl_certificate_cache valid=5m` 兜底，5 分钟内重读磁盘 |
| agent 容器重启 / 全新前端机 | 重启零下载（state.json + 304）；空卷全新机自动全量同步——新机初始化即标准部署流程 ✅ |
| 多台前端机 | 全量独立副本，各自轮询同一组公司 API，互不依赖；任一台可服务全部域名，DNS 切换即互备 |

---

## 7. 迁移 Runbook（新机平移，按公司批次切解析）

1. **Phase 1 — Java API**（✅ 已实施 2026-06-12）：各公司实例上线 `FrontAgentController`（manifest + 证书下载 + token，token 由运维 `openssl rand -hex 32` 生成入密码库），与旧 git 链路并行，互不影响。落地文件：`FrontAgentController` / `FrontAgentInterceptor`（鉴权+按 Host 设租户）/ `FrontAgentConfigurer` / `FrontAgentManifestService` / `CertFingerprintCache` / `FrontAgentProperties` / `FrontAgentReport`+Repository / `TopLevelDomainRepository.findAllAgentServableDomains·findValidByName` / `CompanyTenantInterceptor` 放行 / `application.yml.example` 的 `application.front-agent` 段。
2. **Phase 2 — 新前端机**（✅ 交付物已实现 2026-06-12，真机部署验证待执行）：开新机（新 IP，校准 NTP——证书有效期校验依赖系统时间），部署 `nginx-front/` compose；agent 全量同步全部公司 → `nginx -t` 通过 → 用 `curl --resolve` 实测各形态域名（nuxt / legacy / 泛子域名 / 占位证书域名 / 未知域名 444）。交付物：顶级目录 `nginx-front/`——`docker-compose.yml`（nginx+agent 双容器、pid 命名空间共享、SIGQUIT 优雅停机）、`.env.example`（全参数）、`nginx/nginx.conf`（通用配置）+ 空卷首启种子机制、Go agent（9 个源文件全中文教学级注释，纯标准库零依赖）+ 28 项单测/端到端测试（含护栏冻结解冻、坏证书拒收、跨公司冲突、内容寻址收敛、防抖、调优分档、日志轮转）、`README.md` 运维手册。
   - **ultrareview 修复（✅ 2026-06-12）**：云端评审 6 项全部属实并已修复——① 跨公司冲突由「整单拒绝」改为**全局去重 + per-domain 写时复制剔除**（修复被拒公司的 `prev.Domains` 仍把争议域名渲染出去、制造 nginx 重复 key 的漏洞）；② Java `build()` 对**未配 upstream 地址的服务类型按域名跳过**（不再因一个老类型域名整单冻结全公司）；③ `buildRelease` 命名返回值 + `defer` 错误时清理 `.building-*` 临时目录；④ `gzipFile` 改 `.gz.tmp` + rename 原子化（半成品不污染日志保留）；⑤ `CertFingerprintCache` 增 `retainAll` 反向清扫 + `NoSuchFileException` 清条目（防单调增长）；⑥ compose `nofile` 参数化默认 1048576（高于调优上限 131072）。新增回归用例 7 个（Go 4 + Java 3）。
3. **Phase 3 — 按公司批次切解析**：复用 `analyzeDomain` 云平台能力做批量改解析（指向新机 IP/CNAME），一批一观察（新机 access log、回报版本、业务侧抽查）；DNS TTL 提前调低。回切 = 解析改回老机。
4. **Phase 4 — 退役**：全部域名切完 → 老前端机下线；删除 `NginxConfigWriter`、`pushAndRefresh` 的 `RuntimeUtil.exec`、`push.sh` 调用与 git 配置仓库（归档）；`FrontServer.requiredUpdate` 字段废弃，同步状态改由 `t_front_agent_report` 提供。

---

## 8. 改动文件清单

**新增 `nginx-front/`（顶级目录）**
1. `docker-compose.yml`、`.env.example`、`README.md`（部署/新增公司/回滚手册）
2. `nginx/`：基础通用配置（即 4.3）、占位证书生成脚本、空卷首启种子 release 的入口脚本
3. `agent/`：Go 源码（`main.go` 主循环 / `config.go` .env 解析 / `company.go` 单公司对账 / `render.go` 片段渲染 / `tuning.go` 资源调优计算 / `logrotate.go` 日志轮转与磁盘水位 / `nginxctl.go` nginx -t 与 SIGHUP·USR1 / `state.go` 状态文件），**全部中文教学级注释**；`Dockerfile`
4. agent 单测：对账逻辑（增/删/指纹变化/空列表/请求失败保持现状/跨公司冲突/删除护栏冻结与 touch 解冻/坏证书拒收/reload 防抖）、渲染快照测试、调优公式分档测试、日志轮转与磁盘水位测试

**后端 `v7-shop-services`**
5. **新增** `v7-shop-admin/.../controller/FrontAgentController.java` + `FrontAgentService`（manifest 组装、SHA-256 version、304、回报落库、证书流式输出、token 校验）
6. **新增** `t_front_agent_report` 实体 + Repository（agentName 唯一键，轮询时 upsert）；`FrontServer` 实体不动，`requiredUpdate` 废弃
7. **Phase 4 删除**：`NginxConfigWriter`（及调用点 `SubDomainService`/`TopLevelDomainService`/`DomainExpiryCheckTask` 的写 conf 分支）、`FrontServerService.pushAndRefresh` 的 exec、`NginxTransfer.java`
8. `NginxConfigType` **保留**（语义降级为「服务类型」，manifest 的 `serviceType` 即它）

---

## 9. 测试与验证

- **Java**：manifest 内容/version 稳定性（同数据同 hash）、304 路径、**多 token 并存/401/恒定时间比较**、回报 upsert 落库与超时标红 — `./gradlew :v7-shop-admin:test`
- **agent**：`go test ./...`；本地用 docker compose + WireMock（模拟两家公司 API）跑端到端：增删域名、证书轮换、公司 API 宕机、坏证书被密码学校验拒收、批量删除触发护栏与 touch 解冻、变更风暴防抖、坏 map 数据被白名单拒绝
- **容量验证**：用脚本生成 2 公司 × 1 万域名的假 manifest + 自签证书，实测 map 哈希内存、reload 耗时、首次全量同步时长、`ssl_certificate_cache` 命中率与内存
- **手动验收**：4.3 行为差异逐条核对（统一 access log、444、占位证书）；`openssl s_client -servername` 验证 SNI 选证；压测确认 `ssl_certificate_cache` 生效（握手无磁盘 IO 尖刺）
