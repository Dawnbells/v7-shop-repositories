package cn.v7soft.dao.enums;

public enum TaskType {
    /**
     * 订单下载任务
     */
    ORDER_DOWNLOAD,
    /**
     * 订单上传任务
     */
    ORDER_UPLOAD,
    /**
     * 第三方商城订单同步任务
     */
    THIRD_PARTY_ORDER_SYNC,
    /**
     * 商品 AI 翻译任务（指定 AI 账号）
     */
    PRODUCT_AI_TRANSLATE,
    /**
     * 地址库导入任务
     */
    ADDRESS_IMPORT,
    /**
     * 商品 AI 实时翻译任务（编辑页 SSE/单图，单次请求即结算，不进入异步调度）
     */
    PRODUCT_AI_REALTIME_TRANSLATE,
    /**
     * 复制员工名下全部 SPU 给指定员工（批量深拷贝分享）
     */
    EMPLOYEE_SPU_COPY,
}
