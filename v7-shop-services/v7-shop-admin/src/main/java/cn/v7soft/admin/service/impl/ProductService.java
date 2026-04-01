package cn.v7soft.admin.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import org.apache.http.util.TextUtils;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditCloakInfoRequest;
import cn.v7soft.admin.controller.req.EditProductRequest;
import cn.v7soft.admin.controller.req.EditProductSpecification;
import cn.v7soft.admin.controller.req.EditProductSpecificationAttribute;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.req.TranslateProductRequest;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.admin.service.ITaskService;
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
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.enums.MediaState;
import cn.v7soft.dao.enums.MediaType;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.properties.MultimediaFileProperty;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProductService extends BaseDataRangeService<Product, ProductRepository> implements IProductService {

    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private final IProductSKUService productSKUService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final EntityManager entityManager;
    private final IMultimediaFileService multimediaFileService;
    private final SpuRepository spuRepository;
    private final IS3Service s3Service;
    private final AsyncTaskRepository asyncTaskRepository;
    private final ITaskService taskService;
    private final TranslateTaskMetrics translateTaskMetrics;

    public ProductService(ProductRepository repository, IProductSKUService productSKUService,
                          ILanguageService languageService, ICountryService countryService,
                          EntityManager entityManager,
                          IMultimediaFileService multimediaFileService, SpuRepository spuRepository,
                          IS3Service s3Service,
                          AsyncTaskRepository asyncTaskRepository, ITaskService taskService,
                          TranslateTaskMetrics translateTaskMetrics) {
        super(repository);
        this.productSKUService = productSKUService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.entityManager = entityManager;
        this.multimediaFileService = multimediaFileService;
        this.spuRepository = spuRepository;
        this.s3Service = s3Service;
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskService = taskService;
        this.translateTaskMetrics = translateTaskMetrics;
    }

    @Override
    protected void checkKeyConstraint(Product data) {
        Long userId;
        try {
            userId = SaSessionUtil.getLoginUser().getLongId();
        } catch (Exception e) {
            userId = data.getOwner() != null ? data.getOwner().getId() : null;
        }
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(data.getLanguage(), "请选择商品语言");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(data.getCountry(), "请选择商品国家");
        Product existingProduct = repository.findBySameCountryLanguageForUser(data.getSpu().getId(), data.getId(), userId, data.getCountry().getId(), data.getLanguage().getId());
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
    @Transactional(readOnly = true)
    public Product getByIdWithSpecifications(Long id) {
        Product product = getById(id);
        Hibernate.initialize(product.getSpecificationList());
        for (ProductSpecification spec : product.getSpecificationList()) {
            Hibernate.initialize(spec.getAttributes());
        }
        return product;
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

    @Override
    @Transactional
    public AsyncTaskResponse submitTranslateByAI(TranslateByAIRequest request) {
        Product product = getById(Long.parseLong(request.getProductId()));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(product, "商品不存在");

        Country country = countryService.getById(Long.valueOf(request.getCountryId()));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(country, "目标国家不存在");

        Language language = languageService.getById(Long.valueOf(request.getLanguageId()));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(language, "目标语言不存在");

        boolean languageBelongsToCountry = country.getLanguages() != null
                && country.getLanguages().stream().anyMatch(l -> l.getId().equals(language.getId()));
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(languageBelongsToCountry,
                "所选语言不属于目标国家支持的语言");

        Long userId;
        try {
            userId = cn.v7soft.dao.utils.SaSessionUtil.getLoginUser().getLongId();
        } catch (Exception e) {
            userId = product.getOwner() != null ? product.getOwner().getId() : null;
        }
        Product duplicate = repository.findBySameCountryLanguageForUser(
                product.getSpu().getId(), null, userId, country.getId(), language.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(duplicate,
                "同一SPU下该国家和语言已存在商品，不允许重复");

        String dedupKey = "PRODUCT_AI_TRANSLATE:" + userId + ":" +
                request.getProductId() + ":" + request.getCountryId() + ":" + request.getLanguageId();

        List<AsyncTask> existing = asyncTaskRepository.findByTaskTypeAndDedupKeyAndStateIn(
                TaskType.PRODUCT_AI_TRANSLATE, dedupKey,
                List.of(TaskState.PENDING, TaskState.PROCESSING));
        if (!existing.isEmpty()) {
            translateTaskMetrics.recordDedupHit();
            return AsyncTaskResponse.convert(existing.get(0));
        }

        String parameters = cn.hutool.json.JSONUtil.toJsonStr(request);

        String title = StrUtil.isNotBlank(product.getTitle())
                ? product.getTitle()
                : "商品#" + product.getId();
        String taskName = "AI翻译: " + title + " → " + language.getName();

        AsyncTask asyncTask = AsyncTask.builder()
                .taskType(TaskType.PRODUCT_AI_TRANSLATE)
                .state(TaskState.PENDING)
                .progress(0)
                .parameters(parameters)
                .name(taskName)
                .dedupKey(dedupKey)
                .build()
                .fillOwner();
        asyncTask = asyncTaskRepository.saveAndFlush(asyncTask);
        translateTaskMetrics.recordSubmit();
        taskService.submitAsyncTask(asyncTask.getId());
        return AsyncTaskResponse.convert(asyncTask);
    }

    @Override
    @Transactional
    public ProductResponse assembleTranslatedProduct(
            Product product, Language language, Country country, SystemUser owner,
            List<String> translatedTexts, String translatedIntroduction,
            Map<String, byte[]> translatedImageMap) throws Exception {

        String translatedTitle = translatedTexts.get(0);
        String translatedSummary = translatedTexts.get(1);

        // 按固定位置索引消费 translatedTexts: [0]=title, [1]=summary, 之后每对 = spec attr name/value
        int textIdx = 2;

        // 替换 introduction 中的图片引用
        String finalIntroduction = translatedIntroduction;
        if (finalIntroduction != null && translatedImageMap != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(finalIntroduction);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String imgId = matcher.group(1);
                byte[] imgBytes = translatedImageMap.get(imgId);
                if (imgBytes != null) {
                    MultimediaFile originalFile = multimediaFileService.getById(Long.valueOf(imgId));
                    MultimediaFile newFile = saveTranslatedImage(imgBytes, originalFile.getSuffix(), owner);
                    matcher.appendReplacement(sb, "/multimedia/" + newFile.getId());
                } else {
                    matcher.appendReplacement(sb, matcher.group());
                }
            }
            matcher.appendTail(sb);
            finalIntroduction = sb.toString();
        }

        // 按固定顺序消费 translatedTexts: [0]=title, [1]=summary, 之后每对 = spec attr name/value
        List<ProductSpecification> newSpecs = new ArrayList<>();
        for (ProductSpecification spec : product.getSpecificationList()) {
            ProductSpecification newSpec = ProductSpecification.builder()
                    .specificationImage(spec.getSpecificationImage())
                    .sid(spec.getSid())
                    .sellPrice(spec.getSellPrice())
                    .originPrice(spec.getOriginPrice())
                    .costPrice(spec.getCostPrice())
                    .barcode(spec.getBarcode())
                    .stockQuantity(spec.getStockQuantity())
                    .linkStock(spec.isLinkStock())
                    .sku(spec.getSku())
                    .product(null)
                    .attributes(null)
                    .build();
            List<ProductSpecificationAttributes> newAttrs = new ArrayList<>();
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                String translatedName = textIdx < translatedTexts.size() ? translatedTexts.get(textIdx++) : attr.getName();
                String translatedValue = textIdx < translatedTexts.size() ? translatedTexts.get(textIdx++) : attr.getValue();
                newAttrs.add(ProductSpecificationAttributes.builder()
                        .name(translatedName)
                        .value(translatedValue)
                        .multimediaFile(attr.getMultimediaFile())
                        .productSpecification(newSpec)
                        .build());
            }
            newSpec.setAttributes(newAttrs);
            newSpecs.add(newSpec);
        }

        Product newProduct = Product.builder()
                .title(translatedTitle)
                .summary(translatedSummary)
                .introduction(finalIntroduction)
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
                .specificationList(newSpecs)
                .sku(product.getSku())
                .videoFile(product.getVideoFile())
                .imageFiles(product.getImageFiles())
                .language(language)
                .spu(product.getSpu())
                .alternativeSkus(product.getAlternativeSkus())
                .country(country)
                .botShowSpu(product.getBotShowSpu())
                .riskUserShowSpu(product.getRiskUserShowSpu())
                .blacklistedUserShowSpu(product.getBlacklistedUserShowSpu())
                .build();
        newProduct.setOwner(owner);
        newSpecs.forEach(spec -> spec.setProduct(newProduct));

        spuRepository.refreshUpdateTime(product.getSpu().getId());
        return ProductResponse.convertEntity(multimediaFileService, saveAndFlush(newProduct));
    }

    private MultimediaFile saveTranslatedImage(byte[] imageBytes, String suffix, SystemUser owner) throws Exception {
        String newFileName = IdUtil.fastSimpleUUID();
        LocalDateTime now = LocalDateTime.now();
        String relativePath = MultimediaFileProperty.makeRelativePath(
                MediaType.IMAGE, newFileName, now, suffix);

        String mimeType = "image/" + (suffix.equalsIgnoreCase("jpg") ? "jpeg" : suffix.toLowerCase());
        s3Service.upload(new ByteArrayInputStream(imageBytes), relativePath, mimeType);

        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        int width = bufferedImage != null ? bufferedImage.getWidth() : 0;
        int height = bufferedImage != null ? bufferedImage.getHeight() : 0;

        MultimediaFile file = MultimediaFile.builder()
                .name(newFileName).suffix(suffix)
                .width(width).height(height)
                .fileSize(imageBytes.length).mediaType(MediaType.IMAGE)
                .relativePath(relativePath).createTime(now)
                .mediaState(MediaState.UPLOADED).build();
        file.setOwner(owner);
        return multimediaFileService.saveAndFlush(file);
    }
}
