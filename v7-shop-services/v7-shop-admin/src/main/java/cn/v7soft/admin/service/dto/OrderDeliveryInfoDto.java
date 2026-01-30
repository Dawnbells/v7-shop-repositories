package cn.v7soft.admin.service.dto;

import java.util.regex.Pattern;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderDeliveryInfoDto {

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
     * 是否偏远地区
     */
    @Builder.Default
    private boolean remoteArea = false;

    /**
     * 备注
     */
    private String remark;

    public static OrderDeliveryInfoDto convert(OrderDeliveryInfo deliveryInfo, String phonePrefix) {
        if (deliveryInfo == null) {
            return null;
        }
        String phone = normalizePhone(deliveryInfo.getPhone(), phonePrefix);
        return OrderDeliveryInfoDto.builder()
                .email(deliveryInfo.getEmail())
                .receiveUpdates(deliveryInfo.getReceiveUpdates())
                .firstName(deliveryInfo.getFirstName())
                .lastName(deliveryInfo.getLastName())
                .phone(phone)
                .province(deliveryInfo.getProvince())
                .city(deliveryInfo.getCity())
                .district(deliveryInfo.getDistrict())
                .postalCode(deliveryInfo.getPostalCode())
                .address(deliveryInfo.getAddress())
                .remoteArea(deliveryInfo.isRemoteArea())
                .remark(deliveryInfo.getRemark())
                .build();
    }

    public OrderDeliveryInfo toDeliveryInfo() {
        String phoneLast8 = this.phone;
        if (StrUtil.isBlank(phoneLast8)) {
            phoneLast8 = "";
        } else {
            phoneLast8 = phoneLast8.length() > 8 ? phoneLast8.substring(phoneLast8.length() - 8) : phoneLast8;
        }
        OrderDeliveryInfo deliveryInfo = new OrderDeliveryInfo();
        deliveryInfo.setEmail(this.email);
        deliveryInfo.setReceiveUpdates(this.receiveUpdates);
        deliveryInfo.setFirstName(this.firstName);
        deliveryInfo.setLastName(this.lastName);
        deliveryInfo.setPhone(this.phone);
        deliveryInfo.setProvince(this.province);
        deliveryInfo.setCity(this.city);
        deliveryInfo.setDistrict(this.district);
        deliveryInfo.setPostalCode(this.postalCode);
        deliveryInfo.setAddress(this.address);
        deliveryInfo.setRemoteArea(this.remoteArea);
        deliveryInfo.setRemark(this.remark);
        deliveryInfo.setPhoneLast8(phoneLast8);
        return deliveryInfo;
    }


    public static String normalizePhone(String phone, String phonePrefix) {
        // 1. 判空
        if (StrUtil.isBlank(phone)) {
            return "";
        }

        // 2. 只保留数字
        phone = phone.replaceAll("\\D+", "");

        // 3. 去掉渠道前缀（如 +86 / 86）
        if (StrUtil.isNotBlank(phonePrefix)) {
            String prefixDigits = phonePrefix.replaceAll("\\D+", "");
            if (StrUtil.isNotBlank(prefixDigits) && phone.startsWith(prefixDigits)) {
                phone = phone.substring(prefixDigits.length());
            }
        }

        // 4. 去掉前导 0
        phone = phone.replaceFirst("^0+", "");

        return phone;
    }

}
