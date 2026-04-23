package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ThirdPartyWebsiteRepository extends BaseRepository<ThirdPartyWebsite> {

    Optional<ThirdPartyWebsite> findByToken(String token);

    Optional<ThirdPartyWebsite> findByHandle(String handle);

    List<ThirdPartyWebsite> findByStatusAndAuthStatus(StatusEnum status, ThirdPartyAuthStatusEnum authStatus);

    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.lastSyncTime = :syncTime, w.lastSyncHasNewOrders = :hasNew, " +
            "w.lastSyncOrderTime = COALESCE(:orderTime, w.lastSyncOrderTime), " +
            "w.lastSyncOrderId = COALESCE(:orderId, w.lastSyncOrderId) " +
            "WHERE w.id = :id")
    void updateSyncInfo(@Param("id") Long id,
                        @Param("syncTime") LocalDateTime syncTime,
                        @Param("hasNew") Boolean hasNew,
                        @Param("orderTime") LocalDateTime orderTime,
                        @Param("orderId") String orderId);
}
