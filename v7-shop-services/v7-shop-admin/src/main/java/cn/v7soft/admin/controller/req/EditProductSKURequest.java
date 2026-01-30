package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditProductSKURequest extends IdRequest {
    @NotBlank(message = "SKU 编码不能为空")
    @Schema(title = "SKU 编码", example = "SKU12345")
    private String skuCode;

    @Schema(title = "品名", example = "商品名称")
    private String name;


    @Schema(title = "是否同步修改到已生成的订单", example = "true")
    private Boolean syncChangeOrder;
}
