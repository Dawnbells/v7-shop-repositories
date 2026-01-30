package cn.v7soft.dao.enums;

import cn.v7soft.core.enums.ClientResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatus {
    PENDING("待审核"),
    CONFIRMED("已确认"),
    SHIPPED("已发货"),
    DELIVERED("已签收"),
    REJECTED("拒收"),
    LOST("丢件"),
    CUSTOMER_CANCELLED("客户取消"),
    INVALID("无效单");
    private final String name;

    public static OrderStatus fromName(String name) {
        for (OrderStatus status : values()) {
            if (status.name.equals(name)) {
                return status;
            }
        }
        throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("订单状态不合法");
    }
}
