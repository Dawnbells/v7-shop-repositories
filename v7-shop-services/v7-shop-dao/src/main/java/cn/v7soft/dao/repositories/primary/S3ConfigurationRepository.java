package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.S3Configuration;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface S3ConfigurationRepository extends BaseRepository<S3Configuration> {

    @Query("""
            from S3Configuration where companyId=:tenant
            """)
    Optional<S3Configuration> findByTenant(@Param("tenant") Long tenant);
}
