package cn.v7soft.admin.task;

/** 翻译子任务的内容类型 */
public enum AiAccountTranslateSubTaskType {
    /** 纯文本（标题/摘要/规格属性名值） */
    TEXT,
    /** HTML 富文本（产品详情） */
    HTML,
    /** 图片（产品主图/规格图/详情内嵌图） */
    IMAGE
}
