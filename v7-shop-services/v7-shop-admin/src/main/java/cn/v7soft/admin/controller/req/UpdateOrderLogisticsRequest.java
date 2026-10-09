package cn.v7soft.admin.controller.req;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量修改订单渠道/仓库：值留空表示不修改，clear 为 true 表示清空（优先于值）。
 */
@Getter
@Setter
public class UpdateOrderLogisticsRequest {
    @NotEmpty(message = "请选择订单")
    private List<@NotNull @Positive Long> ids;

    @Size(max = 255, message = "渠道不能超过255个字符")
    private String deliveryChannel;

    @Size(max = 255, message = "仓库不能超过255个字符")
    private String storehouse;

    private boolean clearDeliveryChannel;

    private boolean clearStorehouse;
}
