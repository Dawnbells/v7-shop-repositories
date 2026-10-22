package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.dao.entities.primary.ShopifyWebhookEvent;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.ShopifyWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Shopify webhook 事件落库与去重。只做一次店铺查询 + 一次插入，业务处理交给 {@link ShopifyWebhookProcessor}。
 * <p>
 * 不能标注 @Transactional：{@link ShopifyWebsiteStore#findByShopDomain} 需在事务外调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyWebhookService {
    public static final String TOPIC_ORDERS_CREATE = "orders/create";
    public static final String TOPIC_ORDERS_UPDATED = "orders/updated";
    public static final String TOPIC_ORDERS_CANCELLED = "orders/cancelled";
    public static final String TOPIC_APP_UNINSTALLED = "app/uninstalled";
    public static final Set<String> ORDER_TOPICS = Set.of(TOPIC_ORDERS_CREATE, TOPIC_ORDERS_UPDATED, TOPIC_ORDERS_CANCELLED);
    public static final Set<String> SUPPORTED_TOPICS = Set.of(TOPIC_ORDERS_CREATE, TOPIC_ORDERS_UPDATED,
            TOPIC_ORDERS_CANCELLED, TOPIC_APP_UNINSTALLED, ShopifyComplianceService.TOPIC_SHOP_REDACT,
            ShopifyComplianceService.TOPIC_CUSTOMERS_REDACT, ShopifyComplianceService.TOPIC_CUSTOMERS_DATA_REQUEST);

    static final int WEBHOOK_ID_MAX_LENGTH = 64;
    static final int TOPIC_MAX_LENGTH = 64;
    static final int SHOP_DOMAIN_MAX_LENGTH = 128;
    static final int API_VERSION_MAX_LENGTH = 16;

    /**
     * 落库结果
     */
    public enum EnqueueResult {
        /** webhook_id 已存在，未落库 */
        DUPLICATE,
        /** 已落库待处理 */
        PENDING,
        /** 已落库但不处理（不支持的 topic，或非合规类 topic 找不到店铺） */
        IGNORED
    }

    private final ShopifyWebhookEventRepository eventRepository;
    private final ShopifyWebsiteStore websiteStore;

    public static boolean isSupportedTopic(String topic) {
        return topic != null && SUPPORTED_TOPICS.contains(topic);
    }

    public static boolean isOrderTopic(String topic) {
        return topic != null && ORDER_TOPICS.contains(topic);
    }

    /**
     * 按 webhook_id 去重后落库。支持的 topic 按 shop_domain 解析公开应用店铺：找到落 PENDING；找不到时合规类仍落 PENDING
     * （处理器只记录后 DONE），其余落 IGNORED；不支持的 topic 落 IGNORED。
     * <p>
     * 同 handle 的凭证模式店铺（accessMode 为 CREDENTIALS 或 null）按「找不到」处理，webhook 不得作用于它。
     */
    public EnqueueResult enqueue(String webhookId, String topic, String shopDomain, String apiVersion, String payload) {
        if (eventRepository.existsByWebhookId(webhookId)) {
            return EnqueueResult.DUPLICATE;
        }
        boolean supported = isSupportedTopic(topic);
        Optional<ThirdPartyWebsite> website = supported ? findShopifyWebsite(shopDomain) : Optional.empty();
        ShopifyWebhookEvent.State state;
        if (!supported) {
            state = ShopifyWebhookEvent.State.IGNORED;
        } else if (website.isPresent() || ShopifyComplianceService.isComplianceTopic(topic)) {
            state = ShopifyWebhookEvent.State.PENDING;
        } else {
            state = ShopifyWebhookEvent.State.IGNORED;
        }

        LocalDateTime now = LocalDateTime.now();
        ShopifyWebhookEvent event = ShopifyWebhookEvent.builder()
                .webhookId(webhookId)
                .topic(StrUtil.maxLength(topic, TOPIC_MAX_LENGTH - 3))
                .shopDomain(StrUtil.maxLength(shopDomain, SHOP_DOMAIN_MAX_LENGTH - 3))
                .apiVersion(StrUtil.maxLength(apiVersion, API_VERSION_MAX_LENGTH - 3))
                .websiteId(website.map(ThirdPartyWebsite::getId).orElse(null))
                .companyId(website.map(ThirdPartyWebsite::getCompanyId).orElse(null))
                .payload(payload)
                .state(state)
                .attempts(0)
                .nextAttemptAt(now)
                .receivedAt(now)
                .processedAt(state == ShopifyWebhookEvent.State.IGNORED ? now : null)
                .build();
        try {
            eventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // 并发投递同一 webhook：唯一索引 uk_webhook_id 冲突即视为重复
            log.info("[ShopifyWebhook] 重复事件（并发）: webhookId={}, topic={}", webhookId, topic);
            return EnqueueResult.DUPLICATE;
        }
        log.info("[ShopifyWebhook] 事件落库: webhookId={}, topic={}, shop={}, state={}", webhookId, topic, shopDomain, state);
        return state == ShopifyWebhookEvent.State.PENDING ? EnqueueResult.PENDING : EnqueueResult.IGNORED;
    }

    private Optional<ThirdPartyWebsite> findShopifyWebsite(String shopDomain) {
        return websiteStore.findByShopDomain(shopDomain).filter(ShopifyWebhookService::isPublicAppWebsite);
    }

    /**
     * 是否为通过公开应用接入的 Shopify 店铺（只有这类店铺接收公开应用的 webhook）
     */
    public static boolean isPublicAppWebsite(ThirdPartyWebsite website) {
        return website != null && website.getWebsiteType() == WebsiteTypeEnum.SHOPIFY
                && ShopifyAccessMode.isPublicApp(website.getAccessMode());
    }
}
