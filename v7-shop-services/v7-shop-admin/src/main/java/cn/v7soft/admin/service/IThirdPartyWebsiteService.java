package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.CountThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.resp.CountThirdPartyOrderResponse;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;

import java.util.List;
import java.util.Optional;

public interface IThirdPartyWebsiteService extends IBaseDataRangeService<ThirdPartyWebsite> {

    Optional<ThirdPartyWebsite> getByToken(String token);

    Optional<ThirdPartyWebsite> getByAppKeyAndAuthType(String appKey, ThirdPartyAuthTypeEnum authType);

    CountThirdPartyOrderResponse countOrders(CountThirdPartyOrdersRequest request);

    Long submitSyncOrders(SyncThirdPartyOrdersRequest request);

    /**
     * 拉取订单并写入临时表，返回下一页 page_info（null 表示没有更多页）
     * @param updateSyncTime 是否更新商城的 lastSyncTime（自动同步传 true，手动同步传 false）
     */
    String loadOrders(SyncThirdPartyOrdersRequest request, String pageInfo, boolean updateSyncTime);

    ThirdPartyWebsiteDto getThirdPartyWebsiteDtoById(Long id);

    /**
     * 验证商城 Token 有效性，更新 authStatus 和 authMessage
     */
    void verifyAndUpdateAuthStatus(ThirdPartyWebsite website);

    /**
     * 查询所有启用自动同步且已认证的商城
     */
    List<ThirdPartyWebsite> findSyncEnabledWebsites();

    /**
     * 更新商城的上次手动同步时间
     */
    void updateLastManualSyncTime(Long websiteId);
}
