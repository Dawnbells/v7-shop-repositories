package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * 订单下载模版
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_order_template")
public class OrderTemplate extends BaseDataRangeEntity {

    /**
     * 模版名称
     */
    @Column(name = "template_name", nullable = false)
    private String templateName;

    /**
     * 是否是下载模板
     */
    @Column(name = "download_template", nullable = false)
    private Boolean downloadTemplate;

    /**
     * 模版下的表头配置
     */
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "orderTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<OrderTemplateColumn> columns;
}
