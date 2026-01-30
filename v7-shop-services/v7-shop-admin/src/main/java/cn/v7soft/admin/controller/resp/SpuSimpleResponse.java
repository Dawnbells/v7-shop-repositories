package cn.v7soft.admin.controller.resp;


import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Spu;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * SPU响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "SPU信息响应")
public class SpuSimpleResponse extends IdResponse {
    @Schema(title = "SPU代码", example = "SPU12345")
    private String code;

    @Schema(title = "商品名称", example = "产品名称")
    private String name;

    @Schema(title = "商品描述", example = "商品描述")
    private String description;

    @Schema(title = "是否是统一汇率", example = "true")
    private Boolean useStandardExchangeRate;

    @Schema(title = "产品分类", example = "产品分类信息")
    private ProductCategoryResponse productCategory;

    public static SpuSimpleResponse convertEntity(Spu spu) {
        if (spu == null) {
            return null;
        }
        return filling(spu, SpuSimpleResponse.builder()
                .code(String.valueOf(spu.getCode()))
                .name(spu.getName())
                .description(spu.getDescription())
                .productCategory(ProductCategoryResponse.convertEntity(spu.getProductCategory()))
                .build());
    }
}