package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThirdPartyWebsiteRepository extends BaseRepository<ThirdPartyWebsite> {

    Optional<ThirdPartyWebsite> findByToken(String token);

    @Query("""
            SELECT tpw 
            FROM ThirdPartyWebsite tpw 
            WHERE tpw.appKey = :appKey 
            AND tpw.authType = :authType
           """)
    Optional<ThirdPartyWebsite> findByAppKeyAndAuthType(
            @Param("appKey") String appKey,
            @Param("authType") ThirdPartyAuthTypeEnum authType
    );

    List<ThirdPartyWebsite> findBySyncEnabledTrueAndAuthStatus(ThirdPartyAuthStatusEnum authStatus);
}
