package cn.v7soft.dao.entities.meta;

import cn.v7soft.dao.enums.AddressOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryMeta {
    /**
     * 当前市场收货填写的电话规则。
     */
    @Column(name = "phone_rule", length = 128)
    private String phoneRule;

    /**
     * 当前市场收货填写的电话规则。
     */
    @Column(name = "phone_prefix", length = 128)
    private String phonePrefix;

    /**
     * 当前市场收货填写的地址规则。
     */
    @Column(name = "address_rule", length = 128)
    private String addressRule;
    /**
     * 是否使用全名, true-只输入一个名字，false-分开first name和last name
     */
    @Column(name = "use_full_name")
    private Boolean useFullName;
    /**
     * 底部版权信息
     */
    @Column(name = "footer_copyright_info")
    private String footerCopyrightInfo;
    /**
     * 是否必填电话。
     */
    @Builder.Default
    @Column(name = "required_phone", nullable = false)
    private Boolean requiredPhone = false;

    /**
     * 是否必填邮箱。
     */
    @Builder.Default
    @Column(name = "required_email", nullable = false)
    private Boolean requiredEmail = false;
    /**
     * 地址包含字段
     */
    @Builder.Default
    @Column(name = "address_fields")
    private String addressFields = "province,city,district,postal_code";

    /**
     * 订单下载时完整地址的拼接顺序。
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "address_order", length = 16)
    private AddressOrder addressOrder = AddressOrder.REVERSE;
}
