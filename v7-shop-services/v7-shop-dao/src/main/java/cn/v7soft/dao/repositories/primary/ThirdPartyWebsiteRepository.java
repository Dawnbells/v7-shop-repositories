package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThirdPartyWebsiteRepository extends BaseRepository<ThirdPartyWebsite> {

    /**
     * 根据 Token 查询第三方网站
     *
     * @param token 第三方网站的 Token
     * @return 第三方网站
     */
    Optional<ThirdPartyWebsite> findByToken(String token);

    /**
     * 根据应用 Key 和认证类型查询第三方网站
     *
     * @param appKey 应用 Key
     * @param authType 认证类型
     * @return 第三方网站
     */
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
}
