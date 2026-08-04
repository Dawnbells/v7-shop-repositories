package cn.v7soft.admin.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cn.v7soft.admin.service.IPushPlusNotificationService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServerHealthNotificationListener {

    private final IPushPlusNotificationService pushPlusNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onServerHealthNotification(ServerHealthNotificationEvent event) {
        pushPlusNotificationService.sendServerHealthNotification(event);
    }
}
