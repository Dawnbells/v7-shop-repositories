package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 受保护客户数据（Shopify 订单收货人信息）访问日志，按租户隔离。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_protected_data_access_logs", indexes = {
        @Index(name = "idx_protected_access_time", columnList = "access_time")
})
public class ProtectedDataAccessLog extends BaseTenantEntity {

    /**
     * 操作人 ID
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 操作人名字
     */
    @Column(name = "user_name", length = 128)
    private String userName;

    /**
     * LIST / EXPORT
     */
    @Column(name = "action", length = 16)
    private String action;

    /**
     * TEMPORARY / ORDER
     */
    @Column(name = "order_type", length = 16)
    private String orderType;

    /**
     * 本次返回/导出的订单主键，逗号分隔，最多 500 个
     */
    @Column(name = "order_ids", columnDefinition = "TEXT")
    private String orderIds;

    @Column(name = "order_count")
    private Integer orderCount;

    @Column(name = "access_time")
    private LocalDateTime accessTime;
}
