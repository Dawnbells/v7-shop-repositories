package cn.v7soft.dao.entities.primary;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.core.utils.V7IdentifierGenerator;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.enums.CheckStatus;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.hibernate.annotations.BatchSize;

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
@Table(name = "t_orders", indexes = {
        @Index(name = "idx_names", columnList = "first_name,last_name"),
        @Index(name = "idx_phone_last8", columnList = "phone_last_8"),
        @Index(name = "idx_createTime", columnList = "create_time"),
        @Index(name = "idx_order_time", columnList = "order_time"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_origin_order_id", columnList = "origin_order_id"),
        @Index(name = "idx_platform", columnList = "platform"),
        @Index(name = "idx_order_status", columnList = "order_status"),
        @Index(name = "idx_bot_order_status", columnList = "bot_order_status"),
        @Index(name = "idx_address", columnList = "address"),
        @Index(name = "idx_contacted", columnList = "is_contacted"),
})
@SQLRestriction("status <> 'DELETED'")
public class Order extends BaseDataRangeEntity {

    static {
        V7IdentifierGenerator.addIgnoreClass(Order.class);
    }

    /**
     * 订单编号(补充)，ID为订单编号，该字段为补充编号
     */
    @Column(name = "order_no_alias")
    private String orderNoAlias;
    /**
     * 订单来源
     */
    @Column(name = "order_from", nullable = false)
    private String from;

    /**
     * 订单来源URL
     */
    @Column(name = "order_from_url", length = 1024)
    private String fromUrl;

    @Column(name = "sku_codes", length = 1024)
    private String skuCodes;

    @Column(name = "sku_names", length = 1024)
    private String skuNames;

    @Column(name = "quantity", columnDefinition = "int default 0")
    private long quantity;

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
     * 产品数量
     */
    @Column(name = "item_count")
    private int itemCount;

    /**
     * 当前订单状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 32, nullable = false)
    private OrderStatus orderStatus;

    /**
     * 审单批注
     */
    @Column(name = "order_remark", length = 256)
    private String orderCheckRemark;

    /**
     * 自动审单状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bot_order_status", length = 32, nullable = false)
    private CheckStatus botOrderStatus;

    /**
     * 导单日期
     */
    @Column(name = "import_time")
    private LocalDateTime importTime;

    /**
     * 支付相关信息
     */
    @Embedded
    private OrderPaymentInfo paymentInfo;

    /**
     * 订单金额相关信息
     */
    @Embedded
    private OrderFinancialInfo financialInfo;

    /**
     * 订单收货信息
     */
    @Embedded
    private OrderDeliveryInfo deliveryInfo;

    /**
     * 商品信息
     */
    @Nonnull
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemInfo> itemInfos;

    /**
     * 订单归属信息
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "context_info_id")
    private OrderContextInfo contextInfo;

    /**
     * 访问风险记录
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "risk_info_id")
    private OrderRiskRecordInfo riskInfo;

    /*
     * 关联的自动审单信息
     * 【自动审单信息】
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_bot_check_info_id")
    private OrderBotCheckInfo botOrderCheckInfo;

    /**
     * 物流信息
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "logistics_info_id")
    private OrderLogisticsInfo logisticsInfo;

    /**
     * 是否已建联（私域与客户建立联系）
     */
    @Builder.Default
    @Column(name = "is_contacted", nullable = false)
    private Boolean contacted = false;

    /**
     * 建联备注
     */
    @Column(name = "contact_remark", length = 256)
    private String contactRemark;
}
