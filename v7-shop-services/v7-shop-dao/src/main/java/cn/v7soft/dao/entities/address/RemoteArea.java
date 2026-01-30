package cn.v7soft.dao.entities.address;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 偏远地区
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Table(name = "t_remote_area")
@SQLRestriction("status <> 'DELETED'")
public class RemoteArea extends BaseEntity {
    /**
     * 邮编
     */
    @Column(name = "country_code")
    private String countryCode;
    /**
     * 邮编
     */
    @Column(name = "postal_code")
    private String postalCode;
    /**
     * 额外提示，比如 物流线，用于替换
     */
    @Column(name = "tip")
    private String tip;
}

