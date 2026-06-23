# v7 前端机（nginx + agent）

多公司共享的前端入口：一份**通用 nginx 配置**承载全部域名（无 per-domain conf），
Go agent 轮询各公司 Java 的 manifest API，把域名路由、证书、调优参数渲染成
**不可变 release** 并原子切换。设计文档：
`docs/superpowers/specs/2026-06-12-nginx-config-refactor-design.md`。

```
客户 ──HTTPS──▶ nginx 容器（数据面，只读本地 active release）
                  ▲ SIGHUP/USR1（共享 pid 命名空间）
               agent 容器 ──轮询──▶ 公司A Java /front-agent/*（Bearer token）
                          ──轮询──▶ 公司B Java /front-agent/*
```

核心保证：

- **任何公司 Java 宕机/重启不影响本机流量**——nginx 只读本地文件，控制面故障只是变更延迟。
- **重启秒级复原现场**：配置/证书/状态都在持久卷；agent 重启读 `state.json` 带版本轮询，304 零下载。
- **变更安全**：渲染白名单 → 证书密码学校验 → 同版本 `nginx -t` 影子校验 → 原子切换 → graceful reload；任一步失败保持最后已知良好。

## 目录结构

```
nginx-front/
├── docker-compose.yml        # nginx + agent 两个服务
├── .env.example              # 全部配置项（复制为 .env 后填写）
├── nginx/
│   ├── nginx.conf            # 静态主配置（动态内容全部 include 自 active/）
│   ├── docker-entrypoint.d/00-seed-release.sh   # 空卷首启种子脚本
│   └── seed/_system/         # 种子调优配置
└── agent/                    # Go agent 源码（全中文注释）+ Dockerfile + 单测
```

持久卷（`HOST_DATA_DIR`，默认 `/data/v7-front`）布局：

```
releases/<UTC时间戳>-<哈希8位>/   # 不可变 release（保留最近 5 个）
│   ├── _system/{main,events,http}.conf   # 按资源配额渲染的调优参数
│   └── <公司>/{routes,certs,caches}.map + upstreams.conf + certs/<域名>/*.pem
active -> releases/...            # 当前生效 release（原子软链）
placeholder/                      # 占位证书（未知 SNI 兜底，agent 自动生成）
state.json                        # agent 状态（各公司已应用版本）
logs/                             # nginx error/access 日志（agent 轮转封顶）
ack-<公司>                         # 批量删除护栏的解冻文件（运维 touch）
```

## 首次部署

```bash
# 0) 前置：Linux + docker compose v2；校准 NTP（证书校验依赖系统时间）
# 1) 拷贝本目录到前端机，准备配置
cp .env.example .env
vi .env        # 填 FRONT_SERVER_NAME（唯一）+ COMPANIES（token 从密码库取）

# 2) 起服务（首次会构建 agent 镜像）
docker compose up -d --build

# 3) 验证
docker compose logs -f agent      # 应看到各公司「已应用版本 …」
ls -l /data/v7-front/active       # 软链已指向真实 release
curl --resolve shop-domain.com:443:127.0.0.1 https://shop-domain.com -kI   # 实测一个域名
```

空卷首启顺序无依赖：nginx 由种子 release 先行启动（未知域名占位证书 + 444），
agent 随后全量同步（证书 32 路并发，分钟级）并替换种子。

**新增前端机 = 复制本目录 + `.env` 改唯一的 `FRONT_SERVER_NAME`**，COMPANIES 与其他机器
完全相同（全量独立副本模型），公司侧零改动；起来后各公司后台回报表自动出现新机器。

## 日常运维

| 操作 | 步骤 |
|---|---|
| **新增公司** | 运维 `openssl rand -hex 32` 生成 token 入密码库 → 公司 Java 配 `FRONT_AGENT_TOKENS` → 所有前端机 `.env` 的 `COMPANIES` 加一条 → `docker compose up -d` |
| **token 轮换** | ① 公司 Java 加新 token（新旧并存）→ ② 各前端机 `.env` 换新 + `up -d` → ③ 确认回报正常后公司侧删旧。顺序错了的表现：agent 401、该公司冻结、后台回报超时标红 |
| **批量删除解冻** | 某公司单轮删除超 max(100, 30%) 会冻结并报 error；确认是真实意图后 `touch /data/v7-front/ack-<公司名>`，下一轮放行（ack 用掉即删） |
| **开 access 日志排障** | `.env` 改 `NGINX_ACCESS_LOG=on` → `up -d`；日志在 `logs/access.log`（格式含 `$host` 可按域名 grep）；排障完改回 off |
| **回滚** | `ln -sfn /data/v7-front/releases/<旧版本> /data/v7-front/active && docker compose exec nginx nginx -s reload`；注意 agent 下轮可能再次应用新数据——根因要在公司侧修 |
| **改资源配额/调优** | `.env` 改 `FRONT_*_LIMIT` 或 `TUNE_*` → `up -d`；agent 首轮重渲染 `_system/` 并自动 reload |
| **升级 nginx 版本** | `.env` 改 `NGINX_IMAGE` → `docker compose up -d --build`（agent 镜像随之同版本重建，保证 `nginx -t` 一致） |

⚠️ **所有 `.env` 改动都要 `docker compose up -d`（重建容器）才生效，`restart` 不会重读 `.env`。**

## 排障

- `docker compose logs agent`：info 级只在状态变化时输出（应用版本/冻结/水位），排障改 `AGENT_LOG_LEVEL=debug`。
- `state.json`：各公司 `appliedVersion/lastStatus/lastError` 一目了然。
- 公司后台「前端机回报」（`t_front_agent_report`）：`reportedAt` 停滞 >5 分钟 = agent 失联/断网/鉴权失败。
- 某域名打不开：`grep <域名> /data/v7-front/active/*/routes.map` 看路由是否在；`openssl s_client -servername <域名> -connect <本机>:443` 看证书是否正确。
- agent 反复重启：多半是 nginx 容器被重建导致 pid 命名空间失效的自愈动作，稳定后自动恢复；若持续，看 nginx 容器是否健康。
- 磁盘水位告警（可用 < max(2G, 5%)）：清理无关文件或扩盘；日志轮转会持续释放空间，水位恢复自动解冻。

## 与旧 per-domain conf 方案的行为差异（已确认接受）

1. access 日志默认关闭；开启后也是统一文件 + `$host` 字段，不再 per-domain 文件。
2. 未知/未绑定域名：占位证书完成握手后 444 断连（旧方案是连接失败）。
3. 老模板里的 `root /vhost/v7-shop-mallix`、`ssl_trusted_certificate` 属死配置，已移除。
4. nuxt 域名的 `/static/` 不缓存不加 expires（由缓存档位 map 控制，与旧 NUXT 模板一致）；
   legacy 域名的 `/static/` 仍是 IMAGE_CACHE 30 天 + 强缓存头。

## agent 开发

```bash
cd agent
go build ./... && go vet ./... && go test ./...   # 纯标准库，无第三方依赖
```

源码按职责分文件，全部中文注释：`main.go`（主循环编排）、`config.go`（.env 解析）、
`company.go`（对账/护栏/证书校验）、`render.go`（渲染/内容寻址/原子切换）、
`tuning.go`（资源调优公式）、`logrotate.go`（日志封顶/磁盘水位）、
`nginxctl_linux.go`（nginx -t 与信号）、`state.go`（状态持久化）、`placeholder.go`（占位证书）。
