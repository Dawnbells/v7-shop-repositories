package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.ShopifyWebhookEvent;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.repositories.primary.ShopifyWebhookEventRepository;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import cn.v7soft.dao.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyWebhookProcessorTest {
    private static final Long COMPANY_ID = 10L;
    private static final Long WEBSITE_ID = 20L;

    @Mock private ShopifyWebhookEventRepository eventRepository;
    @Mock private ThirdPartyWebsiteRepository websiteRepository;
    @Mock private ShopifyWebsiteStore websiteStore;
    @Mock private IShopifyOrderSyncService orderSyncService;
    @Mock private ITemporaryOrderService temporaryOrderService;
    @Mock private ShopifyComplianceService complianceService;
    @Mock private ICompanyService companyService;

    @InjectMocks
    private ShopifyWebhookProcessor processor;

    private final Company company = new Company();

    @BeforeEach
    void setUp() {
        lenient().when(companyService.companyCached(COMPANY_ID)).thenReturn(company);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ShopifyWebhookEvent event(String topic, String payload) {
        return ShopifyWebhookEvent.builder()
                .id(1L)
                .webhookId("wh-1")
                .topic(topic)
                .shopDomain("demo.myshopify.com")
                .websiteId(WEBSITE_ID)
                .companyId(COMPANY_ID)
                .payload(payload)
                .state(ShopifyWebhookEvent.State.PENDING)
                .attempts(0)
                .build();
    }

    private void websiteStatus(StatusEnum status) {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder().id(WEBSITE_ID).companyId(COMPANY_ID).status(status).build();
        when(websiteRepository.findById(WEBSITE_ID)).thenReturn(Optional.of(website));
    }

    private ThirdPartyWebsiteDto websiteDto() {
        ThirdPartyWebsiteDto dto = ThirdPartyWebsiteDto.builder().id(String.valueOf(WEBSITE_ID)).handle("demo").build();
        when(websiteStore.getDtoById(WEBSITE_ID)).thenReturn(dto);
        return dto;
    }

    private static void assertDone(ShopifyWebhookEvent event) {
        assertEquals(ShopifyWebhookEvent.State.DONE, event.getState());
        assertNotNull(event.getProcessedAt());
    }

    // ==================== 分发 ====================

    @Test
    @DisplayName("orders/create → importWebhookOrder(dto, payload, false)，处理时租户为店铺公司，结束后清理")
    void shouldImportCreatedOrderUnderTenant() {
        websiteStatus(StatusEnum.VALID);
        ThirdPartyWebsiteDto dto = websiteDto();
        AtomicReference<Long> tenantDuringImport = new AtomicReference<>();
        AtomicReference<Company> companyDuringImport = new AtomicReference<>();
        when(orderSyncService.importWebhookOrder(any(), any(), anyBoolean())).thenAnswer(inv -> {
            tenantDuringImport.set(TenantContext.getCurrentTenant());
            companyDuringImport.set(TenantContext.getCurrentTenantEntity());
            return true;
        });
        ShopifyWebhookEvent event = event("orders/create", "{\"id\":450789469,\"name\":\"#1001\"}");

        processor.process(event);

        ArgumentCaptor<JSONObject> payload = ArgumentCaptor.forClass(JSONObject.class);
        verify(orderSyncService).importWebhookOrder(same(dto), payload.capture(), eq(false));
        assertEquals("450789469", payload.getValue().getStr("id"));
        assertEquals(COMPANY_ID, tenantDuringImport.get());
        assertSame(company, companyDuringImport.get());
        assertNull(TenantContext.getCurrentTenant(), "处理后清理租户");
        assertFalse(TenantContext.isSilent());
        assertDone(event);
        assertNull(event.getError());
        verify(eventRepository).save(event);
    }

    @Test
    @DisplayName("orders/updated → importWebhookOrder(dto, payload, true)")
    void shouldImportUpdatedOrder() {
        websiteStatus(StatusEnum.VALID);
        ThirdPartyWebsiteDto dto = websiteDto();
        ShopifyWebhookEvent event = event("orders/updated", "{\"id\":1}");

        processor.process(event);

        verify(orderSyncService).importWebhookOrder(same(dto), any(), eq(true));
        assertDone(event);
    }

    @Test
    @DisplayName("orders/cancelled → markCustomerCancelled(payload.id 字符串)")
    void shouldMarkCustomerCancelled() {
        websiteStatus(StatusEnum.VALID);
        ShopifyWebhookEvent event = event("orders/cancelled", "{\"id\":820982911946154508,\"cancel_reason\":\"customer\"}");

        processor.process(event);

        verify(temporaryOrderService).markCustomerCancelled("820982911946154508");
        verifyNoInteractions(orderSyncService);
        assertDone(event);
    }

    @Test
    @DisplayName("app/uninstalled → markDisconnected，不碰已同步订单")
    void shouldMarkDisconnectedOnUninstall() {
        ShopifyWebhookEvent event = event("app/uninstalled", "{\"id\":548380009,\"domain\":\"demo.myshopify.com\"}");

        processor.process(event);

        verify(websiteStore).markDisconnected(WEBSITE_ID);
        verifyNoInteractions(orderSyncService, temporaryOrderService, complianceService);
        assertDone(event);
    }

    @Test
    @DisplayName("合规 topic 分发到 ShopifyComplianceService 对应方法，且在店铺租户下执行")
    void shouldDispatchComplianceTopics() {
        AtomicReference<Long> tenant = new AtomicReference<>();
        when(complianceService.shopRedact(any())).thenAnswer(inv -> {
            tenant.set(TenantContext.getCurrentTenant());
            return null;
        });
        ShopifyWebhookEvent shopRedact = event("shop/redact", "{}");
        ShopifyWebhookEvent customersRedact = event("customers/redact", "{}");
        ShopifyWebhookEvent dataRequest = event("customers/data_request", "{}");

        processor.process(shopRedact);
        processor.process(customersRedact);
        processor.process(dataRequest);

        verify(complianceService).shopRedact(shopRedact);
        verify(complianceService).customersRedact(customersRedact);
        verify(complianceService).customersDataRequest(dataRequest);
        assertEquals(COMPANY_ID, tenant.get());
        assertDone(shopRedact);
        assertDone(customersRedact);
        assertDone(dataRequest);
        assertNull(TenantContext.getCurrentTenant());
    }

    @ParameterizedTest
    @ValueSource(strings = {"shop/redact", "customers/redact", "customers/data_request"})
    @DisplayName("合规 topic 店铺不存在：只记合规记录后 DONE，不设租户")
    void shouldRecordComplianceWhenWebsiteMissing(String topic) {
        ShopifyWebhookEvent event = event(topic, "{}");
        event.setWebsiteId(null);
        event.setCompanyId(null);

        processor.process(event);

        verify(complianceService).recordShopNotFound(event);
        verify(complianceService, never()).shopRedact(any());
        verify(complianceService, never()).customersRedact(any());
        verify(complianceService, never()).customersDataRequest(any());
        verifyNoInteractions(companyService);
        assertDone(event);
        assertEquals("店铺不存在", event.getError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders/create", "orders/updated", "orders/cancelled"})
    @DisplayName("店铺已暂停（status=INVALID）：订单类 topic 直接 DONE 并记「店铺已暂停」")
    void shouldSkipOrderTopicsWhenWebsitePaused(String topic) {
        websiteStatus(StatusEnum.INVALID);
        ShopifyWebhookEvent event = event(topic, "{\"id\":1}");

        processor.process(event);

        verifyNoInteractions(orderSyncService, temporaryOrderService);
        assertDone(event);
        assertEquals("店铺已暂停", event.getError());
        assertEquals(0, event.getAttempts());
    }

    @Test
    @DisplayName("店铺暂停不影响 app/uninstalled 与合规 topic")
    void shouldProcessNonOrderTopicsWhenPaused() {
        ShopifyWebhookEvent event = event("app/uninstalled", "{}");

        processor.process(event);

        verify(websiteStore).markDisconnected(WEBSITE_ID);
        verify(websiteRepository, never()).findById(any());
    }

    // ==================== 失败退避 ====================

    @Test
    @DisplayName("处理异常：attempts+1，next_attempt_at = now + 2^attempts 分钟，仍为 PENDING，记录错误，清理租户")
    void shouldBackoffOnFailure() {
        websiteStatus(StatusEnum.VALID);
        websiteDto();
        when(orderSyncService.importWebhookOrder(any(), any(), anyBoolean())).thenThrow(new IllegalStateException("boom"));
        ShopifyWebhookEvent event = event("orders/create", "{\"id\":1}");
        event.setAttempts(2);
        LocalDateTime before = LocalDateTime.now();

        processor.process(event);

        assertEquals(3, event.getAttempts());
        assertEquals(ShopifyWebhookEvent.State.PENDING, event.getState());
        long minutes = Duration.between(before, event.getNextAttemptAt()).toMinutes();
        assertTrue(minutes >= 7 && minutes <= 8, "2^3 = 8 分钟，实际 " + minutes);
        assertTrue(event.getError().contains("boom"));
        assertNull(event.getProcessedAt());
        assertNull(TenantContext.getCurrentTenant());
        verify(eventRepository).save(event);
    }

    @Test
    @DisplayName("第一次失败退避 2 分钟")
    void shouldBackoffTwoMinutesOnFirstFailure() {
        when(complianceService.shopRedact(any())).thenThrow(new IllegalStateException("x"));
        ShopifyWebhookEvent event = event("shop/redact", "{}");
        LocalDateTime before = LocalDateTime.now();

        processor.process(event);

        assertEquals(1, event.getAttempts());
        long seconds = Duration.between(before, event.getNextAttemptAt()).toSeconds();
        assertTrue(seconds >= 119 && seconds <= 121, "实际 " + seconds);
    }

    @Test
    @DisplayName("第 5 次失败置 FAILED，错误截断到 1000 字符")
    void shouldFailAfterFiveAttempts() {
        when(complianceService.customersRedact(any())).thenThrow(new IllegalStateException("e".repeat(5000)));
        ShopifyWebhookEvent event = event("customers/redact", "{}");
        event.setAttempts(4);

        processor.process(event);

        assertEquals(5, event.getAttempts());
        assertEquals(ShopifyWebhookEvent.State.FAILED, event.getState());
        assertTrue(event.getError().length() <= 1000);
        assertTrue(event.getError().startsWith("IllegalStateException: eee"));
    }

    @Test
    @DisplayName("保存失败状态时出错也不抛出")
    void shouldSwallowSaveErrorOnFailure() {
        when(complianceService.shopRedact(any())).thenThrow(new IllegalStateException("x"));
        when(eventRepository.save(any())).thenThrow(new IllegalStateException("db"));

        assertDoesNotThrow(() -> processor.process(event("shop/redact", "{}")));
    }

    // ==================== 调度 ====================

    @Test
    @DisplayName("processPending：取 PENDING 且到期的最早 50 条依次处理，单条失败不影响其余")
    void shouldProcessPendingBatch() {
        ShopifyWebhookEvent failing = event("shop/redact", "{}");
        ShopifyWebhookEvent ok = event("app/uninstalled", "{}");
        ok.setId(2L);
        when(eventRepository.findTop50ByStateAndNextAttemptAtBeforeOrderByReceivedAtAsc(eq(ShopifyWebhookEvent.State.PENDING), any()))
                .thenReturn(List.of(failing, ok));
        when(complianceService.shopRedact(any())).thenThrow(new IllegalStateException("x"));
        LocalDateTime before = LocalDateTime.now();

        processor.processPending();

        ArgumentCaptor<LocalDateTime> time = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(eventRepository).findTop50ByStateAndNextAttemptAtBeforeOrderByReceivedAtAsc(eq(ShopifyWebhookEvent.State.PENDING),
                time.capture());
        assertFalse(time.getValue().isBefore(before), "截止时间不早于 now（next_attempt_at <= now 的事件都取到）");
        assertEquals(1, failing.getAttempts());
        verify(websiteStore).markDisconnected(WEBSITE_ID);
        assertDone(ok);
    }

    @Test
    @DisplayName("@Scheduled(fixedDelay = 2000, initialDelay = 30000)")
    void shouldBeScheduled() throws Exception {
        Scheduled scheduled = ShopifyWebhookProcessor.class.getMethod("processPending").getAnnotation(Scheduled.class);
        assertEquals(2000, scheduled.fixedDelay());
        assertEquals(30000, scheduled.initialDelay());
    }
}
