package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.CountThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.resp.CountThirdPartyOrderResponse;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;

import java.util.Optional;

public interface IThirdPartyWebsiteService extends IBaseDataRangeService<ThirdPartyWebsite> {

    /**
     * 根据 Token 获取第三方网站
     *
     * @param token 第三方网站的 Token
     * @return 第三方网站
     */
    Optional<ThirdPartyWebsite> getByToken(String token);

    /**
     * 根据应用 Key 和认证类型获取第三方网站
     *
     * @param appKey   应用 Key
     * @param authType 认证类型
     * @return 第三方网站
     */
    Optional<ThirdPartyWebsite> getByAppKeyAndAuthType(String appKey, ThirdPartyAuthTypeEnum authType);

    /**
     * 统计第三方商城订单数量
     *
     * @param request 请求查询参数
     */
    CountThirdPartyOrderResponse countOrders(CountThirdPartyOrdersRequest request);

    /**
     * 提交订单同步任务
     *
     * @param request 请求
     * @return 结果
     */
    Long submitSyncOrders(SyncThirdPartyOrdersRequest request);

    /**
     * 加载订单
     * @param request 请求
     * @param pageInfo 分页信息
     */
    void loadOrders(SyncThirdPartyOrdersRequest request, String pageInfo);

    ThirdPartyWebsiteDto getThirdPartyWebsiteDtoById(Long id);
}
