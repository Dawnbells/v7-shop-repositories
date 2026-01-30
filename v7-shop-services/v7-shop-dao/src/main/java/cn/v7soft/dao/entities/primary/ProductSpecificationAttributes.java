package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 规格属性
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_product_specification_attributes")
public class ProductSpecificationAttributes extends BaseDataRangeEntity {
    /**
     * 规格名称，例如 "颜色" 或 "尺寸"
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 规格值，例如 "红色" 或 "L"
     */
    @Column(name = "value", nullable = false, length = 1024)
    private String value;

    /**
     * 图片地址
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "multimedia_file_id")
    private MultimediaFile multimediaFile;
    /**
     * 归属规格
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_specification_id")
    private ProductSpecification productSpecification;
}
