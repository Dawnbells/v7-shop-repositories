package cn.v7soft.dao.entities.primary;

import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.dao.converter.ListStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


/**
 * 货品
 */
@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_merchandise_group")
public class MerchandiseGroup extends BaseEntity {
    @Column(name = "merchandises", columnDefinition = "json")
    @Convert(converter = ListStringConverter.class)
    private String merchandises;
}
