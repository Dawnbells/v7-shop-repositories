package cn.v7soft.admin.service.impl;

import cn.v7soft.dao.entities.primary.ShopifyWebhookEvent;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.ShopifyWebhookEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyWebhookServiceTest {
    private static final String SHOP = "demo.myshopify.com";

    @Mock private ShopifyWebhookEventRepository eventRepository;
    @Mock private ShopifyWebsiteStore websiteStore;

    @InjectMocks
    private ShopifyWebhookService service;

    private static ThirdPartyWebsite website(WebsiteTypeEnum type) {
        return ThirdPartyWebsite.builder().id(20L).companyId(10L).handle("demo").websiteType(type).build();
    }

    private ShopifyWebhookEvent savedEvent() {
        ArgumentCaptor<ShopifyWebhookEvent> captor = ArgumentCaptor.forClass(ShopifyWebhookEvent.class);
        verify(eventRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("webhook_id 已存在：返回 DUPLICATE，不查店铺不落库")
    void shouldSkipDuplicate() {
        when(eventRepository.existsByWebhookId("wh-1")).thenReturn(true);

        assertEquals(ShopifyWebhookService.EnqueueResult.DUPLICATE,
                service.enqueue("wh-1", "orders/create", SHOP, "2026-07", "{}"));

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(websiteStore);
    }

    @Test
    @DisplayName("并发重复投递触发唯一索引冲突：视为 DUPLICATE")
    void shouldTreatUniqueViolationAsDuplicate() {
        when(websiteStore.findByShopDomain(SHOP)).thenReturn(Optional.of(website(WebsiteTypeEnum.SHOPIFY)));
        when(eventRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_webhook_id"));

        assertEquals(ShopifyWebhookService.EnqueueResult.DUPLICATE,
                service.enqueue("wh-1", "orders/create", SHOP, "2026-07", "{}"));
    }

    @Test
    @DisplayName("支持的 topic 且找到店铺：落 PENDING，带店铺 id、公司 id、原始正文与头信息")
    void shouldEnqueuePendingWithWebsite() {
        when(websiteStore.findByShopDomain(SHOP)).thenReturn(Optional.of(website(WebsiteTypeEnum.SHOPIFY)));

        assertEquals(ShopifyWebhookService.EnqueueResult.PENDING,
                service.enqueue("wh-1", "orders/create", SHOP, "2026-07", "{\"id\":1}"));

        ShopifyWebhookEvent event = savedEvent();
        assertEquals("wh-1", event.getWebhookId());
        assertEquals("orders/create", event.getTopic());
        assertEquals(SHOP, event.getShopDomain());
        assertEquals("2026-07", event.getApiVersion());
        assertEquals("{\"id\":1}", event.getPayload());
        assertEquals(20L, event.getWebsiteId());
        assertEquals(10L, event.getCompanyId());
        assertEquals(ShopifyWebhookEvent.State.PENDING, event.getState());
        assertEquals(0, event.getAttempts());
        assertNotNull(event.getNextAttemptAt());
        assertNotNull(event.getReceivedAt());
        assertNull(event.getProcessedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"products/update", "orders/paid", ""})
    @DisplayName("不支持的 topic：落 IGNORED，不查店铺")
    void shouldIgnoreUnsupportedTopic(String topic) {
        assertEquals(ShopifyWebhookService.EnqueueResult.IGNORED, service.enqueue("wh-1", topic, SHOP, "2026-07", "{}"));

        assertEquals(ShopifyWebhookEvent.State.IGNORED, savedEvent().getState());
        verifyNoInteractions(websiteStore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders/create", "orders/updated", "orders/cancelled", "app/uninstalled"})
    @DisplayName("非合规 topic 找不到店铺：落 IGNORED")
    void shouldIgnoreWhenWebsiteMissing(String topic) {
        when(websiteStore.findByShopDomain(SHOP)).thenReturn(Optional.empty());

        assertEquals(ShopifyWebhookService.EnqueueResult.IGNORED, service.enqueue("wh-1", topic, SHOP, "2026-07", "{}"));

        ShopifyWebhookEvent event = savedEvent();
        assertEquals(ShopifyWebhookEvent.State.IGNORED, event.getState());
        assertNull(event.getWebsiteId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"shop/redact", "customers/redact", "customers/data_request"})
    @DisplayName("合规 topic 即使找不到店铺也落 PENDING（websiteId 为空）")
    void shouldEnqueueComplianceWithoutWebsite(String topic) {
        when(websiteStore.findByShopDomain(SHOP)).thenReturn(Optional.empty());

        assertEquals(ShopifyWebhookService.EnqueueResult.PENDING, service.enqueue("wh-1", topic, SHOP, "2026-07", "{}"));

        ShopifyWebhookEvent event = savedEvent();
        assertEquals(ShopifyWebhookEvent.State.PENDING, event.getState());
        assertNull(event.getWebsiteId());
        assertNull(event.getCompanyId());
    }

    @Test
    @DisplayName("同 handle 的非 Shopify 店铺不算找到")
    void shouldIgnoreNonShopifyWebsite() {
        when(websiteStore.findByShopDomain(SHOP)).thenReturn(Optional.of(website(WebsiteTypeEnum.SHOPLINE)));

        assertEquals(ShopifyWebhookService.EnqueueResult.IGNORED,
                service.enqueue("wh-1", "orders/create", SHOP, "2026-07", "{}"));
        assertNull(savedEvent().getWebsiteId());
    }
}
