package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.CountThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.resp.CountThirdPartyOrderResponse;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ShopifyCancelCheckResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;

import java.time.LocalDateTime;

/**
 * Shopify 订单同步，与 Shopline 的实现相互独立
 */
public interface IShopifyOrderSyncService {

    CountThirdPartyOrderResponse countOrders(ThirdPartyWebsite website, CountThirdPartyOrdersRequest request);

    /**
     * 拉取订单并写入临时表，返回下一页 page_info（null 表示没有更多页）
     * @param syncMode AUTO 时使用 since_id 去重并更新同步游标；MANUAL 时按时间范围全量拉取
     */
    ShoplineOrderLoadResult loadOrders(ThirdPartyWebsiteDto website, SyncThirdPartyOrdersRequest request, String pageInfo, SyncMode syncMode);

    /**
     * 按 updated_at_min 拉取最近更新过的订单，把已同步且客户已取消的订单标记为客户取消。
     * @param pageInfo 上次未完成的续页游标，首次检查传 null
     * @return 最大订单更新时间（东八区，无订单时保留起点）及下一页游标
     */
    ShopifyCancelCheckResult checkCancelledOrders(ThirdPartyWebsiteDto website, LocalDateTime updatedAtMin, String pageInfo);

    /**
     * 用 Client ID/Secret 换取 token 并验证，更新 token、authStatus 和 authMessage。
     * 换取 token 失败时抛出异常（token 为必填列，无法落库）
     */
    void verifyAndUpdateAuthStatus(ThirdPartyWebsite website);
}
