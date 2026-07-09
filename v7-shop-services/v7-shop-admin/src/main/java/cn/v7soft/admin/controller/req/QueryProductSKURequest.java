package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class QueryProductSKURequest extends BasePageRequest {
    @Schema(title = "SKU 编码", example = "SKU12345")
    private String skuCode;

    @Schema(title = "品名", example = "商品名称")
    private String name;

    @Builder.Default
    @Schema(title = "是否是虚拟 SKU", example = "true")
    private boolean isVirtual = false;
}
