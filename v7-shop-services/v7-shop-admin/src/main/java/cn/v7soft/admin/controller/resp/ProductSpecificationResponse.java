package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;

import com.fasterxml.jackson.annotation.JsonFormat;

import cn.v7soft.dao.entities.primary.ProductSpecification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用于返回产品规格信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "产品规格信息响应")
public class ProductSpecificationResponse extends DataRangeResponse {

    @Schema(title = "规格图片")
    private MultimediaFileResponse skuImage;

    @Schema(title = "规格属性列表")
    private List<ProductSpecificationAttributesResponse> attributes;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "商品售价", example = "100.00")
    private BigDecimal sellPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "原价", example = "120.00")
    private BigDecimal originPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "成本价", example = "80.00")
    private BigDecimal costPrice;

    @Schema(title = "条码", example = "123456789")
    private String barcode;

    @Schema(title = "SKU", example = "123456789")
    private String skuCode;

    @Schema(title = "品名", example = "123456789")
    private String skuName;

    @Schema(title = "SKU ID", example = "123456789")
    private String skuId;

    @Schema(title = "库存", example = "100")
    private int stockQuantity;

    @Schema(title = "是否关联库存", example = "true")
    private boolean linkStock;

    @Schema(title = "规格ID")
    private Long sid;

    @Schema(title = "SKU信息")
    private ProductSKUResponse sku;

    /**
     * 从 `ProductSpecification` 实体转换为 `ProductSpecificationResponse` 的静态方法。
     */
    public static ProductSpecificationResponse convertEntity(ProductSpecification productSpecification) {
        return filling(productSpecification, ProductSpecificationResponse.builder()
                .skuImage(MultimediaFileResponse.convertEntity(productSpecification.getSpecificationImage()))
                .attributes(ProductSpecificationAttributesResponse.convertList(productSpecification.getAttributes()))
                .sellPrice(productSpecification.getSellPrice())
                .originPrice(productSpecification.getOriginPrice())
                .costPrice(productSpecification.getCostPrice())
                .sid(productSpecification.getSid())
                .barcode(productSpecification.getBarcode())
                .stockQuantity(productSpecification.getStockQuantity())
                .linkStock(productSpecification.isLinkStock())
                .skuCode(productSpecification.getSku().getSkuCode())
                .skuName(productSpecification.getSku().getName())
                .skuId(String.valueOf(productSpecification.getSku().getId()))
                .sku(ProductSKUResponse.convertEntity(productSpecification.getSku()))
                .build());
    }
}
