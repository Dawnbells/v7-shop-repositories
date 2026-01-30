package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "财务信息请求实体类")
public class TemporaryOrderFinancialInfoRequest {

    @Schema(description = "总价")
    private BigDecimal totalAmount;

    @Schema(description = "运费")
    private BigDecimal shippingFee;

    @Schema(description = "折扣金额")
    private BigDecimal discountAmount;

    @Schema(description = "税费")
    private BigDecimal taxAmount;
}
