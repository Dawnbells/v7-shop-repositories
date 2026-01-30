package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.entities.meta.WebsiteMeta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * 商城实体类，代表一个在线商城。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Table(name = "t_websites", indexes = {
        @Index(name = "idx_name", columnList = "name"),
        @Index(name = "idx_country_id", columnList = "country_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_create_time", columnList = "create_time"),
})
@SQLRestriction("status <> 'DELETED'")
public class Website extends BaseDataRangeEntity {
    /**
     * 商城名称
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 商城的目标市场对应的国家，多对一。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    /**
     * 商城默认使用的语言
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_language_id")
    private Language defaultLanguage;

    /**
     * 商城使用的语言，多对多。language == country.language
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_website_languages",
            joinColumns = @JoinColumn(name = "website_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id"))
    private List<Language> languages;

    /**
     * 商城使用的货币，多对一。 currency == country.currency
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    private Currency currency;

    /**
     * 当前商城的主域名，可空, 用于跳转
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_domain")
    private SubDomain mainDomain;

    /**
     * 商城的元数据。
     */
    @Embedded
    private WebsiteMeta websiteMeta;
}
