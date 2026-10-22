package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.dao.repositories.primary.OrderPiiRepository;
import cn.v7soft.dao.repositories.primary.OrderRiskRecordInfoPiiRepository;
import cn.v7soft.dao.repositories.primary.ProtectedDataAccessLogRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderPiiRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRiskRecordInfoPiiRepository;
import cn.v7soft.dao.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyPiiRedactionServiceTest {
    private static final Long COMPANY_ID = 10L;
    private static final Long WEBSITE_ID = 20L;

    @Mock private TemporaryOrderPiiRepository temporaryOrderPiiRepository;
    @Mock private OrderPiiRepository orderPiiRepository;
    @Mock private TemporaryOrderRiskRecordInfoPiiRepository temporaryRiskPiiRepository;
    @Mock private OrderRiskRecordInfoPiiRepository orderRiskPiiRepository;
    @Mock private ProtectedDataAccessLogRepository accessLogRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private ShopifyAppProperties properties;
    private ShopifyPiiRedactionService service;

    @BeforeEach
    void setUp() {
        properties = new ShopifyAppProperties();
        service = new ShopifyPiiRedactionService(temporaryOrderPiiRepository, orderPiiRepository,
                temporaryRiskPiiRepository, orderRiskPiiRepository, accessLogRepository, properties, transactionTemplate);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void transactionRunsCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    // ==================== 按店铺 ====================

    @Test
    @DisplayName("redactByWebsite：两张订单表按店铺脱敏，先查风险记录 id 再脱敏再清 IP，返回两表合计行数")
    void shouldRedactByWebsiteOnBothTables() {
        when(temporaryOrderPiiRepository.findRiskInfoIdsByWebsite(COMPANY_ID, "SHOPIFY", WEBSITE_ID)).thenReturn(List.of(1L, 2L));
        when(orderPiiRepository.findRiskInfoIdsByWebsite(COMPANY_ID, "SHOPIFY", WEBSITE_ID)).thenReturn(List.of(3L));
        when(temporaryOrderPiiRepository.redactByWebsite(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), any())).thenReturn(4);
        when(orderPiiRepository.redactByWebsite(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), any())).thenReturn(2);
        LocalDateTime before = LocalDateTime.now();

        int affected = service.redactByWebsite(COMPANY_ID, WEBSITE_ID);

        assertEquals(6, affected);
        InOrder inOrder = inOrder(temporaryOrderPiiRepository, orderPiiRepository, temporaryRiskPiiRepository, orderRiskPiiRepository);
        inOrder.verify(temporaryOrderPiiRepository).findRiskInfoIdsByWebsite(COMPANY_ID, "SHOPIFY", WEBSITE_ID);
        inOrder.verify(orderPiiRepository).findRiskInfoIdsByWebsite(COMPANY_ID, "SHOPIFY", WEBSITE_ID);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        inOrder.verify(temporaryOrderPiiRepository).redactByWebsite(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), now.capture());
        inOrder.verify(orderPiiRepository).redactByWebsite(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), eq(now.getValue()));
        inOrder.verify(temporaryRiskPiiRepository).clearIpByIds(List.of(1L, 2L));
        inOrder.verify(orderRiskPiiRepository).clearIpByIds(List.of(3L));
        assertFalse(now.getValue().isBefore(before), "piiRedactedAt 写入当前时间");
    }

    @Test
    @DisplayName("redactByWebsite：没有风险记录时不清 IP")
    void shouldSkipRiskClearWhenNoRiskIds() {
        when(temporaryOrderPiiRepository.findRiskInfoIdsByWebsite(any(), any(), any())).thenReturn(List.of());
        when(orderPiiRepository.findRiskInfoIdsByWebsite(any(), any(), any())).thenReturn(List.of());

        assertEquals(0, service.redactByWebsite(COMPANY_ID, WEBSITE_ID));
        verifyNoInteractions(temporaryRiskPiiRepository, orderRiskPiiRepository);
    }

    @Test
    @DisplayName("风险记录 id 去重后按 500 一批清空 IP")
    void shouldClearRiskIpsInBatches() {
        List<Long> ids = new ArrayList<>(LongStream.rangeClosed(1, 1200).boxed().toList());
        ids.add(1L);
        when(temporaryOrderPiiRepository.findRiskInfoIdsByWebsite(any(), any(), any())).thenReturn(ids);
        when(orderPiiRepository.findRiskInfoIdsByWebsite(any(), any(), any())).thenReturn(List.of());

        service.redactByWebsite(COMPANY_ID, WEBSITE_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> batches = ArgumentCaptor.forClass(Collection.class);
        verify(temporaryRiskPiiRepository, times(3)).clearIpByIds(batches.capture());
        assertEquals(List.of(500, 500, 200), batches.getAllValues().stream().map(Collection::size).toList());
    }

    // ==================== 按客户 ====================

    @Test
    @DisplayName("redactByCustomer：订单 id 去空白去重、email 转小写，三个条件都生效")
    void shouldRedactByCustomerWithNormalizedParams() {
        when(temporaryOrderPiiRepository.redactByCustomer(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), any())).thenReturn(1);
        when(orderPiiRepository.redactByCustomer(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), any())).thenReturn(1);
        when(temporaryOrderPiiRepository.findRiskInfoIdsByCustomer(any(), any(), any(), anyBoolean(), any(), anyBoolean(),
                any(), anyBoolean(), any())).thenReturn(List.of(7L));

        int affected = service.redactByCustomer(COMPANY_ID, WEBSITE_ID, Arrays.asList(" 1001 ", "1002", "1001", "", null),
                " Alice@Example.COM ", " +15551234 ");

        assertEquals(2, affected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(temporaryOrderPiiRepository).redactByCustomer(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), eq(true),
                ids.capture(), eq(true), eq("alice@example.com"), eq(true), eq("+15551234"), any());
        assertEquals(List.of("1001", "1002"), new ArrayList<>(ids.getValue()));
        verify(orderPiiRepository).redactByCustomer(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), eq(true),
                any(), eq(true), eq("alice@example.com"), eq(true), eq("+15551234"), any());
        verify(temporaryRiskPiiRepository).clearIpByIds(List.of(7L));
    }

    @Test
    @DisplayName("redactByCustomer：没有订单 id 时传占位值且 hasIds=false，空 email/phone 的条件关闭")
    void shouldDisableBlankCustomerConditions() {
        service.redactByCustomer(COMPANY_ID, WEBSITE_ID, List.of(), "a@b.com", "  ");

        verify(temporaryOrderPiiRepository).redactByCustomer(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), eq(false),
                eq(List.of(ShopifyPiiRedactionService.NO_MATCH_ORDER_ID)), eq(true), eq("a@b.com"), eq(false), eq(""), any());
        verify(orderPiiRepository).findRiskInfoIdsByCustomer(eq(COMPANY_ID), eq("SHOPIFY"), eq(WEBSITE_ID), eq(false),
                eq(List.of(ShopifyPiiRedactionService.NO_MATCH_ORDER_ID)), eq(true), eq("a@b.com"), eq(false), eq(""));
    }

    @Test
    @DisplayName("redactByCustomer：订单 id、email、phone 都为空时不脱敏任何订单")
    void shouldNotRedactWhenNoCustomerCondition() {
        assertEquals(0, service.redactByCustomer(COMPANY_ID, WEBSITE_ID, null, null, ""));
        verifyNoInteractions(temporaryOrderPiiRepository, orderPiiRepository, temporaryRiskPiiRepository, orderRiskPiiRepository);
    }

    // ==================== 保留期 ====================

    @Test
    @DisplayName("redactExpired：silent 全租户，在事务内按 now - days 脱敏两表并清 IP，结束后恢复 silent 状态")
    void shouldRedactExpiredSilently() {
        transactionRunsCallback();
        TenantContext.setCurrentTenant(99L, null);
        AtomicBoolean silentDuringQuery = new AtomicBoolean(false);
        when(temporaryOrderPiiRepository.redactExpired(eq("SHOPIFY"), any(), any())).thenAnswer(inv -> {
            silentDuringQuery.set(TenantContext.isSilent());
            return 3;
        });
        when(orderPiiRepository.redactExpired(eq("SHOPIFY"), any(), any())).thenReturn(5);
        when(orderPiiRepository.findRiskInfoIdsExpired(eq("SHOPIFY"), any())).thenReturn(List.of(8L));

        int affected = service.redactExpired(365);

        assertEquals(8, affected);
        assertTrue(silentDuringQuery.get(), "查询时处于 silent（全租户）");
        assertFalse(TenantContext.isSilent(), "结束后恢复");
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(temporaryOrderPiiRepository).redactExpired(eq("SHOPIFY"), before.capture(), now.capture());
        assertEquals(Duration.ofDays(365), Duration.between(before.getValue(), now.getValue()));
        verify(temporaryOrderPiiRepository).findRiskInfoIdsExpired("SHOPIFY", before.getValue());
        verify(orderRiskPiiRepository).clearIpByIds(List.of(8L));
        verify(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("redactExpired：保留天数 <= 0 时不处理")
    void shouldSkipExpiredWhenRetentionDisabled() {
        assertEquals(0, service.redactExpired(0));
        verifyNoInteractions(transactionTemplate, temporaryOrderPiiRepository, orderPiiRepository);
    }

    @Test
    @DisplayName("cleanupAccessLogs：silent 全租户删除 access_time < now - days 的访问日志")
    void shouldCleanupAccessLogsSilently() {
        transactionRunsCallback();
        AtomicBoolean silent = new AtomicBoolean(false);
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        when(accessLogRepository.deleteByAccessTimeBefore(before.capture())).thenAnswer(inv -> {
            silent.set(TenantContext.isSilent());
            return 12;
        });
        LocalDateTime expected = LocalDateTime.now().minusDays(30);

        assertEquals(12, service.cleanupAccessLogs(30));
        assertTrue(silent.get());
        assertTrue(Math.abs(Duration.between(expected, before.getValue()).toSeconds()) < 5);
    }

    @Test
    @DisplayName("定时任务按配置天数执行；脱敏失败不影响访问日志清理")
    void shouldRunRetentionTaskWithConfiguredDays() {
        properties.setPiiRetentionDays(100);
        properties.setAccessLogRetentionDays(200);
        when(transactionTemplate.execute(any()))
                .thenThrow(new IllegalStateException("db down"))
                .thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        when(accessLogRepository.deleteByAccessTimeBefore(before.capture())).thenReturn(1);
        LocalDateTime expected = LocalDateTime.now().minusDays(200);

        assertDoesNotThrow(() -> service.runRetentionTask());

        verify(transactionTemplate, times(2)).execute(any());
        assertTrue(Math.abs(Duration.between(expected, before.getValue()).toSeconds()) < 5);
    }

    @Test
    @DisplayName("@Scheduled 为每天 3 点")
    void shouldScheduleAtThreeAm() throws Exception {
        Method method = ShopifyPiiRedactionService.class.getMethod("runRetentionTask");
        assertEquals("0 0 3 * * ?", method.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class).cron());
    }

    // ==================== SQL 字符串 ====================

    @Test
    @DisplayName("脱敏字段集：姓名 [redacted]、电话空串，其余个人字段置空，写入 pii_redacted_at；城市/省份不动")
    void redactSetShouldMatchContract() {
        for (String set : List.of(TemporaryOrderPiiRepository.REDACT_SET, OrderPiiRepository.REDACT_SET)) {
            assertTrue(set.contains("o.first_name = '[redacted]'"));
            assertTrue(set.contains("o.last_name = '[redacted]'"));
            assertTrue(set.contains("o.phone = ''"));
            for (String column : List.of("phone_last_8", "email", "address", "postal_code", "district", "remark")) {
                assertTrue(set.contains("o." + column + " = NULL"), column);
            }
            assertTrue(set.contains("o.pii_redacted_at = :now"));
            assertFalse(set.contains("city"));
            assertFalse(set.contains("province"));
            assertFalse(set.contains("country"));
        }
        assertTrue(TemporaryOrderPiiRepository.REDACT_SET.startsWith("UPDATE t_temporary_orders o"));
        assertTrue(OrderPiiRepository.REDACT_SET.startsWith("UPDATE t_orders o"));
    }

    @Test
    @DisplayName("条件：按店铺限定公司/平台/未脱敏/website_id；按客户三者任一；到期按 update_time；不过滤软删除")
    void conditionsShouldMatchContract() {
        for (String condition : List.of(TemporaryOrderPiiRepository.WEBSITE_CONDITION, OrderPiiRepository.WEBSITE_CONDITION)) {
            assertTrue(condition.contains("o.company_id = :companyId"));
            assertTrue(condition.contains("o.platform = :platform"));
            assertTrue(condition.contains("o.pii_redacted_at IS NULL"));
            assertTrue(condition.contains("c.website_id = :websiteId"));
            assertFalse(condition.contains("status"));
        }
        assertTrue(TemporaryOrderPiiRepository.WEBSITE_CONDITION.contains("FROM t_temporary_order_context_infos c"));
        assertTrue(OrderPiiRepository.WEBSITE_CONDITION.contains("FROM t_order_context_infos c"));
        for (String condition : List.of(TemporaryOrderPiiRepository.CUSTOMER_CONDITION, OrderPiiRepository.CUSTOMER_CONDITION)) {
            assertTrue(condition.contains("o.origin_order_id IN (:originOrderIds)"));
            assertTrue(condition.contains("LOWER(o.email) = :email"));
            assertTrue(condition.contains("o.phone = :phone"));
            assertTrue(condition.contains(" OR "));
        }
        for (String condition : List.of(TemporaryOrderPiiRepository.EXPIRED_CONDITION, OrderPiiRepository.EXPIRED_CONDITION)) {
            assertTrue(condition.contains("o.platform = :platform"));
            assertTrue(condition.contains("o.pii_redacted_at IS NULL"));
            assertTrue(condition.contains("o.update_time < :before"));
            assertFalse(condition.contains("company_id"), "到期脱敏全租户");
        }
    }

    @Test
    @DisplayName("仓库方法：UPDATE 均为 @Modifying 原生 SQL；风险记录清空远程/真实 IP")
    void repositoryQueriesShouldBeNativeAndModifying() throws Exception {
        for (Class<?> repository : List.of(TemporaryOrderPiiRepository.class, OrderPiiRepository.class,
                TemporaryOrderRiskRecordInfoPiiRepository.class, OrderRiskRecordInfoPiiRepository.class)) {
            for (Method method : repository.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                assertNotNull(query, method.getName());
                assertTrue(query.nativeQuery(), method.getName());
                assertEquals(query.value().startsWith("UPDATE"), method.isAnnotationPresent(Modifying.class), method.getName());
            }
        }
        for (Class<?> repository : List.of(TemporaryOrderRiskRecordInfoPiiRepository.class, OrderRiskRecordInfoPiiRepository.class)) {
            String sql = repository.getMethod("clearIpByIds", Collection.class).getAnnotation(Query.class).value();
            for (String column : List.of("remote_ip", "remote_ip_info", "real_ip", "real_ip_info")) {
                assertTrue(sql.contains("r." + column + " = NULL"), column);
            }
            assertTrue(sql.contains("WHERE r.id IN (:ids)"));
        }
        assertTrue(TemporaryOrderRiskRecordInfoPiiRepository.class.getMethod("clearIpByIds", Collection.class)
                .getAnnotation(Query.class).value().contains("t_temporary_risk_record_infos"));
        assertTrue(OrderRiskRecordInfoPiiRepository.class.getMethod("clearIpByIds", Collection.class)
                .getAnnotation(Query.class).value().contains("t_order_risk_record_infos"));
    }
}
