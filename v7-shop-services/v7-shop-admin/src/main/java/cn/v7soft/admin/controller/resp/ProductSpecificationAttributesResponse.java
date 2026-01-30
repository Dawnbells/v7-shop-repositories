package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用于返回产品规格属性信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "产品规格属性信息响应")
public class ProductSpecificationAttributesResponse extends DataRangeResponse {

    @Schema(title = "属性名称", example = "颜色")
    private String name;

    @Schema(title = "属性值", example = "红色")
    private String value;

    @Schema(title = "图片", example = "图片")
    private MultimediaFileResponse image;
    /**
     * 从 `ProductSpecificationAttributes` 实体转换为 `ProductSpecificationAttributesResponse` 的静态方法。
     */
    public static ProductSpecificationAttributesResponse convertEntity(ProductSpecificationAttributes attributes) {
        return filling(attributes, ProductSpecificationAttributesResponse.builder()
                .name(attributes.getName())
                .value(attributes.getValue())
                .image(MultimediaFileResponse.convertEntity(attributes.getMultimediaFile()))
                .build());
    }

    /**
     * 将 `ProductSpecificationAttributes` 实体列表转换为 `ProductSpecificationAttributesResponse` 列表的静态方法。
     */
    public static List<ProductSpecificationAttributesResponse> convertList(List<ProductSpecificationAttributes> attributesList) {
        return attributesList.stream()
                .map(ProductSpecificationAttributesResponse::convertEntity)
                .collect(Collectors.toList());
    }
}
