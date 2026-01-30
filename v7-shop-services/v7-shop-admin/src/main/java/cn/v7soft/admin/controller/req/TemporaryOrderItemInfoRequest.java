package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "商品信息请求实体类")
public class TemporaryOrderItemInfoRequest {

    @Schema(description = "SPU ID")
    private String spuId;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "商品标题")
    private String title;

    @Schema(description = "规格标题")
    private String specTitle;

    @Schema(description = "商品图片")
    private String image;

    @Schema(description = "销售价格")
    private BigDecimal sellPrice;

    @Schema(description = "原始价格")
    private BigDecimal originPrice;

    @Schema(description = "成本价格")
    private BigDecimal costPrice;

    @Schema(description = "税费")
    private BigDecimal tax;

    @Schema(description = "条形码")
    private String barcode;

    @Schema(description = "数量")
    private int quantity;

    @Schema(description = "SKU ID")
    private long skuId;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "SKU 是否虚拟商品")
    private boolean skuIsVirtual;

    @Schema(description = "商品名称")
    private String merchandise;

    @Schema(description = "运单商品名称")
    private String waybillProductName;
}
