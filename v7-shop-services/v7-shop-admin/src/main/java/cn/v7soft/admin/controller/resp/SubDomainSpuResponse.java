package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.Spu;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 子域名绑定的SPU响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "子域名绑定的SPU信息响应")
public class SubDomainSpuResponse extends SpuSimpleResponse {

    @Schema(title = "是否支持当前子域名绑定的国家", example = "true")
    private Boolean supportCurrentCountry;

    public static SubDomainSpuResponse convertEntity(Spu spu) {
        if (spu == null) {
            return null;
        }
        return filling(spu, SubDomainSpuResponse.builder()
                .code(String.valueOf(spu.getCode()))
                .name(spu.getName())
                .description(spu.getDescription())
                .productCategory(ProductCategoryResponse.convertEntity(spu.getProductCategory()))
                .build());
    }
}

