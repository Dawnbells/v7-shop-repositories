package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规格实体类，代表商品的不同规格。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_product_specifications", indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_sku_id", columnList = "sku_id"),
        @Index(name = "idx_status", columnList = "status"),
})
public class ProductSpecification extends BaseDataRangeEntity {
    /**
     * 规格图片
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specification_image_id")
    private MultimediaFile specificationImage;

    /**
     * 规格属性列表
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "productSpecification")
    private List<ProductSpecificationAttributes> attributes;

    @Column(name = "sid")
    private Long sid;

    /**
     * 商品售价
     */
    @Column(name = "sell_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal sellPrice;

    /**
     * 原价
     */
    @Column(name = "origin_price", precision = 19, scale = 8)
    private BigDecimal originPrice;

    /**
     * 成本价
     */
    @Column(name = "cost_price", precision = 19, scale = 8)
    private BigDecimal costPrice;

    /**
     * 条码
     */
    @Column(name = "barcode", length = 50)
    private String barcode;

    /**
     * 库存
     */
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    /**
     * 是否关联库存
     * false: 缺货后允许继续销售
     * true: 缺货后不允许继续销售
     */
    @Column(name = "link_stock", nullable = false)
    private boolean linkStock;

    /**
     * SKU
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private ProductSKU sku;

    /**
     * 归属产品
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
