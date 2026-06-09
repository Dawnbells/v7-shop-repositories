# Order Search Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build per-user saved search presets for the order management and order audit pages, persisted in the backend and restored from a dropdown.

**Architecture:** Add a focused backend resource `OrderSearchPreset` with owner-based access enforced in service methods, then add a small frontend API layer plus search preset helpers. The existing order search form remains the source of truth; applying a preset mutates `queryForm`, updates URL query through the existing watcher, resets to page 1, locks search type inference, and emits search.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, Sa-Token session user, Hutool JSON, Vue 3 `<script setup>`, Element Plus, Vite, TypeScript.

---

## File Structure

Backend files:

- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetPageType.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetTimeMode.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/entities/primary/OrderSearchPreset.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/OrderSearchPresetRepository.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/SaveOrderSearchPresetRequest.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderSearchPresetResponse.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IOrderSearchPresetService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderSearchPresetService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/OrderSearchPresetController.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderSearchPresetServiceTest.java`

Frontend files:

- Create: `v7-shop-admin/src/api/orderSearchPreset.ts`
- Create: `v7-shop-admin/src/views/order/vabAutoComponents/orderSearchPresetHelper.ts`
- Modify: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue`

Verification:

- Run backend service unit test.
- Run backend `:v7-shop-admin:compileJava`.
- Run frontend `pnpm vue-tsc`.
- Manually verify order management, order audit, and order contact page behavior.

---

### Task 1: Backend Service TDD And Domain Model

**Files:**
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderSearchPresetServiceTest.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetPageType.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetTimeMode.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/entities/primary/OrderSearchPreset.java`
- Create: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/OrderSearchPresetRepository.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/SaveOrderSearchPresetRequest.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IOrderSearchPresetService.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderSearchPresetService.java`

- [ ] **Step 1: Write the failing service test**

Create `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderSearchPresetServiceTest.java`:

```java
package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import cn.v7soft.dao.repositories.primary.OrderSearchPresetRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSearchPresetServiceTest {

    @Mock
    private OrderSearchPresetRepository repository;

    private OrderSearchPresetService service;
    private MockedStatic<SaSessionUtil> saSessionUtil;
    private final SystemUserDto loginUser = SystemUserDto.builder()
            .id("101")
            .companyId(1L)
            .name("Alice")
            .build();

    @BeforeEach
    void setUp() {
        service = new OrderSearchPresetService(repository);
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(loginUser);
    }

    @AfterEach
    void tearDown() {
        saSessionUtil.close();
    }

    @Test
    @DisplayName("savePreset: no same-name preset creates a preset owned by current login user")
    void savePresetCreatesCurrentUserPreset() {
        JSONObject queryParams = new JSONObject();
        queryParams.set("searchType", "ORDER_ID");
        queryParams.set("keywords", "10001");
        SaveOrderSearchPresetRequest request = SaveOrderSearchPresetRequest.builder()
                .pageType(OrderSearchPresetPageType.ORDER_AUDIT)
                .timeMode(OrderSearchPresetTimeMode.ABSOLUTE)
                .name(" 每日审单 ")
                .queryParams(queryParams)
                .build();

        when(repository.findValidByOwnerAndPageTypeAndName(
                101L, OrderSearchPresetPageType.ORDER_AUDIT, "每日审单"))
                .thenReturn(Optional.empty());
        when(repository.save(any(OrderSearchPreset.class))).thenAnswer(invocation -> {
            OrderSearchPreset preset = invocation.getArgument(0);
            preset.setId(900L);
            return preset;
        });

        OrderSearchPreset result = service.savePreset(request);

        assertThat(result.getId()).isEqualTo(900L);
        assertThat(result.getOwner().getId()).isEqualTo(101L);
        assertThat(result.getName()).isEqualTo("每日审单");
        assertThat(result.getPageType()).isEqualTo(OrderSearchPresetPageType.ORDER_AUDIT);
        assertThat(result.getTimeMode()).isEqualTo(OrderSearchPresetTimeMode.ABSOLUTE);
        assertThat(result.getQueryParams().getStr("keywords")).isEqualTo("10001");
    }

    @Test
    @DisplayName("savePreset: same-name preset updates existing current-user page preset")
    void savePresetOverwritesSameNameCurrentUserPreset() {
        OrderSearchPreset existing = OrderSearchPreset.builder()
                .id(10L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_MANAGER)
                .timeMode(OrderSearchPresetTimeMode.ABSOLUTE)
                .name("常用")
                .queryParams(new JSONObject().set("keywords", "old"))
                .build();
        JSONObject queryParams = new JSONObject().set("keywords", "new");
        SaveOrderSearchPresetRequest request = SaveOrderSearchPresetRequest.builder()
                .pageType(OrderSearchPresetPageType.ORDER_MANAGER)
                .timeMode(OrderSearchPresetTimeMode.RELATIVE)
                .name("常用")
                .queryParams(queryParams)
                .build();

        when(repository.findValidByOwnerAndPageTypeAndName(
                101L, OrderSearchPresetPageType.ORDER_MANAGER, "常用"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(OrderSearchPreset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderSearchPreset result = service.savePreset(request);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getOwner().getId()).isEqualTo(101L);
        assertThat(result.getTimeMode()).isEqualTo(OrderSearchPresetTimeMode.RELATIVE);
        assertThat(result.getQueryParams().getStr("keywords")).isEqualTo("new");
    }

    @Test
    @DisplayName("listCurrentUserPresets: returns current user and page presets using repository ordering")
    void listCurrentUserPresetsUsesOwnerAndPageType() {
        OrderSearchPreset preset = OrderSearchPreset.builder()
                .id(11L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_AUDIT)
                .name("审单")
                .timeMode(OrderSearchPresetTimeMode.RELATIVE)
                .queryParams(new JSONObject())
                .build();
        when(repository.findValidByOwnerAndPageTypeOrderByUsage(
                101L, OrderSearchPresetPageType.ORDER_AUDIT))
                .thenReturn(List.of(preset));

        List<OrderSearchPreset> result = service.listCurrentUserPresets(OrderSearchPresetPageType.ORDER_AUDIT);

        assertThat(result).containsExactly(preset);
        verify(repository).findValidByOwnerAndPageTypeOrderByUsage(
                101L, OrderSearchPresetPageType.ORDER_AUDIT);
    }

    @Test
    @DisplayName("usePreset: updates lastUsedTime only for current user's preset")
    void usePresetUpdatesLastUsedTime() {
        OrderSearchPreset preset = OrderSearchPreset.builder()
                .id(12L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_AUDIT)
                .name("审单")
                .timeMode(OrderSearchPresetTimeMode.RELATIVE)
                .queryParams(new JSONObject())
                .build();
        when(repository.findValidByIdAndOwnerId(12L, 101L)).thenReturn(Optional.of(preset));
        when(repository.save(any(OrderSearchPreset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderSearchPreset result = service.usePreset(12L);

        assertThat(result.getLastUsedTime()).isNotNull();
    }

    @Test
    @DisplayName("deletePreset: soft deletes only current user's preset")
    void deletePresetSoftDeletesCurrentUserPreset() {
        OrderSearchPreset preset = OrderSearchPreset.builder()
                .id(13L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_MANAGER)
                .name("管理")
                .timeMode(OrderSearchPresetTimeMode.ABSOLUTE)
                .queryParams(new JSONObject())
                .build();
        when(repository.findValidByIdAndOwnerId(13L, 101L)).thenReturn(Optional.of(preset));

        service.deletePreset(13L);

        ArgumentCaptor<OrderSearchPreset> captor = ArgumentCaptor.forClass(OrderSearchPreset.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusEnum.DELETED);
    }
}
```

- [ ] **Step 2: Run the test and verify it fails before implementation**

Run:

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.OrderSearchPresetServiceTest"
```

Expected: FAIL with compilation errors for missing `OrderSearchPreset`, `OrderSearchPresetRepository`, `OrderSearchPresetService`, `OrderSearchPresetPageType`, `OrderSearchPresetTimeMode`, and `SaveOrderSearchPresetRequest`.

- [ ] **Step 3: Add page type enum**

Create `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetPageType.java`:

```java
package cn.v7soft.dao.enums;

public enum OrderSearchPresetPageType {
    ORDER_MANAGER,
    ORDER_AUDIT
}
```

- [ ] **Step 4: Add time mode enum**

Create `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetTimeMode.java`:

```java
package cn.v7soft.dao.enums;

public enum OrderSearchPresetTimeMode {
    ABSOLUTE,
    RELATIVE
}
```

- [ ] **Step 5: Add `OrderSearchPreset` entity**

Create `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/entities/primary/OrderSearchPreset.java`:

```java
package cn.v7soft.dao.entities.primary;

import cn.hutool.json.JSONObject;
import cn.v7soft.dao.converter.JSONConverter;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_order_search_presets", indexes = {
        @Index(name = "idx_order_search_preset_owner_page", columnList = "user_id,page_type"),
        @Index(name = "idx_order_search_preset_company", columnList = "company_id")
})
public class OrderSearchPreset extends BaseDataRangeEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_type", nullable = false, length = 30)
    private OrderSearchPresetPageType pageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_mode", nullable = false, length = 20)
    private OrderSearchPresetTimeMode timeMode;

    @Builder.Default
    @Column(name = "query_params", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JSONConverter.class)
    private JSONObject queryParams = new JSONObject();

    @Column(name = "last_used_time")
    private LocalDateTime lastUsedTime;
}
```

- [ ] **Step 6: Add repository queries scoped to owner and page**

Create `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/OrderSearchPresetRepository.java`:

```java
package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderSearchPresetRepository extends BaseRepository<OrderSearchPreset> {

    @Query("""
            FROM OrderSearchPreset p
            WHERE p.owner.id = :ownerId
              AND p.pageType = :pageType
              AND p.name = :name
              AND p.status = 'VALID'
            """)
    Optional<OrderSearchPreset> findValidByOwnerAndPageTypeAndName(
            @Param("ownerId") Long ownerId,
            @Param("pageType") OrderSearchPresetPageType pageType,
            @Param("name") String name
    );

    @Query("""
            FROM OrderSearchPreset p
            WHERE p.owner.id = :ownerId
              AND p.pageType = :pageType
              AND p.status = 'VALID'
            ORDER BY
              CASE WHEN p.lastUsedTime IS NULL THEN 1 ELSE 0 END ASC,
              p.lastUsedTime DESC,
              p.createTime DESC
            """)
    List<OrderSearchPreset> findValidByOwnerAndPageTypeOrderByUsage(
            @Param("ownerId") Long ownerId,
            @Param("pageType") OrderSearchPresetPageType pageType
    );

    @Query("""
            FROM OrderSearchPreset p
            WHERE p.id = :id
              AND p.owner.id = :ownerId
              AND p.status = 'VALID'
            """)
    Optional<OrderSearchPreset> findValidByIdAndOwnerId(
            @Param("id") Long id,
            @Param("ownerId") Long ownerId
    );
}
```

- [ ] **Step 7: Add save request DTO**

Create `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/SaveOrderSearchPresetRequest.java`:

```java
package cn.v7soft.admin.controller.req;

import cn.hutool.json.JSONObject;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SaveOrderSearchPresetRequest {

    @NotNull(message = "页面类型不能为空")
    @Schema(title = "页面类型", example = "ORDER_AUDIT")
    private OrderSearchPresetPageType pageType;

    @NotBlank(message = "条件名称不能为空")
    @Size(max = 50, message = "条件名称不能超过50个字符")
    @Schema(title = "条件名称")
    private String name;

    @NotNull(message = "时间保存方式不能为空")
    @Schema(title = "时间保存方式", example = "RELATIVE")
    private OrderSearchPresetTimeMode timeMode;

    @Schema(title = "搜索条件JSON")
    private JSONObject queryParams;
}
```

- [ ] **Step 8: Add service interface**

Create `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IOrderSearchPresetService.java`:

```java
package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;

import java.util.List;

public interface IOrderSearchPresetService extends IBaseService<OrderSearchPreset> {

    List<OrderSearchPreset> listCurrentUserPresets(OrderSearchPresetPageType pageType);

    OrderSearchPreset savePreset(SaveOrderSearchPresetRequest request);

    OrderSearchPreset usePreset(Long id);

    void deletePreset(Long id);
}
```

- [ ] **Step 9: Add service implementation**

Create `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderSearchPresetService.java`:

```java
package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.admin.service.IOrderSearchPresetService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.repositories.primary.OrderSearchPresetRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderSearchPresetService
        extends BaseService<OrderSearchPreset, OrderSearchPresetRepository>
        implements IOrderSearchPresetService {

    public OrderSearchPresetService(OrderSearchPresetRepository repository) {
        super(repository);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSearchPreset> listCurrentUserPresets(OrderSearchPresetPageType pageType) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(pageType, "页面类型不能为空");
        return repository.findValidByOwnerAndPageTypeOrderByUsage(currentUserId(), pageType);
    }

    @Override
    @Transactional
    public OrderSearchPreset savePreset(SaveOrderSearchPresetRequest request) {
        validateSaveRequest(request);
        Long ownerId = currentUserId();
        String name = request.getName().trim();
        OrderSearchPreset preset = repository
                .findValidByOwnerAndPageTypeAndName(ownerId, request.getPageType(), name)
                .orElseGet(() -> OrderSearchPreset.builder()
                        .owner(currentUser().toOwner())
                        .pageType(request.getPageType())
                        .build());

        preset.setName(name);
        preset.setTimeMode(request.getTimeMode());
        preset.setQueryParams(request.getQueryParams() == null ? new JSONObject() : request.getQueryParams());
        return repository.save(preset);
    }

    @Override
    @Transactional
    public OrderSearchPreset usePreset(Long id) {
        OrderSearchPreset preset = findCurrentUserPreset(id);
        preset.setLastUsedTime(LocalDateTime.now());
        return repository.save(preset);
    }

    @Override
    @Transactional
    public void deletePreset(Long id) {
        OrderSearchPreset preset = findCurrentUserPreset(id);
        preset.setStatus(StatusEnum.DELETED);
        repository.save(preset);
    }

    private OrderSearchPreset findCurrentUserPreset(Long id) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        return repository.findValidByIdAndOwnerId(id, currentUserId())
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("条件预设不存在或无权访问"));
    }

    private void validateSaveRequest(SaveOrderSearchPresetRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request, "请求不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request.getPageType(), "页面类型不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request.getTimeMode(), "时间保存方式不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getName(), "条件名称不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(
                StrUtil.length(request.getName().trim()) <= 50,
                "条件名称不能超过50个字符"
        );
    }

    private SystemUserDto currentUser() {
        return SaSessionUtil.getLoginUser();
    }

    private Long currentUserId() {
        return currentUser().getLongId();
    }
}
```

- [ ] **Step 10: Run the service test and verify it passes**

Run:

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.OrderSearchPresetServiceTest"
```

Expected: PASS.

- [ ] **Step 11: Commit backend service/domain**

Run:

```powershell
git add v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetPageType.java v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetTimeMode.java v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/entities/primary/OrderSearchPreset.java v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/OrderSearchPresetRepository.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/SaveOrderSearchPresetRequest.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IOrderSearchPresetService.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderSearchPresetService.java v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderSearchPresetServiceTest.java
git commit -m "feat(order): add search preset backend service"
```

---

### Task 2: Backend Controller And Response DTO

**Files:**
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderSearchPresetResponse.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/OrderSearchPresetController.java`
- Test: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderSearchPresetServiceTest.java`

- [ ] **Step 1: Add response DTO**

Create `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderSearchPresetResponse.java`:

```java
package cn.v7soft.admin.controller.resp;

import cn.hutool.json.JSONObject;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
@Schema(description = "订单搜索条件预设")
public class OrderSearchPresetResponse extends IdResponse {

    private String name;
    private OrderSearchPresetPageType pageType;
    private OrderSearchPresetTimeMode timeMode;
    private JSONObject queryParams;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;

    public static OrderSearchPresetResponse convertEntity(OrderSearchPreset entity) {
        return filling(entity, OrderSearchPresetResponse.builder()
                .name(entity.getName())
                .pageType(entity.getPageType())
                .timeMode(entity.getTimeMode())
                .queryParams(entity.getQueryParams())
                .lastUsedTime(entity.getLastUsedTime())
                .createTime(entity.getCreateTime())
                .build());
    }
}
```

- [ ] **Step 2: Add controller**

Create `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/OrderSearchPresetController.java`:

```java
package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.admin.controller.resp.OrderSearchPresetResponse;
import cn.v7soft.admin.service.IOrderSearchPresetService;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/order-search-presets")
@Tag(name = "订单管理-个人搜索条件预设")
public class OrderSearchPresetController {

    private final IOrderSearchPresetService service;

    public OrderSearchPresetController(IOrderSearchPresetService service) {
        this.service = service;
    }

    @SaCheckLogin
    @GetMapping
    @Operation(summary = "查询当前用户当前页面的搜索条件预设")
    public List<OrderSearchPresetResponse> list(@RequestParam OrderSearchPresetPageType pageType) {
        return service.listCurrentUserPresets(pageType)
                .stream()
                .map(OrderSearchPresetResponse::convertEntity)
                .toList();
    }

    @SaCheckLogin
    @PostMapping
    @Operation(summary = "保存或覆盖当前用户搜索条件预设")
    public OrderSearchPresetResponse save(@Valid @RequestBody SaveOrderSearchPresetRequest request) {
        return OrderSearchPresetResponse.convertEntity(service.savePreset(request));
    }

    @SaCheckLogin
    @DeleteMapping("/{id}")
    @Operation(summary = "删除当前用户搜索条件预设")
    public void delete(@PathVariable Long id) {
        service.deletePreset(id);
    }

    @SaCheckLogin
    @PostMapping("/{id}/use")
    @Operation(summary = "使用当前用户搜索条件预设并刷新最近使用时间")
    public OrderSearchPresetResponse use(@PathVariable Long id) {
        return OrderSearchPresetResponse.convertEntity(service.usePreset(id));
    }
}
```

- [ ] **Step 3: Compile backend admin module**

Run:

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the service test again**

Run:

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.OrderSearchPresetServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit controller**

Run:

```powershell
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderSearchPresetResponse.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/OrderSearchPresetController.java
git commit -m "feat(order): expose search preset endpoints"
```

---

### Task 3: Frontend API And Time Helper

**Files:**
- Create: `v7-shop-admin/src/api/orderSearchPreset.ts`
- Create: `v7-shop-admin/src/views/order/vabAutoComponents/orderSearchPresetHelper.ts`

- [ ] **Step 1: Add API wrapper**

Create `v7-shop-admin/src/api/orderSearchPreset.ts`:

```ts
import request from '/@/utils/request'

export type OrderSearchPresetPageType = 'ORDER_MANAGER' | 'ORDER_AUDIT'
export type OrderSearchPresetTimeMode = 'ABSOLUTE' | 'RELATIVE'

export interface OrderSearchPreset {
  id: string
  name: string
  pageType: OrderSearchPresetPageType
  timeMode: OrderSearchPresetTimeMode
  queryParams: Record<string, any>
  lastUsedTime?: string
  createTime?: string
}

export interface SaveOrderSearchPresetRequest {
  pageType: OrderSearchPresetPageType
  name: string
  timeMode: OrderSearchPresetTimeMode
  queryParams: Record<string, any>
}

export function listOrderSearchPresets(pageType: OrderSearchPresetPageType) {
  return request({
    url: '/order-search-presets',
    method: 'get',
    params: { pageType },
  })
}

export function saveOrderSearchPreset(data: SaveOrderSearchPresetRequest) {
  return request({
    url: '/order-search-presets',
    method: 'post',
    data,
  })
}

export function deleteOrderSearchPreset(id: string) {
  return request({
    url: `/order-search-presets/${id}`,
    method: 'delete',
  })
}

export function useOrderSearchPreset(id: string) {
  return request({
    url: `/order-search-presets/${id}/use`,
    method: 'post',
  })
}
```

- [ ] **Step 2: Add pure helper for snapshot and apply logic**

Create `v7-shop-admin/src/views/order/vabAutoComponents/orderSearchPresetHelper.ts`:

```ts
import type { OrderSearchPresetTimeMode } from '/@/api/orderSearchPreset'

const PRESET_FIELDS = [
  'searchType',
  'keywords',
  'orderStatus',
  'botOrderStatus',
  'repeatType',
  'countryId',
  'platform',
  'belongEmployeeIds',
  'belongDepartmentIds',
  'contacted',
] as const

interface RelativeDateEndpoint {
  dayOffset: number
  time: string
}

interface RelativeDateRange {
  mode: 'RELATIVE'
  start: RelativeDateEndpoint
  end: RelativeDateEndpoint
}

export const defaultOrderSearchPresetQuery = () => ({
  searchType: 'ORDER_ID',
  keywords: '',
  orderStatus: undefined,
  botOrderStatus: undefined,
  repeatType: undefined,
  countryId: undefined,
  platform: undefined,
  dateRange: undefined,
  belongEmployeeIds: undefined,
  belongDepartmentIds: undefined,
  contacted: undefined,
})

export const buildPresetQueryParams = (
  queryForm: Record<string, any>,
  timeMode: OrderSearchPresetTimeMode,
  now = new Date()
) => {
  const snapshot: Record<string, any> = {}

  for (const field of PRESET_FIELDS) {
    const value = queryForm[field]
    if (!isEmptyPresetValue(value)) {
      snapshot[field] = clonePresetValue(value)
    }
  }

  const dateRange = normalizeDateRange(queryForm.dateRange)
  if (dateRange) {
    snapshot.dateRange =
      timeMode === 'RELATIVE'
        ? toRelativeDateRange(dateRange[0], dateRange[1], now)
        : dateRange.map((date) => date.toISOString())
  }

  return snapshot
}

export const applyPresetQueryParams = (
  queryForm: Record<string, any>,
  queryParams: Record<string, any> | undefined,
  now = new Date()
) => {
  const pageSize = queryForm.pageSize
  Object.assign(queryForm, defaultOrderSearchPresetQuery(), {
    pageNo: 1,
    pageSize,
  })

  const params = queryParams || {}
  for (const field of PRESET_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(params, field)) {
      queryForm[field] = clonePresetValue(params[field])
    }
  }

  if (Object.prototype.hasOwnProperty.call(params, 'dateRange')) {
    queryForm.dateRange = restoreDateRange(params.dateRange, now)
  }
}

const isEmptyPresetValue = (value: any) => {
  if (value === false) return false
  if (value === 0) return false
  if (value === undefined || value === null) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

const clonePresetValue = (value: any) => {
  if (Array.isArray(value)) {
    return value.map((item) => clonePresetValue(item))
  }
  if (value instanceof Date) {
    return value.toISOString()
  }
  if (value && typeof value === 'object') {
    return JSON.parse(JSON.stringify(value))
  }
  return value
}

const normalizeDateRange = (value: any): [Date, Date] | undefined => {
  if (!Array.isArray(value) || value.length !== 2) return undefined
  const start = toValidDate(value[0])
  const end = toValidDate(value[1])
  if (!start || !end) return undefined
  return [start, end]
}

const toValidDate = (value: any): Date | undefined => {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return undefined
  return date
}

const toRelativeDateRange = (start: Date, end: Date, now: Date): RelativeDateRange => ({
  mode: 'RELATIVE',
  start: {
    dayOffset: diffLocalDays(start, now),
    time: formatTime(start),
  },
  end: {
    dayOffset: diffLocalDays(end, now),
    time: formatTime(end),
  },
})

const restoreDateRange = (value: any, now: Date): [Date, Date] | undefined => {
  if (isRelativeDateRange(value)) {
    return [restoreRelativeEndpoint(value.start, now), restoreRelativeEndpoint(value.end, now)]
  }

  const normalized = normalizeDateRange(value)
  return normalized ? [normalized[0], normalized[1]] : undefined
}

const isRelativeDateRange = (value: any): value is RelativeDateRange =>
  value &&
  value.mode === 'RELATIVE' &&
  value.start &&
  value.end &&
  typeof value.start.dayOffset === 'number' &&
  typeof value.start.time === 'string' &&
  typeof value.end.dayOffset === 'number' &&
  typeof value.end.time === 'string'

const restoreRelativeEndpoint = (endpoint: RelativeDateEndpoint, now: Date) => {
  const date = startOfLocalDay(now)
  date.setDate(date.getDate() + endpoint.dayOffset)
  const [hour, minute, second] = endpoint.time.split(':').map((item) => Number(item))
  date.setHours(hour || 0, minute || 0, second || 0, 0)
  return date
}

const diffLocalDays = (date: Date, base: Date) => {
  const oneDay = 24 * 60 * 60 * 1000
  return Math.round((startOfLocalDay(date).getTime() - startOfLocalDay(base).getTime()) / oneDay)
}

const startOfLocalDay = (date: Date) => {
  const value = new Date(date)
  value.setHours(0, 0, 0, 0)
  return value
}

const formatTime = (date: Date) =>
  [date.getHours(), date.getMinutes(), date.getSeconds()]
    .map((item) => String(item).padStart(2, '0'))
    .join(':')
```

- [ ] **Step 3: Run frontend type check**

Run:

```powershell
cd v7-shop-admin
pnpm vue-tsc
```

Expected: PASS.

- [ ] **Step 4: Commit frontend API and helper**

Run:

```powershell
git add v7-shop-admin/src/api/orderSearchPreset.ts v7-shop-admin/src/views/order/vabAutoComponents/orderSearchPresetHelper.ts
git commit -m "feat(order): add search preset frontend helpers"
```

---

### Task 4: Wire Presets Into Order Search Form

**Files:**
- Modify: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue`
- Test: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue`

- [ ] **Step 1: Add API and helper imports**

In `OrderQueryParamLayout.vue`, add these imports in the `<script setup lang="ts">` import section:

```ts
import {
  deleteOrderSearchPreset,
  listOrderSearchPresets,
  saveOrderSearchPreset,
  useOrderSearchPreset,
  type OrderSearchPreset,
  type OrderSearchPresetPageType,
  type OrderSearchPresetTimeMode,
} from '/@/api/orderSearchPreset'
import {
  applyPresetQueryParams,
  buildPresetQueryParams,
} from './orderSearchPresetHelper'
```

- [ ] **Step 2: Change the keyword row to include preset controls**

In the first `<el-form-item label="" label-width="35px">`, wrap the existing keyword `el-input` and append the preset controls in an `el-space`.

Use this shape for the form item body:

```vue
<el-space alignment="center">
  <el-input
    v-model="queryForm.keywords"
    clearable
    placeholder="请输入查询关键字"
    style="width: 515px"
  >
    <template #prepend>
      <div class="search-type-select-wrap">
        <el-select
          v-model="queryForm.searchType"
          class="search-type-select"
          @change="onSearchTypeManualChange"
        >
          <el-option label="订单编号" value="ORDER_ID" />
          <el-option label="中文品名" value="MERCHANDISE" />
          <el-option label="手机号码" value="TELEPHONE" />
          <el-option label="客户姓名" value="NAME" />
          <el-option label="产品标题" value="PRODUCT_TITLE" />
          <el-option label="远程IP" value="REMOTE_IP" />
          <el-option label="客户地址" value="ADDRESS" />
          <el-option label="下单域名" value="DOMAIN" />
          <el-option label="重单查询" value="REPEAT" />
        </el-select>
        <el-tooltip :content="inferTooltip" placement="top">
          <button
            class="infer-toggle-button"
            :style="{
              color: autoInferSearchType
                ? 'var(--el-color-primary)'
                : 'var(--el-text-color-disabled)',
            }"
            type="button"
            @click.stop="onToggleAutoInfer"
          >
            <el-icon>
              <MagicStick />
            </el-icon>
          </button>
        </el-tooltip>
      </div>
    </template>
    <template #append>
      <el-button
        :loading="listLoading"
        native-type="submit"
        type="primary"
        @click="queryData"
      >
        搜索
      </el-button>
    </template>
  </el-input>
  <el-select
    v-if="presetPageType"
    v-model="selectedPresetId"
    clearable
    filterable
    :loading="presetLoading"
    placeholder="已保存条件"
    style="width: 190px"
    @change="handleApplyPreset"
  >
    <el-option
      v-for="item in presetOptions"
      :key="item.id"
      :label="item.name"
      :value="item.id"
    >
      <div class="preset-option">
        <span class="preset-option-name">{{ item.name }}</span>
        <el-button
          text
          size="small"
          type="danger"
          @click.stop="handleDeletePreset(item)"
        >
          删除
        </el-button>
      </div>
    </el-option>
  </el-select>
  <el-button
    v-if="presetPageType"
    type="primary"
    plain
    @click="openPresetDialog"
  >
    保存当前条件
  </el-button>
</el-space>
```

- [ ] **Step 3: Add save dialog template**

Place this dialog near the existing progress dialog in `OrderQueryParamLayout.vue`:

```vue
<el-dialog
  v-model="presetDialogVisible"
  append-to-body
  title="保存搜索条件"
  width="420px"
>
  <el-form label-width="110px" :model="presetForm" @submit.prevent>
    <el-form-item label="条件名称">
      <el-input
        v-model="presetForm.name"
        maxlength="50"
        placeholder="请输入条件名称"
        show-word-limit
      />
    </el-form-item>
    <el-form-item label="时间保存方式">
      <el-radio-group v-model="presetForm.timeMode">
        <el-radio-button label="ABSOLUTE">绝对时间</el-radio-button>
        <el-radio-button label="RELATIVE">相对时间</el-radio-button>
      </el-radio-group>
    </el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="presetDialogVisible = false">取消</el-button>
    <el-button :loading="presetSaving" type="primary" @click="handleSavePreset">
      保存
    </el-button>
  </template>
</el-dialog>
```

- [ ] **Step 4: Add preset state and page type computed**

In `OrderQueryParamLayout.vue`, after `const countryOptions = ref<any>([])`, add:

```ts
const presetLoading = ref(false)
const presetSaving = ref(false)
const presetDialogVisible = ref(false)
const selectedPresetId = ref<string | undefined>(undefined)
const presetOptions = ref<OrderSearchPreset[]>([])
const presetForm = reactive<{
  name: string
  timeMode: OrderSearchPresetTimeMode
}>({
  name: '',
  timeMode: 'RELATIVE',
})

const presetPageType = computed<OrderSearchPresetPageType | undefined>(() => {
  if (props.isContact) return undefined
  return props.isAudit ? 'ORDER_AUDIT' : 'ORDER_MANAGER'
})
```

- [ ] **Step 5: Add fetch, save, apply, and delete methods**

In `OrderQueryParamLayout.vue`, add these methods after `const queryData = () => { emit('onSearch') }`:

```ts
const fetchOrderSearchPresets = async () => {
  if (!presetPageType.value) {
    presetOptions.value = []
    return
  }
  presetLoading.value = true
  try {
    const res = await listOrderSearchPresets(presetPageType.value)
    presetOptions.value = res?.data || []
  } finally {
    presetLoading.value = false
  }
}

const openPresetDialog = () => {
  presetForm.name = ''
  presetForm.timeMode = 'RELATIVE'
  presetDialogVisible.value = true
}

const handleSavePreset = async () => {
  if (!presetPageType.value) return
  const name = presetForm.name.trim()
  if (!name) {
    $baseMessage('请输入条件名称', 'warning', 'hey')
    return
  }

  const existing = presetOptions.value.find((item) => item.name === name)
  if (existing) {
    await ElMessageBox.confirm(`已存在名为“${name}”的搜索条件，是否覆盖？`, '覆盖确认', {
      confirmButtonText: '覆盖',
      cancelButtonText: '取消',
      type: 'warning',
    })
  }

  presetSaving.value = true
  try {
    await saveOrderSearchPreset({
      pageType: presetPageType.value,
      name,
      timeMode: presetForm.timeMode,
      queryParams: buildPresetQueryParams(queryForm.value, presetForm.timeMode),
    })
    $baseMessage('保存成功', 'success', 'hey')
    presetDialogVisible.value = false
    await fetchOrderSearchPresets()
  } finally {
    presetSaving.value = false
  }
}

const handleApplyPreset = async (presetId?: string) => {
  if (!presetId) return
  const res = await useOrderSearchPreset(presetId)
  const preset = res?.data as OrderSearchPreset | undefined
  if (!preset) return

  autoInferSearchType.value = false
  applyPresetQueryParams(queryForm.value, preset.queryParams)
  queryForm.value.pageNo = 1
  await fetchOrderSearchPresets()
  queryData()
}

const handleDeletePreset = async (preset: OrderSearchPreset) => {
  await ElMessageBox.confirm(`确认删除搜索条件“${preset.name}”？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await deleteOrderSearchPreset(preset.id)
  if (selectedPresetId.value === preset.id) {
    selectedPresetId.value = undefined
  }
  $baseMessage('删除成功', 'success', 'hey')
  await fetchOrderSearchPresets()
}
```

- [ ] **Step 6: Fetch presets when the component activates**

In the existing `onActivated` callback block, add this line before the closing brace:

```ts
fetchOrderSearchPresets()
```

Then add an initial fetch for first mount after the existing `onActivated` block:

```ts
onBeforeMount(() => {
  fetchOrderSearchPresets()
})
```

If `OrderQueryParamLayout.vue` already has an `onBeforeMount` block after this edit, keep one block and include `fetchOrderSearchPresets()` inside it; do not create two separate `onBeforeMount` blocks in the same file.

- [ ] **Step 7: Add dropdown option styles**

In `OrderQueryParamLayout.vue` scoped style, add:

```css
.preset-option {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.preset-option-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

- [ ] **Step 8: Run frontend type check**

Run:

```powershell
cd v7-shop-admin
pnpm vue-tsc
```

Expected: PASS.

- [ ] **Step 9: Commit order search form wiring**

Run:

```powershell
git add v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue
git commit -m "feat(order): wire search presets into order query form"
```

---

### Task 5: End-To-End Verification And Cleanup

**Files:**
- Verify: `v7-shop-services`
- Verify: `v7-shop-admin`
- Inspect: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue`

- [ ] **Step 1: Run backend compile**

Run:

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run backend search preset test**

Run:

```powershell
cd v7-shop-services
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.OrderSearchPresetServiceTest"
```

Expected: PASS.

- [ ] **Step 3: Run frontend type check**

Run:

```powershell
cd v7-shop-admin
pnpm vue-tsc
```

Expected: PASS.

- [ ] **Step 4: Manual verification in browser**

Start the existing dev stack the same way this repo is normally run. Then verify these exact cases:

1. In `/order/orderAudit`, select filters including date range `昨天 09:00 ~ 今天 09:00`, save as `每日审单` with relative time.
2. Select `每日审单`; expected: form restores, `pageNo` becomes 1, search runs immediately, URL query updates, search type stays as saved.
3. Save another audit preset as `绝对审单` with absolute time; select it the next day or by inspecting saved JSON; expected: date range remains the original absolute ISO dates.
4. In `/order/orderManager`, open saved presets; expected: audit presets are absent.
5. Save a manager preset with the same name `每日审单`; expected: it is allowed and only appears in order manager.
6. Delete the manager preset; expected: the option disappears after confirmation.
7. Open `/order/orderContact`; expected: no saved-condition dropdown and no save button.
8. Save with blank name; expected: frontend warning `请输入条件名称`.
9. Save a preset named `每日审单` again on audit page; expected: overwrite confirmation appears and confirmed save updates the existing preset.

- [ ] **Step 5: Inspect final diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only files from this plan are changed.

- [ ] **Step 6: Commit verification fixes if any**

If Step 4 or Step 5 requires small fixes, stage only files touched by this feature and commit:

```powershell
git add v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetPageType.java v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/enums/OrderSearchPresetTimeMode.java v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/entities/primary/OrderSearchPreset.java v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/repositories/primary/OrderSearchPresetRepository.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/req/SaveOrderSearchPresetRequest.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/resp/OrderSearchPresetResponse.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IOrderSearchPresetService.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/OrderSearchPresetService.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/controller/OrderSearchPresetController.java v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/OrderSearchPresetServiceTest.java v7-shop-admin/src/api/orderSearchPreset.ts v7-shop-admin/src/views/order/vabAutoComponents/orderSearchPresetHelper.ts v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue
git commit -m "fix(order): polish search preset behavior"
```

If no fixes are needed, do not create an empty commit.

---

## Self-Review

Spec coverage:

- Personal backend persistence: Task 1 entity/repository/service and Task 2 controller.
- Page isolation: `pageType` enum and repository queries in Task 1, frontend `presetPageType` in Task 4.
- Absolute and relative time: helper in Task 3 and manual checks in Task 5.
- No pagination save: helper in Task 3 omits pagination and apply keeps `pageSize`, resets `pageNo`.
- Immediate search: Task 4 `handleApplyPreset` calls `queryData()`.
- Search type inference lock: Task 4 sets `autoInferSearchType.value = false` before applying.
- Add, overwrite, delete: Task 1 service and Task 4 UI methods.
- Recent-use ordering: Task 1 repository query and `usePreset`.
- Order contact excluded: Task 4 `presetPageType` returns `undefined` for contact and Task 5 verifies it.

Placeholder scan:

- The plan contains no placeholder markers, no deferred implementation slots, and no unspecified files.

Type consistency:

- Backend enum names match frontend string union values: `ORDER_MANAGER`, `ORDER_AUDIT`, `ABSOLUTE`, `RELATIVE`.
- API function names match imports used by `OrderQueryParamLayout.vue`.
- Helper function names match Task 4 imports.
