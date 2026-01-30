package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Company;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends BaseRepository<Company> {
    /**
     * 根据主域名查询公司
     *
     * @param domain 一级域名
     * @return 公司
     */
    @Query("from Company  where domain=:domain")
    Optional<Company> findByDomain(@Param("domain") String domain);
}
