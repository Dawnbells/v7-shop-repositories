package cn.v7soft.admin.controller;

import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.EditCompanyRequest;
import cn.v7soft.admin.controller.req.QueryCompanyRequest;
import cn.v7soft.admin.controller.resp.CompanyResponse;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.common.forest.resp.CompanyIdentityResponse;
import cn.v7soft.common.utils.DomainUtils;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.Company;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@Tag(name = "管理系统-公司管理")
@RequestMapping("/company")
public class CompanyController extends BaseController<Company, ICompanyService, CompanyResponse, QueryCompanyRequest, EditCompanyRequest> {

    protected CompanyController(ICompanyService service) {
        super(service);
    }
    @PostMapping("/identity")
    @Operation(summary = "根据域名获取公司信息")
    public CompanyIdentityResponse identity(HttpServletRequest request) {
        String topLevelDomain = DomainUtils.getOriginTopLevelDomain(request);
        Company identity = this.service.identityCached(topLevelDomain);
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(identity, "获取公司信息失败，请联系管理员");
        CompanyIdentityResponse response = CompanyIdentityResponse.builder().build();
        BeanUtil.copyProperties(identity, response);
        return response;
    }


    @GetMapping("/refresh")
    public void refresh() {
        List<Company> allCompanies = service.findAll(); // 或者查你想刷的 ID
        log.debug("刷新公司缓存，数量: {}", allCompanies.size());
        for (Company company : allCompanies) {
            try {
                log.info("刷新公司缓存: id={}, domain={}, imageBaseUrl={}", company.getId(), company.getDomain(), company.getImageBaseUrl());
                service.evictCompanyCache(company.getId());
                service.evictIdentityCache(company.getDomain());
                Company companyCached = service.companyCached(company.getId());
                log.info("刷新公司缓存成功 company: id={}, domain={}, imageBaseUrl={}",
                         companyCached.getId(), companyCached.getDomain(), companyCached.getImageBaseUrl());
                companyCached = service.identityCached(company.getDomain());
                log.info("刷新公司缓存成功 identity: id={}, domain={}, imageBaseUrl={}",
                         companyCached.getId(), companyCached.getDomain(), companyCached.getImageBaseUrl());
            } catch (Exception e) {
                log.warn("刷新公司缓存失败: id={}, domain={}, 原因={}", company.getId(), company.getDomain(), e.getMessage());
            }
        }
    }

    @Override
    protected CompanyResponse convertEntity(Company company) {
        return CompanyResponse.convertEntity(company);
    }

    @Override
    protected Company convertRequest(Company dbEntity, EditCompanyRequest request) {
        return Company.builder().build();
    }

    @Override
    protected String getPermissionPrefix() {
        return "company";
    }

}
