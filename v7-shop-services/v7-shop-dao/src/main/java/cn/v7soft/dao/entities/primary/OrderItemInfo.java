package cn.v7soft.dao.entities.primary;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.entities.base.BaseTenantEntity;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 订单商品信息
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_order_items", indexes = {
        @Index(name = "idx_sku_id", columnList = "sku_id"),
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_sku_code", columnList = "sku_code"),
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_spu_id", columnList = "spu_id"),
})
public class OrderItemInfo extends BaseTenantEntity {
    /**
     * SPU ID
     */
    @Column(name = "spu_id", nullable = false)
    private Long spuId;
    /**
     * 产品ID
      */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * 商品标题
     */
    @Column(name = "title", nullable = false, length = 512)
    private String title;
    /**
     * 商品标题
     */
    @Column(name = "spec_title", nullable = false, length = 512)
    private String specTitle;
    /**
     * 主图
     */
    @ManyToOne(fetch = FetchType.EAGER)
    private MultimediaFile image;

    /**
     * 商品售价
     */
    @Column(name = "sell_price", nullable = false)
    private BigDecimal sellPrice;

    /**
     * 原价
     */
    @Column(name = "origin_price")
    private BigDecimal originPrice;

    /**
     * 成本价
     */
    @Column(name = "cost_price")
    private BigDecimal costPrice;

    /**
     * 税费
     */
    @Column(name = "tax")
    private BigDecimal tax;

    /**
     * 条码
     */
    @Column(name = "barcode", length = 50)
    private String barcode;

    /**
     * 购买数量
     */
    @Column(name = "quantity", nullable = false)
    private Long quantity;

    /**
     * SKU ID
     */
    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /**
     * SKU 编码
     */
    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    /**
     * 品名
     */
    @Column(name = "sku_name", nullable = false)
    private String skuName;

    /**
     * 是否是虚拟 SKU
     */
    @Column(name = "is_virtual", nullable = false)
    private boolean skuIsVirtual;

    /**
     * 中文品名
     */
    @Column(name = "merchandise", nullable = false, length = 512)
    private String merchandise;
    /**
     * 面单品名
     */
    @Column(name = "waybill_product_name")
    private String waybillProductName;

    /**
     * 订单
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
}
