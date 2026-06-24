# Order Statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the frozen V1 order statistics feature with permission-scoped employee/department aggregation, timezone-aware day/month buckets, configurable exchange rates, immutable Redis snapshots, and aggregate Excel export.

**Architecture:** Add a dedicated `order-statistics` backend slice instead of extending the homepage dashboard controller. Keep status classification, currency conversion, time-bucket generation, and permission scope as focused testable components; use native aggregate SQL for order data and Redis for immutable 30-minute results. Add a Vue statistics page composed from small filter, summary, chart, and table components, with a shared personal report configuration API.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, MySQL 8, Redis, Hutool Excel, Sa-Token, Vue 3, TypeScript, Element Plus, ECharts, Decimal.js.

---

## File Structure

Backend domain and configuration:

- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/entities/primary/OrderStatisticsUserConfig.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/OrderStatisticsUserConfigRepository.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderStatisticsDimension.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderStatisticsGranularity.java`
- Modify: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/TaskType.java`

Backend API and services:

- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/OrderStatisticsController.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/OrderStatisticsQueryRequest.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/SaveOrderStatisticsConfigRequest.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/OrderStatisticsPageRequest.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderStatisticsConfigResponse.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderStatisticsQueryResponse.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderStatisticsResultResponse.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderStatisticsOptionResponse.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IOrderStatisticsService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IOrderStatisticsConfigService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderStatisticsService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderStatisticsConfigService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderStatisticsSnapshotService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderStatisticsExportService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/statistics/OrderStatisticsClassifier.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/statistics/OrderStatisticsCurrencyConverter.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/statistics/OrderStatisticsBucketFactory.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/statistics/OrderStatisticsAccessScopeResolver.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/statistics/OrderStatisticsQueryRepository.java`
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/TaskExecutorService.java`

Frontend:

- Create: `v7-shop-admin/src/api/orderStatistics.ts`
- Create: `v7-shop-admin/src/views/statistics/OrderStatistics.vue`
- Create: `v7-shop-admin/src/views/statistics/components/StatisticsFilters.vue`
- Create: `v7-shop-admin/src/views/statistics/components/StatisticsSummary.vue`
- Create: `v7-shop-admin/src/views/statistics/components/StatisticsCharts.vue`
- Create: `v7-shop-admin/src/views/statistics/components/StatisticsTables.vue`
- Create: `v7-shop-admin/src/views/statistics/components/ExchangeRateDialog.vue`
- Create: `v7-shop-admin/src/views/setting/PersonalCenter.vue`
- Create: `v7-shop-admin/src/views/setting/components/ReportExchangeRateSettings.vue`
- Modify: `v7-shop-admin/src/router/index.ts`

Tests:

- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderStatisticsConfigServiceTest.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/statistics/OrderStatisticsClassifierTest.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/statistics/OrderStatisticsCurrencyConverterTest.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/statistics/OrderStatisticsBucketFactoryTest.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/statistics/OrderStatisticsAccessScopeResolverTest.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderStatisticsSnapshotServiceTest.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/controller/OrderStatisticsControllerTest.java`

---

### Task 1: Personal Report Configuration

**Files:**
- Create the configuration entity, repository, request/response DTOs, service, controller endpoints, and service test listed above.

- [ ] **Step 1: Write failing service tests**

Cover these behaviors in `OrderStatisticsConfigServiceTest`:

```java
@Test
void getOrCreateDefaultsToUsdAndBrowserTimezone() {
    when(repository.findByCompanyIdAndOwnerId(9L, 101L)).thenReturn(Optional.empty());
    OrderStatisticsUserConfig result = service.getOrCreate("America/Los_Angeles");
    assertThat(result.getDefaultTargetCurrencyCode()).isEqualTo("USD");
    assertThat(result.getTimeZoneId()).isEqualTo("America/Los_Angeles");
    assertThat(result.getExchangeRates()).containsEntry("USD", "1");
}

@Test
void saveRejectsInvalidTimezoneAndRate() {
    SaveOrderStatisticsConfigRequest request = request("Not/AZone", Map.of("CNY", "0"));
    assertThatThrownBy(() -> service.save(request))
            .hasMessageContaining("时区");
}

@Test
void saveAlwaysForcesUsdToOneAndCurrentOwner() {
    SaveOrderStatisticsConfigRequest request =
            request("Asia/Shanghai", Map.of("USD", "3", "CNY", "7.2"));
    OrderStatisticsUserConfig result = service.save(request);
    assertThat(result.getOwner().getId()).isEqualTo(101L);
    assertThat(result.getExchangeRates()).containsEntry("USD", "1");
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.OrderStatisticsConfigServiceTest"
```

Expected: compilation failure because the configuration classes do not exist.

- [ ] **Step 3: Implement the minimal entity and service**

Use a tenant-scoped entity with unique `company_id,user_id`, store exchange rates as JSON string values, validate `ZoneId.of(timeZoneId)`, validate rates with `BigDecimal`, scale ≤ 8, `0 < rate <= 1000000000`, and force `USD=1`.

- [ ] **Step 4: Add current-user-only endpoints**

Implement:

```text
GET /order-statistics/config
PUT /order-statistics/config
GET /order-statistics/options/currencies
```

Do not accept owner or company identifiers from the request.

- [ ] **Step 5: Run tests and compile**

```powershell
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.OrderStatisticsConfigServiceTest"
.\gradlew.bat :v7-shop-admin:compileJava
```

Expected: PASS and `BUILD SUCCESSFUL`.

### Task 2: Status Classification and Currency Conversion

**Files:**
- Create `OrderStatisticsClassifier`, `OrderStatisticsCurrencyConverter`, their value records, and tests.

- [ ] **Step 1: Write classifier tests**

```java
@ParameterizedTest
@EnumSource(OrderStatus.class)
void classifiesEveryStatusExactlyOnce(OrderStatus status) {
    OrderStatisticsCategory category = classifier.classify(status);
    if (status == OrderStatus.INVALID) assertThat(category).isEqualTo(INVALID);
    else if (status == OrderStatus.DELIVERED) assertThat(category).isEqualTo(DELIVERED);
    else assertThat(category).isEqualTo(UNDELIVERED);
}
```

Also verify `valid = total - invalid`, `undelivered = valid - delivered`, and null delivery rate when valid count is zero.

- [ ] **Step 2: Verify classifier RED**

Run the classifier test and confirm missing-class compilation failure.

- [ ] **Step 3: Implement classifier**

Use only `OrderStatus`; never inspect `PaymentStatus`.

- [ ] **Step 4: Write currency converter tests**

Cover:

```java
assertThat(convert("720", "CNY", "USD", rates("CNY", "7.2", "USD", "1")))
        .isEqualByComparingTo("100");
assertThat(convert("100", "USD", "CNY", rates("CNY", "7.2", "USD", "1")))
        .isEqualByComparingTo("720");
assertThat(convert("100", "EUR", "EUR", Map.of())).isEqualByComparingTo("100");
assertThat(resolveSourceRate(temp, personal, history)).isEqualTo(temp);
```

Verify missing source/target rates produce an explicit missing-rate result rather than 1:1 conversion.

- [ ] **Step 5: Verify converter RED, implement, and run GREEN**

Use `BigDecimal` with a working scale of 16 and `HALF_UP`; do not round to target currency fraction digits until aggregation is complete.

### Task 3: Time Bucket Generation

**Files:**
- Create `OrderStatisticsBucketFactory` and test.

- [ ] **Step 1: Write failing timezone tests**

Cover:

```java
@Test
void createsLosAngelesDayAcrossDstSpringForward() {
    List<Bucket> buckets = factory.create(
        LocalDate.parse("2026-03-08"),
        LocalDate.parse("2026-03-08"),
        DAY,
        ZoneId.of("America/Los_Angeles"),
        Instant.parse("2026-03-09T12:00:00Z"));
    assertThat(Duration.between(buckets.getFirst().startInstant(), buckets.getFirst().endInstant()))
        .isEqualTo(Duration.ofHours(23));
}
```

Also cover 25-hour fall-back day, partial first/last month, current-day cutoff, future-date rejection, 62-day limit, 5-year limit, and Beijing `LocalDateTime` query bounds.

- [ ] **Step 2: Verify RED**

Run the test and confirm missing factory compilation failure.

- [ ] **Step 3: Implement bucket factory**

Generate user-zone `ZonedDateTime` boundaries first, convert to `Instant`, then convert to `Asia/Shanghai` local query bounds.

- [ ] **Step 4: Run GREEN**

Run the bucket test and compile backend.

### Task 4: Permission Scope and Option Queries

**Files:**
- Create `OrderStatisticsAccessScopeResolver`, option responses, native option queries, and tests.

- [ ] **Step 1: Write scope resolver tests**

Test `ADMIN`, `COMPANY_ADMIN`, `DEEP_DEPARTMENT_MANAGER`, `DEPARTMENT_MANAGER`, `EMPLOYEE`, `DEPARTMENT_TREE`, cross-department include/exclude, personal view mode, website-admin scope, and unassigned permission.

- [ ] **Step 2: Verify RED**

Run resolver tests before creating implementation.

- [ ] **Step 3: Implement immutable scope object**

The scope must carry:

```java
record OrderStatisticsAccessScope(
    long companyId,
    long requesterUserId,
    boolean companyWide,
    boolean personalOnly,
    Set<Long> allowedDepartmentIds,
    Set<Long> excludedDepartmentIds,
    boolean allowUnassigned,
    boolean websiteScoped,
    Long websiteId,
    ViewMode viewMode
) {}
```

- [ ] **Step 4: Implement candidates**

Add permission-scoped endpoints:

```text
GET /order-statistics/options/context
GET /order-statistics/options/employees
GET /order-statistics/options/departments
GET /order-statistics/options/domains
```

Use native SQL for deleted/historical users and departments so Hibernate `@SQLRestriction` does not suppress them.

- [ ] **Step 5: Verify tests and compile**

Run resolver tests and backend compile.

### Task 5: Aggregate Query and Result Assembly

**Files:**
- Create query request/response DTOs, native repository, service, and controller tests.

- [ ] **Step 1: Write failing request validation and service tests**

Cover employee/department mutual exclusion, minimum selection, unassigned role restrictions, platform validation, exact domain normalization, date limits, temporary rate validation, and website context capture.

- [ ] **Step 2: Verify RED**

Run `OrderStatisticsControllerTest` and confirm failures are caused by missing endpoints/services.

- [ ] **Step 3: Implement native aggregate query**

Query only aggregate columns:

```text
bucket_key
group_id
group_name_snapshot
currency_code
currency_exchange_rate
order_status
count(*)
sum(total_amount)
```

Always apply company, entity status, time, permission, website, platform, domain, and selected dimension filters.

- [ ] **Step 4: Implement result assembly**

Classify status, resolve rates, convert amounts, fill zero buckets, build summary, time trend, group summary, bucket-group rows, original-currency totals, and missing-rate totals.

- [ ] **Step 5: Run tests**

Run focused controller/service tests and compile.

### Task 6: Redis Snapshot, Cache, Async Query, and Cancellation

**Files:**
- Create `OrderStatisticsSnapshotService` and tests; extend controller/service.

- [ ] **Step 1: Write failing snapshot tests**

Verify:

- Result token is bound to company and user.
- Result TTL is 30 minutes and does not slide.
- Query cache TTL is one minute.
- `forceRefresh` bypasses cache.
- Group pages read immutable snapshot data.
- Expired token is rejected.
- A new query marks the previous active query cancelled.

- [ ] **Step 2: Verify RED**

Run snapshot test before implementation.

- [ ] **Step 3: Implement Redis storage**

Use opaque random tokens and the key namespace defined in the spec.

- [ ] **Step 4: Add five-second synchronous wait**

Run one query execution; return completed result within five seconds or return a Redis-backed job ID while the same future continues.

- [ ] **Step 5: Add degraded mode**

When Redis is unavailable, return synchronous summary/trend and first 100 groups with `degraded=true`; disable paging and export.

- [ ] **Step 6: Run tests**

Run snapshot and controller tests.

### Task 7: Aggregate Excel Export

**Files:**
- Modify `TaskType` and `TaskExecutorService`; create export service and tests.

- [ ] **Step 1: Write failing export task tests**

Verify one active export per user, expired result rejection, cancellation, and sheet names:

```text
查询说明
汇总
时间趋势
分组汇总
时间分组明细
原币汇总
汇率说明
缺失汇率
```

- [ ] **Step 2: Verify RED**

Run the focused export test.

- [ ] **Step 3: Implement `ORDER_STATISTICS_EXPORT`**

Capture result snapshot at task start, generate Hutool workbook, upload to existing S3 storage, and expose through existing task download.

- [ ] **Step 4: Enforce 24-hour download**

Reject downloads older than 24 hours for this task type and document the required S3 lifecycle prefix.

- [ ] **Step 5: Run tests and compile**

Run export tests and backend compile.

### Task 8: Frontend API and Personal Configuration

**Files:**
- Create frontend API, personal center, and report settings component.

- [ ] **Step 1: Add typed API contracts**

All IDs and money values are strings. Define config, query, result, job, option, and export contracts in `orderStatistics.ts`.

- [ ] **Step 2: Add personal center route target**

Create the missing `/setting/personalCenter` page already referenced by `VabAvatar`, with a “报表汇率” section.

- [ ] **Step 3: Implement configuration UI**

Validate IANA timezone, positive rates, max eight decimals, USD fixed to one, and show a conversion example before save.

- [ ] **Step 4: Run type check**

```powershell
cd v7-shop-admin
pnpm vue-tsc
```

Expected: PASS.

### Task 9: Statistics Page

**Files:**
- Create statistics page/components and modify router.

- [ ] **Step 1: Add route and initial non-querying page**

Add “统计分析” under the homepage route and do not query on mount.

- [ ] **Step 2: Implement filters**

Support day/month, employee/department, historical toggles, platform/domain multiselect, target currency, temporary rates, session storage, and role-specific visibility.

- [ ] **Step 3: Implement job polling and cancellation**

Handle 200 completed, 202 processing, manual refresh, previous-query cancellation, token expiry, and degraded mode.

- [ ] **Step 4: Implement summary, charts, and tables**

Use separate order, delivery-rate, and sales charts; limit grouped chart series to ten; paginate groups and bucket-groups from snapshot.

- [ ] **Step 5: Implement export**

Start export from result token, reuse existing task polling/download, and prevent duplicate active exports.

- [ ] **Step 6: Run frontend checks**

Run `pnpm vue-tsc` and `pnpm build`.

### Task 10: Database Plans, Verification, and Documentation

**Files:**
- Modify entity indexes or `DatabaseInitializer.sql` based on actual query plan.
- Update the design spec if implementation details changed without changing requirements.

- [ ] **Step 1: Run aggregate SQL EXPLAIN**

Verify company/time/status and context joins use indexes for employee, department, website, platform, and domain filters.

- [ ] **Step 2: Add only proven indexes**

At minimum evaluate:

```text
t_orders(company_id, order_time, order_status)
t_order_context_infos(company_id, sales_uid, department_id)
t_orders(company_id, platform, order_time)
t_order_context_infos(company_id, website_id, website_url)
```

- [ ] **Step 3: Run backend verification**

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:test
.\gradlew.bat :v7-shop-admin:compileJava
```

- [ ] **Step 4: Run frontend verification**

```powershell
cd v7-shop-admin
pnpm vue-tsc
pnpm build
```

- [ ] **Step 5: Manual acceptance**

Verify all acceptance sections in `docs/superpowers/specs/2026-06-24-order-statistics-design.md`, including DST, permission boundaries, missing rates, website scope, snapshot consistency, cancellation, and export expiry.

---

## Self-Review

Spec coverage:

- Metrics and status rules: Tasks 2 and 5.
- Employee/department aggregation and historical records: Tasks 4 and 5.
- Timezone and DST: Task 3.
- Personal/temporary exchange rates: Tasks 1, 2, and 8.
- Platform/domain filters and website scope: Tasks 4, 5, and 9.
- Redis snapshots, caching, long queries, and cancellation: Task 6.
- Aggregate Excel export and 24-hour expiry: Task 7.
- Menu, charts, tables, session state, and mobile behavior: Tasks 8 and 9.
- Index validation and complete verification: Task 10.

Placeholder scan:

- No deferred feature placeholders are present. Every implementation area has an exact file boundary, behavior, test command, and expected result.

Type consistency:

- Backend and frontend use `DAY`/`MONTH`, `EMPLOYEE`/`DEPARTMENT`, decimal-string amounts, decimal-string IDs, opaque `resultToken`, and opaque `queryJobId`.
