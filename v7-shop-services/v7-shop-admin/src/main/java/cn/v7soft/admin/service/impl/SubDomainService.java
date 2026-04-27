package cn.v7soft.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.controller.req.BindPixelsRequest;
import cn.v7soft.admin.controller.req.CreateAndBindSpuPixelRequest;
import cn.v7soft.admin.controller.req.SaveAdConfigRequest;
import cn.v7soft.admin.controller.resp.PixelSimpleResponse;
import cn.v7soft.admin.controller.resp.SpuSimpleWithCountryResponse;
import cn.v7soft.admin.controller.resp.SubDomainSpuDetailResponse;
import cn.v7soft.admin.controller.resp.ThemeSimpleResponse;
import cn.v7soft.admin.service.ICloudPlatformAccountService;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ISpuService;
import cn.v7soft.admin.service.ISubDomainService;
import cn.v7soft.admin.service.IThemeCustomService;
import cn.v7soft.admin.service.IWebsiteService;
import cn.v7soft.admin.service.dto.SubDomainDto;
import cn.v7soft.admin.service.ssl.ISslCertificateRequester;
import cn.v7soft.admin.utils.NginxConfigWriter;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.Protocol;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.SubDomainSpuLandingPage;
import cn.v7soft.dao.entities.primary.SubDomainSpuLandingPageId;
import cn.v7soft.dao.entities.primary.SubDomainSpuPixel;
import cn.v7soft.dao.entities.primary.SubDomainSpuPixelId;
import cn.v7soft.dao.entities.primary.ThemeCustom;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.enums.CloakStrategy;
import cn.v7soft.dao.enums.DomainType;
import cn.v7soft.dao.enums.LandingPageType;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelAccountState;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.PixelAccountRepository;
import cn.v7soft.dao.repositories.primary.SubDomainRepository;
import cn.v7soft.dao.repositories.primary.SubDomainSpuLandingPageRepository;
import cn.v7soft.dao.repositories.primary.SubDomainSpuPixelRepository;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.properties.ThemeEditorProperty;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubDomainService extends BaseService<SubDomain, SubDomainRepository> implements ISubDomainService {

    private final IThemeCustomService themeCustomService;
    private final IWebsiteService websiteService;
    private final IFrontServerService frontServerService;
    private final ICloudPlatformAccountService cloudPlatformAccountService;
    private final SubDomainSpuPixelRepository subDomainSpuPixelRepository;
    private final SubDomainSpuLandingPageRepository subDomainSpuLandingPageRepository;
    private final ISpuService spuService;
    private final ThemeEditorProperty themeEditorProperty;
    private final ProductRepository productRepository;
    private final PixelAccountRepository pixelAccountRepository;
    private SubDomainService subDomainService;

    public SubDomainService(SubDomainRepository repository, IThemeCustomService themeCustomService, IWebsiteService websiteService, IFrontServerService frontServerService, ICloudPlatformAccountService cloudPlatformAccountService, SubDomainSpuPixelRepository subDomainSpuPixelRepository, SubDomainSpuLandingPageRepository subDomainSpuLandingPageRepository, ISpuService spuService, ThemeEditorProperty themeEditorProperty, ProductRepository productRepository, PixelAccountRepository pixelAccountRepository) {
        super(repository);
        this.themeCustomService = themeCustomService;
        this.websiteService = websiteService;
        this.frontServerService = frontServerService;
        this.cloudPlatformAccountService = cloudPlatformAccountService;
        this.subDomainSpuPixelRepository = subDomainSpuPixelRepository;
        this.subDomainSpuLandingPageRepository = subDomainSpuLandingPageRepository;
        this.spuService = spuService;
        this.themeEditorProperty = themeEditorProperty;
        this.productRepository = productRepository;
        this.pixelAccountRepository = pixelAccountRepository;
    }

    @Override
    protected void checkKeyConstraint(SubDomain data) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        SubDomain existingSubDomain = repository.findBySameName(data.getFullName(), data.getId(), user.getLongId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existingSubDomain, "域名名称不允许重复");
    }

    @Override
    public List<SubDomain> queryDomainsByKeyword(String keyword) {
        return repository.findAll((Specification<SubDomain>) (root, query, criteriaBuilder) -> {
            Predicate websitePredicate = criteriaBuilder.isNull(root.get("website"));
            SystemUserDto loginUser = SaSessionUtil.getLoginUser();
            Predicate dataRangePredicate = criteriaBuilder.conjunction();
            if (loginUser.isDepartmentManager()) {
                dataRangePredicate = criteriaBuilder.equal(root.get("parentDomain").get("owner").get("department").get("id"), loginUser.getDepartmentId());
            } else if (!loginUser.isAdmin()) {
                dataRangePredicate = criteriaBuilder.equal(root.get("parentDomain").get("owner").get("id"), loginUser.getLongId());
            }
            if (keyword == null || keyword.isEmpty()) {
                return criteriaBuilder.and(dataRangePredicate, websitePredicate);  // 返回一个总为真的断言，表示不筛选任何条件
            }
            return criteriaBuilder.and(
                    dataRangePredicate,
                    websitePredicate,
                    criteriaBuilder.like(root.get("fullName"), "%" + keyword + "%"),
                    criteriaBuilder.equal(root.get("type"), DomainType.WEBSITE));
        });
    }

    @Override
    public List<SubDomain> queryRelayDomainsByKeyword(String keyword) {
        return repository.findAll((Specification<SubDomain>) (root, query, criteriaBuilder) -> {
            Predicate typePredicate = criteriaBuilder.equal(root.get("type"), DomainType.RELAY);
            Predicate frontServerPredicate = criteriaBuilder.isNull(root.get("frontServer"));
            Predicate andPredicate = criteriaBuilder.and(typePredicate, frontServerPredicate);
            if (keyword == null || keyword.isEmpty()) {
                return andPredicate;  // 返回一个总为真的断言，表示不筛选任何条件
            }
            return criteriaBuilder.and(
                    andPredicate,
                    criteriaBuilder.like(root.get("fullName"), "%" + keyword + "%"),
                    criteriaBuilder.equal(root.get("type"), DomainType.RELAY));
        });
    }

    @Override
    public void doCreate(Long id) {
        SubDomain subDomain = getById(id);

        String websiteId = subDomain.getWebsite() == null ? "" : subDomain.getWebsite().getId().toString();
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(subDomain.getWebsite(), "该域名已绑定其他商城: " + websiteId);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(subDomain.getType() == DomainType.WEBSITE, "域名用途不正确: " + subDomain.getType());
        TopLevelDomain topLevelDomain = subDomain.getParentDomain();

        // 检查域名证书是否正常
        SslCertificateUtil.valid(topLevelDomain);
        LocalDateTime expiryDate = SslCertificateUtil.getExpiryDate(topLevelDomain);
        ServiceResponseEnum.ERR_NO_SSL.assertTrue(expiryDate != null && expiryDate.isAfter(LocalDateTime.now()));

        Long currentWebsiteId = WebsiteContext.getCurrentWebsiteId();
        Website website = websiteService.getById(currentWebsiteId);
        FrontServer frontServer = website.getCountry().getFrontServer();
        subDomain.setFrontServer(frontServer);
        subDomain.setWebsite(website);

        String name = frontServer.getName();

        // 添加解析
        CloudPlatformAccount cloudPlatformAccount = topLevelDomain.getCloudPlatformAccount();
        if (cloudPlatformAccount != null) {
            ISslCertificateRequester certificateRequester = cloudPlatformAccountService.getCertificateRequester(cloudPlatformAccount);
            boolean analyzeDomain = certificateRequester.analyzeDomain(topLevelDomain, subDomain.getName(), frontServer.getCnameRecord());
            subDomain.setAnalyzeSuccess(analyzeDomain);

            // 统计计数
            frontServer.setResolutionCount(frontServer.getResolutionCount() + 1);
            frontServer.setActiveResolutionCount(frontServer.getActiveResolutionCount() + 1);
            frontServerService.save(frontServer);
        } else {
            subDomain.setAnalyzeSuccess(false);
        }
        // 写入域名nginx配置
        boolean writeNginx = NginxConfigWriter.writeNginx(name, topLevelDomain.getName(), topLevelDomain.getNginxConfigType(), String.valueOf(topLevelDomain.getCompanyId()));
        if (writeNginx) {
            frontServerService.pushAndRefresh(frontServer.getId());
        }
        subDomainService.save(subDomain);
    }

    @Override
    @Transactional
    public void setupNginxForNuxtMall(SubDomain subDomain) {
        TopLevelDomain topLevelDomain = subDomain.getParentDomain();
        Country country = subDomain.getCountry();
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(country, "子域名未绑定国家");
        FrontServer frontServer = country.getFrontServer();
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(frontServer, "该国家未配置前端服务器");

        subDomain.setFrontServer(frontServer);

        CloudPlatformAccount cloudPlatformAccount = topLevelDomain.getCloudPlatformAccount();
        if (cloudPlatformAccount != null) {
            ISslCertificateRequester certificateRequester = cloudPlatformAccountService.getCertificateRequester(cloudPlatformAccount);
            boolean analyzeDomain = certificateRequester.analyzeDomain(topLevelDomain, subDomain.getName(), frontServer.getCnameRecord());
            subDomain.setAnalyzeSuccess(analyzeDomain);

            frontServer.setResolutionCount(frontServer.getResolutionCount() + 1);
            frontServer.setActiveResolutionCount(frontServer.getActiveResolutionCount() + 1);
            frontServerService.save(frontServer);
        } else {
            subDomain.setAnalyzeSuccess(false);
        }

        boolean writeNginx = NginxConfigWriter.writeNginx(
                frontServer.getName(), topLevelDomain.getName(),
                topLevelDomain.getNginxConfigType(), String.valueOf(topLevelDomain.getCompanyId()));
        if (writeNginx) {
            frontServerService.pushAndRefresh(frontServer.getId());
        }
        subDomainService.save(subDomain);
    }

    @Override
    @Transactional
    public void deleteAll(List<Long> ids) {
        for (Long id : ids) {
            doDelete(getById(id));
        }
    }

    @Override
    @Transactional
    public void doDeleteAll(List<Long> ids) {
        for (Long id : ids) {
            doDelete(getById(id));
        }
    }

    @Transactional
    public void doDelete(SubDomain subDomain) {
        TopLevelDomain parentDomain = subDomain.getParentDomain();
        FrontServer frontServer;
        try {
            frontServer = subDomain.getFrontServer();
            if (frontServer != null) {
                frontServer.setActiveResolutionCount(frontServer.getActiveResolutionCount() - 1);
                frontServerService.save(frontServer);
            }
        } catch (Exception ignored) {
            frontServer = null;
        }
        subDomain.setAnalyzeSuccess(false);
        subDomain.setFrontServer(null);
        subDomain.setWebsite(null);
        subDomain.setStatus(StatusEnum.DELETED);
        repository.saveAndFlush(subDomain);

        if (frontServer != null) {
            int count = repository.countTopLevelDomainInSameServer(parentDomain.getId(), frontServer.getId());
            log.info("删除子域名后检查nginx: subDomainId={}, parentDomain={}, frontServer={}, remainingCount={}",
                    subDomain.getId(), parentDomain.getName(), frontServer.getName(), count);
            if (count <= 0) {
                log.info("该顶级域名在服务器上无其他活跃子域名，删除nginx配置: domain={}, server={}",
                        parentDomain.getName(), frontServer.getName());
                NginxConfigWriter.deleteNginx(frontServer.getName(), parentDomain.getName());
                frontServerService.pushAndRefresh(frontServer.getId());
            }
        }
    }

    @Override
    @Transactional
    public void deleteAllBindInWebsite(Long id) {
        List<SubDomain> subDomains = repository.findAllByWebsite(id);
        for (SubDomain subDomain : subDomains) {
            doDelete(subDomain);
        }
    }

    @Lazy
    @Autowired
    public void setSubDomainService(SubDomainService subDomainService) {
        this.subDomainService = subDomainService;
    }

    @Override
    @Transactional
    public Optional<SubDomainDto> findRelayDomainByFullName(String cnameRecord) {
        return repository.findByFullNameAndType(cnameRecord, DomainType.RELAY).map(subDomain -> SubDomainDto.builder()
                .subDomain(subDomain)
                .topLevelDomain(subDomain.getParentDomain())
                .cloudPlatformAccount(subDomain.getParentDomain().getCloudPlatformAccount())
                .build());
    }

    @Override
    @Transactional
    public void bindTheme(Long id, Long themeId) {
        ThemeCustom themeCustom = themeCustomService.getById(themeId);
        SubDomain subDomain = getById(id);
        subDomain.setTheme(themeCustom);
        save(subDomain);
    }

    @Override
    @Transactional
    public void bindPixels(BindPixelsRequest request) {
        SubDomain subDomain = getById(request.getIdLongValue());
        if (request.getPixelIds() == null || request.getPixelIds().isEmpty()) {
            subDomain.setPixelAccounts(null);
        } else {
            List<PixelAccount> pixelAccounts = request.getPixelIds().stream()
                    .map(id -> PixelAccount.builder().id(Long.valueOf(id)).build())
                    .collect(Collectors.toList());
            subDomain.setPixelAccounts(pixelAccounts);
        }
        save(subDomain);
    }


    @Override
    @Transactional
    public void clearDomainThemes(List<Long> themeIds) {
        if (themeIds == null || themeIds.isEmpty()) {
            return;
        }
        repository.clearDomainThemes(themeIds);
    }

    @Override
    @Transactional
    public Spu bindSpu(Long subDomainId, Long spuId) {
        // 检查是否已经绑定（通过 LAND 类型判断）
        boolean alreadyBound = subDomainSpuLandingPageRepository
                .existsBySubDomainIdAndSpuIdAndLandingPageType(subDomainId, spuId, LandingPageType.LAND);

        if (!alreadyBound) {
            LocalDateTime now = LocalDateTime.now();
            // 为每种 LandingPageType 创建记录
            for (LandingPageType type : LandingPageType.values()) {
                SubDomainSpuLandingPage binding = SubDomainSpuLandingPage.builder()
                        .subDomainId(subDomainId)
                        .spuId(spuId)
                        .landingPageType(type)
                        .landingSpuId(type == LandingPageType.LAND ? spuId : null)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                subDomainSpuLandingPageRepository.save(binding);
            }
        }
        // 返回绑定的SPU实体（包含productList用于判断是否支持当前国家）
        return spuService.getById(spuId);
    }

    @Override
    @Transactional
    public void unbindSpu(Long subDomainId, Long spuId) {
        // 删除所有落地页类型的配置记录
        subDomainSpuLandingPageRepository.deleteBySubDomainIdAndSpuId(subDomainId, spuId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Spu> getBoundSpus(Long subDomainId, String keyword) {
        // 通过 LAND 类型查询绑定的 SPU
        // 数据库层面限制100条，避免大数据量时全量加载到内存
        // 预加载productList和country，用于判断SPU是否支持子域名绑定的国家
        return subDomainSpuLandingPageRepository.findBoundSpusBySubDomainIdWithKeyword(subDomainId, keyword, PageRequest.of(0, 100));
    }

    @Override
    @Transactional(readOnly = true)
    public SubDomainSpuDetailResponse getBoundSpuDetail(Long subDomainId, Long spuId) {
        SubDomain subDomain = getById(subDomainId);
        Spu spu = spuService.getById(spuId);
        Long countryId = subDomain.getCountry() != null ? subDomain.getCountry().getId() : null;

        // 查询落地页配置
        List<SubDomainSpuLandingPage> landingPageBindings = subDomainSpuLandingPageRepository.findBySubDomainIdAndSpuId(subDomainId, spuId);

        // 构建落地页类型到配置的映射
        java.util.Map<LandingPageType, SubDomainSpuLandingPage> landingPageMap = new java.util.HashMap<>();
        for (SubDomainSpuLandingPage lp : landingPageBindings) {
            landingPageMap.put(lp.getLandingPageType(), lp);
        }

        // 检查SPU是否支持当前国家
        boolean spuSupportsCountry = checkSpuSupportsCountry(spu, countryId);

        // 构建真实落地页SPU（始终使用当前SPU）
        SpuSimpleWithCountryResponse realLandingPageSpu = SpuSimpleWithCountryResponse.convertEntity(spu, spuSupportsCountry);

        // 构建风险用户落地页SPU（从 landingSpuId 获取）
        SpuSimpleWithCountryResponse riskUserLandingPageSpu = null;
        SubDomainSpuLandingPage cloakLandingPage = landingPageMap.get(LandingPageType.CLOAK);
        if (cloakLandingPage != null && cloakLandingPage.getLandingSpuId() != null) {
            Spu cloakSpu = spuService.getById(cloakLandingPage.getLandingSpuId());
            if (cloakSpu != null) {
                boolean cloakSupportsCountry = checkSpuSupportsCountry(cloakSpu, countryId);
                riskUserLandingPageSpu = SpuSimpleWithCountryResponse.convertEntity(cloakSpu, cloakSupportsCountry);
            }
        }

        // 构建黑名单落地页SPU（从 landingSpuId 获取）
        SpuSimpleWithCountryResponse blacklistLandingPageSpu = null;
        SubDomainSpuLandingPage blacklistLandingPage = landingPageMap.get(LandingPageType.BLACKLISTED);
        if (blacklistLandingPage != null && blacklistLandingPage.getLandingSpuId() != null) {
            Spu blacklistSpu = spuService.getById(blacklistLandingPage.getLandingSpuId());
            if (blacklistSpu != null) {
                boolean blacklistSupportsCountry = checkSpuSupportsCountry(blacklistSpu, countryId);
                blacklistLandingPageSpu = SpuSimpleWithCountryResponse.convertEntity(blacklistSpu, blacklistSupportsCountry);
            }
        }

        // 获取主题信息
        ThemeSimpleResponse theme = ThemeSimpleResponse.convertEntity(subDomain.getTheme());

        // 获取像素列表
        List<SubDomainSpuPixel> pixelBindings = subDomainSpuPixelRepository.findBySubDomainIdAndSpuId(subDomainId, spuId);
        List<PixelSimpleResponse> pixels = pixelBindings.stream()
                .map(binding -> PixelSimpleResponse.convertEntity(binding.getPixelAccount()))
                .collect(Collectors.toList());

        // 构建主题编辑器访问地址
        String themeEditorUrl;
        String activeProfile = cn.v7soft.admin.utils.EnvironmentHelper.getProperty("spring.profiles.active");
        if ("dev".equals(activeProfile)) {
            themeEditorUrl = themeEditorProperty.getDevUrl() + "?subDomainId=" + subDomainId + "&spuId=" + spuId;
        } else {
            String companyDomain = cn.v7soft.dao.tenant.TenantContext.getCurrentTenantEntity().getDomain();
            themeEditorUrl = "https://theme." + companyDomain + "?subDomainId=" + subDomainId + "&spuId=" + spuId;
        }

        // 构建各落地页类型的协议信息
        java.util.Map<LandingPageType, SubDomainSpuDetailResponse.ProtocolInfo> protocolInfoMap = new java.util.HashMap<>();
        for (SubDomainSpuLandingPage lp : landingPageBindings) {
            if (lp.getProtocol() != null) {
                Protocol proto = lp.getProtocol();
                protocolInfoMap.put(lp.getLandingPageType(), SubDomainSpuDetailResponse.ProtocolInfo.builder()
                        .protocolId(proto.getId())
                        .protocolName(proto.getName())
                        .placeholderValues(lp.getProtocolPlaceholderValues())
                        .build());
            }
        }

        SubDomainSpuLandingPage landLandingPage = landingPageMap.get(LandingPageType.LAND);

        return SubDomainSpuDetailResponse.builder()
                .realLandingPageSpu(realLandingPageSpu)
                .riskUserLandingPageSpu(riskUserLandingPageSpu)
                .blacklistLandingPageSpu(blacklistLandingPageSpu)
                .theme(theme)
                .pixels(pixels)
                .themeEditorUrl(themeEditorUrl)
                .realLandingPageProtocol(protocolInfoMap.get(LandingPageType.LAND))
                .riskUserLandingPageProtocol(protocolInfoMap.get(LandingPageType.CLOAK))
                .blacklistLandingPageProtocol(protocolInfoMap.get(LandingPageType.BLACKLISTED))
                .adPlatform(landLandingPage != null && landLandingPage.getAdPlatform() != null ? landLandingPage.getAdPlatform().name() : null)
                .medium(landLandingPage != null ? landLandingPage.getMedium() : null)
                .cloakStrategy(landLandingPage != null && landLandingPage.getCloakStrategy() != null ? landLandingPage.getCloakStrategy().name() : null)
                .campaign(landLandingPage != null ? landLandingPage.getCampaign() : null)
                .campaignDate(landLandingPage != null ? landLandingPage.getCampaignDate() : null)
                .build();
    }

    /**
     * 检查SPU是否支持指定国家
     */
    private boolean checkSpuSupportsCountry(Spu spu, Long countryId) {
        if (spu == null || countryId == null || spu.getProductList() == null) {
            return false;
        }
        return spu.getProductList().stream()
                .anyMatch(p -> p.getCountry() != null && countryId.equals(p.getCountry().getId()));
    }

    @Override
    @Transactional
    public void bindSpuPixel(Long subDomainId, Long spuId, Long pixelId) {
        // 检查是否已存在绑定
        if (subDomainSpuPixelRepository.existsBySubDomainIdAndSpuIdAndPixelId(subDomainId, spuId, pixelId)) {
            return;
        }
        SubDomainSpuPixel binding = SubDomainSpuPixel.builder()
                .subDomainId(subDomainId)
                .spuId(spuId)
                .pixelId(pixelId)
                .build();
        subDomainSpuPixelRepository.save(binding);
    }

    @Override
    @Transactional
    public PixelSimpleResponse createAndBindSpuPixel(CreateAndBindSpuPixelRequest request) {
        if (request.getPlatform() == PixelAccountPlatform.GOOGLE) {
            ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getAccessToken(), "请输入 Google Ads 转化标签");
        }
        PixelAccount pixelAccount = PixelAccount.builder()
                .pixelName(request.getPixelName())
                .pixelId(request.getPixelId())
                .accessToken(request.getAccessToken() == null ? "" : request.getAccessToken())
                .platform(request.getPlatform())
                .state(PixelAccountState.WAIT_VALID)
                .trackingType(request.getTrackingType())
                .conversionEvent(request.getConversionEvent())
                .embedCode(request.getEmbedCode())
                .website(WebsiteContext.isWebsiteAdmin() ? WebsiteContext.getCurrentWebsite() : null)
                .build();
        pixelAccount = pixelAccountRepository.save(pixelAccount);
        bindSpuPixel(request.getSubDomainId(), request.getSpuId(), pixelAccount.getId());
        return PixelSimpleResponse.convertEntity(pixelAccount);
    }

    @Override
    @Transactional
    public void unbindSpuPixel(Long subDomainId, Long spuId, Long pixelId) {
        subDomainSpuPixelRepository.deleteById(new SubDomainSpuPixelId(subDomainId, spuId, pixelId));
    }

    @Override
    @Transactional
    public void bindLandingPageSpu(Long subDomainId, Long spuId, Long landingSpuId, LandingPageType landingPageType) {
        SubDomainSpuLandingPageId id = new SubDomainSpuLandingPageId(subDomainId, spuId, landingPageType);
        SubDomainSpuLandingPage landingPage = subDomainSpuLandingPageRepository.findById(id)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("落地页配置不存在"));

        landingPage.setLandingSpuId(landingSpuId);
        landingPage.setUpdatedAt(LocalDateTime.now());
        subDomainSpuLandingPageRepository.save(landingPage);
    }

    @Override
    @Transactional
    public void unbindLandingPageSpu(Long subDomainId, Long spuId, LandingPageType landingPageType) {
        SubDomainSpuLandingPageId id = new SubDomainSpuLandingPageId(subDomainId, spuId, landingPageType);
        SubDomainSpuLandingPage landingPage = subDomainSpuLandingPageRepository.findById(id)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("落地页配置不存在"));

        landingPage.setLandingSpuId(null);
        landingPage.setUpdatedAt(LocalDateTime.now());
        subDomainSpuLandingPageRepository.save(landingPage);
    }

    @Override
    @Transactional
    public void bindLandingPageProtocol(cn.v7soft.admin.controller.req.BindLandingPageProtocolRequest request) {
        SubDomainSpuLandingPageId id = new SubDomainSpuLandingPageId(
                request.getSubDomainId(), request.getSpuId(), request.getLandingPageType());
        SubDomainSpuLandingPage landingPage = subDomainSpuLandingPageRepository.findById(id)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("落地页配置不存在"));

        if (cn.hutool.core.util.StrUtil.isBlank(request.getProtocolId())) {
            landingPage.setProtocol(null);
            landingPage.setProtocolPlaceholderValues(null);
        } else {
            landingPage.setProtocol(Protocol.builder().id(Long.valueOf(request.getProtocolId())).build());
            landingPage.setProtocolPlaceholderValues(request.getPlaceholderValues());
        }
        landingPage.setUpdatedAt(LocalDateTime.now());
        subDomainSpuLandingPageRepository.save(landingPage);
    }

    @Override
    @Transactional
    public void saveAdConfig(SaveAdConfigRequest request) {
        SubDomainSpuLandingPageId id = new SubDomainSpuLandingPageId(
                request.getSubDomainId(), request.getSpuId(), LandingPageType.LAND);
        SubDomainSpuLandingPage landingPage = subDomainSpuLandingPageRepository.findById(id)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("SPU绑定记录不存在"));

        landingPage.setAdPlatform(request.getAdPlatform() != null ? PixelAccountPlatform.valueOf(request.getAdPlatform()) : null);
        landingPage.setMedium(request.getMedium());
        landingPage.setCloakStrategy(request.getCloakStrategy() != null ? CloakStrategy.valueOf(request.getCloakStrategy()) : null);
        landingPage.setCampaign(request.getCampaign());
        if (request.getCampaign() != null && !request.getCampaign().isBlank()) {
            landingPage.setCampaignDate(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
        } else {
            landingPage.setCampaignDate(null);
        }
        landingPage.setUpdatedAt(LocalDateTime.now());
        subDomainSpuLandingPageRepository.save(landingPage);
    }
}
