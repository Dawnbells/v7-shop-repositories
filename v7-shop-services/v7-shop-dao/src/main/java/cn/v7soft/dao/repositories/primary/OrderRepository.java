package cn.v7soft.dao.repositories.primary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Order;

public interface OrderRepository extends BaseRepository<Order> {

    //
//    /**
//     * 根据订单状态查询订单列表
//     *
//     * @param status 订单状态
//     * @return 符合条件的订单列表
//     */
//    @Query("SELECT o FROM Order o WHERE o.status = :status")
//    List<Order> findOrdersByStatus(@Param("status") String status);
//
//    /**
//     * 根据用户ID查询订单列表
//     *
//     * @param userId 用户ID
//     * @return 用户的订单列表
//     */
//    @Query("SELECT o FROM Order o WHERE o.id= :userId")
//    List<Order> findOrdersByUserId(@Param("userId") Long userId);
//
//    @Query("SELECT o FROM Order o WHERE o.id = :userId")
//    Page<Order> findAllByCreatedBy(Long userId, Pageable pageable);
//
    @Modifying
    @Transactional
    @Query("UPDATE OrderItemInfo as i set i.skuCode=:skuCode, i.skuName=:name where i.skuId=:id")
    void syncChangeSkuInfo(@Param("id") Long id, @Param("name") String name, @Param("skuCode") String skuCode);

    /**
     * 根据终端ID查询同一国家的历史订单数
     *
     * @param deviceId    终端ID
     * @param countryCode 国家代码
     * @return 匹配条件的订单数量
     */
    @Query("SELECT COUNT(*) FROM Order AS o WHERE o.riskInfo.deviceId = :deviceId AND o.contextInfo.countryCode = :countryCode")
    int findEarlierOrdersByDeviceId(@Param("deviceId") String deviceId, @Param("countryCode") String countryCode);

    /**
     * 根据手机号码后8位查询同一国家的历史订单数
     *
     * @param phone       手机号码后8位
     * @param countryCode 国家代码
     * @return 匹配条件的订单数量
     */
    @Query("SELECT COUNT(*) FROM Order AS o WHERE o.deliveryInfo.phoneLast8 = :phone AND o.contextInfo.countryCode = :countryCode")
    int findEarlierOrdersByPhoneLast8(@Param("phone") String phone, @Param("countryCode") String countryCode);

    @Query("SELECT COUNT(*) FROM Order AS o WHERE o.riskInfo.remoteIp = :ip AND o.contextInfo.countryCode = :countryCode")
    int findEarlierOrdersByRemoteIp(@Param("ip") String customIp, @Param("countryCode") String countryCode);

    @Query("SELECT COUNT(*) FROM Order AS o WHERE o.riskInfo.realIp = :ip AND o.contextInfo.countryCode = :countryCode")
    int findEarlierOrdersByRealIp(@Param("ip") String customIp, @Param("countryCode") String countryCode);

    @Query("""
             SELECT count(*)
             FROM Order AS o
             WHERE o.deliveryInfo.firstName = :firstName
             AND o.deliveryInfo.lastName = :lastName
             AND o.contextInfo.countryCode = :countryCode
            """)
    int findEarlierOrdersByName(@Param("firstName") String firstName, @Param("lastName") String lastName, @Param("countryCode") String countryCode);

    @Query("SELECT o FROM Order o WHERE o.originOrderId = :orderId")
    Optional<Order> findByOriginOrderId(@Param("orderId") String orderId);

    @Query(value = """
            select o.* from t_orders o
            inner join t_order_risk_record_infos r on r.id = o.risk_info_id
            inner join t_order_context_infos c on c.id = o.context_info_id
            where o.order_time < :orderTime
            and r.remote_ip = :customIp
            and c.country_code = :countryCode
            limit 1
            """, nativeQuery = true)
    Optional<Order> findLastEarlierOrdersByRemoteIp(@Param("customIp") String customIp,
                                                    @Param("orderTime") LocalDateTime orderTime,
                                                    @Param("countryCode") String countryCode);

    @Query(value = """
            select o.*
            from t_orders o
            inner join t_order_context_infos c on c.id = o.context_info_id
            where o.order_time < :orderTime
            and o.phone_last_8 = :phone
            and c.country_code = :countryCode
            limit 1
            """, nativeQuery = true)
    Optional<Order> findLastEarlierOrdersByPhone(@Param("phone") String phone,
                                                 @Param("orderTime") LocalDateTime orderTime,
                                                 @Param("countryCode") String countryCode);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderTime >= :start")
    long countOrdersAfter(@Param("start") LocalDateTime start);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderTime >= :start AND o.owner.id IN :ownerIds")
    long countOrdersAfterByOwners(@Param("start") LocalDateTime start, @Param("ownerIds") List<Long> ownerIds);

    @Query("SELECT COALESCE(SUM(o.financialInfo.totalAmount), 0) FROM Order o WHERE o.orderTime >= :start")
    BigDecimal sumSalesAfter(@Param("start") LocalDateTime start);

    @Query("SELECT COALESCE(SUM(o.financialInfo.totalAmount), 0) FROM Order o WHERE o.orderTime >= :start AND o.owner.id IN :ownerIds")
    BigDecimal sumSalesAfterByOwners(@Param("start") LocalDateTime start, @Param("ownerIds") List<Long> ownerIds);
}
