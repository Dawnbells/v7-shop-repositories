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
    // 添加自定义查询和过滤条件

    @Builder.Default
    @Schema(title = "是否是虚拟 SKU", example = "true")
    private boolean isVirtual = false;
}
