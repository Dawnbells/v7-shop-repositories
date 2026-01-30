package cn.v7soft.admin.service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ThirdPartyOrderDTO {
    private Long orderId;
    private String orderSn;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {
        private Long itemId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}
