package cn.v7soft.admin.controller.resp;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.utils.MultimediaUtil;
import cn.v7soft.common.controller.resp.CountryResponse;
import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.common.controller.resp.LanguageResponse;
import cn.v7soft.common.controller.resp.WebsiteResponse;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductLibrary;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.enums.TaxationMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * 用于返回商品信息的响应类。
 */
@Slf4j
@Getter
@Setter
@SuperBuilder
@Schema(description = "商品信息响应")
public class ProductResponse extends DataRangeResponse {

    @Schema(title = "商品标题", example = "高端智能手机")
    private String title;

    @Schema(title = "商品摘要", example = "这是一款高端智能手机")
    private String summary;

    @Schema(title = "中文品名", example = "手机")
    private String merchandise;

    @Schema(title = "面单品名", example = "手机")
    private String waybillProductName;

    @Schema(title = "商品描述", example = "<p>高端智能手机，最新款，功能强大。</p>")
    private String introduction;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "商品售价", example = "4999.99")
    private BigDecimal sellPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "原价", example = "5999.99")
    private BigDecimal originPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "成本价", example = "3999.99")
    private BigDecimal costPrice;

    @Schema(title = "是否收取税费", example = "true")
    private boolean isTaxable;

    @Schema(title = "税费收取方式", example = "PERCENTAGE")
    private TaxationMethod taxationMethod;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "固定税费金额", example = "100.00")
    private BigDecimal fixedTaxAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "按金额收取税费的条件金额", example = "1000.00")
    private BigDecimal taxAmountThreshold;

    @Schema(title = "按购买量收取税费的条件数量", example = "10")
    private int taxQuantityThreshold;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(title = "按金额收取的税费或按购买量收取的税费", example = "5.00")
    private BigDecimal taxPerBase;

    @Schema(title = "条码", example = "1234567890123")
    private String barcode;

    @Schema(title = "库存", example = "100")
    private int stockQuantity;

    @Schema(title = "是否关联库存", example = "true")
    private boolean linkStock;

    @Schema(title = "是否是多规格", example = "true")
    @JsonProperty("isMultiSpecs")
    private boolean isMultiSpecs;

    @Schema(title = "规格列表")
    private List<ProductSpecificationResponse> specifications;

    @Schema(title = "SKU")
    private ProductSKUResponse sku;

    @Schema(title = "视频文件")
    private MultimediaFileResponse skuVideo;

    @Schema(title = "图片文件列表")
    private List<MultimediaFileResponse> skuImages;

    @Schema(title = "货币")
    private CurrencyResponse currency;

    @Schema(title = "语言")
    private LanguageResponse language;

    @Schema(title = "国家")
    private CountryResponse country;

    @Schema(title = "SPU")
    private SpuResponse spu;

    @Schema(title = "公司库")
    private CompanyResponse companyLibrary;

    @Schema(title = "网站列表")
    private List<WebsiteResponse> websiteList;

    @Schema(title = "产品库列表")
    private List<ProductLibrary> productLibraries;
    @Schema(title = "备用SKU列表")
    private List<String> alternativeSkuCodes;

    @Schema(title = "斗篷规则")
    private List<CloakInfoResponse> cloakInfos;

    @Schema(title = "爬虫显示SPU")
    private SpuSimpleResponse botShowSpu;

    @Schema(title = "分险用户显示SPU")
    private SpuSimpleResponse riskUserShowSpu;

    @Schema(title = "黑名单用户显示SPU")
    private SpuSimpleResponse blacklistedShowSpu;

    /**
     * 从 `Product` 实体转换为 `ProductResponse` 的静态方法。
     */
    public static ProductResponse convertEntity(IMultimediaFileService multimediaFileService,
                                                Product product) {
//        log.debug("convert product: {}", JSONUtil.toJsonStr(product));
//        String introduction = product.getIntroduction();
        String introduction =    MultimediaUtil.replacementIntroductionsMultimedia(multimediaFileService,
                                                                  product.getIntroduction());
        return filling(product, ProductResponse.builder()
                .title(product.getTitle())
                .summary(product.getSummary())
                .introduction(introduction)
                .waybillProductName(product.getWaybillProductName())
                .sellPrice(product.getSellPrice())
                .originPrice(product.getOriginPrice())
                .costPrice(product.getCostPrice())
                .isTaxable(product.isTaxable())
                .taxationMethod(product.getTaxationMethod())
                .fixedTaxAmount(product.getFixedTaxAmount())
                .taxAmountThreshold(product.getTaxAmountThreshold())
                .taxQuantityThreshold(product.getTaxQuantityThreshold())
                .taxPerBase(product.getTaxPerBase())
                .barcode(product.getBarcode())
                .stockQuantity(product.getStockQuantity())
                .linkStock(product.isLinkStock())
                .isMultiSpecs(product.isMultiSpecs())
                .specifications(product.getSpecificationList().stream().map(ProductSpecificationResponse::convertEntity).collect(Collectors.toList()))
                .sku(ProductSKUResponse.convertEntity(product.getSku()))
                .skuVideo(MultimediaFileResponse.convertEntity(product.getVideoFile()))
                .skuImages(product.getImageFiles() == null ? null : product.getImageFiles().stream().map(MultimediaFileResponse::convertEntity).collect(Collectors.toList()))
                .country(CountryResponse.convertEntity(product.getCountry()))
                .language(LanguageResponse.convertEntity(product.getLanguage()))
                .merchandise(product.getMerchandise())
                .alternativeSkuCodes(product.getAlternativeSkus().stream().map(ProductSKU::getSkuCode).toList())
                .cloakInfos(product.getCloakInfos().stream().map(CloakInfoResponse::convert).toList())
                .botShowSpu(SpuSimpleResponse.convertEntity(product.getBotShowSpu()))
                .riskUserShowSpu(SpuSimpleResponse.convertEntity(product.getRiskUserShowSpu()))
                .blacklistedShowSpu(SpuSimpleResponse.convertEntity(product.getBlacklistedUserShowSpu()))
//                .spu(SpuResponse.convertEntity(product.getSpu()))
//                .companyLibrary(CompanyResponse.convertEntity(product.getCompanyLibrary()))
//                .websiteList(product.getWebsiteList().stream().map(WebsiteResponse::convertEntity).collect(Collectors.toList()))
//                .productLibraries(product.getProductLibraries())
                .build());
    }
}
