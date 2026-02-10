package cn.v7soft.admin.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.enums.LandingPageType;
import jakarta.validation.Valid;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.BindLandingPageProtocolRequest;
import cn.v7soft.admin.controller.req.BindLandingPageSpuRequest;
import cn.v7soft.admin.controller.req.BindPixelsRequest;
import cn.v7soft.admin.controller.req.BindSpuPixelRequest;
import cn.v7soft.admin.controller.req.EditSubDomainRequest;
import cn.v7soft.admin.controller.req.UnbindLandingPageSpuRequest;
import cn.v7soft.admin.controller.req.QuerySubDomainRequest;
import cn.v7soft.admin.controller.resp.SubDomainResponse;
import cn.v7soft.admin.controller.resp.SubDomainSpuDetailResponse;
import cn.v7soft.admin.controller.resp.SubDomainSpuResponse;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ISubDomainService;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Validated
@RestController
@RequestMapping("/sub-domain")
@Tag(name = "域名管理/二级域名管理")
public class SubDomainController extends BaseController<SubDomain, ISubDomainService, SubDomainResponse, QuerySubDomainRequest, EditSubDomainRequest> {

    private final ITopLevelDomainService topLevelDomainService;
    private final ICountryService countryService;

    protected SubDomainController(ISubDomainService service, ITopLevelDomainService topLevelDomainService, ICountryService countryService) {
        super(service);
        this.topLevelDomainService = topLevelDomainService;
        this.countryService = countryService;
    }

    @Override
    protected SubDomainResponse convertEntity(SubDomain subDomain) {
        SubDomainResponse subDomainResponse = SubDomainResponse.convertEntity(subDomain);
        return filling(subDomain, subDomainResponse);
    }

    @Override
    protected QueryPageRequest<SubDomain> convertQueryPageRequest(QuerySubDomainRequest request) {
        return super.convertQueryPageRequest(request).addConstraint(true, new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.equal(root.get("parentDomain").get("id"), request.getParentId());
            }
        });
    }

    @Override
    protected SubDomain convertRequest(@Nullable SubDomain dbEntity, EditSubDomainRequest request) {
        SubDomain subDomain = Optional.ofNullable(dbEntity).orElse(SubDomain.builder().build());
        BeanUtil.copyProperties(request, subDomain);
        TopLevelDomain parentDomain = topLevelDomainService.getById(request.getParentDomainId());
        subDomain.setParentDomain(parentDomain);
        subDomain.setType(parentDomain.getType());
        if ("@".equals(request.getName())) {
            subDomain.setFullName(parentDomain.getName());
        } else {
            subDomain.setFullName(request.getName() + "." + parentDomain.getName());
        }
        // 绑定国家及其关联的货币和语言
        if (request.getCountryId() != null) {
            Country country = countryService.getById(request.getCountryId());
            subDomain.setCountry(country);
            subDomain.setCurrency(country.getCurrency());
            // 取国家的第一个语言作为默认语言
            if (country.getLanguages() != null && !country.getLanguages().isEmpty()) {
                subDomain.setLanguage(country.getLanguages().get(0));
            } else {
                subDomain.setLanguage(null);
            }
        } else {
            subDomain.setCountry(null);
            subDomain.setCurrency(null);
            subDomain.setLanguage(null);
        }
        return subDomain;
    }

    @Override
    protected String getPermissionPrefix() {
        return "subDomain";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return true;
    }

    @PostMapping("/bindTheme/{id}/{themeId}")
    @Operation(summary = "二级域名绑定自定义主题")
    public void bindTheme(@PathVariable("id") Long id, @PathVariable("themeId") Long themeId) {
        service.bindTheme(id, themeId);
    }

    @PostMapping("/bindPixels")
    @Operation(summary = "绑定像素账号")
    public void bindPixels(@Valid @RequestBody BindPixelsRequest request) {
        service.bindPixels(request);
    }

    @PostMapping("/bindSpu/{subDomainId}/{spuId}")
    @Operation(summary = "绑定SPU到二级域名")
    public void bindSpu(@PathVariable("subDomainId") Long subDomainId, @PathVariable("spuId") Long spuId) {
        service.bindSpu(subDomainId, spuId);
    }

    @PostMapping("/unbindSpu/{subDomainId}/{spuId}")
    @Operation(summary = "解绑SPU与二级域名")
    public void unbindSpu(@PathVariable("subDomainId") Long subDomainId, @PathVariable("spuId") Long spuId) {
        service.unbindSpu(subDomainId, spuId);
    }

    @GetMapping("/getBoundSpus/{subDomainId}")
    @Operation(summary = "获取二级域名绑定的SPU列表")
    public List<SubDomainSpuResponse> getBoundSpus(
            @PathVariable("subDomainId") Long subDomainId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        SubDomain subDomain = service.getById(subDomainId);
        Long countryId = subDomain.getCountry() != null ? subDomain.getCountry().getId() : null;

        return service.getBoundSpus(subDomainId, keyword).stream()
                .map(spu -> {
                    SubDomainSpuResponse response = SubDomainSpuResponse.convertEntity(spu);
                    // 检查 SPU 的 productList 是否包含该国家的 Product
                    boolean supportCountry = countryId != null && spu.getProductList() != null
                            && spu.getProductList().stream()
                            .anyMatch(p -> p.getCountry() != null && countryId.equals(p.getCountry().getId()));
                    response.setSupportCurrentCountry(supportCountry);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/getBoundSpuDetail/{subDomainId}/{spuId}")
    @Operation(summary = "获取子域名绑定的SPU详情")
    public SubDomainSpuDetailResponse getBoundSpuDetail(
            @PathVariable("subDomainId") Long subDomainId,
            @PathVariable("spuId") Long spuId) {
        return service.getBoundSpuDetail(subDomainId, spuId);
    }

    @PostMapping("/bindSpuPixel")
    @Operation(summary = "绑定像素到子域名SPU")
    public void bindSpuPixel(@Valid @RequestBody BindSpuPixelRequest request) {
        service.bindSpuPixel(request.getSubDomainId(), request.getSpuId(), request.getPixelId());
    }

    @PostMapping("/unbindSpuPixel")
    @Operation(summary = "解绑像素与子域名SPU")
    public void unbindSpuPixel(@Valid @RequestBody BindSpuPixelRequest request) {
        service.unbindSpuPixel(request.getSubDomainId(), request.getSpuId(), request.getPixelId());
    }

    @PostMapping("/bindLandingPageSpu")
    @Operation(summary = "绑定落地页SPU到子域名SPU（个性化配置）")
    public void bindLandingPageSpu(@Valid @RequestBody BindLandingPageSpuRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notEquals(request.getLandingPageType(), LandingPageType.LAND, "不支持修改真实落地页");
        service.bindLandingPageSpu(request.getSubDomainId(), request.getSpuId(),
                request.getLandingPageSpuId(), request.getLandingPageType());
    }

    @PostMapping("/unbindLandingPageSpu")
    @Operation(summary = "解绑落地页SPU（删除个性化配置，使用默认配置）")
    public void unbindLandingPageSpu(@Valid @RequestBody UnbindLandingPageSpuRequest request) {
        service.unbindLandingPageSpu(request.getSubDomainId(), request.getSpuId(), request.getLandingPageType());
    }

    @PostMapping("/bindLandingPageProtocol")
    @Operation(summary = "绑定协议到落地页")
    public void bindLandingPageProtocol(@Valid @RequestBody BindLandingPageProtocolRequest request) {
        service.bindLandingPageProtocol(request);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<SubDomainResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<SubDomain> request = QueryPageRequest.fromRequest(QuerySubDomainRequest.builder().pageNo(1).build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(LikeAttribute.builder().name("name").value(query.trim()).build())
                    .add(LikeAttribute.builder().name("fullName").value(query.trim()).build())
                    .next()
                    .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }
        return service.findPaginated(request)
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }
}
