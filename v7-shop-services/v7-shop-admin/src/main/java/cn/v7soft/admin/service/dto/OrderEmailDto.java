package cn.v7soft.admin.service.dto;

import java.math.BigDecimal;
import java.util.List;

import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderEmailDto {
    private Long id;
    private String originOrderId;
    private Long companyId;

    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String district;
    private String city;
    private String province;
    private String postalCode;
    private String remark;

    private Long departmentId;
    private String languageCode;
    private String currencyCode;

    private BigDecimal totalAmount;

    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private String specTitle;
        private BigDecimal sellPrice;
        private Long quantity;
    }

    public static OrderEmailDto from(Order order) {
        OrderDeliveryInfo d = order.getDeliveryInfo();
        OrderContextInfo c = order.getContextInfo();
        return OrderEmailDto.builder()
                .id(order.getId())
                .originOrderId(order.getOriginOrderId())
                .companyId(order.getCompanyId())
                .email(d.getEmail())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .phone(d.getPhone())
                .address(d.getAddress())
                .district(d.getDistrict())
                .city(d.getCity())
                .province(d.getProvince())
                .postalCode(d.getPostalCode())
                .remark(d.getRemark())
                .departmentId(c.getDepartmentId())
                .languageCode(c.getLanguageCode())
                .currencyCode(c.getCurrencyCode())
                .totalAmount(order.getFinancialInfo().getTotalAmount())
                .items(order.getItemInfos().stream().map(item ->
                        Item.builder()
                                .specTitle(item.getSpecTitle())
                                .sellPrice(item.getSellPrice())
                                .quantity(item.getQuantity())
                                .build()
                ).toList())
                .build();
    }
}
