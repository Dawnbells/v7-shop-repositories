package cn.v7soft.admin.event;

import cn.v7soft.admin.service.IPushPlusNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AiTranslateTaskNotificationListener {

    private final IPushPlusNotificationService pushPlusNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiTranslateTaskNotification(AiTranslateTaskNotificationEvent event) {
        pushPlusNotificationService.sendAiTranslateTaskNotification(event);
    }
}
