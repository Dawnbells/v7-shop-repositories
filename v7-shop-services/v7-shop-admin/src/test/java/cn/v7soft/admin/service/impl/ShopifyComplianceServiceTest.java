package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.INoticeService;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ShopifyComplianceRecord;
import cn.v7soft.dao.entities.primary.ShopifyWebhookEvent;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.repositories.primary.ShopifyComplianceRecordRepository;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyComplianceServiceTest {
    private static final Long COMPANY_ID = 10L;
    private static final Long WEBSITE_ID = 20L;
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 1, 8, 30, 0);

    @Mock private ShopifyComplianceRecordRepository recordRepository;
    @Mock private ShopifyPiiRedactionService redactionService;
    @Mock private ShopifyWebsiteStore websiteStore;
    @Mock private INoticeService noticeService;
    @Mock private ThirdPartyWebsiteRepository websiteRepository;

    @InjectMocks
    private ShopifyComplianceService service;

    /** 每次 save 时记录的状态快照（同一对象会被多次修改） */
    private final List<ShopifyComplianceRecord.State> savedStates = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(recordRepository.save(any())).thenAnswer(inv -> {
            ShopifyComplianceRecord record = inv.getArgument(0);
            savedStates.add(record.getState());
            return record;
        });
    }

    private void storedWebsite(ThirdPartyAuthStatusEnum authStatus, LocalDateTime uninstalledAt) {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder().id(WEBSITE_ID).companyId(COMPANY_ID)
                .accessMode(ShopifyAccessMode.PUBLIC_APP).authStatus(authStatus).uninstalledAt(uninstalledAt).build();
        when(websiteRepository.findById(WEBSITE_ID)).thenReturn(Optional.of(website));
    }

    private static ShopifyWebhookEvent event(String topic, String payload, Long websiteId) {
        return ShopifyWebhookEvent.builder()
                .webhookId("wh-1")
                .topic(topic)
                .shopDomain("demo.myshopify.com")
                .websiteId(websiteId)
                .companyId(websiteId == null ? null : COMPANY_ID)
                .payload(payload)
                .receivedAt(RECEIVED_AT)
                .build();
    }

    @Test
    @DisplayName("shop/redact：记录 PENDING → 按店铺脱敏 → 清凭证 → DONE + affectedCount，截止时间 +30 天")
    void shouldHandleShopRedact() {
        storedWebsite(ThirdPartyAuthStatusEnum.DISCONNECTED, RECEIVED_AT.minusDays(2));
        when(redactionService.redactByWebsite(COMPANY_ID, WEBSITE_ID)).thenReturn(9);

        ShopifyComplianceRecord record = service.shopRedact(event("shop/redact",
                "{\"shop_id\":954889,\"shop_domain\":\"demo.myshopify.com\"}", WEBSITE_ID));

        InOrder inOrder = inOrder(recordRepository, redactionService, websiteStore);
        inOrder.verify(recordRepository).save(any());
        inOrder.verify(redactionService).redactByWebsite(COMPANY_ID, WEBSITE_ID);
        inOrder.verify(websiteStore).clearCredentials(WEBSITE_ID);
        inOrder.verify(recordRepository).save(any());
        assertEquals(List.of(ShopifyComplianceRecord.State.PENDING, ShopifyComplianceRecord.State.DONE), savedStates);
        assertEquals(ShopifyComplianceRecord.State.DONE, record.getState());
        assertEquals(9, record.getAffectedCount());
        assertNotNull(record.getCompletedAt());
        assertEquals("shop/redact", record.getTopic());
        assertEquals("demo.myshopify.com", record.getShopDomain());
        assertEquals(WEBSITE_ID, record.getWebsiteId());
        assertEquals(COMPANY_ID, record.getCompanyId());
        assertEquals(RECEIVED_AT, record.getReceivedAt());
        assertEquals(RECEIVED_AT.plusDays(30), record.getDueAt());
        assertNull(record.getNote());
        verify(websiteStore, never()).markDisconnected(any());
    }

    @Test
    @DisplayName("shop/redact 异常时序（未收到卸载、uninstalledAt 为空）：先断开店铺再脱敏清凭证，note 写明")
    void shouldDisconnectBeforeShopRedactWhenNotUninstalled() {
        storedWebsite(ThirdPartyAuthStatusEnum.AUTHED, null);
        when(redactionService.redactByWebsite(COMPANY_ID, WEBSITE_ID)).thenReturn(3);

        ShopifyComplianceRecord record = service.shopRedact(event("shop/redact", "{}", WEBSITE_ID));

        InOrder inOrder = inOrder(websiteStore, redactionService);
        inOrder.verify(websiteStore).markDisconnected(WEBSITE_ID);
        inOrder.verify(redactionService).redactByWebsite(COMPANY_ID, WEBSITE_ID);
        inOrder.verify(websiteStore).clearCredentials(WEBSITE_ID);
        assertEquals(ShopifyComplianceRecord.State.DONE, record.getState());
        assertEquals(3, record.getAffectedCount());
        assertEquals("未收到卸载事件，按异常时序处理", record.getNote());
    }

    @Test
    @DisplayName("shop/redact：卸载后又重新授权（uninstalledAt 有值但仍 AUTHED）同样先断开")
    void shouldDisconnectWhenReauthorizedAfterUninstall() {
        storedWebsite(ThirdPartyAuthStatusEnum.AUTHED, RECEIVED_AT.minusDays(10));

        ShopifyComplianceRecord record = service.shopRedact(event("shop/redact", "{}", WEBSITE_ID));

        verify(websiteStore).markDisconnected(WEBSITE_ID);
        assertEquals("未收到卸载事件，按异常时序处理", record.getNote());
    }

    @Test
    @DisplayName("shop/redact：店铺已被删除时不断开，照常脱敏与清凭证")
    void shouldRedactDeletedWebsiteWithoutDisconnect() {
        when(websiteRepository.findById(WEBSITE_ID)).thenReturn(Optional.empty());
        when(redactionService.redactByWebsite(COMPANY_ID, WEBSITE_ID)).thenReturn(1);

        ShopifyComplianceRecord record = service.shopRedact(event("shop/redact", "{}", WEBSITE_ID));

        verify(websiteStore, never()).markDisconnected(any());
        verify(websiteStore).clearCredentials(WEBSITE_ID);
        assertEquals(ShopifyComplianceRecord.State.DONE, record.getState());
        assertNull(record.getNote());
    }

    @Test
    @DisplayName("shop/redact：脱敏失败时记录失败原因、不清凭证，异常抛出交给处理器重试")
    void shouldKeepRecordPendingWhenShopRedactFails() {
        storedWebsite(ThirdPartyAuthStatusEnum.DISCONNECTED, RECEIVED_AT);
        when(redactionService.redactByWebsite(COMPANY_ID, WEBSITE_ID)).thenThrow(new IllegalStateException("db down"));
        ShopifyWebhookEvent event = event("shop/redact", "{\"shop_domain\":\"demo.myshopify.com\"}", WEBSITE_ID);

        assertThrows(IllegalStateException.class, () -> service.shopRedact(event));

        verify(websiteStore, never()).clearCredentials(any());
        ArgumentCaptor<ShopifyComplianceRecord> captor = ArgumentCaptor.forClass(ShopifyComplianceRecord.class);
        verify(recordRepository, times(2)).save(captor.capture());
        ShopifyComplianceRecord record = captor.getValue();
        assertEquals(ShopifyComplianceRecord.State.PENDING, record.getState());
        assertTrue(record.getNote().contains("db down"));
    }

    @Test
    @DisplayName("重试时复用同一事件上次留下的 PENDING 记录，不重复建记录")
    @SuppressWarnings("unchecked")
    void shouldReusePendingRecordOnRetry() {
        ShopifyComplianceRecord existing = ShopifyComplianceRecord.builder()
                .id(77L).topic("shop/redact").state(ShopifyComplianceRecord.State.PENDING)
                .receivedAt(RECEIVED_AT).dueAt(RECEIVED_AT.plusDays(30)).build();
        when(recordRepository.findAll(any(Specification.class))).thenReturn(List.of(existing));
        storedWebsite(ThirdPartyAuthStatusEnum.DISCONNECTED, RECEIVED_AT);
        when(redactionService.redactByWebsite(COMPANY_ID, WEBSITE_ID)).thenReturn(1);

        ShopifyComplianceRecord record = service.shopRedact(event("shop/redact", "{}", WEBSITE_ID));

        assertSame(existing, record);
        assertEquals(ShopifyComplianceRecord.State.DONE, record.getState());
        assertEquals(1, record.getAffectedCount());
    }

    @Test
    @DisplayName("customers/redact：只按该客户（orders_to_redact、email、phone）脱敏，记录客户信息与订单 id")
    void shouldHandleCustomersRedact() {
        when(redactionService.redactByCustomer(eq(COMPANY_ID), eq(WEBSITE_ID), any(), any(), any())).thenReturn(2);
        String payload = "{\"shop_id\":954889,\"shop_domain\":\"demo.myshopify.com\","
                + "\"customer\":{\"id\":191167,\"email\":\"john@example.com\",\"phone\":\"555-625-1199\"},"
                + "\"orders_to_redact\":[299938,280263,220458]}";

        ShopifyComplianceRecord record = service.customersRedact(event("customers/redact", payload, WEBSITE_ID));

        verify(redactionService).redactByCustomer(COMPANY_ID, WEBSITE_ID, List.of("299938", "280263", "220458"),
                "john@example.com", "555-625-1199");
        verify(websiteStore, never()).clearCredentials(any());
        verify(redactionService, never()).redactByWebsite(any(), any());
        assertEquals(ShopifyComplianceRecord.State.DONE, record.getState());
        assertEquals(2, record.getAffectedCount());
        assertEquals("191167", record.getCustomerId());
        assertEquals("john@example.com", record.getCustomerEmail());
        assertEquals("555-625-1199", record.getCustomerPhone());
        assertEquals("299938,280263,220458", record.getOrderIds());
        assertEquals(List.of(ShopifyComplianceRecord.State.PENDING, ShopifyComplianceRecord.State.DONE), savedStates);
    }

    @Test
    @DisplayName("customers/redact：customer 缺 email/phone 时传 null")
    void shouldPassNullWhenCustomerFieldsMissing() {
        service.customersRedact(event("customers/redact", "{\"customer\":{\"id\":1},\"orders_to_redact\":[]}", WEBSITE_ID));

        verify(redactionService).redactByCustomer(COMPANY_ID, WEBSITE_ID, List.of(), null, null);
    }

    @Test
    @DisplayName("customers/data_request：记录 MANUAL，给店铺归属人发站内通知，内容含店铺、客户、订单与截止时间")
    void shouldHandleCustomersDataRequest() {
        ThirdPartyWebsiteDto website = ThirdPartyWebsiteDto.builder()
                .id(String.valueOf(WEBSITE_ID))
                .handle("demo")
                .owner(SystemUserDto.builder().id("555").build())
                .build();
        when(websiteStore.getDtoById(WEBSITE_ID)).thenReturn(website);
        storedWebsite(ThirdPartyAuthStatusEnum.AUTHED, null);
        String payload = "{\"shop_id\":954889,\"shop_domain\":\"demo.myshopify.com\",\"orders_requested\":[299938,280263],"
                + "\"customer\":{\"id\":191167,\"email\":\"john@example.com\",\"phone\":\"555-625-1199\"},"
                + "\"data_request\":{\"id\":9999}}";

        ShopifyComplianceRecord record = service.customersDataRequest(event("customers/data_request", payload, WEBSITE_ID));

        assertEquals(ShopifyComplianceRecord.State.MANUAL, record.getState());
        assertEquals(List.of(ShopifyComplianceRecord.State.MANUAL), savedStates);
        assertEquals("299938,280263", record.getOrderIds());
        assertEquals(RECEIVED_AT.plusDays(30), record.getDueAt());
        assertTrue(record.getNote().contains("9999"));
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(noticeService).createNotice(eq("Shopify 客户数据请求"), content.capture(), eq("SHOPIFY_COMPLIANCE"), eq(555L));
        assertEquals("店铺 demo，客户 john@example.com，涉及订单 299938,280263，请在 2026-10-31 08:30 前导出并交付商家",
                content.getValue());
        verifyNoInteractions(redactionService);
        verify(websiteStore, never()).markDisconnected(any());
    }

    @Test
    @DisplayName("customers/data_request：店铺已被软删除时记录 MANUAL 并写明原因，不发通知、不抛异常")
    void shouldKeepManualWithoutNoticeWhenWebsiteDeleted() {
        when(websiteRepository.findById(WEBSITE_ID)).thenReturn(Optional.empty());
        String payload = "{\"orders_requested\":[1],\"customer\":{\"email\":\"a@b.com\"},\"data_request\":{\"id\":42}}";

        ShopifyComplianceRecord record = assertDoesNotThrow(
                () -> service.customersDataRequest(event("customers/data_request", payload, WEBSITE_ID)));

        assertEquals(ShopifyComplianceRecord.State.MANUAL, record.getState());
        assertTrue(record.getNote().contains("data_request_id=42"));
        assertTrue(record.getNote().contains("店铺已删除或不存在，未发送站内通知，请人工处理"));
        verify(recordRepository).save(record);
        verifyNoInteractions(noticeService);
        verify(websiteStore, never()).getDtoById(any());
    }

    @Test
    @DisplayName("合规事件跳过处理（店铺不存在 / 非公开应用店铺）：只记录 DONE + note，不脱敏不通知不改店铺")
    void shouldRecordSkipped() {
        ShopifyComplianceRecord record = service.recordSkipped(event("shop/redact",
                "{\"shop_domain\":\"gone.myshopify.com\"}", null), "店铺不存在");
        ShopifyComplianceRecord notPublic = service.recordSkipped(event("customers/redact",
                "{\"customer\":{\"email\":\"a@b.com\"},\"orders_to_redact\":[1]}", null), "非公开应用店铺");

        assertEquals(ShopifyComplianceRecord.State.DONE, record.getState());
        assertEquals("店铺不存在", record.getNote());
        assertEquals(0, record.getAffectedCount());
        assertNull(record.getWebsiteId());
        assertEquals(ShopifyComplianceRecord.State.DONE, notPublic.getState());
        assertEquals("非公开应用店铺", notPublic.getNote());
        verifyNoInteractions(redactionService, websiteStore, noticeService, websiteRepository);
    }

    @Test
    @DisplayName("isComplianceTopic 只认三个合规 topic")
    void shouldRecognizeComplianceTopics() {
        assertTrue(ShopifyComplianceService.isComplianceTopic("shop/redact"));
        assertTrue(ShopifyComplianceService.isComplianceTopic("customers/redact"));
        assertTrue(ShopifyComplianceService.isComplianceTopic("customers/data_request"));
        assertFalse(ShopifyComplianceService.isComplianceTopic("orders/create"));
        assertFalse(ShopifyComplianceService.isComplianceTopic(null));
    }
}
