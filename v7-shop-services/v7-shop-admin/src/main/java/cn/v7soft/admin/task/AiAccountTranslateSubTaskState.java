package cn.v7soft.admin.task;

/** 翻译子任务的状态。PENDING → PROCESSING → COMPLETED/FAILED，失败可 retry 回 PENDING */
public enum AiAccountTranslateSubTaskState {
    PENDING, PROCESSING, COMPLETED, FAILED
}
