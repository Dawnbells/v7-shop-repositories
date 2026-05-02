package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.dao.enums.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeminiOfficialProvider implements TranslateProvider {

    private volatile TranslateProviderCallback callback;

    @Override
    public AiProvider getProviderType() {
        return AiProvider.GEMINI_OFFICIAL_STANDARD;
    }

    @Override
    public void setCallback(TranslateProviderCallback callback) {
        this.callback = callback;
    }

    @Override
    public void executeSubTask(AiAccountTranslateSubTask subTask) {
        callback.onSubTaskFailed(subTask, "Gemini Official provider not yet implemented", false);
    }
}
