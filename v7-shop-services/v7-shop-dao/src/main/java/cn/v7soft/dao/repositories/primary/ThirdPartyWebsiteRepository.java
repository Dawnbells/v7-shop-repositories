package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
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

    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.lastManualSyncTime = :syncTime WHERE w.id = :id")
    void updateLastManualSyncTime(@Param("id") Long id, @Param("syncTime") LocalDateTime syncTime);

    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.lastCancelCheckTime = :checkTime, w.cancelCheckPageInfo = :pageInfo WHERE w.id = :id")
    void updateCancelCheckProgress(@Param("id") Long id, @Param("checkTime") LocalDateTime checkTime,
                                   @Param("pageInfo") String pageInfo);

    List<ThirdPartyWebsite> findByStatusAndAuthStatusAndWebsiteType(StatusEnum status, ThirdPartyAuthStatusEnum authStatus, WebsiteTypeEnum websiteType);

    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.token = :token, w.tokenExpiresAt = :expiresAt WHERE w.id = :id")
    void updateToken(@Param("id") Long id, @Param("token") String token, @Param("expiresAt") LocalDateTime expiresAt);

    List<ThirdPartyWebsite> findByStatusAndAuthStatusAndWebsiteTypeAndAccessMode(StatusEnum status,
                                                                                ThirdPartyAuthStatusEnum authStatus,
                                                                                WebsiteTypeEnum websiteType,
                                                                                ShopifyAccessMode accessMode);

    /**
     * 更新公开应用的 access token 与 refresh token。
     * refresh token、过期时间、scope 为 null 时保留原值（刷新响应可能不返回这些字段）。
     */
    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.token = :token, w.tokenExpiresAt = :expiresAt, " +
            "w.refreshToken = COALESCE(:refreshToken, w.refreshToken), " +
            "w.refreshTokenExpiresAt = COALESCE(:refreshExpiresAt, w.refreshTokenExpiresAt), " +
            "w.grantedScopes = COALESCE(:scopes, w.grantedScopes), " +
            "w.hasAllOrdersScope = COALESCE(:hasAllOrdersScope, w.hasAllOrdersScope) " +
            "WHERE w.id = :id")
    void updatePublicAppTokens(@Param("id") Long id,
                               @Param("token") String token,
                               @Param("expiresAt") LocalDateTime expiresAt,
                               @Param("refreshToken") String refreshToken,
                               @Param("refreshExpiresAt") LocalDateTime refreshExpiresAt,
                               @Param("scopes") String scopes,
                               @Param("hasAllOrdersScope") Boolean hasAllOrdersScope);

    /**
     * 店铺卸载应用：token 列 NOT NULL + 唯一，写入占位值而非 null
     */
    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.authStatus = :authStatus, w.token = :placeholderToken, " +
            "w.tokenExpiresAt = :now, w.refreshToken = NULL, w.refreshTokenExpiresAt = NULL, " +
            "w.status = :status, w.uninstalledAt = :now WHERE w.id = :id")
    void markDisconnected(@Param("id") Long id,
                          @Param("authStatus") ThirdPartyAuthStatusEnum authStatus,
                          @Param("placeholderToken") String placeholderToken,
                          @Param("status") StatusEnum status,
                          @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.lastReconcileTime = :time WHERE w.id = :id")
    void updateReconcileTime(@Param("id") Long id, @Param("time") LocalDateTime time);

    /**
     * shop/redact：清除店铺凭证，token 写入占位值
     */
    @Modifying
    @Query("UPDATE ThirdPartyWebsite w SET w.token = :placeholderToken, w.tokenExpiresAt = :now, " +
            "w.refreshToken = NULL, w.refreshTokenExpiresAt = NULL, w.clientSecret = NULL WHERE w.id = :id")
    void clearCredentials(@Param("id") Long id,
                          @Param("placeholderToken") String placeholderToken,
                          @Param("now") LocalDateTime now);
}
