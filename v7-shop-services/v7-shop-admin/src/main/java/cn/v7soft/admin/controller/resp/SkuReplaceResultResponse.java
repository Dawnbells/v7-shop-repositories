package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 替换 SKU 结果响应。
 */
@Getter
@Setter
@Builder
@Schema(description = "替换 SKU 结果响应")
public class SkuReplaceResultResponse {
    @Schema(title = "实际受影响的商品数（去重）", example = "12")
    private long affectedProductCount;
}
