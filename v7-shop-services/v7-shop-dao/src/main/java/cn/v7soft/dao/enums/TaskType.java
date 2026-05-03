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
}
