package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 查询源 SKU 交集候选：在每个选中 SPU 下（管理范围内，主/规格/备用任一引用）都出现的 SKU。
 */
@Getter
@Setter
@Schema(description = "源 SKU 交集候选请求")
public class SkuReplaceSourceQueryRequest {
    @NotEmpty(message = "SPU 不能为空")
    @Schema(title = "选中的 SPU ID 列表", example = "[10, 11]")
    private List<Long> spuIds;
}
