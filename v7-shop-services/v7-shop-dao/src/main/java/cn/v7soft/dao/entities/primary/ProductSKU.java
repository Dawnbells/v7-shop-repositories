package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * 商品 SKU 实体类。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_product_skus", indexes = {
        @Index(name = "idx_sku_code", columnList = "sku_code"),
        @Index(name = "idx_name", columnList = "name"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_create_time", columnList = "create_time"),
})
public class ProductSKU extends BaseDataRangeEntity {
    /**
     * SKU 编码
     */
    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    /**
     * 品名
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 总销售数量
     */
    @Column(name = "total_units_sold")
    private long totalUnitsSold;

    /**
     * 总销售额
     */
    @Column(name = "total_sales_revenue", precision = 19, scale = 8)
    private BigDecimal totalSalesRevenue;

    /**
     * 是否是虚拟 SKU
     */
    @Column(name = "is_virtual", nullable = false)
    private boolean isVirtual;
}
