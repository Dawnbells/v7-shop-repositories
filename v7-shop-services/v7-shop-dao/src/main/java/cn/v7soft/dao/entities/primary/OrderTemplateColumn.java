package cn.v7soft.dao.entities.primary;

import jakarta.persistence.*;
import lombok.*;

/**
 * 订单模版的表头配置
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "t_order_template_column")
public class OrderTemplateColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 对应的模版
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_template_id", nullable = false)
    private OrderTemplate orderTemplate;

    /**
     * 表头显示名称
     */
    @Column(name = "header_name", nullable = false)
    private String headerName;

    /**
     * 对应数据字段的key
     */
    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    /**
     * 排序顺序
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
