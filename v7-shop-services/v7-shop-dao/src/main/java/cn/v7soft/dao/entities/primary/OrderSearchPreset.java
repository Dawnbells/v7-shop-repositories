package cn.v7soft.dao.entities.primary;

import cn.hutool.json.JSONObject;
import cn.v7soft.dao.converter.JSONConverter;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 订单搜索条件预设（登录用户个人私有）
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_order_search_presets", indexes = {
        @Index(name = "idx_order_search_preset_owner_page", columnList = "user_id,page_type"),
        @Index(name = "idx_order_search_preset_company", columnList = "company_id")
})
public class OrderSearchPreset extends BaseDataRangeEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_type", nullable = false, length = 30)
    private OrderSearchPresetPageType pageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_mode", nullable = false, length = 20)
    private OrderSearchPresetTimeMode timeMode;

    @Builder.Default
    @Column(name = "query_params", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JSONConverter.class)
    private JSONObject queryParams = new JSONObject();

    @Column(name = "last_used_time")
    private LocalDateTime lastUsedTime;
}
