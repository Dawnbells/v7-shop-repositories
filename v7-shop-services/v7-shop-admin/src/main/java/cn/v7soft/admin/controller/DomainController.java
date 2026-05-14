package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.admin.controller.req.CreateWebsiteSubDomainRequest;
import cn.v7soft.admin.controller.req.QueryDomainsByKeywordRequest;
import cn.v7soft.admin.controller.req.QueryWebsiteDomainRequest;
import cn.v7soft.admin.controller.resp.SubDomainResponse;
import cn.v7soft.admin.service.ISubDomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.TextUtils;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Validated
@RestController
@Tag(name = "域名-域名管理")
@RequestMapping("/website-domain")
@AllArgsConstructor
public class DomainController {
    private ISubDomainService subDomainService;

    @PostMapping("/page")
    @Operation(summary = "分页查询")
    @SaCheckPermission("website-domain.page")
    public Page<SubDomainResponse> page(@Valid @RequestBody QueryWebsiteDomainRequest request) {
        return subDomainService.findPaginated(QueryPageRequest.<SubDomain>fromRequest(request).add(new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                Predicate websitePredicate = criteriaBuilder.equal(root.get("website").get("id"), WebsiteContext.getCurrentWebsiteId());
                if (TextUtils.isBlank(request.getKeyword())) {
                    return websitePredicate;
                }
                Predicate keywordPredicate = criteriaBuilder.like(root.get("fullName"), "%" + request.getKeyword() + "%");
                return criteriaBuilder.and(websitePredicate, keywordPredicate);
            }
        })).map(SubDomainResponse::convertEntity);
    }


    @PostMapping("/query")
    @Operation(summary = "远程查询根据关键字所有归属域名")
    @SaCheckPermission("website-domain.page")
    public List<SubDomainResponse> queryDomainsByKeyword(@Valid @RequestBody QueryDomainsByKeywordRequest request) {
        return subDomainService.queryDomainsByKeyword(request.getKeyword()).stream().map(SubDomainResponse::convertEntity).toList();
    }


    @PostMapping("/queryRelay")
    @Operation(summary = "远程未分配的中继域名")
    @SaCheckPermission("website-domain.page")
    public List<SubDomainResponse> queryRelayDomainsByKeyword(@Valid @RequestBody QueryDomainsByKeywordRequest request) {
        return subDomainService.queryRelayDomainsByKeyword(request.getKeyword()).stream().map(SubDomainResponse::convertEntity).toList();
    }

    @PostMapping("/doEdit")
    @Operation(summary = "新增绑定域名")
    @SaCheckPermission("website-domain.create")
    public void doCreate(@Valid @RequestBody CreateWebsiteSubDomainRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getId(), "绑定的域名不能为空");
        subDomainService.doCreate(request.getIdLongValue());
    }

    @PostMapping("/doDelete")
    @Operation(summary = "删除绑定域名")
    @SaCheckPermission("website-domain.delete")
    public void doDelete(@Valid @RequestBody DeleteRequest request) {
        List<Long> ids = new ArrayList<>();
        try {
            ids = Arrays.stream(request.getIds().split(",")).map(Long::parseLong).collect(Collectors.toList());
        } catch (Exception e) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("IDS参数错: " + request.getIds());
        }
        ClientResponseEnum.PARAMETER_ILLEGAL.notEmpty(ids, "IDS参数为空");
        subDomainService.doDeleteAll(ids);
    }
}
