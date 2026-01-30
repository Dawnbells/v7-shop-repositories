package cn.v7soft.admin.controller.resp;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.ReUtil;
import cn.v7soft.common.utils.RegexPattern;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "配送信息")
public class OrderDeliveryInfoResponse {

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 是否接收邮件
     */
    private Boolean receiveUpdates;

    /**
     * 姓
     */
    private String firstName;

    /**
     * 名
     */
    private String lastName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 省
     */
    private String province;

    /**
     * 市
     */
    private String city;

    /**
     * 区
     */
    private String district;

    /**
     * 邮编
     */
    private String postalCode;

    /**
     * 地址
     */
    private String address;

    /**
     * 备注
     */
    private String remark;

    public static OrderDeliveryInfoResponse convert(OrderDeliveryInfo orderDeliveryInfo, boolean desensitized) {
        String email = orderDeliveryInfo.getEmail();
        if (desensitized) {
            if (ReUtil.isMatch(RegexPattern.REGEX_EMAIL, email)) {
                email = DesensitizedUtil.email(email);
            } else {
                email = DesensitizedUtil.idCardNum(email, 1, 1);
            }
        }
        return OrderDeliveryInfoResponse.builder()
                .email(email)
                .receiveUpdates(orderDeliveryInfo.getReceiveUpdates())
                .firstName(desensitized ? DesensitizedUtil.chineseName(orderDeliveryInfo.getFirstName()) : orderDeliveryInfo.getFirstName())
                .lastName(desensitized ? DesensitizedUtil.chineseName(orderDeliveryInfo.getLastName()) : orderDeliveryInfo.getLastName())
                .phone(desensitized ? DesensitizedUtil.idCardNum(orderDeliveryInfo.getPhone(), 1, 1) : orderDeliveryInfo.getPhone())
                .province(orderDeliveryInfo.getProvince())
                .city(orderDeliveryInfo.getCity())
                .district(orderDeliveryInfo.getDistrict())
                .postalCode(orderDeliveryInfo.getPostalCode())
                .address(orderDeliveryInfo.getAddress())
                .remark(orderDeliveryInfo.getRemark())
                .build();
    }
}
