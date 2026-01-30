package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.ProductSKU;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@SuperBuilder
@Schema(description = "商品 SKU 信息响应")
public class ProductSKUResponse extends DataRangeResponse {
    @Schema(title = "SKU 编码", example = "SKU12345")
    private String skuCode;

    @Schema(title = "品名", example = "商品名称")
    private String name;

    @Schema(title = "总销售数量", example = "100")
    private long totalUnitsSold;

    @Schema(title = "总销售额", example = "5000.00")
    private BigDecimal totalSalesRevenue;

    @Schema(title = "是否虚拟 SKU", example = "true")
    private boolean isVirtual;

    public static ProductSKUResponse convertEntity(ProductSKU entity) {
        if (entity == null) {
            return null;
        }
        return filling(entity, ProductSKUResponse.builder()
                .skuCode(entity.getSkuCode())
                .name(entity.getName())
                .totalUnitsSold(entity.getTotalUnitsSold())
                .totalSalesRevenue(entity.getTotalSalesRevenue())
                .isVirtual(entity.isVirtual())
                .build());
    }
}
