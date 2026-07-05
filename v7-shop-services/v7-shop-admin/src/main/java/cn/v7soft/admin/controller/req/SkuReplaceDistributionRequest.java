package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    /**
     * 可选：限定归属 SPU（SPU 管理批量替换入口），与替换共用同一套谓词保证预览=实际。
     * 传了就必须非空，空数组直接 400。
     */
    @Size(min = 1, message = "限定SPU时至少选择一个")
    @Schema(title = "限定归属 SPU ID 列表（可选，传了须非空）", example = "[10, 11]")
    private List<Long> spuIds;
}
