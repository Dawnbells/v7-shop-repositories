package cn.v7soft.admin.event;

import cn.v7soft.admin.service.IPushPlusNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ServerIpSwitchNotificationListener {

    private final IPushPlusNotificationService pushPlusNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onServerIpSwitchNotification(ServerIpSwitchNotificationEvent event) {
        pushPlusNotificationService.sendServerIpSwitchNotification(event);
    }
}
