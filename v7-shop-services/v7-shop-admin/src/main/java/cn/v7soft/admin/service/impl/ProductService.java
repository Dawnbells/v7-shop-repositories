package cn.v7soft.admin.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.http.util.TextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditCloakInfoRequest;
import cn.v7soft.admin.controller.req.EditProductRequest;
import cn.v7soft.admin.controller.req.EditProductSpecification;
import cn.v7soft.admin.controller.req.EditProductSpecificationAttribute;
import cn.v7soft.admin.controller.req.TranslateProductRequest;
import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.ISpuService;
import cn.v7soft.admin.utils.MultimediaUtil;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.CloakInfo;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProductService extends BaseDataRangeService<Product, ProductRepository> implements IProductService {

    private final IProductSKUService productSKUService;
    private final ILanguageService languageService;
    private final EntityManager entityManager;
    private final IMultimediaFileService multimediaFileService;
    private final SpuRepository spuRepository;

    public ProductService(ProductRepository repository, IProductSKUService productSKUService, ILanguageService languageService, EntityManager entityManager,
                          IMultimediaFileService multimediaFileService, SpuRepository spuRepository) {
        super(repository);
        this.productSKUService = productSKUService;
        this.languageService = languageService;
        this.entityManager = entityManager;
        this.multimediaFileService = multimediaFileService;
        this.spuRepository = spuRepository;
    }

    @Override
    protected void checkKeyConstraint(Product data) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(data.getLanguage(), "请选择商品语言");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(data.getCountry(), "请选择商品国家");
        Product existingProduct = repository.findBySameCountryLanguageForUser(data.getSpu().getId(), data.getId(), user.getLongId(), data.getCountry().getId(), data.getLanguage().getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existingProduct, "同一SPU下商品语言不允许重复");
    }

    @Override
    @Transactional
    public Product createOrUpdateProduct(final EditProductRequest request) {
        ProductSKU productSKU = null;
        List<ProductSKU> specificationSkus = new ArrayList<>();
        // 保存SKU
        if (request.getIsMultiSpecs()) {
            // 多规格
            specificationSkus = request.getSpecifications().stream().map(editProductSpecification -> {
                ProductSKU tempSku = TextUtils.isBlank(editProductSpecification.getSkuId()) ?
                                     ProductSKU.builder()
                                             .skuCode(editProductSpecification.getSkuCode())
                                             .name(editProductSpecification.getSkuName())
                                             .totalSalesRevenue(BigDecimal.ZERO)
                                             .totalUnitsSold(0L)
                                             .isVirtual(true)
                                             .build()
                                                                                            :
                                     ProductSKU.builder()
                                             .id(ConvertUtils.parseLongOrNull(editProductSpecification.getSkuId()))
                                             .build();

                return productSKUService.getOrSaveBySkuCode(tempSku);
            }).collect(Collectors.toList());
        } else {
            productSKU = TextUtils.isBlank(request.getSkuId()) ?
                         // 非多规格的情况下，新建产品或者更新SKU
                         ProductSKU.builder()
                                 .skuCode(request.getSkuCode())
                                 .name(request.getSkuName())
                                 .isVirtual(true)
                                 .totalSalesRevenue(BigDecimal.ZERO)
                                 .totalUnitsSold(0L)
                                 .isVirtual(true)
                                 .build()
                                                               :
                         ProductSKU.builder()
                                 .id(ConvertUtils.parseLongOrNull(request.getSkuId()))
                                 .build();
            productSKU = productSKUService.getOrSaveBySkuCode(productSKU);
        }

        Product product;
        if (TextUtils.isBlank(request.getId())) {
            product = Product.builder().build();
            product.setOwner(SaSessionUtil.getLoginOwner());
        } else {
            product = getById(ConvertUtils.parseLong(request.getId()));
        }
        BeanUtil.copyProperties(request, product);
        product.getAlternativeSkus().clear();
        product.getAlternativeSkus().addAll(productSKUService.listBySkuCodes(request.getAlternativeSkuCodes()));
        product.setTaxable(request.getIsTaxable());
        product.setSpu(Spu.builder().id(Long.parseLong(request.getSpuId())).build());
        product.setLanguage(Language.builder().id(Long.parseLong(request.getLanguageId())).build());
        product.setCountry(Country.builder().id(Long.parseLong(request.getCountryId())).build());
        product.setIntroduction(MultimediaUtil.removeAllPrefix(product.getIntroduction()));
        product.setImageFiles(request.getSkuImageIds().stream().map((Function<String, MultimediaFile>) s -> MultimediaFile.builder().id(Long.parseLong(s)).build()).collect(Collectors.toList()));
        if (!TextUtils.isBlank(request.getSkuVideoId())) {
            product.setVideoFile(MultimediaFile.builder().id(Long.parseLong(request.getSkuVideoId())).build());
        }
        product.setSku(productSKU);
        // 删除旧的规格信息
        product.getSpecificationList().forEach(productSpecification -> {
            productSpecification.getAttributes().forEach(productSpecificationAttributes -> productSpecificationAttributes.setProductSpecification(null));
            productSpecification.getAttributes().clear();
            productSpecification.setSku(null);
            productSpecification.setProduct(null);
        });
        product.getSpecificationList().clear();
        // 绑定新的多规格信息
        if (request.getIsMultiSpecs()) {
            List<ProductSpecification> specificationList = new ArrayList<>();
            for (int index = 0; index < request.getSpecifications().size(); index++) {
                EditProductSpecification editProductSpecification = request.getSpecifications().get(index);
                ProductSKU specSku = specificationSkus.get(index);
                ProductSpecification productSpecification = ProductSpecification.builder()
                        .specificationImage(
                                TextUtils.isBlank(editProductSpecification.getSpecificationImageId())
                                ? null
                                : MultimediaFile.builder()
                                        .id(ConvertUtils.parseLong(editProductSpecification.getSpecificationImageId(), "规格图片错"))
                                        .build()
                        )
                        .attributes(
                                editProductSpecification.getAttributes()
                                        .stream()
                                        .map((Function<EditProductSpecificationAttribute,
                                                     ProductSpecificationAttributes>) editProductSpecificationAttribute -> {
                                                 ProductSpecificationAttributes.ProductSpecificationAttributesBuilder<?, ?> builder = ProductSpecificationAttributes.builder()
                                                         .name(editProductSpecificationAttribute.getName())
                                                         .value(editProductSpecificationAttribute.getValue());
                                                 if (editProductSpecificationAttribute.getImage() != null && StrUtil.isNotBlank(editProductSpecificationAttribute.getImage().getId())) {
                                                     builder.multimediaFile(MultimediaFile.builder().id(editProductSpecificationAttribute.getImage().getIdLongValue()).build());
                                                 } else {
                                                     builder.multimediaFile(null);
                                                 }
                                                 return builder.build();
                                             }
                                        ).collect(Collectors.toList())
                        )
                        .sid(editProductSpecification.getSid())
                        .sellPrice(editProductSpecification.getSellPrice())
                        .originPrice(editProductSpecification.getOriginPrice())
                        .costPrice(editProductSpecification.getCostPrice())
                        .barcode(editProductSpecification.getBarcode())
                        .stockQuantity(editProductSpecification.getStockQuantity())
                        .linkStock(editProductSpecification.getLinkStock())
                        .sku(specSku)
                        .product(product)
                        .build();
                productSpecification.getAttributes().forEach(productSpecificationAttributes -> productSpecificationAttributes.setProductSpecification(productSpecification));
                specificationList.add(productSpecification);
            }
            product.getSpecificationList().addAll(specificationList);
        }
        // 处理斗篷规则
//        List<CloakInfo> cloakInfos = new ArrayList<>();
//        if (request.getCloakInfos() != null) {
//            List<EditCloakInfoRequest> requestCloakInfos = request.getCloakInfos();
//            for (int i = 0; i < requestCloakInfos.size(); i++) {
//                EditCloakInfoRequest editCloakInfo = requestCloakInfos.get(i);
//                CloakInfo cloakInfo = CloakInfo.builder()
//                        .ordering(i)
//                        .name(editCloakInfo.getName())
//                        .spuId(editCloakInfo.getSpuId())
//                        .includeCountryCode(editCloakInfo.getIncludeCountryCode())
//                        .excludeCountryCode(editCloakInfo.getExcludeCountryCode())
//                        .includeCrawler(String.join(",", editCloakInfo.getIncludeCrawler()))
//                        .excludeCrawler(String.join(",", editCloakInfo.getExcludeCrawler()))
//                        .build();
//                cloakInfos.add(cloakInfo);
//            }
//        }
//        product.getCloakInfos().clear();
//        product.getCloakInfos().addAll(cloakInfos);
//        cloakInfos.forEach(cloakInfo -> cloakInfo.setProduct(product));
        if (ConvertUtils.isLong(request.getBotShowSpuId())) {
            product.setBotShowSpu(Spu.builder().id(Long.valueOf(request.getBotShowSpuId())).build());
        } else {
            product.setBotShowSpu(null);
        }
        if (ConvertUtils.isLong(request.getRiskUserShowSpuId())) {
            product.setRiskUserShowSpu(Spu.builder().id(Long.valueOf(request.getRiskUserShowSpuId())).build());
        } else {
            product.setRiskUserShowSpu(null);
        }
        if (ConvertUtils.isLong(request.getBlacklistedUserShowSpuId())) {
            product.setBlacklistedUserShowSpu(Spu.builder().id(Long.valueOf(request.getBlacklistedUserShowSpuId())).build());
        } else {
            product.setBlacklistedUserShowSpu(null);
        }
        spuRepository.refreshUpdateTime(product.getSpu().getId());
        return super.save(product);
    }

    @Override
    public List<String> remoteQueryMerchandise(String query) {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        Long departmentId = loginUser.isAdmin() ? null : loginUser.getDepartmentId();
        return repository.remoteQueryMerchandise("%" + query + "%", departmentId);
    }

    @Override
    @Transactional
    public ProductResponse translate(TranslateProductRequest request) {
        Product product = getById(Long.parseLong(request.getProductId()));
        Language language = languageService.getById(Long.valueOf(request.getLanguageId()));
        List<ProductSpecification> productSpecifications = product.getSpecificationList().stream().map(productSpecification -> {
            ProductSpecification newProductSpecification = ProductSpecification.builder()
                    .specificationImage(productSpecification.getSpecificationImage())
                    .sid(productSpecification.getSid())
                    .sellPrice(productSpecification.getSellPrice())
                    .originPrice(productSpecification.getOriginPrice())
                    .costPrice(productSpecification.getCostPrice())
                    .barcode(productSpecification.getBarcode())
                    .stockQuantity(productSpecification.getStockQuantity())
                    .linkStock(productSpecification.isLinkStock())
                    .sku(productSpecification.getSku())
                    .product(null)
                    .attributes(null)
                    .build();

            List<ProductSpecificationAttributes> productSpecificationAttributesList = productSpecification
                    .getAttributes()
                    .stream()
                    .map((Function<ProductSpecificationAttributes, ProductSpecificationAttributes>) productSpecificationAttributes ->
                            ProductSpecificationAttributes.builder()
                                    .name(productSpecificationAttributes.getName())
                                    .value(productSpecificationAttributes.getValue())
                                    .multimediaFile(productSpecificationAttributes.getMultimediaFile())
                                    .productSpecification(newProductSpecification)
                                    .build()).toList();
            newProductSpecification.setAttributes(productSpecificationAttributesList);
            return newProductSpecification;
        }).toList();
        Product newProduct = Product.builder()
                .title(product.getTitle())
                .summary(product.getSummary())
                .introduction(product.getIntroduction())
                .merchandise(product.getMerchandise())
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
                .specificationList(productSpecifications)
                .sku(product.getSku())
                .videoFile(product.getVideoFile())
                .imageFiles(product.getImageFiles())
                .language(language)
                .spu(product.getSpu())
                .alternativeSkus(product.getAlternativeSkus())
                .country(product.getCountry())
                .build();
        product.getSpecificationList().forEach(productSpecification -> productSpecification.setProduct(newProduct));
        return ProductResponse.convertEntity(multimediaFileService, saveAndFlush(product));
    }
}
