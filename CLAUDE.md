# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

Always respond in Simplified Chinese (简体中文), including internal reasoning.

## Git Commits

Format: Conventional Commits，中文描述。示例：`feat(admin): 新增广告配置功能`

Never run any `git` operation unless explicitly instructed with a clear commit directive (e.g. "提交"/"commit"/"帮我提交"). "OK"/"可以了" are not authorization.

## Repository Structure

This is a monorepo containing five projects:

| 目录 | 技术栈 | 说明 |
|------|--------|------|
| `v7-shop-admin/` | Vue 3 + Vite + Element Plus | 运营后台 |
| `v7-shop-mall/` | Nuxt 4 SSR | 买家商城前台 |
| `v7-shop-services/` | Spring Boot 3 + Gradle | Java 后端服务 |
| `v7-shop-docs/` | Docusaurus 3 | 文档站点 |
| `turboflow-bridge/` | Chrome Extension MV3 | 图片翻译浏览器扩展 |

---

## v7-shop-admin (Vue 3 运营后台)

**Dev commands** (run from `v7-shop-admin/`):
```bash
pnpm dev          # 开发模式
pnpm build        # 类型检查 + 构建
pnpm build:fast   # 跳过类型检查，直接构建（内存受限时用）
pnpm lint:eslint  # ESLint 修复
pnpm lint:prettier
pnpm lint:stylelint
pnpm vue-tsc      # 仅类型检查
pnpm template     # Plop 代码生成器
```

**Architecture**:
- Path aliases: `/@/` → `src/`，`/@vab/` → 框架 layout 库
- 配置入口: `src/config/index.ts` 聚合四个子配置（cli / net / setting / theme）
- HTTP 层: `src/utils/request.ts`，基于 Axios，统一处理 Token、错误码、Loading
- 路由: `src/router/index.ts`，Hash 模式（可配置），权限路由动态挂载（`src/router/permissions.ts`）
- 状态: Pinia，模块在 `src/store/modules/`
- API: `src/api/` 下按业务分文件，每个文件对应一个后端 Controller
- 视图: `src/views/` 按业务模块组织（product / order / domain / ai / article / website / system …）
- Pre-commit hook: lint-staged（`*.{js,ts,vue}` 自动 ESLint + Prettier）

---

## v7-shop-mall (Nuxt 4 SSR 商城)

**Dev commands** (run from `v7-shop-mall/`):
```bash
pnpm dev       # 开发服务器
pnpm build     # 生产构建
pnpm generate  # 静态导出
pnpm preview   # 预览构建产物
```

Windows 快捷启动（含代理）：`start-dev.bat mall`

**Architecture**:
- Nuxt 4 目录约定：`app/` 下存放页面、组件、composables；`server/` 下存放 Nitro API 路由
- `server/api/` 按业务分组（product / order / checkout / article / address / builder / email / languages）；数据库直连 MySQL（`mysql2`），不经过 Java 后端
- `app/components/blocks/` 下的组件自动以 `Block` 前缀注册
- 样式：UnoCSS + `@unocss/reset/tailwind.css`，字体 Inter via `@nuxt/fonts`
- 运行时配置：`nuxt.config.ts` 中的 `runtimeConfig` 定义 DB / Redis / 代理 / 图片 CDN 等，通过环境变量注入

---

## v7-shop-services (Spring Boot 3 后端)

**Dev commands** (run from `v7-shop-services/`):
```bash
./gradlew build                          # 全量构建
./gradlew :v7-shop-entrance:bootRun      # 启动入口服务（开发）
./gradlew test                           # 运行测试
./gradlew :v7-shop-admin:test            # 运行单个模块测试
```

**Module dependency order**:
```
v7-soft-core  →  v7-shop-dao  →  v7-shop-common
                                       ↓
                    v7-shop-admin   v7-shop-account-service
                                       ↓
                              v7-shop-entrance  (部署入口)
```

- `v7-soft-core`：框架基础（注解、AOP、断言、响应体 `R<T>`、自定义 MySQL Dialect、数据范围过滤拦截器）
- `v7-shop-dao`：JPA 实体（entities/）、Repository 接口、枚举、多租户解析器
- `v7-shop-common`：业务通用 DTO、Forest HTTP 客户端配置（调用外部服务）、工具类
- `v7-shop-admin`：运营后台 API（Controller → Service → DAO），含事件、任务、DNS/SSL 管理
- `v7-shop-account-service`：账号服务 API
- `v7-shop-entrance`：Spring Boot 启动类，聚合所有模块，暴露最终可部署 JAR

**Key tech**:
- 认证：Sa-Token + JWT，Token 存 Redis（独立 Redis 数据库），不支持多端同时登录
- 数据库：JPA/Hibernate，双数据源（`primary` 主库 + `address` 地址库），`hbm2ddl.auto=update`
- HTTP 客户端：Forest（`common/forest/`），自定义 Jackson Converter
- 配置：`application.yml` 主配置 + `config/application-dev.yml` / `config/application-prod.yml` 环境变量覆盖文件
- API 文档：`/doc.html`（Knife4j），`/swagger-ui.html`（Springdoc）
- Aliyun Repositories 用于 Maven 依赖加速

---

## v7-shop-docs (Docusaurus 文档站)

**Dev commands** (run from `v7-shop-docs/`):
```bash
npm run start   # 本地开发
npm run build   # 生产构建
npm run serve   # 预览构建产物
```

---

## turboflow-bridge (Chrome 扩展)

纯静态文件，无构建步骤。在 Chrome `chrome://extensions/` 开启开发者模式后，"加载已解压的扩展程序" 指向 `turboflow-bridge/` 目录即可。修改 `flow-api.js` 或 `manifest.json` 后需点击扩展页面的刷新按钮。
