package cn.v7soft.admin.service;

/**
 * 订单同步模式
 */
public enum SyncMode {
    /**
     * 自动同步：使用 since_id 增量去重，每次拉取后更新 lastSyncTime 和 lastSyncOrderId
     */
    AUTO,
    /**
     * 手动同步：按用户指定时间范围全量拉取，不使用 since_id，不更新自动同步状态
     */
    MANUAL
}
