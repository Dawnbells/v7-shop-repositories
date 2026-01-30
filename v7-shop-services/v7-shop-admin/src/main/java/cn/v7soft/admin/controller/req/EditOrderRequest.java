package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditOrderRequest extends IdRequest {

    @Schema(title = "订单来源", example = "Website", requiredMode = Schema.RequiredMode.REQUIRED)
    private String from;

    @Schema(title = "订单来源URL", example = "https://example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fromUrl;

    @Schema(title = "支付信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private OrderPaymentInfo paymentInfo;

    @Schema(title = "财务信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private OrderFinancialInfo financialInfo;

    @Schema(title = "配送信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private OrderDeliveryInfo deliveryInfo;

    @Schema(title = "上下文信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private OrderContextInfo contextInfo;
}
