package cn.v7soft.admin.controller.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EditProductSpecification {
    @Schema(title = "规格图片ID", example = "1")
    private String specificationImageId;

    @NotEmpty(message = "当前规格包含的属性列表不能为空")
    @Schema(title = "当前规格包含的规格属性列表", example = "[]")
    private List<EditProductSpecificationAttribute> attributes;

    @PositiveOrZero(message = "商品售价不允许为负数")
    @Schema(title = "商品售价", example = "4999.99", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal sellPrice;

    @PositiveOrZero(message = "商品原价不允许为负数")
    @Schema(title = "原价", example = "5999.99")
    private BigDecimal originPrice;

    @Schema(title = "成本价", example = "3999.99")
    private BigDecimal costPrice;

    @Schema(title = "条码", example = "1234567890123")
    private String barcode;

    @PositiveOrZero(message = "库存不允许为负数")
    @Schema(title = "库存", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stockQuantity;

    @Schema(title = "是否关联库存", example = "true")
    private Boolean linkStock;


    @Schema(title = "SKU ID", example = "SLI22000023")
    private String skuId;

    @Schema(title = "虚拟SKU", example = "SLI22000023")
    private String skuCode;

    @Schema(title = "虚拟SKU品名", example = "寿司")
    private String skuName;

    @Schema(title = "规格ID", example = "0")
    private Long sid;
}
