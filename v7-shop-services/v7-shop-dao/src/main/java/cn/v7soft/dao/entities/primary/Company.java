package cn.v7soft.dao.entities.primary;

import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.dao.enums.IndustryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalTime;

/**
 * 表示系统中的一家公司。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_companies", indexes = {
        @Index(name = "idx_id", columnList = "id"),
        @Index(name = "idx_domain", columnList = "domain", unique = true),
        @Index(name = "idx_status", columnList = "status")
})
public class Company extends BaseEntity {
    /**
     * 公司名称。
     */
    @Column(name = "name", nullable = false)
    private String name;
    /**
     * 公司昵称
     */
    @Column(name = "nick", nullable = false)
    private String nick;
    /**
     * 公司Logo
     */
    @Column(name = "logo", nullable = false)
    private String logo;
    /**
     * 联系人
     */
    @Column(name = "contacts")
    private String contacts;
    /**
     * 联系电话
     */
    @Column(name = "contacts_phone")
    private String contactsPhone;
    /**
     * 公司所在的行业领域。
     */
    @Column(name = "industry")
    @Enumerated(EnumType.STRING)
    private IndustryType industry;

    /**
     * 公司所在国家。
     */
    @Column(name = "country")
    private String country;
    /**
     * 公司所在省份
     */
    @Column(name = "province")
    private String province;

    /**
     * 公司所在城市。
     */
    @Column(name = "city")
    private String city;

    /**
     * 公司地址。
     */
    @Column(name = "address")
    private String address;

    /**
     * 公司位置的邮政编码。
     */
    @Column(name = "postal_code")
    private String postalCode;

    /**
     * 公司的联系电话。
     */
    @Column(name = "phone")
    private String phone;

    /**
     * 公司的电子邮件地址。
     */
    @Column(name = "email")
    private String email;

    /**
     * 公司网站的一级域名, 全局唯一
     * 总管理后台：admin.$domain
     * 子商城后台：adminxx.$domain
     * 前端预览：shopxx.$domain
     */
    @Column(name = "domain", unique = true)
    private String domain;

    /**
     * 每日审单起始时间
     */
    @Builder.Default
    @Column(name = "daily_order_check_start_time")
    private LocalTime dailyOrderCheckStartTime = LocalTime.of(9, 0);

    /**
     * 每个商品在途中的最大订单数
     */
    @Column(name = "max_in_transit_orders")
    private int maxInTransitOrders;

    /**
     * 图片基础地址
     */
    @Column(name = "image_base_url")
    private String imageBaseUrl;

    @Column(name = "access_key")
    private String accessKey;
}
