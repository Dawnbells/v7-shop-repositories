package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.task.AiAccountTranslateSubTaskType;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
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
    public int estimateSubTaskCredits(AiAccountTranslateSubTask subTask) {
        InvokeMode mode = getProviderType().getInvokeMode();
        if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
            return TokenCostCalculator.estimateCredits(0, TokenCostCalculator.estimateImageTokens(), mode);
        }
        int textTokens = TokenCostCalculator.estimateTextTokens(subTask.getContent());
        return TokenCostCalculator.estimateCredits(textTokens, 0, mode);
    }

    @Override
    public void executeSubTask(AiAccountTranslateSubTask subTask) {
        callback.onSubTaskFailed(subTask, "Gemini Official provider not yet implemented", false, null);
    }
}
