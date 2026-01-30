package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.DynamicConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DynamicConfigRepository extends BaseRepository<DynamicConfig> {

    /**
     * 查找公司级别配置（departmentId 为 null）
     */
    @Query("FROM DynamicConfig WHERE configName = :configName AND departmentId IS NULL AND companyId = :companyId")
    Optional<DynamicConfig> findCompanyConfig(@Param("configName") String configName, @Param("companyId") Long companyId);

    /**
     * 查找部门级别配置
     */
    @Query("FROM DynamicConfig WHERE configName = :configName AND departmentId = :departmentId AND companyId = :companyId")
    Optional<DynamicConfig> findDepartmentConfig(@Param("configName") String configName, @Param("departmentId") Long departmentId, @Param("companyId") Long companyId);
}

