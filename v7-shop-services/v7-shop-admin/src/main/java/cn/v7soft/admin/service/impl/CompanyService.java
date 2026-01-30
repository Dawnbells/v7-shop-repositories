package cn.v7soft.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.common.forest.RemoteCompanyService;
import cn.v7soft.common.forest.req.CompanyIdentityRequest;
import cn.v7soft.common.forest.resp.CompanyIdentityResponse;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.result.CommonResult;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.repositories.primary.CompanyRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class CompanyService extends BaseService<Company, CompanyRepository> implements ICompanyService {
    private final static String COMPANY_CACHE_NAME = "company_identity";
    private final static String COMPANY_CACHE_BY_ID_NAME = "company_cache";
    private final RemoteCompanyService remoteCompanyService;

    public CompanyService(RemoteCompanyService remoteCompanyService, CompanyRepository companyRepository) {
        super(companyRepository);
        this.remoteCompanyService = remoteCompanyService;
    }

    @Nullable
    @Override
    @Transactional
    @Cacheable(value = COMPANY_CACHE_NAME, key = "#domain", cacheManager = "cacheManager", unless = "#result == null")
    public Company identityCached(String domain) {
//        Company company = identity(domain);
//        if (company != null) {
//            return company;
//        }
        // 如果数据库中没有则接口重试
        return this.repository.findByDomain(domain).orElse(null);
        //.orElseGet(() -> identity(domain));
    }

    @Nullable
    @Override
    @Transactional
    @Cacheable(value = COMPANY_CACHE_BY_ID_NAME, key = "#id", cacheManager = "cacheManager", unless = "#result == null")
    public Company companyCached(Long id) {
        // 如果数据库中没有则接口重试
        return this.repository.findById(id).orElse(null);
    }

    @Override
    @CacheEvict(value = COMPANY_CACHE_NAME, key = "#domain", cacheManager = "cacheManager")
    public void evictIdentityCache(String domain) {
        log.info("已清除公司域名缓存: {}", domain);
    }

    @Override
    @CacheEvict(value = COMPANY_CACHE_BY_ID_NAME, key = "#id", cacheManager = "cacheManager")
    public void evictCompanyCache(Long id) {
        log.info("已清除公司ID缓存: {}", id);
    }

    @Override
    public List<Company> findAll() {
        return this.repository.findAll();
    }

    /**
     * 请求接口更新公司信息
     *
     * @param domain 域名
     * @return 公司信息
     */
    @Override
    @Transactional
    public @Nullable Company identity(String domain) {
        log.debug("forest request company identity info for " + domain);
        try {
            CommonResult<CompanyIdentityResponse> result = remoteCompanyService.identity(
                    CompanyIdentityRequest.builder().domain(domain).build()
            );
            if (result == null || result.getData() == null || !Objects.equals(result.getCode(), ClientResponseEnum.SUCCESS.getCode())) {
                log.warn("forest request company identity info error!!!");
                return null;
            }
            CompanyIdentityResponse companyIdentityResponse = result.getData();
            log.debug("forest request company identity info success >> " + companyIdentityResponse.getName());
            Company company = repository.findByDomain(domain).orElse(Company.builder().build());
            BeanUtil.copyProperties(companyIdentityResponse, company);
            company.setId(companyIdentityResponse.getId());
            repository.saveAndFlush(company);
            return company;
        } catch (Exception e) {
            log.warn("请求远程公司接口失败: " + e.getMessage());
            return repository.findByDomain(domain).orElse(null);
        }
    }
}
