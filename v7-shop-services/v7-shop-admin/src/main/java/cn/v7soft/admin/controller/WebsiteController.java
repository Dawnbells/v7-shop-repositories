package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.TransferUserRequest;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ISubDomainService;
import cn.v7soft.admin.service.IWebsiteService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.admin.controller.req.EditWebsiteRequest;
import cn.v7soft.admin.controller.req.QueryWebsiteRequest;
import cn.v7soft.common.controller.resp.WebsiteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.Valid;
import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/website")
@Tag(name = "建站系统/网站管理")
public class WebsiteController extends BaseDataRangeController<Website, IWebsiteService, WebsiteResponse, QueryWebsiteRequest, EditWebsiteRequest> {
    private final ICountryService countryService;
    private final ISubDomainService subDomainService;

    protected WebsiteController(IWebsiteService service, ICountryService countryService, ISubDomainService subDomainService) {
        super(service);
        this.countryService = countryService;
        this.subDomainService = subDomainService;
    }

    @Override
    protected QueryPageRequest<Website> convertQueryPageRequest(QueryWebsiteRequest request) {
        return super.convertQueryPageRequest(request)
                .addConstraint(StrUtil.isNotBlank(request.getTitle()), LikeAttribute.builder().name("name").value(request.getTitle()).leftMatch(true).build());
    }

    @Override
    protected WebsiteResponse convertEntity(Website website) {
        WebsiteResponse websiteResponse = WebsiteResponse.convertEntity(website);
        return filling(website, websiteResponse);
    }

    @Override
    protected Website convertRequest(@Nullable Website dbEntity, EditWebsiteRequest request) {
        Website website = Optional.ofNullable(dbEntity).orElse(Website.builder().build());
        BeanUtil.copyProperties(request, website);
        Country country = countryService.getById(request.getCountryId());
        website.setCountry(country);
        website.setLanguages(country.getLanguages().stream().map(language -> Language.builder().id(language.getId()).build()).collect(Collectors.toList()));
        website.setCurrency(country.getCurrency());
        return website;
    }

    @PostMapping("/transfer")
    @Operation(summary = "转移用户")
    @SaCheckPermission("website.transfer")
    public void transferUser(@Valid @RequestBody TransferUserRequest request) {
        service.transferUser(request);
    }

    @Override
    protected String getPermissionPrefix() {
        return "website";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return false;
        }
        // 在删除网站之前，检查是否有子域名或其他依赖
        for (String id : request.getIds().split(",")) {
            Optional<Website> websiteOption = service.findById(Long.valueOf(id));
            if (websiteOption.isEmpty()) {
                continue; // 如果网站不存在，跳过
            }
            subDomainService.deleteAllBindInWebsite(websiteOption.get().getId());
        }
        return true;
    }
}
