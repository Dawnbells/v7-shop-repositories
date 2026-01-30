package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 产品库实体类，代表一个产品库。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_product_libraries")
public class ProductLibrary extends BaseDataRangeEntity {
    // 您可以根据需要在此处添加更多字段和注释
}
