package cn.v7soft.admin.controller.resp;


import cn.hutool.core.collection.ListUtil;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SystemUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SPU响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "SPU信息响应")
public class SpuResponse extends IdResponse {
    @Schema(title = "SPU代码", example = "SPU12345")
    private String code;

    @Schema(title = "商品名称", example = "产品名称")
    private String name;

    @Schema(title = "商品描述", example = "商品描述")
    private String description;

    @Schema(title = "商品归属", example = "张三")
    private String belong;

    @Schema(title = "是否是统一汇率", example = "true")
    private Boolean useStandardExchangeRate;

    @Schema(title = "是否是共享状态", example = "true")
    private boolean isOpen;

    @Schema(title = "产品分类", example = "产品分类信息")
    private ProductCategoryResponse productCategory;

    @Schema(title = "产品列表", example = "[]")
    private List<ProductResponse> productList;

    @Schema(title = "已配置国家ID列表", example = "[]")
    private List<String> countryIds;

    public static SpuResponse convertEntity(IMultimediaFileService multimediaFileService, Spu spu) {
        List<Product> products = spu.getProductList();
        SystemUser owner = spu.getOwner();
        return SpuResponse.builder().code(String.valueOf(spu.getCode())).name(spu.getName())
                .belong(owner == null ? "" : owner.getName()).description(spu.getDescription())
                .isOpen(Boolean.TRUE.equals(spu.getIsOpen()))
                .productCategory(ProductCategoryResponse.convertEntity(spu.getProductCategory()))
                .productList(products == null ? ListUtil.empty() : products.stream()
                        .map(product -> ProductResponse.convertEntity(multimediaFileService,
                                product)).collect(Collectors.toList())).countryIds(
                        products == null ? ListUtil.empty() : products.stream()
                                .map(product -> String.valueOf(product.getCountry().getId()))
                                .collect(Collectors.toList())).build();
    }
}