package cn.v7soft.admin.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.util.TextUtils;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.v7soft.admin.controller.req.EditProductRequest;
import cn.v7soft.admin.controller.req.EditProductSpecification;
import cn.v7soft.admin.controller.req.EditProductSpecificationAttribute;
import cn.v7soft.admin.controller.req.BatchEditMerchandiseRequest;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.req.TranslateProductRequest;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.controller.resp.BatchEditMerchandiseResponse;
import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.admin.event.AiTranslateTaskNotificationEvent;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.utils.MultimediaUtil;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProductService extends BaseDataRangeService<Product, ProductRepository> implements IProductService {

    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private record PendingMerchandiseChange(Product product, String merchandise, boolean emptied) {
    }

    private final IProductSKUService productSKUService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final IMultimediaFileService multimediaFileService;
    private final SpuRepository spuRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final TranslateTaskMetrics translateTaskMetrics;
    private final AiCreditsService aiCreditsService;
    private final IAiAccountService aiAccountService;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(ProductRepository repository, IProductSKUService productSKUService,
                          ILanguageService languageService, ICountryService countryService,
                          IMultimediaFileService multimediaFileService, SpuRepository spuRepository,
                          AsyncTaskRepository asyncTaskRepository,
                          TranslateTaskMetrics translateTaskMetrics,
                          AiCreditsService aiCreditsService,
                          IAiAccountService aiAccountService,
                          ApplicationEventPublisher eventPublisher) {
        super(repository);
        this.productSKUService = productSKUService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.multimediaFileService = multimediaFileService;
        this.spuRepository = spuRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.translateTaskMetrics = translateTaskMetrics;
        this.aiCreditsService = aiCreditsService;
        this.aiAccountService = aiAccountService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void checkKeyConstraint(Product data) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(data.getLanguage(), "请选择商品语言");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(data.getCountry(), "请选择商品国家");
        Product existingProduct = repository.findBySameCountryLanguage(data.getSpu().getId(), data.getId(), data.getCountry().getId(), data.getLanguage().getId());
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
            }).toList();
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
        assertCanAccessSpu(request.getSpuId());
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
    @Transactional
    public BatchEditMerchandiseResponse batchEditMerchandise(BatchEditMerchandiseRequest request) {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        Set<Long> targetSpuIds = resolveBatchTargetSpuIds(request, loginUser);
        if (targetSpuIds.isEmpty()) {
            return BatchEditMerchandiseResponse.builder().build();
        }

        List<Product> products = repository.findAllBySpuIdIn(targetSpuIds);
        List<PendingMerchandiseChange> pendingChanges = new ArrayList<>();
        String originalMerchandise = request.getOriginalMerchandise().trim();
        String field = request.getField().trim();
        String delimiter = request.getDelimiter();
        long matchedProductCount = 0;
        long alreadyExistsCount = 0;
        long notFoundCount = 0;
        long emptySkippedCount = 0;

        for (Product product : products) {
            if (!originalMerchandise.equals(product.getMerchandise())) {
                continue;
            }
            matchedProductCount++;
            MerchandiseFieldEditor.Result editResult = request.getOperation() == BatchEditMerchandiseRequest.Operation.ADD
                    ? MerchandiseFieldEditor.add(product.getMerchandise(), field, delimiter)
                    : MerchandiseFieldEditor.remove(
                            product.getMerchandise(), field, delimiter, request.getEmptyResultPolicy());
            switch (editResult.outcome()) {
                case ALREADY_EXISTS -> alreadyExistsCount++;
                case NOT_FOUND -> notFoundCount++;
                case EMPTY_SKIPPED -> emptySkippedCount++;
                case UPDATED -> {
                    int resultLength = editResult.value().codePointCount(0, editResult.value().length());
                    ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(
                            resultLength <= 512,
                            "商品[" + product.getId() + "]处理后的中文品名超过512个字符，已取消整批操作");
                    pendingChanges.add(new PendingMerchandiseChange(
                            product, editResult.value(), editResult.emptied()));
                }
            }
        }

        Set<Long> updatedSpuIds = new LinkedHashSet<>();
        long emptiedProductCount = 0;
        for (PendingMerchandiseChange pendingChange : pendingChanges) {
            pendingChange.product().setMerchandise(pendingChange.merchandise());
            if (pendingChange.emptied()) {
                emptiedProductCount++;
            }
            if (pendingChange.product().getSpu() != null) {
                updatedSpuIds.add(pendingChange.product().getSpu().getId());
            }
        }
        updatedSpuIds.forEach(spuRepository::refreshUpdateTime);

        log.info(
                "[中文品名批量编辑] operator={} scope={} operation={} targetSpus={} targetProducts={} matchedProducts={} updatedProducts={} emptiedProducts={}",
                loginUser.getId(), request.getScope(), request.getOperation(), targetSpuIds.size(), products.size(),
                matchedProductCount, pendingChanges.size(), emptiedProductCount);
        return BatchEditMerchandiseResponse.builder()
                .targetSpuCount(targetSpuIds.size())
                .targetProductCount(products.size())
                .matchedProductCount(matchedProductCount)
                .originalMismatchCount(products.size() - matchedProductCount)
                .updatedProductCount(pendingChanges.size())
                .alreadyExistsCount(alreadyExistsCount)
                .notFoundCount(notFoundCount)
                .emptySkippedCount(emptySkippedCount)
                .emptiedProductCount(emptiedProductCount)
                .build();
    }

    private Set<Long> resolveBatchTargetSpuIds(BatchEditMerchandiseRequest request, SystemUserDto loginUser) {
        if (request.getScope() == BatchEditMerchandiseRequest.Scope.OWNED_ALL) {
            return new LinkedHashSet<>(spuRepository.findIdsByOwnerId(loginUser.getLongId()));
        }

        List<Long> requestedIds = request.getSpuIds().stream().distinct().toList();
        Map<Long, Spu> resolvedSpus = spuRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Spu::getId, Function.identity()));
        List<Long> missingIds = requestedIds.stream().filter(id -> !resolvedSpus.containsKey(id)).toList();
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(
                missingIds.isEmpty(), "以下SPU不存在或已删除：" + missingIds);

        for (Long spuId : requestedIds) {
            Spu spu = resolvedSpus.get(spuId);
            SystemUser owner = spu.getOwner();
            Long ownerId = owner == null ? null : owner.getId();
            Long ownerDepartmentId = owner == null || owner.getDepartment() == null
                    ? null
                    : owner.getDepartment().getId();
            ClientResponseEnum.NO_PERMISSION.assertTrue(
                    loginUser.hasManagerPermission(ownerId, ownerDepartmentId),
                    "您无权限操作SPU[" + spuId + "]");
        }
        return new LinkedHashSet<>(requestedIds);
    }

    private void assertCanAccessSpu(String spuId) {
        if (!ConvertUtils.isLong(spuId)) {
            return;
        }
        Spu spu = spuRepository.findById(Long.valueOf(spuId)).orElse(null);
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(spu, "SPU不存在");
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        SystemUser owner = spu.getOwner();
        Long ownerId = owner == null ? null : owner.getId();
        Long ownerDepartmentId = owner == null || owner.getDepartment() == null ? null : owner.getDepartment().getId();
        ClientResponseEnum.NO_PERMISSION.assertTrue(loginUser.hasManagerPermission(ownerId, ownerDepartmentId), "您无权限操作该SPU");
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
        Hibernate.initialize(product.getImageFiles());
        Hibernate.initialize(product.getSpecificationList());
        for (ProductSpecification spec : product.getSpecificationList()) {
            Hibernate.initialize(spec.getAttributes());
            Hibernate.initialize(spec.getSpecificationImage());
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                Hibernate.initialize(attr.getMultimediaFile());
            }
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
                .alternativeSkus(new ArrayList<>(product.getAlternativeSkus()))
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

        Product duplicate = repository.findBySameCountryLanguage(
                product.getSpu().getId(), null, country.getId(), language.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(duplicate,
                                                    "同一SPU下该国家和语言已存在商品，不允许重复");

        AiAccount account = requireAiAccount(request);
        TaskType taskType = TaskType.PRODUCT_AI_TRANSLATE;

        String dedupKey = taskType.name() + ":" +
                          request.getProductId() + ":" + request.getCountryId() + ":" + request.getLanguageId();

        List<AsyncTask> existing = asyncTaskRepository.findByTaskTypeAndDedupKeyAndStateIn(
                taskType, dedupKey,
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

        // R1：提交时只校验积分可用性，实际冻结由 AiAccountTranslateTask.loadTask 阶段
        // 按子任务级精算执行，避免重复冻结导致额度永久泄漏
        requireAvailableCredits();

        AsyncTask asyncTask = AsyncTask.builder()
                .taskType(taskType)
                .state(TaskState.PENDING)
                .progress(0)
                .parameters(parameters)
                .name(taskName)
                .dedupKey(dedupKey)
                .build()
                .fillOwner();
        asyncTask = asyncTaskRepository.saveAndFlush(asyncTask);
        translateTaskMetrics.recordSubmit();
        eventPublisher.publishEvent(AiTranslateTaskNotificationEvent.submitted(
                asyncTask.getCompanyId(),
                asyncTask.getId(),
                resolveOperatorName(asyncTask),
                title,
                country.getName(),
                language.getName(),
                account.getName(),
                resolveCreatedAt(asyncTask)
        ));
        return AsyncTaskResponse.convert(asyncTask);
    }

    private String resolveOperatorName(AsyncTask task) {
        try {
            SystemUserDto loginUser = SaSessionUtil.getLoginUser();
            if (loginUser != null && StrUtil.isNotBlank(loginUser.getName())) {
                return loginUser.getName();
            }
        } catch (Exception ignored) {
            // ignore session lookup fallback
        }
        SystemUser owner = task.getOwner();
        if (owner != null && StrUtil.isNotBlank(owner.getName())) {
            return owner.getName();
        }
        return "未知用户";
    }

    private LocalDateTime resolveCreatedAt(AsyncTask task) {
        return task.getCreateTime() == null ? LocalDateTime.now() : task.getCreateTime();
    }

    private AiAccount requireAiAccount(TranslateByAIRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getAiAccountId(), "请选择AI账号");
        AiAccount account = aiAccountService.getById(Long.parseLong(request.getAiAccountId()));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(account, "AI账号不存在");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(account.getProvider(), "AI账号类型不能为空");
        return account;
    }

    @Override
    @Transactional
    public ProductResponse assembleTranslatedProduct(
            Product product, Language language, Country country, SystemUser owner,
            Map<String, String> translatedTextMap, String translatedIntroduction,
            Map<String, MultimediaFile> translatedImageMap) throws Exception {

        product = getByIdWithSpecifications(product.getId());
        language = languageService.getById(language.getId());
        country = countryService.getById(country.getId());

        if (translatedImageMap != null) {
            Map<String, MultimediaFile> refreshed = new java.util.HashMap<>();
            for (Map.Entry<String, MultimediaFile> e : translatedImageMap.entrySet()) {
                MultimediaFile file = e.getValue();
                if (file != null && file.getId() != null) {
                    refreshed.put(e.getKey(), multimediaFileService.getById(file.getId()));
                } else {
                    refreshed.put(e.getKey(), file);
                }
            }
            translatedImageMap = refreshed;
        }

        String translatedTitle = lookupTranslation(translatedTextMap, product.getTitle());
        String translatedSummary = lookupTranslation(translatedTextMap, product.getSummary());
        String translatedWaybillProductName = lookupTranslation(translatedTextMap, product.getWaybillProductName());

        String finalIntroduction = translatedIntroduction != null ? translatedIntroduction : product.getIntroduction();
        if (finalIntroduction != null && translatedImageMap != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(finalIntroduction);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String imgId = matcher.group(1);
                MultimediaFile newFile = translatedImageMap.get(imgId);
                if (newFile != null) {
                    matcher.appendReplacement(sb, "/multimedia/" + newFile.getId());
                } else {
                    matcher.appendReplacement(sb, matcher.group());
                }
            }
            matcher.appendTail(sb);
            finalIntroduction = sb.toString();
        }

        List<ProductSpecification> newSpecs = new ArrayList<>();
        List<ProductSpecification> sourceSpecs = product.getSpecificationList() == null
                                             ? List.of()
                                             : product.getSpecificationList();
        for (ProductSpecification spec : sourceSpecs) {
            MultimediaFile specImg = spec.getSpecificationImage();
            MultimediaFile translatedSpecImg = specImg != null && specImg.getId() != null && translatedImageMap != null
                                               ? translatedImageMap.getOrDefault(String.valueOf(specImg.getId()), specImg)
                                               : specImg;

            ProductSpecification newSpec = ProductSpecification.builder()
                    .specificationImage(translatedSpecImg)
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
            List<ProductSpecificationAttributes> sourceAttrs = spec.getAttributes() == null
                                                         ? List.of()
                                                         : spec.getAttributes();
            for (ProductSpecificationAttributes attr : sourceAttrs) {
                String translatedName = lookupTranslation(translatedTextMap, attr.getName());
                String translatedValue = lookupTranslation(translatedTextMap, attr.getValue());

                MultimediaFile attrImg = attr.getMultimediaFile();
                MultimediaFile translatedAttrImg = attrImg != null && attrImg.getId() != null && translatedImageMap != null
                                                   ? translatedImageMap.getOrDefault(String.valueOf(attrImg.getId()), attrImg)
                                                   : attrImg;

                newAttrs.add(ProductSpecificationAttributes.builder()
                                     .name(translatedName)
                                     .value(translatedValue)
                                     .multimediaFile(translatedAttrImg)
                                     .productSpecification(newSpec)
                                     .build());
            }
            newSpec.setAttributes(newAttrs);
            newSpecs.add(newSpec);
        }

        List<MultimediaFile> newImageFiles = new ArrayList<>();
        if (product.getImageFiles() != null && translatedImageMap != null) {
            for (MultimediaFile img : product.getImageFiles()) {
                MultimediaFile translated = img != null && img.getId() != null
                                            ? translatedImageMap.get(String.valueOf(img.getId()))
                                            : null;
                newImageFiles.add(translated != null ? translated : img);
            }
        } else if (product.getImageFiles() != null) {
            newImageFiles.addAll(product.getImageFiles());
        }

        List<ProductSKU> alternativeSkus = product.getAlternativeSkus() == null
                                           ? new ArrayList<>()
                                           : new ArrayList<>(product.getAlternativeSkus());

        Product newProduct = Product.builder()
                .title(translatedTitle)
                .summary(translatedSummary)
                .introduction(finalIntroduction)
                .merchandise(product.getMerchandise())
                .waybillProductName(translatedWaybillProductName)
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
                .imageFiles(newImageFiles)
                .language(language)
                .spu(product.getSpu())
                .alternativeSkus(alternativeSkus)
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

    /**
     * 校验当前用户是否有可用 AI 积分。无可用积分则抛 InsufficientCreditsException 触发事务回滚。
     * 不在此处冻结积分：实际冻结由 AiAccountTranslateTask.loadTask 阶段按子任务级精算执行。
     */
    private void requireAvailableCredits() {
        Long userId = SaSessionUtil.getLoginUser().getLongId();
        if (!aiCreditsService.hasAvailableCredits(userId)) {
            throw new cn.v7soft.admin.exception.InsufficientCreditsException("AI额度不足，请充值后重试");
        }
    }

    private String lookupTranslation(Map<String, String> translatedTextMap, String original) {
        if (original == null || original.isBlank()) {
            return original;
        }
        if (translatedTextMap == null) {
            return original;
        }
        String hash = DigestUtil.sha256Hex(original);
        return translatedTextMap.getOrDefault(hash, original);
    }
}
