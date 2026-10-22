package cn.v7soft.admin.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.OrderPiiRepository;
import cn.v7soft.dao.repositories.primary.OrderRiskRecordInfoPiiRepository;
import cn.v7soft.dao.repositories.primary.ProtectedDataAccessLogRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderPiiRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRiskRecordInfoPiiRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shopify 订单收货人个人信息脱敏（审单表 + 正式订单表 + 风险记录 IP）与保留期任务。
 * <p>
 * 脱敏字段集见 {@link TemporaryOrderPiiRepository}；设计文档：docs/features/shopify-public-app/ARCH.md §6.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyPiiRedactionService {
    /** 风险记录 IP 清空时 IN 列表的分批大小 */
    static final int RISK_ID_BATCH_SIZE = 500;
    /** originOrderIds 为空时的占位值（JPQL 的 IN 不能传空集合），配合 hasIds=false 永不生效 */
    static final String NO_MATCH_ORDER_ID = "__none__";
    /** 订单表 platform 列存枚举名 */
    static final String PLATFORM = WebsiteTypeEnum.SHOPIFY.name();

    private final TemporaryOrderPiiRepository temporaryOrderPiiRepository;
    private final OrderPiiRepository orderPiiRepository;
    private final TemporaryOrderRiskRecordInfoPiiRepository temporaryRiskPiiRepository;
    private final OrderRiskRecordInfoPiiRepository orderRiskPiiRepository;
    private final ProtectedDataAccessLogRepository accessLogRepository;
    private final ShopifyAppProperties appProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 脱敏某店铺的全部 Shopify 订单（shop/redact）。调用方需已设置该公司的租户上下文。
     * @return 脱敏的订单行数（审单表 + 正式订单表）
     */
    @Transactional
    public int redactByWebsite(Long companyId, Long websiteId) {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(websiteId, "websiteId");
        LocalDateTime now = LocalDateTime.now();
        String platform = PLATFORM;

        List<Long> temporaryRiskIds = temporaryOrderPiiRepository.findRiskInfoIdsByWebsite(companyId, platform, websiteId);
        List<Long> orderRiskIds = orderPiiRepository.findRiskInfoIdsByWebsite(companyId, platform, websiteId);
        int temporaryCount = temporaryOrderPiiRepository.redactByWebsite(companyId, platform, websiteId, now);
        int orderCount = orderPiiRepository.redactByWebsite(companyId, platform, websiteId, now);
        clearRiskIps(temporaryRiskIds, orderRiskIds);

        log.info("[ShopifyPii] 按店铺脱敏完成: companyId={}, websiteId={}, temporaryOrders={}, orders={}",
                companyId, websiteId, temporaryCount, orderCount);
        return temporaryCount + orderCount;
    }

    /**
     * 脱敏某店铺下指定客户的订单（customers/redact）：originOrderIds、email（忽略大小写）、phone 任一匹配即脱敏，
     * 为空的条件跳过。调用方需已设置该公司的租户上下文。
     * @return 脱敏的订单行数（审单表 + 正式订单表）
     */
    @Transactional
    public int redactByCustomer(Long companyId, Long websiteId, List<String> originOrderIds, String email, String phone) {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(websiteId, "websiteId");
        Set<String> ids = normalizeIds(originOrderIds);
        boolean hasIds = !ids.isEmpty();
        Collection<String> idParam = hasIds ? ids : List.of(NO_MATCH_ORDER_ID);
        boolean hasEmail = StrUtil.isNotBlank(email);
        String emailParam = hasEmail ? email.trim().toLowerCase(Locale.ROOT) : "";
        boolean hasPhone = StrUtil.isNotBlank(phone);
        String phoneParam = hasPhone ? phone.trim() : "";
        if (!hasIds && !hasEmail && !hasPhone) {
            log.warn("[ShopifyPii] 按客户脱敏缺少订单 ID、邮箱、电话，跳过: companyId={}, websiteId={}", companyId, websiteId);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        String platform = PLATFORM;
        List<Long> temporaryRiskIds = temporaryOrderPiiRepository.findRiskInfoIdsByCustomer(companyId, platform, websiteId,
                hasIds, idParam, hasEmail, emailParam, hasPhone, phoneParam);
        List<Long> orderRiskIds = orderPiiRepository.findRiskInfoIdsByCustomer(companyId, platform, websiteId,
                hasIds, idParam, hasEmail, emailParam, hasPhone, phoneParam);
        int temporaryCount = temporaryOrderPiiRepository.redactByCustomer(companyId, platform, websiteId,
                hasIds, idParam, hasEmail, emailParam, hasPhone, phoneParam, now);
        int orderCount = orderPiiRepository.redactByCustomer(companyId, platform, websiteId,
                hasIds, idParam, hasEmail, emailParam, hasPhone, phoneParam, now);
        clearRiskIps(temporaryRiskIds, orderRiskIds);

        log.info("[ShopifyPii] 按客户脱敏完成: companyId={}, websiteId={}, temporaryOrders={}, orders={}",
                companyId, websiteId, temporaryCount, orderCount);
        return temporaryCount + orderCount;
    }

    /**
     * 脱敏 updateTime 早于 now - retentionDays 的全部 Shopify 订单（全租户，silent）。retentionDays <= 0 时不处理。
     * <p>
     * 租户在会话打开时解析，因此先 silent 再用 TransactionTemplate 开事务，不能直接标注 @Transactional。
     * @return 脱敏的订单行数（审单表 + 正式订单表）
     */
    public int redactExpired(int retentionDays) {
        if (retentionDays <= 0) {
            log.info("[ShopifyPii] 保留天数 {} <= 0，跳过到期脱敏", retentionDays);
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime before = now.minusDays(retentionDays);
        String platform = PLATFORM;
        Integer affected = silently(() -> transactionTemplate.execute(status -> {
            List<Long> temporaryRiskIds = temporaryOrderPiiRepository.findRiskInfoIdsExpired(platform, before);
            List<Long> orderRiskIds = orderPiiRepository.findRiskInfoIdsExpired(platform, before);
            int temporaryCount = temporaryOrderPiiRepository.redactExpired(platform, before, now);
            int orderCount = orderPiiRepository.redactExpired(platform, before, now);
            clearRiskIps(temporaryRiskIds, orderRiskIds);
            return temporaryCount + orderCount;
        }));
        int count = affected == null ? 0 : affected;
        log.info("[ShopifyPii] 到期脱敏完成: retentionDays={}, before={}, affected={}", retentionDays, before, count);
        return count;
    }

    /**
     * 删除 access_time 早于 now - retentionDays 的受保护数据访问日志（全租户，silent）。retentionDays <= 0 时不处理。
     * @return 删除行数
     */
    public int cleanupAccessLogs(int retentionDays) {
        if (retentionDays <= 0) {
            log.info("[ShopifyPii] 访问日志保留天数 {} <= 0，跳过清理", retentionDays);
            return 0;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        Integer deleted = silently(() -> transactionTemplate.execute(
                status -> accessLogRepository.deleteByAccessTimeBefore(before)));
        int count = deleted == null ? 0 : deleted;
        log.info("[ShopifyPii] 访问日志清理完成: retentionDays={}, before={}, deleted={}", retentionDays, before, count);
        return count;
    }

    /**
     * 每天 3 点：到期订单脱敏 + 过期访问日志清理，两者互不影响
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void runRetentionTask() {
        try {
            redactExpired(appProperties.getPiiRetentionDays());
        } catch (Exception e) {
            log.error("[ShopifyPii] 到期脱敏失败", e);
        }
        try {
            cleanupAccessLogs(appProperties.getAccessLogRetentionDays());
        } catch (Exception e) {
            log.error("[ShopifyPii] 访问日志清理失败", e);
        }
    }

    private void clearRiskIps(List<Long> temporaryRiskIds, List<Long> orderRiskIds) {
        for (List<Long> batch : partition(temporaryRiskIds)) {
            temporaryRiskPiiRepository.clearIpByIds(batch);
        }
        for (List<Long> batch : partition(orderRiskIds)) {
            orderRiskPiiRepository.clearIpByIds(batch);
        }
    }

    private static List<List<Long>> partition(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        return ListUtil.partition(distinct, RISK_ID_BATCH_SIZE);
    }

    private static Set<String> normalizeIds(List<String> originOrderIds) {
        Set<String> ids = new LinkedHashSet<>();
        if (originOrderIds != null) {
            for (String id : originOrderIds) {
                if (StrUtil.isNotBlank(id)) {
                    ids.add(id.trim());
                }
            }
        }
        return ids;
    }

    /**
     * 临时 silent 执行，结束后恢复调用前的状态
     */
    private static <T> T silently(Supplier<T> action) {
        boolean wasSilent = TenantContext.isSilent();
        TenantContext.silent();
        try {
            return action.get();
        } finally {
            if (!wasSilent) {
                TenantContext.restore();
            }
        }
    }
}
