# 域名管理 · 证书申请「队列中（第N位）」显示 — 设计文档

**日期：** 2026-06-08
**模块：** 后端 `v7-shop-services`（admin）+ 前端 `v7-shop-admin`
**页面：** 证书管理 - 域名管理（`v7-shop-admin/src/views/ssl/TopLevelDomain.vue`）

---

## 1. 背景

域名管理页的「SSL证书」列会展示证书申请状态。当域名 `certificateRequestStatus === 'QUEUE'` 时，目前只显示「队列中」（见 `v7-shop-admin/src/utils/datetime.ts:31`）。需求是让它进一步显示**该域名在队列中的排位**，即「队列中（第N位）」，其中 N 表示前面还有多少个域名在排队 + 1。

### 现有队列机制（关键事实）

- **单线程 FIFO 执行器**：`certificateRequestAsyncExecutor` 配置 `corePoolSize=1, maxPoolSize=1, queueCapacity=100`（`v7-shop-services/v7-soft-core/.../configurer/AsyncConfig.java:13`），底层 `LinkedBlockingQueue` 严格先进先出。同一时刻至多一个域名处于 `REQUESTING`，其余处于 `QUEUE`。
- **三处入队点**，全部经由 `CertificateRequestPublisher.requestCertificate(...)`：
  1. 新增带云平台账户的域名 → `TopLevelDomainController.doEditOperate`
  2. 续期 → `TopLevelDomainController.renewCertificate`
  3. 应用启动重排 → `SslApplicationRunner.run`（遍历 `findAllQueueOrRequesting()`）
- **队列顺序当前完全在内存中，数据库不存任何顺序信息**。唯一忠实来源是执行器内存队列本身。
- **队列是全局的**：`findAllQueueOrRequesting`（`TopLevelDomainRepository.java:22`）显式跨公司、跨数据范围；所有公司的域名排在同一个执行器队列里。
- 前端在存在 `QUEUE`/`REQUESTING` 域名时**已有 5 秒轮询**（`TopLevelDomain.vue` 的 `checkAnyInSslRequesting`），新增字段可免费随轮询刷新。
- 实体 ID 由雪花算法生成（`V7IdentifierGenerator`），**时间有序**，即 id 升序 ≈ 创建顺序。

---

## 2. 目标与非目标

**目标**
- `QUEUE` 状态域名在域名管理列表显示「队列中（第N位）」。
- 排位为**全局排位**（跨所有公司），反映真实等待顺序。
- 排位与执行器**实际处理顺序逐一对应**（最高准确度）。
- 随现有 5 秒轮询自动刷新，队列消化时排位递减。

**非目标 / 范围外**
- 不改变证书申请的处理逻辑、并发度、执行器配置。
- 不修复「排队中删除域名导致的双处理」等既有行为。
- 不支持多实例分布式队列（沿用现有「单执行器单实例处理证书」假设）。

---

## 3. 已确认的设计决策

| 维度 | 决策 | 理由 |
|---|---|---|
| 显示格式 | `队列中（第N位）`，N 从 1 开始，第1位=下一个被处理 | 用户选定；最直观表达「还要等几个」 |
| 排位范围 | 全局队列（跨所有公司） | 执行器全局共享，唯有全局排位反映真实等待 |
| 实现方式 | **方案 C：内存队列 tracker** | 与执行器真实顺序逐一对应，最准；无需绕过租户过滤 |
| 启动重排顺序 | `findAllQueueOrRequesting` 加 `ORDER BY id ASC` | 让重启后的队列恢复为按创建顺序的先进先出，确定且直观 |

> **为何不用「按 updateTime 的 DB 查询」（方案 A）**：A 在常规场景够用，但 `updateTime` 会被无关保存刷新、且重启重排顺序不定，无法做到与执行器逐一对应。用户要求最准，故采用 C。
>
> **为何不直接读执行器内部队列**：执行器队列里是 `FutureTask`/`Runnable` 包装对象，domainId 埋在多层 lambda 闭包中，只能靠脆弱的反射提取；且该执行器还混跑 `refreshNginxConfig` 任务。自己在入队点记录顺序（即 tracker）才是干净、忠实的做法。

---

## 4. 架构设计

### 4.1 核心组件 `CertificateQueueTracker`

新增单例组件，位置：`cn.v7soft.admin.events.trackers.CertificateQueueTracker`（与现有 `events.event`、`events.listener` 子包风格一致）。

职责：用内存有序结构忠实镜像执行器的 FIFO 顺序，**只记录证书申请域名**（天然排除同执行器的 nginx 刷新任务）。

```java
package cn.v7soft.admin.events.trackers;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 证书申请队列顺序跟踪器。
 * 忠实镜像 certificateRequestAsyncExecutor 的 FIFO 提交顺序，
 * 仅用于展示「队列中第N位」，不参与实际调度。
 */
@Component
public class CertificateQueueTracker {

    /** 去重 + 保持首次入队顺序 */
    private final LinkedHashSet<Long> queue = new LinkedHashSet<>();

    /** 入队：发布证书申请事件前调用，追加到队尾（已存在则保持原位） */
    public synchronized void enqueue(Long domainId) {
        queue.add(domainId);
    }

    /** 出队：监听器开始处理（域名转为 REQUESTING）时调用 */
    public synchronized void remove(Long domainId) {
        queue.remove(domainId);
    }

    /** 一次性快照：domainId -> 第几位（从 1 开始），供分页批量查询 */
    public synchronized Map<Long, Integer> positionSnapshot() {
        Map<Long, Integer> snapshot = new HashMap<>();
        int pos = 1;
        for (Long id : queue) {
            snapshot.put(id, pos++);
        }
        return snapshot;
    }
}
```

设计要点：
- `LinkedHashSet`：天然去重（同一域名重复入队不占两个位），保持首次插入顺序。
- 所有方法 `synchronized`：Web 请求线程读、执行器线程移除、请求线程入队三方并发；队列 ≤100，同步开销可忽略。
- **tracker 本身即全局**：不依赖租户上下文，无需 `TenantContext.silent()`。

### 4.2 三个接入点

**① 入队** — `CertificateRequestPublisher.requestCertificate(long domainId, String server)`

所有入队点都经过此方法，故只需改这一处。**必须先入队再发布**：发布后执行器线程可能立刻开始处理并调用 `remove`，若顺序反了会出现「先移除后入队」导致永久泄漏。

```java
public void requestCertificate(long domainId, String server) {
    queueTracker.enqueue(domainId);
    try {
        publisher.publishEvent(new CertificateRequestEvent(this, domainId, server));
    } catch (RuntimeException e) {
        // 提交被拒（如队列满 TaskRejectedException）则回滚，避免泄漏
        queueTracker.remove(domainId);
        throw e;
    }
}
```

**② 出队** — `CertificateRequestListener.handleCertificateRequest(CertificateRequestEvent event)`

在方法**第一行**移除。放第一行可保证即使后续 `cloudPlatformAccount == null` 提前 `return` 或抛异常，tracker 也已清理。

```java
public void handleCertificateRequest(CertificateRequestEvent event) throws IOException {
    queueTracker.remove(event.getDomainId());   // 已被取出处理，不再算"排队中"
    // ...原有逻辑...
}
```

**③ 分页填充** — 重写 `TopLevelDomainController.page(...)`

`super.page()` 仍按当前公司分页（租户过滤照旧）；之后若本页含 `QUEUE` 行，取一次全局快照填充排位。

```java
@Override
@PostMapping("/page")
@Operation(summary = "分页查询")
public Page<TopLevelDomainResponse> page(@Valid @RequestBody QueryTopLevelDomainRequest request) {
    Page<TopLevelDomainResponse> page = super.page(request);
    boolean anyQueue = page.getContent().stream()
            .anyMatch(r -> r.getCertificateRequestStatus() == CertificateRequestStatus.QUEUE);
    if (anyQueue) {
        Map<Long, Integer> snapshot = queueTracker.positionSnapshot();
        page.getContent().forEach(r -> {
            if (r.getCertificateRequestStatus() == CertificateRequestStatus.QUEUE) {
                r.setQueuePosition(snapshot.get(Long.valueOf(r.getId())));
            }
        });
    }
    return page;
}
```

> 注意：`TopLevelDomainResponse` 的 `id` 经 `BaseController.filling()` 被设为 `String.valueOf(t.getId())`（纯数字字符串，非 Base62 的 compactId），故用 `Long.valueOf(r.getId())` 还原。

### 4.3 响应字段

`TopLevelDomainResponse` 新增字段（默认 `null`，仅在重写的 `page()` 中按需填充；其他返回该 DTO 的接口不受影响）：

```java
@Schema(title = "证书申请队列位置（第几位，从1开始；仅排队中域名有值）")
private Integer queuePosition;
```

### 4.4 前端展示

`v7-shop-admin/src/utils/datetime.ts` 的 `certbotInfoStatus`：

```js
if (row.certificateRequestStatus === 'QUEUE') {
  return row.queuePosition ? `队列中（第${row.queuePosition}位）` : '队列中'
}
```

- `certbotInfoType` 对 `QUEUE` 保持 `'info'` 不变。
- 无需其他前端改动：现有 5 秒轮询会自动重取分页，排位随队列消化逐步递减。
- `queuePosition` 为空时优雅回退为纯「队列中」。

### 4.5 启动重排排序优化

`TopLevelDomainRepository.findAllQueueOrRequesting`（`TopLevelDomainRepository.java:22`）加 `ORDER BY id ASC`：

```java
@Query("FROM TopLevelDomain WHERE certificateRequestStatus='REQUESTING' OR certificateRequestStatus='QUEUE' ORDER BY id ASC")
List<TopLevelDomain> findAllQueueOrRequesting();
```

效果：应用重启时 `SslApplicationRunner` 按创建顺序（雪花 id 升序）逐个重新入队，tracker 随之按同序填充，重启后排队恢复为符合直觉的先进先出。**此改动不影响方案 C 的准确性铁律**（显示恒等于执行器实际顺序），仅让重启后的顺序从「不确定」变为「确定且直观」。

---

## 5. 数据流

```
新增/续期/启动重排
   └─> CertificateRequestPublisher.requestCertificate(id)
          ├─ queueTracker.enqueue(id)            # tracker 队尾
          └─ publishEvent(CertificateRequestEvent)
                 └─(@Async 单线程执行器, FIFO)
                       └─> CertificateRequestListener.handleCertificateRequest
                              └─ queueTracker.remove(id)   # 取出处理，排位前移

前端轮询(5s)
   └─> POST /top-level-domain/page
          ├─ super.page()                        # 当前公司分页
          └─ queueTracker.positionSnapshot()     # 全局排位快照 → queuePosition
                 └─> 前端 certbotInfoStatus → 「队列中（第N位）」
```

---

## 6. 边界情况

| 情况 | 处理 |
|---|---|
| `REQUESTING` 中的域名 | 已从 tracker 移除，状态非 `QUEUE`，显示「申请中」，无排位 ✅ |
| 排队中域名被删除 | 监听器开头 `remove` 清理 tracker（事件仍会被处理，`getById` 返回空属既有行为，由既有 `catch(Throwable)` 兜住）|
| 提交被执行器拒绝（队列满） | `requestCertificate` 的 `catch` 回滚 `remove`，不泄漏 |
| 同一域名重复入队（连点续期） | `LinkedHashSet` 去重，保持原位；多余事件由监听器处理（既有行为）|
| 应用重启后 tracker 短暂为空 | `SslApplicationRunner` 启动即重建；空窗期 `queuePosition` 为 `null`，前端回退「队列中」✅ |
| tracker 查不到该 id（`null`） | 前端优雅回退为纯「队列中」，不报错 ✅ |
| 多实例部署 | tracker 为单实例内存态，沿用现有「单执行器单实例处理证书」假设（范围外）|

---

## 7. 改动文件清单

**后端（`v7-shop-services`）**
1. **新增** `v7-shop-admin/.../events/trackers/CertificateQueueTracker.java`
2. **改** `v7-shop-admin/.../events/CertificateRequestPublisher.java` — 注入 tracker，入队 + 回滚
3. **改** `v7-shop-admin/.../events/listener/CertificateRequestListener.java` — 方法首行 `remove`
4. **改** `v7-shop-admin/.../controller/resp/TopLevelDomainResponse.java` — 加 `queuePosition`
5. **改** `v7-shop-admin/.../controller/TopLevelDomainController.java` — 重写 `page()`
6. **改** `v7-shop-dao/.../repositories/primary/TopLevelDomainRepository.java` — `findAllQueueOrRequesting` 加 `ORDER BY id ASC`

**前端（`v7-shop-admin`）**
7. **改** `src/utils/datetime.ts` — `certbotInfoStatus` 输出「第N位」

**测试**
8. **新增** `CertificateQueueTracker` 单元测试：入队顺序、移除后前移、`positionSnapshot` 排位、去重、回滚。纯逻辑，无需 Spring 上下文。

---

## 8. 测试要点

- `enqueue(a); enqueue(b); enqueue(c)` → 快照为 `{a:1, b:2, c:3}`。
- `remove(a)` 后 → `{b:1, c:2}`（排位前移）。
- 重复 `enqueue(a)` 不改变其位置、不产生重复。
- 入队后回滚（`remove`）→ 不残留。
- （可选）并发入队/移除/读取不抛异常、不破坏结构（`synchronized` 保证）。

---

## 9. 验证方式

- 后端：`./gradlew :v7-shop-admin:test`（含新增 tracker 单测）。
- 前端：`pnpm vue-tsc` 类型检查通过。
- 手动：构造多个 `QUEUE` 域名，确认列表显示「队列中（第N位）」，且随队列消化、轮询刷新递减；`REQUESTING` 显示「申请中」无排位。
