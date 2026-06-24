package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.converter.MapStringConverter;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单统计个人配置。按公司和登录用户隔离。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_order_statistics_user_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_statistics_config_company_user",
                        columnNames = {"company_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_order_statistics_config_user", columnList = "user_id")
        })
public class OrderStatisticsUserConfig extends BaseDataRangeEntity {

    @Column(name = "default_target_currency_code", nullable = false, length = 10)
    private String defaultTargetCurrencyCode;

    @Column(name = "time_zone_id", nullable = false, length = 64)
    private String timeZoneId;

    @Builder.Default
    @Convert(converter = MapStringConverter.class)
    @Column(name = "exchange_rates", nullable = false, columnDefinition = "JSON")
    private Map<String, String> exchangeRates = new LinkedHashMap<>();
}
