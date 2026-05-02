package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.dao.enums.AiProvider;

public interface TranslateProvider {

    AiProvider getProviderType();

    void setCallback(TranslateProviderCallback callback);

    void executeSubTask(AiAccountTranslateSubTask subTask);

    default void reclaimExpiredAssignments() {
    }
}
