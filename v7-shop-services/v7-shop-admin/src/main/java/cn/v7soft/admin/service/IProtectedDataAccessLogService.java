package cn.v7soft.admin.service;

import java.util.List;

/**
 * 受保护客户数据（Shopify 订单收货人信息）访问日志。
 */
public interface IProtectedDataAccessLogService {

    String ACTION_LIST = "LIST";
    String ACTION_EXPORT = "EXPORT";
    String ORDER_TYPE_TEMPORARY = "TEMPORARY";
    String ORDER_TYPE_ORDER = "ORDER";

    /**
     * 单条日志最多保存的订单 ID 数
     */
    int MAX_STORED_IDS = 500;

    /**
     * 记录当前登录人对受保护客户数据的访问。
     *
     * @param action    "LIST" | "EXPORT"
     * @param orderType "TEMPORARY" | "ORDER"
     * @param orderIds  本次返回/导出的 Shopify 订单主键，为空则不记录；最多存 500 个（逗号分隔），orderCount 为实际数量
     *                  写入失败只记日志，不抛出。
     */
    void record(String action, String orderType, List<Long> orderIds);

    /**
     * 同 {@link #record(String, String, List)}，但 orderCount 取 {@code totalCount}（用于只取了部分 ID 的场景，如导出）。
     * totalCount 为空或小于 orderIds 数量时按 orderIds 数量计。
     */
    void record(String action, String orderType, List<Long> orderIds, Long totalCount);
}
