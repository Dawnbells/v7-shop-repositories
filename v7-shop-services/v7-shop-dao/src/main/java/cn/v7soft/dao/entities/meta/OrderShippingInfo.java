package cn.v7soft.dao.entities.meta;

import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

@Embeddable
public class OrderShippingInfo {
    private String shippingAddress; // 发货地址
    private LocalDateTime shippingTime; // 发货时间
    private String trackingNumber; // 物流单号

}
