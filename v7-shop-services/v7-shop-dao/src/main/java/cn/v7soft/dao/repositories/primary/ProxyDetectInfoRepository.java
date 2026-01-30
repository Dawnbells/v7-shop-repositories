package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ProxyDetectInfo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProxyDetectInfoRepository extends BaseRepository<ProxyDetectInfo> {
    @Query("""
            from ProxyDetectInfo
            where pdKey = :pdKey
            and pdVal=:pdVal
            and status = 'VALID'
            order by id desc
            limit 1
            """)
    Optional<ProxyDetectInfo> findByPdValAndPdKey(@Param("pdKey") String pdKey, @Param("pdVal") String pdVal);
}
