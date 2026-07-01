package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
}
