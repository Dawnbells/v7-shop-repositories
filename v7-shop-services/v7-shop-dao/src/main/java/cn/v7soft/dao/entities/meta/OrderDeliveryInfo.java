package cn.v7soft.dao.entities.meta;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 订单收货信息
 */
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
public class OrderDeliveryInfo {

    /**
     * 用户邮箱
     */
    @Column(name = "email", length = 255)
    private String email;

    /**
     * 是否接收邮件
     */
    @Column(name = "receive_updates")
    private Boolean receiveUpdates;

    /**
     * 姓
     */
    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    /**
     * 名
     */
    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    /**
     * 手机号
     */
    @Column(name = "phone", length = 50, nullable = false)
    private String phone;

    /**
     * 手机号的尾8位，用于快速检索
     */
    @Column(name = "phone_last_8", length = 8)
    private String phoneLast8;

    /**
     * 省
     */
    @Column(name = "province", length = 100)
    private String province;

    /**
     * 市
     */
    @Column(name = "city", length = 100)
    private String city;

    /**
     * 区
     */
    @Column(name = "district", length = 100)
    private String district;

    /**
     * 邮编
     */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /**
     * 地址
     */
    @Column(name = "address", length = 255)
    private String address;

    /**
     * 是否偏远地区
     */
    @Builder.Default
    @Column(name = "is_remote_area", columnDefinition = "BOOLEAN DEFAULT false")
    private boolean remoteArea = false;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;
}
