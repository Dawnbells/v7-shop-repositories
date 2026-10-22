package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.service.INoticeService;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.dao.entities.primary.ShopifyComplianceRecord;
import cn.v7soft.dao.entities.primary.ShopifyWebhookEvent;
import cn.v7soft.dao.repositories.primary.ShopifyComplianceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Shopify 合规 webhook 处理：shop/redact、customers/redact、customers/data_request。
 * <p>
 * 由 {@link ShopifyWebhookProcessor} 在已设置店铺租户的上下文中调用；设计文档：docs/features/shopify-public-app/ARCH.md §6.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyComplianceService {
    public static final String TOPIC_SHOP_REDACT = "shop/redact";
    public static final String TOPIC_CUSTOMERS_REDACT = "customers/redact";
    public static final String TOPIC_CUSTOMERS_DATA_REQUEST = "customers/data_request";
    public static final Set<String> COMPLIANCE_TOPICS = Set.of(TOPIC_SHOP_REDACT, TOPIC_CUSTOMERS_REDACT,
            TOPIC_CUSTOMERS_DATA_REQUEST);

    /** 合规要求的处理期限（天） */
    static final int DUE_DAYS = 30;
    static final String NOTICE_TITLE = "Shopify 客户数据请求";
    static final String NOTICE_TYPE = "SHOPIFY_COMPLIANCE";
    static final String NOTE_SHOP_NOT_FOUND = "店铺不存在";
    static final int NOTE_MAX_LENGTH = 1000;
    private static final DateTimeFormatter DUE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ShopifyComplianceRecordRepository recordRepository;
    private final ShopifyPiiRedactionService redactionService;
    private final ShopifyWebsiteStore websiteStore;
    private final INoticeService noticeService;

    public static boolean isComplianceTopic(String topic) {
        return topic != null && COMPLIANCE_TOPICS.contains(topic);
    }

    /**
     * 合规事件找不到店铺：只记录（DONE，note「店铺不存在」），无数据可处理
     */
    public ShopifyComplianceRecord recordShopNotFound(ShopifyWebhookEvent event) {
        ShopifyComplianceRecord record = newRecord(event, parse(event));
        record.setState(ShopifyComplianceRecord.State.DONE);
        record.setCompletedAt(LocalDateTime.now());
        record.setAffectedCount(0);
        record.setNote(NOTE_SHOP_NOT_FOUND);
        return recordRepository.save(record);
    }

    /**
     * shop/redact（payload {shop_id, shop_domain}）：记录 → 按店铺脱敏 → 清凭证 → DONE + affectedCount
     */
    public ShopifyComplianceRecord shopRedact(ShopifyWebhookEvent event) {
        ShopifyComplianceRecord record = startRecord(event, parse(event));
        try {
            int affected = redactionService.redactByWebsite(event.getCompanyId(), event.getWebsiteId());
            websiteStore.clearCredentials(event.getWebsiteId());
            return complete(record, affected);
        } catch (RuntimeException e) {
            markError(record, e);
            throw e;
        }
    }

    /**
     * customers/redact（payload {shop_id, shop_domain, customer:{id,email,phone}, orders_to_redact:[ids]}）：
     * 记录 → 仅脱敏该客户的订单 → DONE + affectedCount
     */
    public ShopifyComplianceRecord customersRedact(ShopifyWebhookEvent event) {
        JSONObject payload = parse(event);
        ShopifyComplianceRecord record = startRecord(event, payload);
        try {
            int affected = redactionService.redactByCustomer(event.getCompanyId(), event.getWebsiteId(),
                    idList(payload, "orders_to_redact"), record.getCustomerEmail(), record.getCustomerPhone());
            return complete(record, affected);
        } catch (RuntimeException e) {
            markError(record, e);
            throw e;
        }
    }

    /**
     * customers/data_request（payload {shop_id, shop_domain, orders_requested:[], customer:{id,email,phone},
     * data_request:{id}}）：记录 MANUAL → 给店铺归属人发站内通知，由人工在 dueAt 前导出交付商家
     */
    public ShopifyComplianceRecord customersDataRequest(ShopifyWebhookEvent event) {
        JSONObject payload = parse(event);
        ShopifyComplianceRecord record = findUnfinishedRecord(event, ShopifyComplianceRecord.State.PENDING,
                ShopifyComplianceRecord.State.MANUAL).orElseGet(() -> newRecord(event, payload));
        record.setState(ShopifyComplianceRecord.State.MANUAL);
        JSONObject dataRequest = payload.getJSONObject("data_request");
        String dataRequestId = dataRequest == null ? null : dataRequest.getStr("id");
        if (StrUtil.isNotBlank(dataRequestId)) {
            record.setNote("data_request_id=" + dataRequestId);
        }
        record = recordRepository.save(record);

        ThirdPartyWebsiteDto website = websiteStore.getDtoById(event.getWebsiteId());
        Long ownerUserId = website.getOwner() == null ? null : website.getOwner().getLongId();
        String handle = StrUtil.blankToDefault(website.getHandle(), event.getShopDomain());
        String content = "店铺 " + handle
                + "，客户 " + StrUtil.blankToDefault(record.getCustomerEmail(), "-")
                + "，涉及订单 " + StrUtil.blankToDefault(record.getOrderIds(), "无")
                + "，请在 " + record.getDueAt().format(DUE_FORMATTER) + " 前导出并交付商家";
        noticeService.createNotice(NOTICE_TITLE, content, NOTICE_TYPE, ownerUserId);
        if (ownerUserId == null) {
            log.warn("[ShopifyCompliance] 店铺没有归属人，数据请求通知未指定用户: websiteId={}", event.getWebsiteId());
        }
        return record;
    }

    /**
     * 新建 PENDING 记录；同一事件重试时复用上次留下的 PENDING 记录，避免重复
     */
    private ShopifyComplianceRecord startRecord(ShopifyWebhookEvent event, JSONObject payload) {
        ShopifyComplianceRecord record = findUnfinishedRecord(event, ShopifyComplianceRecord.State.PENDING)
                .orElseGet(() -> newRecord(event, payload));
        record.setState(ShopifyComplianceRecord.State.PENDING);
        return recordRepository.save(record);
    }

    private ShopifyComplianceRecord complete(ShopifyComplianceRecord record, int affected) {
        record.setState(ShopifyComplianceRecord.State.DONE);
        record.setAffectedCount(affected);
        record.setCompletedAt(LocalDateTime.now());
        record.setNote(null);
        return recordRepository.save(record);
    }

    private void markError(ShopifyComplianceRecord record, RuntimeException e) {
        try {
            record.setNote(StrUtil.maxLength("处理失败：" + e.getMessage(), NOTE_MAX_LENGTH - 3));
            recordRepository.save(record);
        } catch (Exception ex) {
            log.warn("[ShopifyCompliance] 记录处理失败原因时出错: recordId={}", record.getId(), ex);
        }
    }

    /**
     * 同一事件（topic + shopDomain + receivedAt）上次处理留下的未完成记录
     */
    private Optional<ShopifyComplianceRecord> findUnfinishedRecord(ShopifyWebhookEvent event,
                                                                   ShopifyComplianceRecord.State... states) {
        if (event.getReceivedAt() == null) {
            return Optional.empty();
        }
        Specification<ShopifyComplianceRecord> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("topic"), event.getTopic()),
                cb.equal(root.get("shopDomain"), event.getShopDomain()),
                cb.equal(root.get("receivedAt"), event.getReceivedAt()),
                root.get("state").in((Object[]) states));
        List<ShopifyComplianceRecord> records = recordRepository.findAll(spec);
        return records.isEmpty() ? Optional.empty() : Optional.of(records.get(0));
    }

    private static ShopifyComplianceRecord newRecord(ShopifyWebhookEvent event, JSONObject payload) {
        LocalDateTime receivedAt = event.getReceivedAt() == null ? LocalDateTime.now() : event.getReceivedAt();
        JSONObject customer = payload.getJSONObject("customer");
        List<String> orderIds = new ArrayList<>(idList(payload, "orders_to_redact"));
        orderIds.addAll(idList(payload, "orders_requested"));
        return ShopifyComplianceRecord.builder()
                .topic(event.getTopic())
                .shopDomain(StrUtil.blankToDefault(event.getShopDomain(), payload.getStr("shop_domain")))
                .websiteId(event.getWebsiteId())
                .companyId(event.getCompanyId())
                .customerId(customer == null ? null : customer.getStr("id"))
                .customerEmail(customer == null ? null : StrUtil.emptyToNull(customer.getStr("email")))
                .customerPhone(customer == null ? null : StrUtil.emptyToNull(customer.getStr("phone")))
                .orderIds(orderIds.isEmpty() ? null : String.join(",", orderIds))
                .state(ShopifyComplianceRecord.State.PENDING)
                .receivedAt(receivedAt)
                .dueAt(receivedAt.plusDays(DUE_DAYS))
                .build();
    }

    static List<String> idList(JSONObject payload, String key) {
        JSONArray array = payload.getJSONArray(key);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(array.size());
        for (Object item : array) {
            if (item != null && StrUtil.isNotBlank(item.toString())) {
                ids.add(item.toString().trim());
            }
        }
        return ids;
    }

    private static JSONObject parse(ShopifyWebhookEvent event) {
        String payload = event.getPayload();
        return StrUtil.isBlank(payload) ? new JSONObject() : JSONUtil.parseObj(payload);
    }
}
