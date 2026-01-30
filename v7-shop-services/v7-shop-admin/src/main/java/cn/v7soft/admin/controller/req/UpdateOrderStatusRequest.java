package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdsRequest;
import cn.v7soft.dao.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest extends IdsRequest {
    private OrderStatus status;
    private String remark;
}
