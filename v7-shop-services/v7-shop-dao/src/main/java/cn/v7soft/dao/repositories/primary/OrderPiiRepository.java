package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Order;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 正式订单表（t_orders）收货人个人信息脱敏，字段集与条件同 {@link TemporaryOrderPiiRepository}（原生 SQL，含软删除行）。
 */
public interface OrderPiiRepository extends BaseRepository<Order> {

    String REDACT_SET = "UPDATE t_orders o SET "
            + "o.first_name = '[redacted]', "
            + "o.last_name = '[redacted]', "
            + "o.phone = '', "
            + "o.phone_last_8 = NULL, "
            + "o.email = NULL, "
            + "o.address = NULL, "
            + "o.postal_code = NULL, "
            + "o.district = NULL, "
            + "o.remark = NULL, "
            + "o.pii_redacted_at = :now ";

    String WEBSITE_CONDITION = "o.company_id = :companyId AND o.platform = :platform AND o.pii_redacted_at IS NULL "
            + "AND o.context_info_id IN (SELECT c.id FROM t_order_context_infos c WHERE c.website_id = :websiteId) ";

    String CUSTOMER_CONDITION = "AND ((:hasIds = TRUE AND o.origin_order_id IN (:originOrderIds)) "
            + "OR (:hasEmail = TRUE AND LOWER(o.email) = :email) "
            + "OR (:hasPhone = TRUE AND o.phone = :phone)) ";

    String EXPIRED_CONDITION = "o.platform = :platform AND o.pii_redacted_at IS NULL AND o.update_time < :before ";

    String RISK_ID_SELECT = "SELECT o.risk_info_id FROM t_orders o WHERE ";

    String RISK_ID_NOT_NULL = "AND o.risk_info_id IS NOT NULL";

    // ==================== 按店铺（shop/redact） ====================

    @Query(value = RISK_ID_SELECT + WEBSITE_CONDITION + RISK_ID_NOT_NULL, nativeQuery = true)
    List<Long> findRiskInfoIdsByWebsite(@Param("companyId") Long companyId,
                                        @Param("platform") String platform,
                                        @Param("websiteId") Long websiteId);

    @Modifying
    @Query(value = REDACT_SET + "WHERE " + WEBSITE_CONDITION, nativeQuery = true)
    int redactByWebsite(@Param("companyId") Long companyId,
                        @Param("platform") String platform,
                        @Param("websiteId") Long websiteId,
                        @Param("now") LocalDateTime now);

    // ==================== 按客户（customers/redact） ====================

    /**
     * hasIds/hasEmail/hasPhone 为 false 时对应条件不生效；originOrderIds 不能为空集合（调用方传占位值），email 需小写
     */
    @Query(value = RISK_ID_SELECT + WEBSITE_CONDITION + CUSTOMER_CONDITION + RISK_ID_NOT_NULL, nativeQuery = true)
    List<Long> findRiskInfoIdsByCustomer(@Param("companyId") Long companyId,
                                         @Param("platform") String platform,
                                         @Param("websiteId") Long websiteId,
                                         @Param("hasIds") boolean hasIds,
                                         @Param("originOrderIds") Collection<String> originOrderIds,
                                         @Param("hasEmail") boolean hasEmail,
                                         @Param("email") String email,
                                         @Param("hasPhone") boolean hasPhone,
                                         @Param("phone") String phone);

    @Modifying
    @Query(value = REDACT_SET + "WHERE " + WEBSITE_CONDITION + CUSTOMER_CONDITION, nativeQuery = true)
    int redactByCustomer(@Param("companyId") Long companyId,
                         @Param("platform") String platform,
                         @Param("websiteId") Long websiteId,
                         @Param("hasIds") boolean hasIds,
                         @Param("originOrderIds") Collection<String> originOrderIds,
                         @Param("hasEmail") boolean hasEmail,
                         @Param("email") String email,
                         @Param("hasPhone") boolean hasPhone,
                         @Param("phone") String phone,
                         @Param("now") LocalDateTime now);

    // ==================== 保留期到期（全租户） ====================

    @Query(value = RISK_ID_SELECT + EXPIRED_CONDITION + RISK_ID_NOT_NULL, nativeQuery = true)
    List<Long> findRiskInfoIdsExpired(@Param("platform") String platform,
                                      @Param("before") LocalDateTime before);

    @Modifying
    @Query(value = REDACT_SET + "WHERE " + EXPIRED_CONDITION, nativeQuery = true)
    int redactExpired(@Param("platform") String platform,
                      @Param("before") LocalDateTime before,
                      @Param("now") LocalDateTime now);
}
