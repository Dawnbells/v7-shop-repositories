package cn.v7soft.admin.service;

import java.util.List;

import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.Company;

public interface ICompanyService extends IBaseService<Company> {
    /**
     * 根据域名获取公司信息，使用缓存信息
     *
     * @param domain 一级域名
     * @return 公司信息
     */
    Company identityCached(String domain);

    /**
     * 根据ID获取公司信息，使用缓存信息
     *
     * @param id ID
     * @return 公司信息
     */
    Company companyCached(Long id);

    /**
     * 根据域名获取公司信息，强制刷新
     *
     * @param domain 一级域名
     * @return 公司信息
     */
    Company identity(String domain);

    List<Company> findAll();

    void evictCompanyCache(Long id);

    void evictIdentityCache(String domain);
}
