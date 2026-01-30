package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import cn.v7soft.dao.entities.meta.CountryMeta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * 国家实体类，代表一个国家。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Table(name = "t_countries")
@SQLRestriction("status <> 'DELETED'")
public class Country extends BaseTenantEntity {
    /**
     * 国家名称
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 国家代码（ISO 3166-1）
     */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    /**
     * 归属大陆
     */
    @Column(name = "continent_code", nullable = false, length = 10)
    private String continentCode;

    /**
     * 使用货币，多对一。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    private Currency currency;

    /**
     * 使用语言，多对多。
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_country_languages",
            joinColumns = @JoinColumn(name = "country_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id"))
    private List<Language> languages;

    /**
     * 国家市场的元数据。
     */
    @Embedded
    private CountryMeta countryMeta;

    /**
     * 国家绑定的前端服务器
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "front_server_id")
    private FrontServer frontServer;
}
