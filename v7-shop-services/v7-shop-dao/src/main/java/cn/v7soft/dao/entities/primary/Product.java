package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.TaxationMethod;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品实体类，代表一个商品。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_products", indexes = {
        @Index(name = "idx_sku_id", columnList = "sku_id"),
        @Index(name = "idx_spu_id", columnList = "spu_id"),
        @Index(name = "idx_country_id", columnList = "country_id"),
        @Index(name = "idx_language_id", columnList = "language_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_create_time", columnList = "create_time"),
})
public class Product extends BaseDataRangeEntity {

    /**
     * 商品标题
     */
    @Column(name = "title", nullable = false, length = 512)
    private String title;

    /**
     * 商品摘要
     */
    @Column(name = "summary", length = 256)
    private String summary;

    /**
     * 商品描述，HTML源码
     */
    @Column(name = "introduction", columnDefinition = "longtext")
    private String introduction;
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
     * 是否收取税费
     */
    @Column(name = "is_taxable", nullable = false)
    private boolean isTaxable;

    /**
     * 税费收取方式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "taxation_method")
    private TaxationMethod taxationMethod;

    /**
     * 如果按固定金额收取
     */
    @Column(name = "fixed_tax_amount", precision = 19, scale = 8)
    private BigDecimal fixedTaxAmount;

    /**
     * 按金额收取税费的条件金额
     */
    @Column(name = "tax_amount_threshold")
    private BigDecimal taxAmountThreshold;

    /**
     * 按购买量收取税费的条件数量
     */
    @Column(name = "tax_quantity_threshold")
    private int taxQuantityThreshold;

    /**
     * 按金额收取的税费或按购买量收取的税费
     */
    @Column(name = "tax_per_base", precision = 19, scale = 8)
    private BigDecimal taxPerBase;

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
     * 是否是多规格
     */
    @Column(name = "is_multi_specs", nullable = false)
    private boolean isMultiSpecs;

    /**
     * 多规格，级联删除
     */
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "product")
    private List<ProductSpecification> specificationList = new ArrayList<>();

    /**
     * 产品SKU
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private ProductSKU sku;

    /**
     * 视频链接
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_file_id")
    private MultimediaFile videoFile;

    /**
     * 主图照片
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_product_images",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "image_file_id"))
    private List<MultimediaFile> imageFiles;

    /**
     * 对应的国家，所属国家，多对一
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    /**
     * 对应的语言，所属语言，多对一
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id")
    private Language language;

    /**
     * 所属SPU，多对一，一个SPU对应多个Product
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spu_id")
    private Spu spu;

    /**
     * 爬虫显示spu，多对一，一个SPU对应多个Product
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_show_spu_id")
    private Spu botShowSpu;
    /**
     * 风险用户显示SPU，多对一，一个SPU对应多个Product
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_user_show_spu_id")
    private Spu riskUserShowSpu;
    /**
     * 黑名单用户显示SPU，多对一，一个SPU对应多个Product
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_listed_user_show_spu_id")
    private Spu blacklistedUserShowSpu;

    /**
     * 备用的SKU列表
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_product_alternative_skus",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "alternative_sku_id"))
    private List<ProductSKU> alternativeSkus = new ArrayList<>();

    /**
     * AB页配置
     */
    @Setter(AccessLevel.NONE)
    @Builder.Default
    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL, // Cascade all operations
            orphanRemoval = true, // Delete orphans automatically
            fetch = FetchType.LAZY
    )
    private List<CloakInfo> cloakInfos = new ArrayList<>();
}
