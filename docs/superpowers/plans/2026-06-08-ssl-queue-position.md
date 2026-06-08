# 域名管理证书队列「第N位」显示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让域名管理列表中处于 `QUEUE` 状态的域名显示「队列中（第N位）」，N 为该域名在全局证书申请队列中的真实排位。

**Architecture:** 新增内存单例 `CertificateQueueTracker` 忠实镜像单线程执行器 `certificateRequestAsyncExecutor` 的 FIFO 顺序——在统一入队出口 `CertificateRequestPublisher` 入队、在 `CertificateRequestListener` 处理开始时出队；分页接口 `TopLevelDomainController.page()` 重写后用一次全局快照把排位写入 `queuePosition` 响应字段；前端据此渲染。tracker 天然全局，无需绕过租户过滤。

**Tech Stack:** Java 17 + Spring Boot 3.3（Gradle 多模块）、JUnit 5 + Mockito；Vue 3 + TypeScript（pnpm，vue-tsc 类型检查）。

**设计文档：** `docs/superpowers/specs/2026-06-08-ssl-queue-position-design.md`

---

## File Structure

**后端 `v7-shop-services`**
- 新增 `v7-shop-admin/src/main/java/cn/v7soft/admin/events/trackers/CertificateQueueTracker.java` — 队列顺序跟踪器（唯一持有排队顺序的内存结构）。
- 新增 `v7-shop-admin/src/test/java/cn/v7soft/admin/events/trackers/CertificateQueueTrackerTest.java` — tracker 纯逻辑单测。
- 改 `v7-shop-admin/src/main/java/cn/v7soft/admin/events/CertificateRequestPublisher.java` — 入队 + 发布失败回滚。
- 新增 `v7-shop-admin/src/test/java/cn/v7soft/admin/events/CertificateRequestPublisherTest.java` — 入队顺序与回滚单测。
- 改 `v7-shop-admin/src/main/java/cn/v7soft/admin/events/listener/CertificateRequestListener.java` — 处理开始时出队。
- 改 `v7-shop-admin/src/test/java/cn/v7soft/admin/events/listener/CertificateRequestListenerTest.java` — 补 `@Mock` 防 NPE + 新增出队断言。
- 改 `v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/TopLevelDomainResponse.java` — 加 `queuePosition` 字段。
- 改 `v7-shop-admin/src/main/java/cn/v7soft/admin/controller/TopLevelDomainController.java` — 重写 `page()` 填充排位。
- 改 `v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/TopLevelDomainRepository.java` — `findAllQueueOrRequesting` 加 `ORDER BY id ASC`。

**前端 `v7-shop-admin`**
- 改 `src/utils/datetime.ts` — `certbotInfoStatus` 输出「队列中（第N位）」。

> 所有 `./gradlew` 命令在 `v7-shop-services/` 目录执行；所有 `pnpm` 命令在 `v7-shop-admin/` 目录执行。

---

## Task 1: 队列顺序跟踪器 CertificateQueueTracker

**Files:**
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/trackers/CertificateQueueTrackerTest.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/trackers/CertificateQueueTracker.java`

- [ ] **Step 1: 写失败测试**

创建 `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/trackers/CertificateQueueTrackerTest.java`：

```java
package cn.v7soft.admin.events.trackers;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CertificateQueueTrackerTest {

    @Test
    @DisplayName("按入队顺序给出从1开始的排位")
    void shouldReturnPositionsInEnqueueOrder() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.enqueue(20L);
        tracker.enqueue(30L);

        Map<Long, Integer> snapshot = tracker.positionSnapshot();
        assertEquals(Integer.valueOf(1), snapshot.get(10L));
        assertEquals(Integer.valueOf(2), snapshot.get(20L));
        assertEquals(Integer.valueOf(3), snapshot.get(30L));
    }

    @Test
    @DisplayName("移除队首后，后续域名排位前移")
    void shouldShiftPositionsAfterRemoveHead() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.enqueue(20L);
        tracker.enqueue(30L);

        tracker.remove(10L);

        Map<Long, Integer> snapshot = tracker.positionSnapshot();
        assertNull(snapshot.get(10L));
        assertEquals(Integer.valueOf(1), snapshot.get(20L));
        assertEquals(Integer.valueOf(2), snapshot.get(30L));
    }

    @Test
    @DisplayName("重复入队同一域名不产生重复、保持原位")
    void shouldDedupeOnDuplicateEnqueue() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.enqueue(20L);
        tracker.enqueue(10L);

        Map<Long, Integer> snapshot = tracker.positionSnapshot();
        assertEquals(2, snapshot.size());
        assertEquals(Integer.valueOf(1), snapshot.get(10L));
        assertEquals(Integer.valueOf(2), snapshot.get(20L));
    }

    @Test
    @DisplayName("未入队域名查不到排位（null）")
    void shouldReturnNullForUnknownDomain() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);

        assertNull(tracker.positionSnapshot().get(999L));
    }

    @Test
    @DisplayName("入队后移除（回滚）不残留")
    void shouldNotLeakAfterEnqueueThenRemove() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.remove(10L);

        assertEquals(0, tracker.positionSnapshot().size());
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败（类不存在）**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.events.trackers.CertificateQueueTrackerTest"`
Expected: 编译失败，`CertificateQueueTracker` 找不到符号。

- [ ] **Step 3: 写最小实现**

创建 `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/trackers/CertificateQueueTracker.java`：

```java
package cn.v7soft.admin.events.trackers;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 证书申请队列顺序跟踪器。
 * <p>
 * 忠实镜像 certificateRequestAsyncExecutor 的 FIFO 提交顺序，仅用于展示「队列中第N位」，
 * 不参与实际调度。仅记录证书申请域名，天然排除同执行器上的 nginx 刷新任务。
 */
@Component
public class CertificateQueueTracker {

    /** 去重 + 保持首次入队顺序 */
    private final LinkedHashSet<Long> queue = new LinkedHashSet<>();

    /** 入队：发布证书申请事件前调用，追加到队尾（已存在则保持原位）。 */
    public synchronized void enqueue(Long domainId) {
        queue.add(domainId);
    }

    /** 出队：监听器开始处理（域名转为 REQUESTING）时调用。 */
    public synchronized void remove(Long domainId) {
        queue.remove(domainId);
    }

    /** 一次性快照：domainId -> 第几位（从 1 开始），供分页批量查询。 */
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

- [ ] **Step 4: 运行测试，确认通过**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.events.trackers.CertificateQueueTrackerTest"`
Expected: PASS（5 个测试全绿）。

- [ ] **Step 5: 提交**

```bash
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/trackers/CertificateQueueTracker.java \
        v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/trackers/CertificateQueueTrackerTest.java
git commit -m "feat(ssl): 新增证书申请队列顺序跟踪器 CertificateQueueTracker" \
           -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 入队接线 + 发布失败回滚 CertificateRequestPublisher

**Files:**
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/CertificateRequestPublisherTest.java`
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/CertificateRequestPublisher.java`

- [ ] **Step 1: 写失败测试**

创建 `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/CertificateRequestPublisherTest.java`：

```java
package cn.v7soft.admin.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import cn.v7soft.admin.events.trackers.CertificateQueueTracker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CertificateRequestPublisherTest {

    @Mock private ApplicationEventPublisher publisher;
    @Mock private CertificateQueueTracker queueTracker;

    @InjectMocks private CertificateRequestPublisher certificateRequestPublisher;

    @Test
    @DisplayName("先入队再发布事件")
    void shouldEnqueueBeforePublish() {
        certificateRequestPublisher.requestCertificate(42L);

        InOrder inOrder = inOrder(queueTracker, publisher);
        inOrder.verify(queueTracker).enqueue(42L);
        inOrder.verify(publisher).publishEvent(any(CertificateRequestEvent.class));
    }

    @Test
    @DisplayName("发布失败时回滚出队并抛出异常")
    void shouldRemoveFromQueueWhenPublishThrows() {
        doThrow(new RuntimeException("队列已满"))
                .when(publisher).publishEvent(any(CertificateRequestEvent.class));

        assertThrows(RuntimeException.class, () -> certificateRequestPublisher.requestCertificate(42L));

        verify(queueTracker).enqueue(42L);
        verify(queueTracker).remove(42L);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.events.CertificateRequestPublisherTest"`
Expected: 编译失败——当前 `CertificateRequestPublisher` 构造器无 `CertificateQueueTracker` 参数，`@InjectMocks` 无法注入；且无入队/回滚逻辑。

- [ ] **Step 3: 写实现**

把 `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/CertificateRequestPublisher.java` 整体替换为：

```java
package cn.v7soft.admin.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import cn.v7soft.admin.events.trackers.CertificateQueueTracker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CertificateRequestPublisher {
    private final ApplicationEventPublisher publisher;
    private final CertificateQueueTracker queueTracker;

    public CertificateRequestPublisher(ApplicationEventPublisher publisher, CertificateQueueTracker queueTracker) {
        this.publisher = publisher;
        this.queueTracker = queueTracker;
    }

    public void requestCertificate(long domainId) {
        requestCertificate(domainId, null);
    }

    public void requestCertificate(long domainId, String server) {
        // 必须先入队再发布：发布后执行器线程可能立刻开始处理并 remove，顺序反了会"先移除后入队"导致泄漏
        queueTracker.enqueue(domainId);
        try {
            publisher.publishEvent(new CertificateRequestEvent(this, domainId, server));
        } catch (RuntimeException e) {
            // 提交被拒（如队列满）则回滚出队，避免残留
            queueTracker.remove(domainId);
            throw e;
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.events.CertificateRequestPublisherTest"`
Expected: PASS（2 个测试）。

- [ ] **Step 5: 提交**

```bash
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/CertificateRequestPublisher.java \
        v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/CertificateRequestPublisherTest.java
git commit -m "feat(ssl): 证书申请入队跟踪并在发布失败时回滚" \
           -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 出队接线 CertificateRequestListener（并修好既有测试）

**Files:**
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/listener/CertificateRequestListener.java`
- Modify: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/listener/CertificateRequestListenerTest.java`

> **关键：** 监听器新增了 `CertificateQueueTracker` 依赖且在 `handleCertificateRequest` 第一行调用 `queueTracker.remove(...)`。既有测试用 `@InjectMocks`，若不补 `@Mock CertificateQueueTracker`，原有 5 个测试都会在第一行 NPE。本任务一并修好。

- [ ] **Step 1: 更新测试（新增 mock + 新增出队断言）**

编辑 `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/listener/CertificateRequestListenerTest.java`：

1）在 import 区加入：
```java
import cn.v7soft.admin.events.trackers.CertificateQueueTracker;
```

2）在 `@Mock private ISslCertificateRequester certificateRequester;` 这一行下方，新增一个 mock 字段：
```java
    @Mock private CertificateQueueTracker queueTracker;
```

3）在类中追加一个新测试方法（放在最后一个 `@Test` 方法之后、`isBlank` 私有方法之前）：
```java
    @Test
    @DisplayName("处理开始即从队列跟踪器移除该域名")
    void shouldRemoveFromQueueTrackerAtStart() throws IOException {
        TopLevelDomain domain = buildDomain(true);
        lenient().when(topLevelDomainService.getById(1L)).thenReturn(domain);

        SslResult success = SslResult.builder()
                .isSuccess(true).isCompleted(true).isError(false)
                .result("ok").errLog("").errorMsg("")
                .build();
        lenient().when(certificateRequester.handleRequestSslCertificate(any(), any())).thenReturn(success);
        doReturn("push ok").when(listener).executePushScript();

        listener.handleCertificateRequest(new CertificateRequestEvent(this, 1L, null));

        verify(queueTracker, times(1)).remove(1L);
    }
```
（所需静态导入 `verify`、`times`、`any`、`doReturn`、`lenient` 在该文件中均已存在。）

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.events.listener.CertificateRequestListenerTest"`
Expected: 编译失败——`CertificateRequestListener` 构造器尚无 `CertificateQueueTracker` 参数，且无 `remove` 调用，新测试 `verify(...).remove(1L)` 失败。

- [ ] **Step 3: 写实现**

编辑 `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/listener/CertificateRequestListener.java`：

1）在 import 区加入：
```java
import cn.v7soft.admin.events.trackers.CertificateQueueTracker;
```

2）在字段区（`private final PlaceholderCertHolder placeholderCertHolder;` 之后）新增字段（类已标注 `@AllArgsConstructor`，构造器参数会自动带上）：
```java
    private final CertificateQueueTracker queueTracker;
```

3）把 `handleCertificateRequest` 方法体的**第一行**改为先出队。即将：
```java
    public void handleCertificateRequest(CertificateRequestEvent event) throws IOException {
        TopLevelDomain domain = null;
```
改为：
```java
    public void handleCertificateRequest(CertificateRequestEvent event) throws IOException {
        // 已被执行器取出处理，不再算"排队中"——放首行确保任何提前 return / 异常路径也已清理
        queueTracker.remove(event.getDomainId());
        TopLevelDomain domain = null;
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test --tests "cn.v7soft.admin.events.listener.CertificateRequestListenerTest"`
Expected: PASS（原 5 个 + 新增 1 个，共 6 个）。

- [ ] **Step 5: 提交**

```bash
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/events/listener/CertificateRequestListener.java \
        v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/events/listener/CertificateRequestListenerTest.java
git commit -m "feat(ssl): 证书处理开始时从队列跟踪器出队" \
           -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 响应新增 queuePosition 字段

**Files:**
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/TopLevelDomainResponse.java`

- [ ] **Step 1: 加字段**

编辑 `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/TopLevelDomainResponse.java`，在字段 `private List<PixelAccountResponse> pixels;` 之后、`public static TopLevelDomainResponse convertEntity(...)` 之前，新增：

```java
    @Schema(title = "证书申请队列位置（第几位，从1开始；仅排队中域名有值）")
    private Integer queuePosition;
```

> 该字段默认 `null`，仅在 Task 5 重写的 `page()` 中按需填充；`convertEntity` 不设置它，其他返回该 DTO 的接口不受影响。`@Getter/@Setter` 已在类上，自动生成 `getQueuePosition/setQueuePosition`。

- [ ] **Step 2: 编译验证**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:compileJava`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/TopLevelDomainResponse.java
git commit -m "feat(ssl): 域名响应新增 queuePosition 队列排位字段" \
           -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: 重写 TopLevelDomainController.page() 填充排位

**Files:**
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/TopLevelDomainController.java`

- [ ] **Step 1: 加导入**

编辑 `TopLevelDomainController.java`，在 import 区加入：
```java
import java.util.Map;

import org.springframework.data.domain.Page;

import cn.v7soft.admin.events.trackers.CertificateQueueTracker;
```
（`CertificateRequestStatus`、`QueryTopLevelDomainRequest`、`TopLevelDomainResponse`、`PostMapping`、`Operation`、`Valid`、`RequestBody` 已在该文件导入。）

- [ ] **Step 2: 注入 tracker（加字段 + 构造器参数）**

在字段区 `private final PlaceholderCertHolder placeholderCertHolder;` 之后新增：
```java
    private final CertificateQueueTracker queueTracker;
```

把构造器替换为（新增最后一个参数与赋值）：
```java
    protected TopLevelDomainController(ITopLevelDomainService service, CertificateRequestPublisher certificateRequestPublisher,
                                       ICloudPlatformAccountService cloudPlatformAccountService, DnsServiceFactory dnsServiceFactory,
                                       PlaceholderCertHolder placeholderCertHolder, CertificateQueueTracker queueTracker) {
        super(service);
        this.certificateRequestPublisher = certificateRequestPublisher;
        this.cloudPlatformAccountService = cloudPlatformAccountService;
        this.dnsServiceFactory = dnsServiceFactory;
        this.placeholderCertHolder = placeholderCertHolder;
        this.queueTracker = queueTracker;
    }
```

- [ ] **Step 3: 重写 page()**

在 `convertQueryPageRequest(...)` 方法之前（紧跟构造器之后）新增重写方法：
```java
    @Override
    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<TopLevelDomainResponse> page(@Valid @RequestBody QueryTopLevelDomainRequest request) {
        Page<TopLevelDomainResponse> page = super.page(request);
        boolean anyQueue = page.getContent().stream()
                .anyMatch(r -> r.getCertificateRequestStatus() == CertificateRequestStatus.QUEUE);
        if (anyQueue) {
            // tracker 为全局内存结构，快照即跨公司真实排位，无需绕过租户过滤
            Map<Long, Integer> snapshot = queueTracker.positionSnapshot();
            page.getContent().forEach(r -> {
                if (r.getCertificateRequestStatus() == CertificateRequestStatus.QUEUE) {
                    // r.getId() 经 BaseController.filling() 设为纯数字字符串，可直接还原为 Long
                    r.setQueuePosition(snapshot.get(Long.valueOf(r.getId())));
                }
            });
        }
        return page;
    }
```

- [ ] **Step 4: 编译并跑 admin 全量测试（确保未破坏既有测试 + 接线正确）**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test`
Expected: BUILD SUCCESSFUL，全部测试通过。

- [ ] **Step 5: 提交**

```bash
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/TopLevelDomainController.java
git commit -m "feat(ssl): 分页接口填充全局队列排位 queuePosition" \
           -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: 启动重排按创建顺序（findAllQueueOrRequesting 加 ORDER BY id ASC）

**Files:**
- Modify: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/TopLevelDomainRepository.java`

- [ ] **Step 1: 改查询**

编辑 `TopLevelDomainRepository.java`，把 `findAllQueueOrRequesting` 的注释与 `@Query` 改为：
```java
    /**
     * 获取所有正在申请证书和排队申请证书的域名，不限公司和数据范围。
     * 按 id 升序（雪花ID≈创建顺序），使应用重启重排后队列恢复为符合直觉的先进先出。
     */
    @Query("FROM TopLevelDomain WHERE certificateRequestStatus='REQUESTING' OR certificateRequestStatus='QUEUE' ORDER BY id ASC")
    List<TopLevelDomain> findAllQueueOrRequesting();
```

- [ ] **Step 2: 编译验证**

Run: `cd v7-shop-services && ./gradlew :v7-shop-dao:compileJava`
Expected: BUILD SUCCESSFUL。

> 说明：JPQL 在应用启动时由 Hibernate 校验；此处仅在既有可用查询上追加 `ORDER BY id ASC`，风险极低。如条件允许，可在本地启动应用确认无 JPQL 解析告警。

- [ ] **Step 3: 提交**

```bash
git add v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/TopLevelDomainRepository.java
git commit -m "feat(ssl): 启动重排按 id 升序恢复证书队列先进先出" \
           -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: 前端显示「队列中（第N位）」

**Files:**
- Modify: `v7-shop-admin/src/utils/datetime.ts`

- [ ] **Step 1: 改 certbotInfoStatus**

编辑 `v7-shop-admin/src/utils/datetime.ts`，把 `certbotInfoStatus` 中处理 `QUEUE` 的分支：
```js
  if (row.certificateRequestStatus === 'QUEUE') {
    return '队列中'
  }
```
改为：
```js
  if (row.certificateRequestStatus === 'QUEUE') {
    return row.queuePosition ? `队列中（第${row.queuePosition}位）` : '队列中'
  }
```
（`certbotInfoType` 对 `QUEUE` 仍返回 `'info'`，不改动。）

- [ ] **Step 2: 类型检查**

Run: `cd v7-shop-admin && pnpm vue-tsc`
Expected: 无类型错误（退出码 0）。

- [ ] **Step 3: 手动验证（需后端联调环境）**

启动 admin 与后端，进入「证书管理 - 域名管理」：
- 构造 ≥2 个 `QUEUE` 域名（如连续新增/续期多个绑定云平台的域名），SSL证书列应显示「队列中（第1位）」「队列中（第2位）」…
- 当前正在处理的域名显示「申请中」，无排位。
- 等待队列消化 / 5 秒轮询，排位应自动递减。
- 若某域名 `queuePosition` 缺失（如刚重启空窗），回退显示纯「队列中」，不报错。

- [ ] **Step 4: 提交**

```bash
git add v7-shop-admin/src/utils/datetime.ts
git commit -m "feat(ssl): 域名管理队列状态显示第N位排位" \
           -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: 整体回归验证

- [ ] **Step 1: 后端全量编译 + admin 模块测试**

Run: `cd v7-shop-services && ./gradlew :v7-shop-admin:test`
Expected: BUILD SUCCESSFUL，包含 `CertificateQueueTrackerTest`(5)、`CertificateRequestPublisherTest`(2)、`CertificateRequestListenerTest`(6) 全绿。

- [ ] **Step 2: 前端类型检查**

Run: `cd v7-shop-admin && pnpm vue-tsc`
Expected: 退出码 0。

- [ ] **Step 3: 端到端手动确认**

按 Task 7 Step 3 完整走一遍：多域名排队 → 第N位正确、随轮询递减、申请中无排位、空值回退。

---

## Self-Review 记录

- **Spec 覆盖：** §4.1 tracker→Task1；§4.2① publisher→Task2；§4.2② listener→Task3；§4.3 字段→Task4；§4.2③ + §4.4(后端) page()→Task5；§4.5 ORDER BY→Task6；§4.4(前端) datetime.ts→Task7；§8 测试要点→Task1/2/3；§9 验证→Task8。无遗漏。
- **Placeholder 扫描：** 无 TBD/TODO；每个改动步骤含完整代码与命令。
- **类型/命名一致性：** `enqueue(Long)`/`remove(Long)`/`positionSnapshot():Map<Long,Integer>` 在 Task1 定义，Task2/3/5 调用一致；`queuePosition`(Integer) 在 Task4 定义、Task5 set、Task7 读取一致；`certificateRequestStatus`/`getId()` 用法与既有代码一致。
- **既有测试影响：** 已在 Task3 显式补 `@Mock CertificateQueueTracker` 防止 5 个既有监听器测试 NPE。
