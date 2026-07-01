package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 源 SKU 市场分布响应：某市场(国家)下受影响的商品数。
 */
@Getter
@Setter
@Builder
@Schema(description = "源 SKU 市场分布响应")
public class SkuReplaceDistributionResponse {
    @Schema(title = "市场(国家) ID", example = "1")
    private Long countryId;

    @Schema(title = "市场(国家)名称", example = "美国")
    private String countryName;

    @Schema(title = "该市场下受影响的商品数", example = "12")
    private long productCount;
}
