package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 商品分类实体类。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_product_categories")
public class ProductCategory extends BaseDataRangeEntity {
    /**
     * 分类名称
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 分类描述
     */
    @Column(name = "description")
    private String description;
}
