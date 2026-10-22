package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 正式订单表（t_orders）收货人个人信息脱敏，字段集与 {@link TemporaryOrderPiiRepository} 一致。
 */
public interface OrderPiiRepository extends BaseRepository<Order> {

    String REDACT_SET = "UPDATE Order o SET "
            + "o.deliveryInfo.firstName = '[redacted]', "
            + "o.deliveryInfo.lastName = '[redacted]', "
            + "o.deliveryInfo.phone = '', "
            + "o.deliveryInfo.phoneLast8 = NULL, "
            + "o.deliveryInfo.email = NULL, "
            + "o.deliveryInfo.address = NULL, "
            + "o.deliveryInfo.postalCode = NULL, "
            + "o.deliveryInfo.district = NULL, "
            + "o.deliveryInfo.remark = NULL, "
            + "o.piiRedactedAt = :now ";

    String WEBSITE_CONDITION = "o.companyId = :companyId AND o.platform = :platform AND o.piiRedactedAt IS NULL "
            + "AND o.contextInfo.id IN (SELECT c.id FROM OrderContextInfo c WHERE c.websiteId = :websiteId) ";

    String CUSTOMER_CONDITION = "AND ((:hasIds = TRUE AND o.originOrderId IN :originOrderIds) "
            + "OR (:hasEmail = TRUE AND LOWER(o.deliveryInfo.email) = :email) "
            + "OR (:hasPhone = TRUE AND o.deliveryInfo.phone = :phone)) ";

    String EXPIRED_CONDITION = "o.platform = :platform AND o.piiRedactedAt IS NULL AND o.updateTime < :before ";

    // ==================== 按店铺（shop/redact） ====================

    @Query("SELECT o.riskInfo.id FROM Order o WHERE " + WEBSITE_CONDITION + "AND o.riskInfo IS NOT NULL")
    List<Long> findRiskInfoIdsByWebsite(@Param("companyId") Long companyId,
                                        @Param("platform") WebsiteTypeEnum platform,
                                        @Param("websiteId") Long websiteId);

    @Modifying
    @Query(REDACT_SET + "WHERE " + WEBSITE_CONDITION)
    int redactByWebsite(@Param("companyId") Long companyId,
                        @Param("platform") WebsiteTypeEnum platform,
                        @Param("websiteId") Long websiteId,
                        @Param("now") LocalDateTime now);

    // ==================== 按客户（customers/redact） ====================

    /**
     * hasIds/hasEmail/hasPhone 为 false 时对应条件不生效；originOrderIds 不能为空集合（调用方传占位值），email 需小写
     */
    @Query("SELECT o.riskInfo.id FROM Order o WHERE " + WEBSITE_CONDITION + CUSTOMER_CONDITION
            + "AND o.riskInfo IS NOT NULL")
    List<Long> findRiskInfoIdsByCustomer(@Param("companyId") Long companyId,
                                         @Param("platform") WebsiteTypeEnum platform,
                                         @Param("websiteId") Long websiteId,
                                         @Param("hasIds") boolean hasIds,
                                         @Param("originOrderIds") Collection<String> originOrderIds,
                                         @Param("hasEmail") boolean hasEmail,
                                         @Param("email") String email,
                                         @Param("hasPhone") boolean hasPhone,
                                         @Param("phone") String phone);

    @Modifying
    @Query(REDACT_SET + "WHERE " + WEBSITE_CONDITION + CUSTOMER_CONDITION)
    int redactByCustomer(@Param("companyId") Long companyId,
                         @Param("platform") WebsiteTypeEnum platform,
                         @Param("websiteId") Long websiteId,
                         @Param("hasIds") boolean hasIds,
                         @Param("originOrderIds") Collection<String> originOrderIds,
                         @Param("hasEmail") boolean hasEmail,
                         @Param("email") String email,
                         @Param("hasPhone") boolean hasPhone,
                         @Param("phone") String phone,
                         @Param("now") LocalDateTime now);

    // ==================== 保留期到期（全租户，调用方需 silent） ====================

    @Query("SELECT o.riskInfo.id FROM Order o WHERE " + EXPIRED_CONDITION + "AND o.riskInfo IS NOT NULL")
    List<Long> findRiskInfoIdsExpired(@Param("platform") WebsiteTypeEnum platform,
                                      @Param("before") LocalDateTime before);

    @Modifying
    @Query(REDACT_SET + "WHERE " + EXPIRED_CONDITION)
    int redactExpired(@Param("platform") WebsiteTypeEnum platform,
                      @Param("before") LocalDateTime before,
                      @Param("now") LocalDateTime now);
}
