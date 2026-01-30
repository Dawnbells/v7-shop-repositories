package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * 货币实体类，代表系统中的货币。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_currencies", indexes = {
        @Index(name = "idx_currency_name", columnList = "name"),
        @Index(name = "idx_currency_code", columnList = "code"),
        @Index(name = "idx_status", columnList = "status"),
})
public class Currency extends BaseTenantEntity {
    /**
     * 货币名称
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 货币符号
     */
    @Column(name = "symbol", length = 10)
    private String symbol;

    /**
     * 货币代码（ISO-4217）
     */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    /**
     * 美元兑换汇率，1美元兑换 #exchangeRate 货币
     */
    @Column(name = "exchange_rate", precision = 19, scale = 4)
    private BigDecimal exchangeRate;

    /**
     * 有效小数位
     */
    @Column(name = "fraction_digits")
    private int fractionDigits;
}
