package cn.v7soft.admin.controller.req;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.TaxationMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于编辑商品信息的请求类。
 */
@Getter
@Setter
public class EditProductRequest extends IdRequest {

    @NotBlank(message = "商品标题不能为空")
    @Schema(title = "商品标题", example = "高端智能手机", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(title = "商品摘要", example = "这是一款高端智能手机")
    private String summary;

    @NotBlank(message = "商品描述不能为空")
    @Schema(title = "商品描述", example = "<p>高端智能手机，最新款，功能强大。</p>")
    private String introduction;

    /**
     * 货品名称
     */
    @NotBlank(message = "中文品名不能为空")
    @Schema(title = "中文品名", example = "手机")
    private String merchandise;

    @NotBlank(message = "面单品名不能为空")
    @Schema(title = "面单品名", example = "手机")
    private String waybillProductName;

    @Schema(title = "备选SKU列表", example = "['222', '232323']")
    private List<String> alternativeSkuCodes;

    @PositiveOrZero(message = "商品售价不允许为负数")
    @NotNull(message = "商品售价不能为空")
    @Schema(title = "商品售价", example = "4999.99", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal sellPrice;

    @PositiveOrZero(message = "商品原价不允许为负数")
    @Schema(title = "原价", example = "5999.99")
    private BigDecimal originPrice;

    @Schema(title = "成本价", example = "3999.99")
    private BigDecimal costPrice;

    @Schema(title = "是否收取税费", example = "true")
    private Boolean isTaxable;

    @Schema(title = "税费收取方式", example = "PERCENTAGE")
    private TaxationMethod taxationMethod;

    @PositiveOrZero(message = "固定税费金额不允许为负数")
    @Schema(title = "固定税费金额", example = "100.00")
    private BigDecimal fixedTaxAmount;

    @PositiveOrZero(message = "按金额收取税费的条件金额不允许为负数")
    @Schema(title = "按金额收取税费的条件金额", example = "1000.00")
    private BigDecimal taxAmountThreshold;

    @PositiveOrZero(message = "按购买量收取税费的条件数量不允许为负数")
    @Schema(title = "按购买量收取税费的条件数量", example = "10")
    private Integer taxQuantityThreshold;

    @PositiveOrZero(message = "按金额收取的税费或按购买量收取的税费不允许为负数")
    @Schema(title = "按金额收取的税费或按购买量收取的税费", example = "5.00")
    private BigDecimal taxPerBase;

    @Schema(title = "条码", example = "1234567890123")
    private String barcode;

    @Schema(title = "库存", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stockQuantity;

    @Schema(title = "是否关联库存", example = "true")
    private Boolean linkStock;

    @Schema(title = "是否是多规格", example = "true")
    private Boolean isMultiSpecs;

    @Schema(title = "SKU ID", example = "SLI22000023")
    private String skuId;

    @Schema(title = "虚拟SKU", example = "SLI22000023")
    private String skuCode;

    @Schema(title = "虚拟SKU品名", example = "寿司")
    private String skuName;

    @Schema(title = "视频文件ID", example = "1")
    private String skuVideoId;

    @NotEmpty(message = "商品主图不能为空")
    @Schema(title = "图片文件ID列表", example = "[1, 2, 3]")
    private List<String> skuImageIds;

    @Schema(title = "SPU ID", example = "1")
    private String spuId;

    @Schema(title = "Language ID", example = "1")
    private String languageId;

    @Schema(title = "Country ID", example = "1")
    private String countryId;

    @Schema(title = "商品是否上架", example = "true")
    private Boolean isAvailable;

    @Schema(title = "黑名单用户显示的落地页", example = "true")
    private String blacklistedUserShowSpuId;
    
    @Schema(title = "分险用户显示的落地页", example = "true")
    private String riskUserShowSpuId;

    @Schema(title = "爬虫显示的落地页", example = "true")
    private String botShowSpuId;

    @Schema(title = "商品规格", example = "true")
    private List<EditProductSpecification> specifications;

    @Schema(title = "斗篷规则", example = "true")
    private List<EditCloakInfoRequest> cloakInfos = new ArrayList<>();
}
