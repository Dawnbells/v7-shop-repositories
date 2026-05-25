package cn.v7soft.admin.service.impl;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.Lists;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.CheckSpuTicketRequest;
import cn.v7soft.admin.controller.req.GenerateSharedUrlRequest;
import cn.v7soft.admin.controller.req.ShareSpuRequest;
import cn.v7soft.admin.controller.resp.SharedSpuResponse;
import cn.v7soft.admin.service.IEmployeeService;
import cn.v7soft.admin.service.ISpuService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.AndQueryAttribute;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.OrQueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.exception.ClientException;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.CurrencyExchangeRate;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.LandingPageType;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.dao.repositories.primary.SubDomainRepository;
import cn.v7soft.dao.repositories.primary.SubDomainSpuLandingPageRepository;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SpuService extends BaseDataRangeService<Spu, SpuRepository> implements ISpuService {
    private final static byte[] KEY = Base64.decode("9mQh7zK8f2Q0lT5x6B3vJ1wR2y4uVnD7gF+H8jK0p2Q=");
    private final IEmployeeService employeeService;
    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;
    private final SubDomainRepository subDomainRepository;
    private final SubDomainSpuLandingPageRepository subDomainSpuLandingPageRepository;

    public SpuService(SpuRepository repository, IEmployeeService employeeService, StringRedisTemplate redisTemplate,
                      ProductRepository productRepository, SubDomainRepository subDomainRepository,
                      SubDomainSpuLandingPageRepository subDomainSpuLandingPageRepository) {
        super(repository);
        this.employeeService = employeeService;
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.subDomainRepository = subDomainRepository;
        this.subDomainSpuLandingPageRepository = subDomainSpuLandingPageRepository;
    }

    @Override
    protected void addIgnoreAccessDataRageCondition(OrQueryAttribute<Spu> or) {
        or.add(
                // 部门级别的共享SPU忽略数据权限
                AndQueryAttribute
                        .create(null)
                        .add(new AccessDataRangeAttribute(AccessDataRangeLevel.DEPARTMENT_TREE))
                        .add(EqualsQueryAttribute.builder().name("isOpen").value(Boolean.TRUE).build())
        );
    }

    @Override
    protected void checkKeyConstraint(Spu data) {
//        Spu existingSpu = repository.findBySameName(data.getName(), data.getId());
//        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existingSpu, "SPU名称不允许重复");
    }

    @Override
    @Transactional
    public void bindSpuToWebsite(Long spuId) {
        repository.bindSpuToWebsite(spuId, WebsiteContext.getCurrentWebsiteId());
    }

    @Override
    @Transactional
    public void unbindSpuToWebsite(List<Long> spuIds) {
        repository.unbindSpuToWebsite(spuIds, WebsiteContext.getCurrentWebsiteId());
    }

    @Override
    public synchronized Integer getNextSpuUserCode() {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        return getNextSpuUserCode(loginUser.getDepartmentId());
    }

    @Override
    @Transactional
    public void switchOpen(Long id, boolean isOpen) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        Spu t = getById(id);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(t.getStatus() != StatusEnum.DELETED, "已删除");
        t.setIsOpen(isOpen);
        save(t);
    }

    @Override
    @Transactional
    public SharedSpuResponse shareSpu(ShareSpuRequest request) {
        SystemUser targetOwner = employeeService.findById(Long.valueOf(request.getTargetUserId()))
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("用户不存在：" + request.getTargetUserId()));
        Long departmentId = targetOwner.getDepartment() != null ? targetOwner.getDepartment().getId() : 1L;
        Spu spu = getById(request.getIdLongValue());
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        SystemUser spuOwner = spu.getOwner();

        // 仅限同部门分享
        if (!loginUser.isAdmin() && !loginUser.isDeepDepartmentManager()) {
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(Objects.equals(spuOwner.getDepartment().getId(), targetOwner.getDepartment().getId()), "仅限部门内分享");
        }
        // 检查分享权限，管理员或者当前部门组长或者创建者本人或者部门内公开的才有权限分享
        boolean isAdmin = loginUser.isAdmin();
        boolean isDepartmentManager = loginUser.isDepartmentManager() && Objects.equals(spuOwner.getDepartment().getId(), loginUser.getDepartmentId());
        boolean isDeepDepartmentManager = loginUser.isDeepDepartmentManager() && loginUser.getAccessDepartmentIds().contains(spuOwner.getDepartment().getId());
        boolean isOwner = Objects.equals(loginUser.getLongId(), spuOwner.getId());
        ClientResponseEnum.NO_PERMISSION.assertTrue(isAdmin || isDepartmentManager || isDeepDepartmentManager || isOwner || spu.getIsOpen(), "您无权限操作");

        List<CurrencyExchangeRate> exchangeRates = spu.getExchangeRates().stream()
                .map(item ->
                             CurrencyExchangeRate.builder()
                                     .exchangeRate(item.getExchangeRate())
                                     .currency(item.getCurrency())
                                     .owner(targetOwner)
                                     .build()
                ).collect(Collectors.toList());

        final Spu shareSpu = Spu.builder()
                .code(getNextSpuUserCode(departmentId))
                .name(spu.getName())
                .description(spu.getDescription())
                .useStandardExchangeRate(spu.getUseStandardExchangeRate())
                .exchangeRates(exchangeRates)
                .productCategory(spu.getProductCategory())
                .companyLibrary(spu.getCompanyLibrary())
                .websiteList(Lists.newArrayList())
                .owner(targetOwner)
                .sharedFrom(spu)
                .build();

        List<Product> productList = spu.getProductList().stream().map(item -> {
            List<ProductSKU> alternativeSkus = item.getAlternativeSkus().stream()
                    .map(productSKU ->
                                 ProductSKU.builder()
                                         .id(productSKU.getId())
                                         .skuCode(productSKU.getSkuCode())
                                         .name(productSKU.getName())
                                         .totalUnitsSold(productSKU.getTotalUnitsSold())
                                         .totalSalesRevenue(productSKU.getTotalSalesRevenue())
                                         .isVirtual(productSKU.isVirtual())
                                         .build()
                    ).collect(Collectors.toList());

            List<MultimediaFile> imageFiles = item.getImageFiles().stream()
                    .map(image ->
                                 MultimediaFile.builder().id(image.getId()).build()
                    ).collect(Collectors.toList());

            Product product = Product.builder()
                    .title(item.getTitle())
                    .summary(item.getSummary())
                    .introduction(item.getIntroduction())
                    .merchandise(item.getMerchandise())
                    .waybillProductName(item.getWaybillProductName())
                    .sellPrice(item.getSellPrice())
                    .originPrice(item.getOriginPrice())
                    .costPrice(item.getCostPrice())
                    .isTaxable(item.isTaxable())
                    .taxationMethod(item.getTaxationMethod())
                    .fixedTaxAmount(item.getFixedTaxAmount())
                    .taxAmountThreshold(item.getTaxAmountThreshold())
                    .taxQuantityThreshold(item.getTaxQuantityThreshold())
                    .taxPerBase(item.getTaxPerBase())
                    .barcode(item.getBarcode())
                    .stockQuantity(item.getStockQuantity())
                    .linkStock(item.isLinkStock())
                    .isMultiSpecs(item.isMultiSpecs())
                    .sku(item.getSku())
                    .videoFile(item.getVideoFile())
                    .imageFiles(imageFiles)
                    .language(item.getLanguage())
                    .alternativeSkus(alternativeSkus)
                    .owner(targetOwner)
                    .spu(shareSpu)
                    .botShowSpu(item.getBotShowSpu())
                    .riskUserShowSpu(item.getRiskUserShowSpu())
                    .blacklistedUserShowSpu(item.getBlacklistedUserShowSpu())
                    .country(item.getCountry())
                    .build();
            List<ProductSpecification> productSpecificationList = item.getSpecificationList()
                    .stream()
                    .map(productSpecification -> {
                        ProductSpecification specification = ProductSpecification.builder()
                                .specificationImage(productSpecification.getSpecificationImage())
                                .sellPrice(productSpecification.getSellPrice())
                                .originPrice(productSpecification.getOriginPrice())
                                .costPrice(productSpecification.getCostPrice())
                                .barcode(productSpecification.getBarcode())
                                .stockQuantity(productSpecification.getStockQuantity())
                                .linkStock(productSpecification.isLinkStock())
                                .sku(productSpecification.getSku())
                                .product(product)
                                .owner(targetOwner)
                                .build();
                        List<ProductSpecificationAttributes> attributes = productSpecification.getAttributes().stream()
                                .map(attribute ->
                                             ProductSpecificationAttributes.builder()
                                                     .name(attribute.getName())
                                                     .value(attribute.getValue())
                                                     .multimediaFile(attribute.getMultimediaFile())
                                                     .owner(targetOwner)
                                                     .productSpecification(specification)
                                                     .build()
                                ).collect(Collectors.toList());
                        specification.setAttributes(attributes);
                        return specification;
                    }).collect(Collectors.toList());
            product.setSpecificationList(productSpecificationList);
            return product;
        }).collect(Collectors.toList());
        // 设置产品
        shareSpu.setProductList(productList);
        Spu savedSpu = repository.save(shareSpu);
        return SharedSpuResponse.convert(savedSpu);
    }

    @Override
    @Transactional
    public boolean checkSpuTicket(CheckSpuTicketRequest request) {
        Spu spu = getById(request.getIdLongValue());
        String tokenValue = this.redisTemplate.opsForValue().get(request.getTicket());
        log.debug("token value = " + tokenValue);
        if (StrUtil.isEmpty(tokenValue)) {
            return false;
        }
        String userId = (String) StpUtil.getLoginIdByToken(tokenValue);
        log.debug("userId = " + userId);
        if (userId == null) {
            return false;
        }
        SystemUser systemUser = employeeService.getById(Long.valueOf(userId));
        SystemUserDto systemUserDto = SystemUserDto.convert(systemUser);
        SystemUser owner = spu.getOwner();

        if (systemUserDto.getUserType() == SystemUserType.ADMIN || Objects.equals(systemUserDto.getLongId(), owner.getId())) {
            // 超级管理员或者自己可见
            return true;
        }

        if (Boolean.TRUE.equals(spu.getIsOpen())) {
            // 部门内公开SPU，同部门可预览
            if (Objects.equals(systemUserDto.getDepartmentId(), owner.getDepartment().getId())) {
                return true;
            }
            if (systemUserDto.getParentDepartmentIds().contains(owner.getDepartment().getId())) {
                // 公开商品允许所有下级用户查看
                return true;
            }
            if (systemUserDto.getAccessDepartmentIds().contains(owner.getDepartment().getId()) && systemUserDto.isDeepDepartmentManager()) {
                // 公开商品允许所有上级部门管理员查看
                return true;
            }
        }

        if (!Objects.equals(spu.getCompanyId(), systemUser.getCompanyId())) {
            // 如果不在同一个公司，不可见
            return false;
        }

        if (systemUserDto.getUserType() == SystemUserType.COMPANY_ADMIN) {
            // 公司管理员可见
            return true;
        }
        Long departmentId = owner.getDepartment().getId();
        boolean contains = systemUserDto.getAccessDepartmentIds().contains(departmentId);
        // 部门管理员可见当前部门
        log.debug("systemUserDto = {}", JSONUtil.toJsonStr(systemUserDto));
        log.debug("departmentId = {}, contains = {}, isDepartmentManager = {}", departmentId, contains, systemUserDto.isDepartmentManager());
        return systemUserDto.isDepartmentManager() && contains;
    }

    @Override
    @Transactional
    public void deleteAllSpuRelatedData(Long id) {
        Spu spu = getById(id);
        spu.getWebsiteList().clear();
        spu.getPixelList().clear();
        save(spu);
        List<Product> allProductRelatedFromSpu = productRepository.findAllProductRelatedFromSpu(id);
        if (allProductRelatedFromSpu != null && !allProductRelatedFromSpu.isEmpty()) {
            for (Product product : allProductRelatedFromSpu) {
                if (product.getBotShowSpu() != null && id.equals(product.getBotShowSpu().getId())) {
                    product.setBotShowSpu(null);
                }
                if (product.getRiskUserShowSpu() != null && id.equals(product.getRiskUserShowSpu().getId())) {
                    product.setRiskUserShowSpu(null);
                }
                if (product.getBlacklistedUserShowSpu() != null && id.equals(product.getBlacklistedUserShowSpu().getId())) {
                    product.setBlacklistedUserShowSpu(null);
                }
            }
            productRepository.saveAll(allProductRelatedFromSpu);
        }
    }

    @Override
    public String generateSharedUrl(GenerateSharedUrlRequest request) {
        try {
            // 解析 URL
            URL u = new URL(request.getUrl());
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(
                    "http".equalsIgnoreCase(u.getProtocol()) || "https".equalsIgnoreCase(u.getProtocol()),
                    "请检查落地页链接是否正确: " + u.getProtocol());

            // 提取 domain
            String domain = u.getHost();

            // 提取 product id
            String path = u.getPath(); // 例如 "/product/1767079109125"
            String[] parts = path.split("/");
            String spuId = null;
            if (parts.length == 3 && "product".equals(parts[1])) {
                spuId = parts[2];
            }
            ClientResponseEnum.PARAMETER_ILLEGAL.isLong(spuId, "请检查落地页链接是否正确: " + spuId);
            ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(domain, "请检查落地页链接是否正确: " + domain);
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(request.getExpireSeconds() > 0, "有效期必须大于0");
            assertCanGenerateSharedUrl(domain, Long.valueOf(spuId));
            // 拼接明文
            String plain = domain + "|" + spuId + "|" + (System.currentTimeMillis() + request.getExpireSeconds() * 1000L);
            // AES 加密
            String encrypted = SecureUtil.aes(KEY).encryptHex(plain);
            // URL 编码
            return "https://" + domain + path + "?xyz-sid=" + URLEncoder.encode(encrypted, StandardCharsets.UTF_8);
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("请检查落地页链接是否正确: " + e.getMessage());
        }
        return null;
    }

    private void assertCanGenerateSharedUrl(String domain, Long spuId) {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        SubDomain subDomain = subDomainRepository.findByFullName(domain);
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(subDomain, "请检查落地页链接是否正确: " + domain);
        assertCanAccessSubDomain(loginUser, subDomain);

        boolean bound = subDomainSpuLandingPageRepository.existsBySubDomainIdAndSpuIdAndLandingPageType(
                subDomain.getId(), spuId, LandingPageType.LAND);
        ClientResponseEnum.NO_PERMISSION.assertTrue(bound, "您无权限生成该落地页共享链接");

        Spu spu = getById(spuId);
        ClientResponseEnum.NO_PERMISSION.assertTrue(canAccessSpu(loginUser, spu), "您无权限生成该落地页共享链接");
    }

    private void assertCanAccessSubDomain(SystemUserDto loginUser, SubDomain subDomain) {
        if (loginUser.isAdmin()) {
            return;
        }
        TopLevelDomain parentDomain = subDomain.getParentDomain();
        SystemUser owner = parentDomain == null ? null : parentDomain.getOwner();
        ClientResponseEnum.NO_PERMISSION.notNull(owner, "您无权限生成该落地页共享链接");
        Long ownerDepartmentId = owner.getDepartment() == null ? null : owner.getDepartment().getId();
        ClientResponseEnum.NO_PERMISSION.assertTrue(
                loginUser.hasManagerPermission(owner.getId(), ownerDepartmentId),
                "您无权限生成该落地页共享链接");
    }

    private boolean canAccessSpu(SystemUserDto loginUser, Spu spu) {
        SystemUser owner = spu.getOwner();
        if (owner == null) {
            return false;
        }
        if (loginUser.getUserType() == SystemUserType.ADMIN || Objects.equals(loginUser.getLongId(), owner.getId())) {
            return true;
        }

        Long ownerDepartmentId = owner.getDepartment() == null ? null : owner.getDepartment().getId();
        if (Boolean.TRUE.equals(spu.getIsOpen())) {
            if (Objects.equals(loginUser.getDepartmentId(), ownerDepartmentId)) {
                return true;
            }
            if (loginUser.getParentDepartmentIds() != null && loginUser.getParentDepartmentIds().contains(ownerDepartmentId)) {
                return true;
            }
            if (loginUser.isDeepDepartmentManager()
                    && loginUser.getAccessDepartmentIds() != null
                    && loginUser.getAccessDepartmentIds().contains(ownerDepartmentId)) {
                return true;
            }
        }

        if (!Objects.equals(spu.getCompanyId(), loginUser.getCompanyId())) {
            return false;
        }
        if (loginUser.getUserType() == SystemUserType.COMPANY_ADMIN) {
            return true;
        }
        return loginUser.isDepartmentManager()
                && loginUser.getAccessDepartmentIds() != null
                && loginUser.getAccessDepartmentIds().contains(ownerDepartmentId);
    }

    private synchronized Integer getNextSpuUserCode(Long departmentId) {
        Integer maxSpuUserCode = repository.getMaxSpuUserCode(departmentId);
        if (maxSpuUserCode == null || maxSpuUserCode <= 1000000) {
            return 1000001;
        }
        return maxSpuUserCode + 1;
    }

}
