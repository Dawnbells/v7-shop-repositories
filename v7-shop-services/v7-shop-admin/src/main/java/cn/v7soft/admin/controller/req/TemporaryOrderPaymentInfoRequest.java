package cn.v7soft.admin.controller.req;

import java.time.LocalDateTime;

import cn.v7soft.dao.enums.PaymentMethod;
import cn.v7soft.dao.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "支付信息请求实体类")
public class TemporaryOrderPaymentInfoRequest {

    @Schema(description = "支付方式")
    private PaymentMethod paymentMethod;

    @Schema(description = "支付状态")
    private PaymentStatus paymentStatus;
    @Schema(description = "支付时间")
    private LocalDateTime paymentTime;
}
