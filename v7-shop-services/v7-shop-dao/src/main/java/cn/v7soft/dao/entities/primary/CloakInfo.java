package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_cloak_infos")
public class CloakInfo extends BaseTenantEntity {
    /**
     * 包含的国家代码
     * include优先级更高
     */
    @Column(name = "include_country_code", length = 512)
    private String includeCountryCode;
    /**
     * 排除的国家代码
     */
    @Column(name = "exclude_country_code", length = 512)
    private String excludeCountryCode;
    /**
     * 包含的爬虫
     * include的优先级更高
     */
    @Column(name = "include_crawler", length = 512)
    private String includeCrawler;
    /**
     * 排除的爬虫
     */
    @Column(name = "exclude_crawler", length = 512)
    private String excludeCrawler;
    /**
     * 显示的SPU_ID
     */
    @Column(name = "spu_id")
    private Long spuId;
    /**
     * 规则名称
     */
    @Column(name = "name")
    private String name;

    @Column(name = "ordering", columnDefinition = "int default 0")
    private int ordering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}