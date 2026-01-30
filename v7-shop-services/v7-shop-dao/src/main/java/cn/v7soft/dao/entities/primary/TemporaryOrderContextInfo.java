package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseAutoIdDataRangeEntity;
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

import java.math.BigDecimal;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_temporary_order_context_infos", indexes = {
        @Index(name = "idx_sales_uid", columnList = "sales_uid"),
        @Index(name = "idx_department_id", columnList = "department_id"),
        @Index(name = "idx_website_id", columnList = "website_id"),
        @Index(name = "idx_country_id", columnList = "country_id"),
})
public class TemporaryOrderContextInfo extends BaseAutoIdDataRangeEntity {

    /**
     * 销售UID
     */
    @Column(name = "sales_uid")
    private Long salesUid;

    /**
     * 销售名字
     */
    @Column(name = "sales_person", length = 100)
    private String salesPerson;

    /**
     * 销售部门ID
     */
    @Column(name = "department_id")
    private Long departmentId;

    /**
     * 销售部门
     */
    @Column(name = "department", length = 100)
    private String department;

    /**
     * 网站ID
     */
    @Column(name = "website_id")
    private Long websiteId;


    /**
     * 网站名称
     */
    @Column(name = "website_name", length = 100)
    private String websiteName;

    /**
     * 网站域名
     */
    @Column(name="website_url", length = 256)
    private String websiteUrl;

    /**
     * 语言ID
     */
    @Column(name = "language_id")
    private String languageId;

    /**
     * 语言名称
     */
    @Column(name = "language", length = 50)
    private String language;

    /**
     * 语言代码
     */
    @Column(name = "language_code", length = 10)
    private String languageCode;

    /**
     * 货币ID
     */
    @Column(name = "currency_id")
    private Long currencyId;

    /**
     * 货币名称
     */
    @Column(name = "currency_name", length = 50)
    private String currencyName;

    /**
     * 货币符号
     */
    @Column(name = "currency_symbol", length = 10)
    private String currencySymbol;

    /**
     * 货币代码（ISO-4217）
     */
    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    /**
     * 美元兑换汇率
     */
    @Column(name = "currency_exchange_rate", precision = 19, scale = 4)
    private BigDecimal currencyExchangeRate;

    /**
     * 有效小数位
     */
    @Column(name = "currency_fraction_digits")
    private int currencyFractionDigits;

    /**
     * 国家ID
     */
    @Column(name = "country_id")
    private Long countryId;

    /**
     * 国家名称
     */
    @Column(name = "country", length = 100)
    private String country;

    /**
     * 国家代码
     */
    @Column(name = "country_code", length = 10)
    private String countryCode;

    /**
     * 电话号码正则
     */
    @Column(name = "phone_rule", length = 32)
    private String phoneRule;
    /**
     * 电话号码前缀
     */
    @Column(name = "phone_prefix", length = 32)
    private String phonePrefix;
    /**
     * 地址规则
     */
    @Column(name = "address_rule", length = 32)
    private String addressRule;
}
