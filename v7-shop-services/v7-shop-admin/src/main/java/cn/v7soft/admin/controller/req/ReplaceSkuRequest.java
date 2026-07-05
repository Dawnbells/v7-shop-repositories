package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 替换 SKU 请求：把选中市场(国家)下全部商品中的源 SKU 替换成目标 SKU。
 */
@Getter
@Setter
@Schema(description = "替换 SKU 请求")
public class ReplaceSkuRequest {
    @NotNull(message = "源 SKU 不能为空")
    @Schema(title = "源 SKU ID（表格所在行）", example = "1")
    private Long sourceSkuId;

    @NotNull(message = "目标 SKU 不能为空")
    @Schema(title = "目标 SKU ID", example = "2")
    private Long targetSkuId;

    @NotEmpty(message = "市场不能为空")
    @Schema(title = "市场(国家) ID 列表，可多选，必选至少一个", example = "[1, 2]")
    private List<Long> countryIds;

    /**
     * 可选：限定归属 SPU（SPU 管理批量替换入口）。不传(null)则不限 SPU（SKU 表格行级入口）；
     * 传了就必须非空，空数组直接 400，避免"看似限定实则不限"的契约歧义。
     * 传入时要求源 SKU 在每个 SPU 下都存在（管理范围内、不限市场），否则整体拒绝。
     */
    @Size(min = 1, message = "限定SPU时至少选择一个")
    @Schema(title = "限定归属 SPU ID 列表（可选，传了须非空）", example = "[10, 11]")
    private List<Long> spuIds;
}
