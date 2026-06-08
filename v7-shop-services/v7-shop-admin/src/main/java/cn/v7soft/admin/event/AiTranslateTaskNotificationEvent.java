package cn.v7soft.admin.event;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public class AiTranslateTaskNotificationEvent {

    private final boolean retry;
    private final Long companyId;
    private final Long originalTaskId;
    private final Long taskId;
    private final String operatorName;
    private final String productTitle;
    private final String countryName;
    private final String languageName;
    private final String aiAccountName;
    private final LocalDateTime createdAt;

    private AiTranslateTaskNotificationEvent(boolean retry,
                                             Long companyId,
                                             Long originalTaskId,
                                             Long taskId,
                                             String operatorName,
                                             String productTitle,
                                             String countryName,
                                             String languageName,
                                             String aiAccountName,
                                             LocalDateTime createdAt) {
        this.retry = retry;
        this.companyId = companyId;
        this.originalTaskId = originalTaskId;
        this.taskId = taskId;
        this.operatorName = operatorName;
        this.productTitle = productTitle;
        this.countryName = countryName;
        this.languageName = languageName;
        this.aiAccountName = aiAccountName;
        this.createdAt = createdAt;
    }

    public static AiTranslateTaskNotificationEvent submitted(Long companyId,
                                                             Long taskId,
                                                             String operatorName,
                                                             String productTitle,
                                                             String countryName,
                                                             String languageName,
                                                             String aiAccountName,
                                                             LocalDateTime createdAt) {
        return new AiTranslateTaskNotificationEvent(false, companyId, null, taskId, operatorName,
                productTitle, countryName, languageName, aiAccountName, createdAt);
    }

    public static AiTranslateTaskNotificationEvent retry(Long companyId,
                                                         Long originalTaskId,
                                                         Long taskId,
                                                         String operatorName,
                                                         String productTitle,
                                                         String countryName,
                                                         String languageName,
                                                         String aiAccountName,
                                                         LocalDateTime createdAt) {
        return new AiTranslateTaskNotificationEvent(true, companyId, originalTaskId, taskId, operatorName,
                productTitle, countryName, languageName, aiAccountName, createdAt);
    }
}
