package cn.v7soft.dao.entities.primary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.dao.entities.base.BaseAutoIdDataRangeEntity;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import jakarta.annotation.Nonnull;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_temporary_orders", indexes = {
        @Index(name = "idx_order_time", columnList = "order_time"),
        @Index(name = "idx_platform", columnList = "platform"),
        @Index(name = "idx_origin_order_id", columnList = "origin_order_id"),
        @Index(name = "idx_order_from", columnList = "order_from"),
        @Index(name = "idx_create_time", columnList = "create_time"),
        @Index(name = "idx_status", columnList = "status"),
})
@SQLRestriction("status <> 'DELETED'")
public class TemporaryOrder extends BaseAutoIdDataRangeEntity {
    /**
     * 订单来源
     */
    @Column(name = "order_from", nullable = false)
    private String from;

    /**
     * 订单来源URL
     */
    @Column(name = "from_url", length = 1024)
    private String fromUrl;

    /**
     * 订单平台
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private WebsiteTypeEnum platform;

    /**
     * 原始订单号
     */
    @Column(name = "origin_order_id")
    private String originOrderId;

    /**
     * 下单时间
     */
    @Column(nullable = false, name = "order_time")
    private LocalDateTime orderTime;

    /**
     * 订单收货信息
     */
    @Embedded
    private OrderDeliveryInfo deliveryInfo;
    /**
     * 订单金额相关信息
     */
    @Embedded
    private OrderFinancialInfo financialInfo;

    /**
     * 支付相关信息
     */
    @Embedded
    private OrderPaymentInfo paymentInfo;

    /**
     * 商品信息
     */
    @Nonnull
    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TemporaryOrderItemInfo> itemInfos = new ArrayList<>();

    /**
     * 订单归属信息
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "context_info_id")
    private TemporaryOrderContextInfo contextInfo;

    /**
     * 访问风险记录
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "risk_info_id")
    private TemporaryOrderRiskRecordInfo riskInfo;
}

