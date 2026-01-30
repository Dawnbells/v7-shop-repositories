package cn.v7soft.dao.entities.address;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 地址实体类，代表一个地址信息。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_addresses")
public class Address extends BaseEntity {

    /**
     * 省份
     */
    @Column(name = "province", nullable = false, length = 100)
    private String province;

    /**
     * 城市
     */
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /**
     * 区域
     */
    @Column(name = "district", nullable = false, length = 100)
    private String district;

    /**
     * 邮政编码
     */
    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;
}
