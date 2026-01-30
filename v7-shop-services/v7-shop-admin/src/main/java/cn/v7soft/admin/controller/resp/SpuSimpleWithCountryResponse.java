package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.Spu;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SPU简单响应（包含是否支持当前国家）
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SPU简单信息响应（含国家支持）")
public class SpuSimpleWithCountryResponse {

    @Schema(title = "SPU ID")
    private String id;

    @Schema(title = "SPU名称")
    private String name;

    @Schema(title = "SPU代码")
    private String code;

    @Schema(title = "是否支持当前国家")
    private Boolean supportCurrentCountry;

    public static SpuSimpleWithCountryResponse convertEntity(Spu spu, Boolean supportCurrentCountry) {
        if (spu == null) {
            return null;
        }
        return SpuSimpleWithCountryResponse.builder()
                .id(String.valueOf(spu.getId()))
                .name(spu.getName())
                .code(String.valueOf(spu.getCode()))
                .supportCurrentCountry(supportCurrentCountry)
                .build();
    }
}

