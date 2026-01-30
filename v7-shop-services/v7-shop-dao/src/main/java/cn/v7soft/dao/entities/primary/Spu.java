package cn.v7soft.dao.entities.primary;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * SPU实体类
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_spus", indexes = {
        @Index(name = "idx_code", columnList = "code"),
        @Index(name = "idx_name", columnList = "name"),
        @Index(name = "idx_product_category_id", columnList = "product_category_id"),
        @Index(name = "idx_company_library_id", columnList = "company_library_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_create_time", columnList = "create_time"),
})
@SQLRestriction("status <> 'DELETED'")
public class Spu extends BaseDataRangeEntity {

    /**
     * SPU代码
     */
    @Column(nullable = false)
    private Integer code;

    /**
     * SPU名称
     */
    @Column(nullable = false)
    private String name;

    /**
     * SPU描述
     */
    @Column(nullable = false)
    private String description;

    /**
     * 使用统一标准汇率转换
     */
    @Column(name = "use_standard_exchange_rate")
    private Boolean useStandardExchangeRate;

    /**
     * 是否开放为部门产品库
     */
    @Column(name = "is_open")
    private Boolean isOpen;

    /**
     * 汇率转换，级联删除，SPU删除后级联删除
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "spu")
    private List<CurrencyExchangeRate> exchangeRates;

    /**
     * 产品分类
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_category_id")
    private ProductCategory productCategory;

    /**
     * 产品列表，支持不同语言，SPU删除后级联删除
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "spu")
    private List<Product> productList;

    /**
     * 归属某个公司的产品库
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_library_id")
    private Company companyLibrary;

    /**
     * 归属网站
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_website_spus",
            joinColumns = @JoinColumn(name = "spu_id"),
            inverseJoinColumns = @JoinColumn(name = "website_id"))
    private List<Website> websiteList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_from_id")
    private Spu sharedFrom;

    /**
     * 像素列表
     */
    @Builder.Default
    @ManyToMany(mappedBy = "spuList", fetch = FetchType.LAZY)
    private List<PixelAccount> pixelList = new ArrayList<>();

    /**
     * 绑定的子域名列表
     */
    @Builder.Default
    @ManyToMany(mappedBy = "spuList", fetch = FetchType.LAZY)
    private List<SubDomain> subDomainList = new ArrayList<>();
//    /**
//     * 归属产品库
//     */
//    @ManyToMany(fetch = FetchType.LAZY)
//    @JoinTable(name = "product_libraries",
//            joinColumns = @JoinColumn(name = "product_id"),
//            inverseJoinColumns = @JoinColumn(name = "product_library_id"))
//    private List<ProductLibrary> productLibraries;
}
