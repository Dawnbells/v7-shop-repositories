package cn.v7soft.dao.entities.primary;


import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * 汇率实体类，代表一种货币与标准货币的兑换汇率。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_currency_exchange_rates")
public class CurrencyExchangeRate extends BaseDataRangeEntity {
    /**
     * 标准货币（通常是美元，可设置）兑换汇率，1美元兑换的货币数量。
     */
    @Column(name = "exchange_rate", precision = 19, scale = 4, nullable = false)
    private BigDecimal exchangeRate;

    /**
     * 与标准货币兑换的货币。
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", referencedColumnName = "id")
    private Currency currency;

    /**
     * 所属SPU，多对一，一个SPU对应多个Product。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spu_id", referencedColumnName = "id")
    private Spu spu;
}
