package cn.v7soft.dao.entities.primary;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_ip_detail_info")
public class IpDetailInfo extends BaseEntity {
    @Column(name = "ip")
    private String ip;

    @Column(name = "country")
    private String country;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "latitude")
    private String latitude;

    @Column(name = "longitude")
    private String longitude;
}
