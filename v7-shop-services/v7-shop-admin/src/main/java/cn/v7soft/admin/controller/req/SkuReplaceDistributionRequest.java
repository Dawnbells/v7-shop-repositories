package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询源 SKU 在管理范围内实际用到的市场(国家)分布，用于替换弹框的市场多选。
 */
@Getter
@Setter
@Schema(description = "源 SKU 市场分布请求")
public class SkuReplaceDistributionRequest {
    @NotNull(message = "源 SKU 不能为空")
    @Schema(title = "源 SKU ID", example = "1")
    private Long sourceSkuId;
}
