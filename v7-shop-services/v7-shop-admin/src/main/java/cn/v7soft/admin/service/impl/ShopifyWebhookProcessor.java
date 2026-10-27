package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ShopifyWebhookEvent;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.ShopifyWebhookEventRepository;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 消费 {@code t_shopify_webhook_events} 中的待处理事件（单线程，每 2 秒最多 50 条）。
 * <p>
 * 每条事件在店铺所属公司的租户上下文中处理，结束后清理；失败按 2^attempts 分钟退避，第 5 次失败置 FAILED。
 * 设计文档：docs/features/shopify-public-app/ARCH.md §6.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyWebhookProcessor {
    static final int MAX_ATTEMPTS = 5;
    static final int ERROR_MAX_LENGTH = 1000;
    static final String NOTE_WEBSITE_PAUSED = "店铺已暂停";
    static final String NOTE_WEBSITE_NOT_FOUND = "店铺不存在";
    static final String NOTE_NOT_PUBLIC_APP = "非公开应用店铺";
    static final String NOTE_UNSUPPORTED_TOPIC = "不支持的 topic";

    private final ShopifyWebhookEventRepository eventRepository;
    private final ThirdPartyWebsiteRepository websiteRepository;
    private final ShopifyWebsiteStore websiteStore;
    private final IShopifyOrderSyncService orderSyncService;
    private final ITemporaryOrderService temporaryOrderService;
    private final ShopifyComplianceService complianceService;
    private final ICompanyService companyService;

    /**
     * 处理结果：最终状态与备注（备注写入事件的 error 列，事件表没有单独的 note 列）
     */
    record Outcome(ShopifyWebhookEvent.State state, String note) {
        static Outcome done() {
            return new Outcome(ShopifyWebhookEvent.State.DONE, null);
        }

        static Outcome done(String note) {
            return new Outcome(ShopifyWebhookEvent.State.DONE, note);
        }
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 30000)
    public void processPending() {
        List<ShopifyWebhookEvent> events;
        try {
            TenantContext.clear();
            events = eventRepository.findTop50ByStateAndNextAttemptAtBeforeOrderByReceivedAtAsc(
                    ShopifyWebhookEvent.State.PENDING, LocalDateTime.now().plusNanos(1_000));
        } catch (Exception e) {
            log.error("[ShopifyWebhook] 查询待处理事件失败", e);
            return;
        }
        for (ShopifyWebhookEvent event : events) {
            process(event);
        }
    }

    /**
     * 处理单条事件，任何异常都不会抛出（避免中断本轮其余事件）
     */
    void process(ShopifyWebhookEvent event) {
        try {
            Outcome outcome = handle(event);
            event.setState(outcome.state());
            event.setError(outcome.note());
            event.setProcessedAt(LocalDateTime.now());
            eventRepository.save(event);
        } catch (Exception e) {
            markFailure(event, e);
        } finally {
            TenantContext.clear();
        }
    }

    Outcome handle(ShopifyWebhookEvent event) {
        String topic = event.getTopic();
        if (event.getWebsiteId() == null) {
            String note = NOTE_WEBSITE_NOT_FOUND;
            if (ShopifyComplianceService.isComplianceTopic(topic)) {
                note = missingWebsiteNote(event);
                complianceService.recordSkipped(event, note);
            }
            return Outcome.done(note);
        }
        if (!ShopifyWebhookService.isSupportedTopic(topic)) {
            return new Outcome(ShopifyWebhookEvent.State.IGNORED, NOTE_UNSUPPORTED_TOPIC);
        }

        Long companyId = event.getCompanyId();
        if (companyId != null) {
            TenantContext.setCurrentTenant(companyId, companyService.companyCached(companyId));
        }

        Optional<ThirdPartyWebsite> website = websiteRepository.findById(event.getWebsiteId());
        if (website.isPresent() && !ShopifyWebhookService.isPublicAppWebsite(website.get())) {
            // 落库后店铺改成了凭证模式等情况：不得作用于非公开应用店铺
            if (ShopifyComplianceService.isComplianceTopic(topic)) {
                complianceService.recordSkipped(event, NOTE_NOT_PUBLIC_APP);
            }
            return Outcome.done(NOTE_NOT_PUBLIC_APP);
        }

        if (ShopifyWebhookService.isOrderTopic(topic)) {
            if (website.isEmpty()) {
                return Outcome.done(NOTE_WEBSITE_NOT_FOUND);
            }
            if (website.get().getStatus() != StatusEnum.VALID) {
                return Outcome.done(NOTE_WEBSITE_PAUSED);
            }
            return handleOrderTopic(event, topic);
        }

        switch (topic) {
            case ShopifyWebhookService.TOPIC_APP_UNINSTALLED -> websiteStore.markDisconnected(event.getWebsiteId());
            case ShopifyComplianceService.TOPIC_SHOP_REDACT -> complianceService.shopRedact(event);
            case ShopifyComplianceService.TOPIC_CUSTOMERS_REDACT -> complianceService.customersRedact(event);
            case ShopifyComplianceService.TOPIC_CUSTOMERS_DATA_REQUEST -> complianceService.customersDataRequest(event);
            default -> {
                return new Outcome(ShopifyWebhookEvent.State.IGNORED, NOTE_UNSUPPORTED_TOPIC);
            }
        }
        return Outcome.done();
    }

    private Outcome handleOrderTopic(ShopifyWebhookEvent event, String topic) {
        JSONObject payload = JSONUtil.parseObj(event.getPayload());
        switch (topic) {
            // 新建与更新同样处理：原始订单 ID 已存在即按它更新
            case ShopifyWebhookService.TOPIC_ORDERS_CREATE, ShopifyWebhookService.TOPIC_ORDERS_UPDATED ->
                    orderSyncService.importWebhookOrder(loadWebsiteDto(event), payload);
            case ShopifyWebhookService.TOPIC_ORDERS_CANCELLED -> {
                String originOrderId = payload.getStr("id");
                if (StrUtil.isBlank(originOrderId)) {
                    throw new IllegalArgumentException("orders/cancelled payload 缺少 id");
                }
                temporaryOrderService.markCustomerCancelled(originOrderId);
            }
            default -> {
                return new Outcome(ShopifyWebhookEvent.State.IGNORED, NOTE_UNSUPPORTED_TOPIC);
            }
        }
        return Outcome.done();
    }

    /**
     * 合规事件落库时没解析到公开应用店铺：同 handle 存在凭证模式店铺时记「非公开应用店铺」，否则「店铺不存在」。
     * 在设置租户前调用（findByShopDomain 内部 silent，且不在事务内）。
     */
    private String missingWebsiteNote(ShopifyWebhookEvent event) {
        boolean shopifyWebsiteExists = websiteStore.findByShopDomain(event.getShopDomain())
                .filter(website -> website.getWebsiteType() == WebsiteTypeEnum.SHOPIFY)
                .isPresent();
        return shopifyWebsiteExists ? NOTE_NOT_PUBLIC_APP : NOTE_WEBSITE_NOT_FOUND;
    }

    private ThirdPartyWebsiteDto loadWebsiteDto(ShopifyWebhookEvent event) {
        return websiteStore.getDtoById(event.getWebsiteId());
    }

    /**
     * attempts++；未满 5 次按 now + 2^attempts 分钟重试，否则置 FAILED。错误截断到 1000 字符
     */
    private void markFailure(ShopifyWebhookEvent event, Exception e) {
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        String error = StrUtil.maxLength(e.getClass().getSimpleName() + ": " + e.getMessage(), ERROR_MAX_LENGTH - 3);
        event.setAttempts(attempts);
        event.setError(error);
        if (attempts >= MAX_ATTEMPTS) {
            event.setState(ShopifyWebhookEvent.State.FAILED);
            log.error("[ShopifyWebhook] 事件处理失败 {} 次，置为 FAILED: id={}, webhookId={}, topic={}",
                    attempts, event.getId(), event.getWebhookId(), event.getTopic(), e);
        } else {
            event.setNextAttemptAt(LocalDateTime.now().plusMinutes(1L << attempts));
            log.warn("[ShopifyWebhook] 事件处理失败，第 {} 次，稍后重试: id={}, webhookId={}, topic={}, error={}",
                    attempts, event.getId(), event.getWebhookId(), event.getTopic(), error);
        }
        try {
            eventRepository.save(event);
        } catch (Exception ex) {
            log.error("[ShopifyWebhook] 保存事件失败状态出错: id={}", event.getId(), ex);
        }
    }
}
