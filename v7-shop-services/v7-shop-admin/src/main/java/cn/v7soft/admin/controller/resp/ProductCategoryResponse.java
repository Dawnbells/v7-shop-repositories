package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "商品分类信息响应")
public class ProductCategoryResponse extends DataRangeResponse {
    @Schema(title = "分类名称", example = "Electronics")
    private String name;

    @Schema(title = "分类描述", example = "电子产品")
    private String description;

    public static ProductCategoryResponse convertEntity(ProductCategory entity) {
        if(entity == null) {
            return null;
        }
        return filling(entity, ProductCategoryResponse.builder()
                .name(entity.getName())
                .description(entity.getDescription())
                .build());
    }
}
