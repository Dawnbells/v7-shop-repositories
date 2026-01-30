package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.enums.RouterPlatform;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SystemRouterRepository extends BaseRepository<SystemRouter> {
    @Query("""
                    from SystemRouter
                    where
                    parent is null
                    and (:status is null or status=:status)
                    and (:platform is null or platform=:platform)
                    order by sortOrder asc, id desc
            """)
    List<SystemRouter> getAllTopSystemRouters(@Param("status") StatusEnum status, @Param("platform") RouterPlatform platform);

    @Query("""
            select distinct router
            from SystemUser user
            join user.roles role
            join role.systemRouterList router
            where user.id = :userId
            and router.status = 'VALID'
            and router.platform = :platform
            order by router.sortOrder asc, router.id desc
            """)
    List<SystemRouter> getAllSystemRoutersForCurrentUser( @Param("platform") RouterPlatform platform, @Param("userId") Long userId);
}
